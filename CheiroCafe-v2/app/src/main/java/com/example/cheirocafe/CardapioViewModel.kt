package com.example.cheirocafe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer

sealed interface ModoExibicaoCardapio {
    data class Subcategorias(
        val titulo: String,
        val itens: List<SubcategoriaItem>
    ) : ModoExibicaoCardapio

    data class GradeProdutos(
        val titulo: String,
        val produtos: List<Produto>
    ) : ModoExibicaoCardapio
}

data class CardapioUiState(
    val isLoading: Boolean = false,
    val todosProdutos: List<Produto> = emptyList(),
    val categoriaSelecionada: CategoriaProduto = CategoriaProduto.BEBIDAS,
    val subcategoriasDisponiveis: List<String> = emptyList(),
    val subcategoriaSelecionada: String? = null,
    val termoBusca: String = "",
    val modoExibicao: ModoExibicaoCardapio = ModoExibicaoCardapio.GradeProdutos(
        titulo = "Bebidas & Cafés",
        produtos = emptyList()
    ),
    val resumoPainel: ResumoPainel? = null,
    val modoOfflineAtivo: Boolean = false,
    val erroMensagem: String? = null
)

class CardapioViewModel(
    private val produtoRepository: ProdutoRepository,
    private val pedidoRepository: PedidoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardapioUiState())
    val uiState: StateFlow<CardapioUiState> = _uiState.asStateFlow()

    init {
        observarCatalogoLocalSingleSourceOfTruth()
        observarResumoPainel()
        carregarProdutos()
    }

    private fun observarCatalogoLocalSingleSourceOfTruth() {
        viewModelScope.launch {
            produtoRepository.observarCatalogoLocal().collect { produtosLocais ->
                _uiState.update { estadoAtual ->
                    val subcats = extrairSubcategoriasUnicas(
                        produtos = produtosLocais,
                        categoria = estadoAtual.categoriaSelecionada
                    )
                    val subcatValida = estadoAtual.subcategoriaSelecionada?.takeIf { sel ->
                        subcats.any { it.equals(sel, ignoreCase = true) }
                    }
                    val novoEstado = estadoAtual.copy(
                        todosProdutos = produtosLocais,
                        subcategoriasDisponiveis = subcats,
                        subcategoriaSelecionada = subcatValida
                    )
                    novoEstado.copy(modoExibicao = calcularModoExibicao(novoEstado))
                }
            }
        }
    }

    private fun observarResumoPainel() {
        viewModelScope.launch {
            pedidoRepository.obterResumoPainel().collect { resumo ->
                _uiState.update { it.copy(resumoPainel = resumo) }
            }
        }
    }

    fun carregarProdutos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, erroMensagem = null) }

            pedidoRepository.sincronizarPedidosPendentesOffline()

            when (val resultado = produtoRepository.sincronizarCatalogo()) {
                is ResultadoSincronizacaoCatalogo.SucessoRemoto -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            modoOfflineAtivo = false,
                            erroMensagem = null
                        )
                    }
                }
                is ResultadoSincronizacaoCatalogo.FallbackCacheLocal -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            modoOfflineAtivo = true,
                            erroMensagem = resultado.motivoOffline
                        )
                    }
                }
                is ResultadoSincronizacaoCatalogo.ErroSemCache -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            modoOfflineAtivo = true,
                            erroMensagem = resultado.mensagem
                        )
                    }
                }
            }
        }
    }

    fun selecionarCategoria(categoria: CategoriaProduto) {
        _uiState.update { estadoAtual ->
            val subcats = extrairSubcategoriasUnicas(
                produtos = estadoAtual.todosProdutos,
                categoria = categoria
            )
            val novoEstado = estadoAtual.copy(
                categoriaSelecionada = categoria,
                subcategoriasDisponiveis = subcats,
                subcategoriaSelecionada = null
            )
            novoEstado.copy(modoExibicao = calcularModoExibicao(novoEstado))
        }
    }

    fun selecionarSubcategoria(subcategoria: String?) {
        _uiState.update { estadoAtual ->
            val novaSubcat = if (estadoAtual.subcategoriaSelecionada.equals(subcategoria, ignoreCase = true)) {
                null
            } else {
                subcategoria
            }
            val novoEstado = estadoAtual.copy(subcategoriaSelecionada = novaSubcat)
            novoEstado.copy(modoExibicao = calcularModoExibicao(novoEstado))
        }
    }

    fun atualizarTermoBusca(termo: String) {
        val termoLimpo = termo.trim()
        _uiState.update { estadoAtual ->
            if (estadoAtual.termoBusca == termoLimpo) return@update estadoAtual
            val novoEstado = estadoAtual.copy(termoBusca = termoLimpo)
            novoEstado.copy(modoExibicao = calcularModoExibicao(novoEstado))
        }
    }

    suspend fun obterNumeroMesaAtiva(): Int {
        return pedidoRepository.obterUltimoItemRascunho()?.numeroMesa ?: 1
    }

    private fun calcularModoExibicao(estado: CardapioUiState): ModoExibicaoCardapio {
        if (estado.termoBusca.isNotBlank()) {
            val queryNormalizada = normalizarTextoBusca(estado.termoBusca)
            val filtradosGlobais = estado.todosProdutos.filter { prod ->
                normalizarTextoBusca(prod.nome).contains(queryNormalizada) ||
                    normalizarTextoBusca(prod.descricao).contains(queryNormalizada) ||
                    normalizarTextoBusca(prod.subcategoria).contains(queryNormalizada) ||
                    normalizarTextoBusca(prod.categoria.name).contains(queryNormalizada)
            }
            return ModoExibicaoCardapio.GradeProdutos(
                titulo = "Resultados da pesquisa (${filtradosGlobais.size})",
                produtos = filtradosGlobais
            )
        }

        val daCategoria = estado.todosProdutos.filter {
            it.categoria == estado.categoriaSelecionada
        }

        val filtradosSubcat = if (estado.subcategoriaSelecionada.isNullOrBlank()) {
            daCategoria
        } else {
            daCategoria.filter { prod ->
                obterSubcategoriaEfetiva(prod).equals(estado.subcategoriaSelecionada, ignoreCase = true)
            }
        }

        val tituloBase = when (estado.categoriaSelecionada) {
            CategoriaProduto.BEBIDAS -> "Bebidas & Cafés"
            CategoriaProduto.DOCES -> "Doces"
            CategoriaProduto.SALGADOS -> "Salgados"
        }

        return ModoExibicaoCardapio.GradeProdutos(
            titulo = tituloBase,
            produtos = filtradosSubcat
        )
    }

    private fun normalizarTextoBusca(texto: String?): String {
        if (texto.isNullOrBlank()) return ""
        val decomposto = Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
        return REGEX_ACENTOS.replace(decomposto, "")
    }

    private fun extrairSubcategoriasUnicas(
        produtos: List<Produto>,
        categoria: CategoriaProduto
    ): List<String> {
        return produtos
            .asSequence()
            .filter { it.categoria == categoria }
            .mapNotNull { obterSubcategoriaEfetiva(it) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.uppercase() }
            .toList()
    }

    private fun obterSubcategoriaEfetiva(produto: Produto): String? {
        val subDireta = produto.subcategoria?.trim()?.takeIf { it.isNotEmpty() }
        if (subDireta != null) return subDireta

        // Compatibilidade com produtos antigos em que a tag de subcategoria foi gravada na descrição
        val desc = produto.descricao?.trim()?.uppercase()
        return if (desc in TAGS_SUBCATEGORIA_LEGADO) desc else null
    }

    companion object {
        private val REGEX_ACENTOS = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        private val TAGS_SUBCATEGORIA_LEGADO = setOf(
            "TRADICIONAL",
            "ESPECIAL",
            "SUCOS_IOGURTES",
            "REFRIGERANTES"
        )
    }

    class Factory(
        private val produtoRepository: ProdutoRepository,
        private val pedidoRepository: PedidoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CardapioViewModel(produtoRepository, pedidoRepository) as T
        }
    }
}
