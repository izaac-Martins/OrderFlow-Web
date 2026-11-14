package com.example.cheirocafe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.RoundingMode

sealed interface ResultadoSincronizacaoCatalogo {
    data class SucessoRemoto(val produtos: List<Produto>) : ResultadoSincronizacaoCatalogo
    data class FallbackCacheLocal(val produtos: List<Produto>, val motivoOffline: String) : ResultadoSincronizacaoCatalogo
    data class ErroSemCache(val mensagem: String) : ResultadoSincronizacaoCatalogo
}

class ProdutoRepository(
    private val produtoDao: ProdutoDao,
    private val apiService: ApiService = RetrofitClient.instance
) {

    /**
     * Single Source of Truth (Offline-First): observa continuamente o catálogo persistido no Room.
     */
    fun observarCatalogoLocal(): Flow<List<Produto>> {
        return produtoDao.observarTodos().map { entidades ->
            entidades.map { it.toDomain() }
        }
    }

    /**
     * Sincroniza o catálogo em background via Retrofit (GET /produtos + GET /produtos/adicionais-globais),
     * atualiza o banco local Room e retorna fallback transparente para o cache caso não haja rede.
     */
    suspend fun sincronizarCatalogo(): ResultadoSincronizacaoCatalogo = withContext(Dispatchers.IO) {
        try {
            val produtosRemotos = apiService.getProdutos()
            val adicionaisGlobais = try {
                apiService.getAdicionaisGlobais()
            } catch (e: Exception) {
                emptyList()
            }

            val produtosNormalizados = produtosRemotos.mapIndexed { index, prod ->
                val escopoCategoria = "CATEGORIA_${prod.categoria.name}"
                val globaisDaCategoria = adicionaisGlobais.filter {
                    it.vinculo.equals(escopoCategoria, ignoreCase = true)
                }

                // Mescla adicionais específicos do produto (PRODUTO) com os globais da categoria (CATEGORIA_*)
                val mapaAdicionais = linkedMapOf<String, Adicional>()
                (prod.adicionais + globaisDaCategoria).forEach { adic ->
                    val chave = "${adic.nome.trim().lowercase()}_${adic.vinculo.uppercase()}"
                    if (!mapaAdicionais.containsKey(chave)) {
                        mapaAdicionais[chave] = adic.copy(
                            preco = adic.preco.setScale(2, RoundingMode.HALF_UP)
                        )
                    }
                }

                val variantesNormalizadas = prod.variantes.map { v ->
                    v.copy(preco = v.preco.setScale(2, RoundingMode.HALF_UP))
                }

                prod.copy(
                    id = prod.id ?: (index + 1L),
                    preco = prod.preco.setScale(2, RoundingMode.HALF_UP),
                    variantes = variantesNormalizadas,
                    adicionais = mapaAdicionais.values.toList()
                )
            }

            val entidadesParaSalvar = produtosNormalizados.mapIndexed { index, produto ->
                ProdutoCatalogoEntity.fromDomain(produto, fallbackId = (index + 1L))
            }

            produtoDao.substituirCatalogo(entidadesParaSalvar)
            ResultadoSincronizacaoCatalogo.SucessoRemoto(produtosNormalizados)
        } catch (e: Exception) {
            val cacheLocal = produtoDao.listarTodosSnapshot().map { it.toDomain() }
            if (cacheLocal.isNotEmpty()) {
                ResultadoSincronizacaoCatalogo.FallbackCacheLocal(
                    produtos = cacheLocal,
                    motivoOffline = "Modo Offline-First ativo: exibindo cardápio salvo no dispositivo."
                )
            } else {
                ResultadoSincronizacaoCatalogo.ErroSemCache(
                    mensagem = "Sem conexão com o servidor e nenhum cardápio em cache local."
                )
            }
        }
    }
}
