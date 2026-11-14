package com.example.cheirocafe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonitorPedidosUiState(
    val isLoading: Boolean = false,
    val todosPedidos: List<PedidoResponseDTO> = emptyList(),
    val pedidosFiltrados: List<PedidoResponseDTO> = emptyList(),
    val filtroSelecionado: StatusPedidoCanonico? = null, // null = TODOS
    val itensPendentesOffline: Int = 0,
    val statusConexaoTexto: String = "Sincronizando com o servidor...",
    val erroMensagem: String? = null
)

class MonitorPedidosViewModel(
    private val pedidoRepository: PedidoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorPedidosUiState())
    val uiState: StateFlow<MonitorPedidosUiState> = _uiState.asStateFlow()

    init {
        observarFilaOffline()
        sincronizarPedidos()
    }

    private fun observarFilaOffline() {
        viewModelScope.launch {
            pedidoRepository.observarQuantidadePendenteSync().collect { qtdPendente ->
                _uiState.update { it.copy(itensPendentesOffline = qtdPendente) }
            }
        }
    }

    fun selecionarFiltro(status: StatusPedidoCanonico?) {
        _uiState.update { estadoAtual ->
            val filtrados = aplicarFiltro(estadoAtual.todosPedidos, status)
            estadoAtual.copy(
                filtroSelecionado = status,
                pedidosFiltrados = filtrados
            )
        }
    }

    fun sincronizarPedidos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, erroMensagem = null) }
            try {
                // 1. Tenta enviar automaticamente quaisquer pedidos gravados offline no Room
                pedidoRepository.sincronizarPedidosPendentesOffline()

                // 2. Busca a lista atualizada de pedidos (GET /pedidos)
                val remotos = pedidoRepository.listarPedidosRemotos()
                _uiState.update { estadoAtual ->
                    val filtrados = aplicarFiltro(remotos, estadoAtual.filtroSelecionado)
                    estadoAtual.copy(
                        isLoading = false,
                        todosPedidos = remotos,
                        pedidosFiltrados = filtrados,
                        statusConexaoTexto = "Online • ${remotos.size} comanda(s) sincronizada(s)",
                        erroMensagem = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { estadoAtual ->
                    estadoAtual.copy(
                        isLoading = false,
                        statusConexaoTexto = "Sem conexão no momento • Verifique o Wi-Fi local",
                        erroMensagem = e.message
                    )
                }
            }
        }
    }

    private fun aplicarFiltro(
        lista: List<PedidoResponseDTO>,
        filtro: StatusPedidoCanonico?
    ): List<PedidoResponseDTO> {
        if (filtro == null) return lista
        return lista.filter { it.statusCanonico() == filtro }
    }

    class Factory(
        private val pedidoRepository: PedidoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MonitorPedidosViewModel(pedidoRepository) as T
        }
    }
}
