package com.example.cheirocafe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val termoBusca: String = "",
    val modoExibicao: ModoExibicaoCardapio = ModoExibicaoCardapio.Subcategorias(
        titulo = "Bebidas & Cafés",
        itens = subcategoriasPadrao()
    ),
    val resumoPainel: ResumoPainel? = null,
    val modoOfflineAtivo: Boolean = false,
    val erroMensagem: String? = null
) {
    companion object {
        fun subcategoriasPadrao(): List<SubcategoriaItem> = listOf(
            SubcategoriaItem("Cafés Tradicionais", R.drawable.cafeprensa),
            SubcategoriaItem("Cafés Especiais", R.drawable.cafecremoso),
            SubcategoriaItem("Sucos Naturais", R.drawable.sucomanga),
            SubcategoriaItem("Refrescos", R.drawable.refrilata)
        )
    }
}

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

    /**
     * Observa continuamente a tabela local do Room (Single Source of Truth).
     * Qualquer atualização vinda da API reflete reativamente na UI.
     */
    private fun observarCatalogoLocalSingleSourceOfTruth() {
        viewModelScope.launch {
            produtoRepository.observarCatalogoLocal().collect { produtosLocais ->
                _uiState.update { estadoAtual ->
                    val novoEstado = estadoAtual.copy(todosProdutos = produtosLocais)
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

    /**
     * Dispara a sincronização em background com o servidor Spring Boot (produtos + fila de pedidos offline),
     * mantendo fallback transparente para o Room caso o Wi-Fi esteja indisponível.
     */
    fun carregarProdutos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, erroMensagem = null) }

            // Aproveita a janela de rede para enviar comandas retidas offline (se houver)
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
            val novoEstado = estadoAtual.copy(categoriaSelecionada = categoria)
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

    fun filtrarProdutosPorSubcategoria(nomeExibicao: String): List<Produto> {
        val tagSubcategoria = mapearSubcategoriaParaTag(nomeExibicao)
        return _uiState.value.todosProdutos.filter { produto ->
            produto.categoria == CategoriaProduto.BEBIDAS &&
                (produto.subcategoria.equals(tagSubcategoria, ignoreCase = true) ||
                    produto.descricao.equals(tagSubcategoria, ignoreCase = true))
        }
    }

    suspend fun obterNumeroMesaAtiva(): Int {
        return pedidoRepository.obterUltimoItemRascunho()?.numeroMesa ?: 1
    }

    private fun calcularModoExibicao(estado: CardapioUiState): ModoExibicaoCardapio {
        if (estado.termoBusca.isNotEmpty()) {
            val filtrados = estado.todosProdutos.filter {
                it.nome.contains(estado.termoBusca, ignoreCase = true) ||
                    (it.descricao?.contains(estado.termoBusca, ignoreCase = true) == true)
            }
            return ModoExibicaoCardapio.GradeProdutos(
                titulo = "Resultados da pesquisa (${filtrados.size})",
                produtos = filtrados
            )
        }

        return when (estado.categoriaSelecionada) {
            CategoriaProduto.BEBIDAS -> ModoExibicaoCardapio.Subcategorias(
                titulo = "Bebidas & Cafés",
                itens = CardapioUiState.subcategoriasPadrao()
            )
            CategoriaProduto.DOCES -> ModoExibicaoCardapio.GradeProdutos(
                titulo = "Doces",
                produtos = estado.todosProdutos.filter { it.categoria == CategoriaProduto.DOCES }
            )
            CategoriaProduto.SALGADOS -> ModoExibicaoCardapio.GradeProdutos(
                titulo = "Salgados",
                produtos = estado.todosProdutos.filter { it.categoria == CategoriaProduto.SALGADOS }
            )
        }
    }

    private fun mapearSubcategoriaParaTag(nomeExibicao: String): String {
        return when {
            nomeExibicao.contains("Tradicionais", ignoreCase = true) -> "TRADICIONAL"
            nomeExibicao.contains("Especiais", ignoreCase = true) -> "ESPECIAL"
            nomeExibicao.contains("Sucos", ignoreCase = true) -> "SUCOS_IOGURTES"
            nomeExibicao.contains("Refrescos", ignoreCase = true) -> "REFRIGERANTES"
            else -> nomeExibicao.uppercase()
        }
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
