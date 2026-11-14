package com.example.cheirocafe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

data class DetalheUiState(
    val tituloTela: String = "Detalhes",
    val listaProdutos: List<Produto> = emptyList(),
    val produtoSelecionado: Produto? = null,
    val varianteSelecionada: Variante? = null,
    val adicionaisSelecionados: Set<Adicional> = emptySet(),
    val quantidade: Int = 1,
    val precoTotalCalculado: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
)

sealed interface DetalheEvento {
    data class PedidoSalvoComSucesso(val nomeProduto: String, val numeroMesa: Int) : DetalheEvento
    data class MensagemErro(val mensagem: String) : DetalheEvento
}

class DetalheViewModel(
    private val pedidoRepository: PedidoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalheUiState())
    val uiState: StateFlow<DetalheUiState> = _uiState.asStateFlow()

    private val _eventos = MutableSharedFlow<DetalheEvento>()
    val eventos: SharedFlow<DetalheEvento> = _eventos.asSharedFlow()

    private var inicializado = false

    fun inicializarDados(titulo: String, produtos: List<Produto>) {
        if (inicializado) return
        inicializado = true

        val primeiroProduto = produtos.firstOrNull()
        val primeiraVariante = primeiroProduto?.variantes?.firstOrNull()
        val precoInicial = calcularPreco(
            produto = primeiroProduto,
            variante = primeiraVariante,
            adicionais = emptySet(),
            quantidade = 1
        )

        _uiState.value = DetalheUiState(
            tituloTela = titulo,
            listaProdutos = produtos,
            produtoSelecionado = primeiroProduto,
            varianteSelecionada = primeiraVariante,
            adicionaisSelecionados = emptySet(),
            quantidade = 1,
            precoTotalCalculado = precoInicial
        )
    }

    fun selecionarProduto(produto: Produto) {
        _uiState.update { estadoAtual ->
            if (estadoAtual.produtoSelecionado?.id == produto.id &&
                estadoAtual.produtoSelecionado?.nome == produto.nome
            ) {
                return@update estadoAtual
            }
            val primeiraVariante = produto.variantes.firstOrNull()
            val novoTotal = calcularPreco(
                produto = produto,
                variante = primeiraVariante,
                adicionais = emptySet(),
                quantidade = estadoAtual.quantidade
            )
            estadoAtual.copy(
                produtoSelecionado = produto,
                varianteSelecionada = primeiraVariante,
                adicionaisSelecionados = emptySet(),
                precoTotalCalculado = novoTotal
            )
        }
    }

    fun selecionarVariante(variante: Variante) {
        _uiState.update { estadoAtual ->
            val novoTotal = calcularPreco(
                produto = estadoAtual.produtoSelecionado,
                variante = variante,
                adicionais = estadoAtual.adicionaisSelecionados,
                quantidade = estadoAtual.quantidade
            )
            estadoAtual.copy(
                varianteSelecionada = variante,
                precoTotalCalculado = novoTotal
            )
        }
    }

    fun alternarAdicional(adicional: Adicional, selecionado: Boolean) {
        _uiState.update { estadoAtual ->
            val novosAdicionais = LinkedHashSet(estadoAtual.adicionaisSelecionados)
            if (selecionado) {
                novosAdicionais.add(adicional)
            } else {
                novosAdicionais.remove(adicional)
            }
            val novoTotal = calcularPreco(
                produto = estadoAtual.produtoSelecionado,
                variante = estadoAtual.varianteSelecionada,
                adicionais = novosAdicionais,
                quantidade = estadoAtual.quantidade
            )
            estadoAtual.copy(
                adicionaisSelecionados = novosAdicionais,
                precoTotalCalculado = novoTotal
            )
        }
    }

    fun incrementarQuantidade() {
        _uiState.update { estadoAtual ->
            val novaQtd = estadoAtual.quantidade + 1
            val novoTotal = calcularPreco(
                produto = estadoAtual.produtoSelecionado,
                variante = estadoAtual.varianteSelecionada,
                adicionais = estadoAtual.adicionaisSelecionados,
                quantidade = novaQtd
            )
            estadoAtual.copy(
                quantidade = novaQtd,
                precoTotalCalculado = novoTotal
            )
        }
    }

    fun decrementarQuantidade() {
        _uiState.update { estadoAtual ->
            if (estadoAtual.quantidade <= 1) return@update estadoAtual
            val novaQtd = estadoAtual.quantidade - 1
            val novoTotal = calcularPreco(
                produto = estadoAtual.produtoSelecionado,
                variante = estadoAtual.varianteSelecionada,
                adicionais = estadoAtual.adicionaisSelecionados,
                quantidade = novaQtd
            )
            estadoAtual.copy(
                quantidade = novaQtd,
                precoTotalCalculado = novoTotal
            )
        }
    }

    fun salvarPedido(pagador: String, numeroMesa: Int) {
        val estado = _uiState.value
        val produto = estado.produtoSelecionado
        if (produto == null) {
            viewModelScope.launch {
                _eventos.emit(DetalheEvento.MensagemErro("Por favor, selecione um produto primeiro!"))
            }
            return
        }

        val tamanhoTexto = estado.varianteSelecionada?.let { v ->
            if (produto.categoria == CategoriaProduto.BEBIDAS && !v.nome.contains("ml", ignoreCase = true)) {
                "${v.nome}ml"
            } else {
                v.nome
            }
        } ?: "Padrão"

        val adicionaisTexto = if (estado.adicionaisSelecionados.isEmpty()) {
            "Nenhum"
        } else {
            estado.adicionaisSelecionados.joinToString(", ") { it.nome }
        }

        val mesaValida = if (numeroMesa > 0) numeroMesa else 1

        val novoItem = PedidoEntity(
            produtoId = produto.id ?: 0L,
            nomeProduto = produto.nome,
            tamanhoEscolhido = tamanhoTexto,
            tipoGraoEscolhido = produto.subcategoria ?: produto.categoria.name,
            adicionaisEscolhidos = adicionaisTexto,
            quantidade = estado.quantidade,
            precoTotalItem = estado.precoTotalCalculado,
            pagadorItem = pagador,
            numeroMesa = mesaValida,
            statusPedido = "RASCUNHO"
        )

        viewModelScope.launch {
            pedidoRepository.inserirItem(novoItem)
            _eventos.emit(DetalheEvento.PedidoSalvoComSucesso(produto.nome, mesaValida))
        }
    }

    private fun calcularPreco(
        produto: Produto?,
        variante: Variante?,
        adicionais: Set<Adicional>,
        quantidade: Int
    ): BigDecimal {
        if (produto == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        val precoBase: BigDecimal = variante?.preco ?: produto.preco
        val somaAdicionais: BigDecimal = adicionais.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item.preco)
        }
        return precoBase.add(somaAdicionais)
            .multiply(BigDecimal.valueOf(quantidade.toLong()))
            .setScale(2, RoundingMode.HALF_UP)
    }

    class Factory(
        private val pedidoRepository: PedidoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DetalheViewModel(pedidoRepository) as T
        }
    }
}
