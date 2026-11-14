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

data class CarrinhoUiState(
    val numeroMesa: Int = 1,
    val itens: List<PedidoEntity> = emptyList(),
    val valorTotalGeral: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
    val isSending: Boolean = false
)

sealed interface CarrinhoEvento {
    data class ItemRemovido(val nomeProduto: String) : CarrinhoEvento
    data class PedidoEnviadoCozinha(val numeroMesa: Int, val pedidoRemotoId: Long?) : CarrinhoEvento
    data class PedidoSalvoOffline(val numeroMesa: Int, val quantidadeItens: Int) : CarrinhoEvento
    data class MensagemErro(val mensagem: String) : CarrinhoEvento
}

class CarrinhoViewModel(
    private val pedidoRepository: PedidoRepository,
    numeroMesaInicial: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(CarrinhoUiState(numeroMesa = numeroMesaInicial))
    val uiState: StateFlow<CarrinhoUiState> = _uiState.asStateFlow()

    private val _eventos = MutableSharedFlow<CarrinhoEvento>()
    val eventos: SharedFlow<CarrinhoEvento> = _eventos.asSharedFlow()

    init {
        observarItensMesa(numeroMesaInicial)
    }

    private fun observarItensMesa(mesaId: Int) {
        viewModelScope.launch {
            pedidoRepository.obterCarrinhoDaMesa(mesaId).collect { listaPedidos ->
                val totalCalculado = listaPedidos.fold(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) { acumulador, pedido ->
                    acumulador.add(pedido.precoTotalItem.setScale(2, RoundingMode.HALF_UP))
                        .setScale(2, RoundingMode.HALF_UP)
                }

                _uiState.update {
                    it.copy(
                        itens = listaPedidos,
                        valorTotalGeral = totalCalculado
                    )
                }
            }
        }
    }

    fun removerItem(pedido: PedidoEntity) {
        viewModelScope.launch {
            pedidoRepository.deletarItem(pedido.id)
            _eventos.emit(CarrinhoEvento.ItemRemovido(pedido.nomeProduto))
        }
    }

    fun enviarPedidoParaCozinha() {
        val estadoAtual = _uiState.value
        if (estadoAtual.isSending) return

        if (estadoAtual.itens.isEmpty()) {
            viewModelScope.launch {
                _eventos.emit(CarrinhoEvento.MensagemErro("O carrinho desta mesa está vazio!"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }

            val resultado = pedidoRepository.enviarPedidoDaMesaParaCozinha(
                mesaId = estadoAtual.numeroMesa,
                itensLocais = estadoAtual.itens,
                valorTotal = estadoAtual.valorTotalGeral
            )

            _uiState.update { it.copy(isSending = false) }

            when (resultado) {
                is ResultadoEnvioPedido.EnviadoServidor -> {
                    _eventos.emit(
                        CarrinhoEvento.PedidoEnviadoCozinha(
                            numeroMesa = estadoAtual.numeroMesa,
                            pedidoRemotoId = resultado.pedidoRemoto?.id
                        )
                    )
                }
                is ResultadoEnvioPedido.EnfileiradoOffline -> {
                    _eventos.emit(
                        CarrinhoEvento.PedidoSalvoOffline(
                            numeroMesa = resultado.numeroMesa,
                            quantidadeItens = resultado.quantidadeItens
                        )
                    )
                }
                is ResultadoEnvioPedido.ErroRejeitado -> {
                    _eventos.emit(
                        CarrinhoEvento.MensagemErro(resultado.mensagem)
                    )
                }
            }
        }
    }

    class Factory(
        private val pedidoRepository: PedidoRepository,
        private val numeroMesa: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CarrinhoViewModel(pedidoRepository, numeroMesa) as T
        }
    }
}
