package com.example.cheirocafe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

sealed interface ResultadoEnvioPedido {
    data class EnviadoServidor(val pedidoRemoto: PedidoResponseDTO?) : ResultadoEnvioPedido
    data class EnfileiradoOffline(val numeroMesa: Int, val quantidadeItens: Int) : ResultadoEnvioPedido
    data class ErroRejeitado(val codigoHttp: Int, val mensagem: String) : ResultadoEnvioPedido
}

class PedidoRepository(
    private val pedidoDao: PedidoDao,
    private val apiService: ApiService = RetrofitClient.instance
) {

    fun obterCarrinhoDaMesa(mesaId: Int): Flow<List<PedidoEntity>> {
        return pedidoDao.obterCarrinhoDaMesa(mesaId)
    }

    fun obterResumoPainel(): Flow<ResumoPainel?> {
        return pedidoDao.obterResumoPainel()
    }

    fun observarQuantidadePendenteSync(): Flow<Int> {
        return pedidoDao.observarQuantidadePendenteSync()
    }

    suspend fun inserirItem(item: PedidoEntity) = withContext(Dispatchers.IO) {
        val itemEscala2 = item.copy(
            precoTotalItem = item.precoTotalItem.setScale(2, RoundingMode.HALF_UP)
        )
        pedidoDao.inserirItem(itemEscala2)
    }

    suspend fun deletarItem(itemId: Long) = withContext(Dispatchers.IO) {
        pedidoDao.deletarItem(itemId)
    }

    suspend fun obterUltimoItemRascunho(): PedidoEntity? = withContext(Dispatchers.IO) {
        pedidoDao.obterUltimoItemRascunho()
    }

    suspend fun listarPedidosRemotos(): List<PedidoResponseDTO> = withContext(Dispatchers.IO) {
        apiService.listarPedidos()
    }

    /**
     * Envia os itens da mesa para a API Spring Boot (POST /pedidos) com BigDecimal(scale=2, HALF_UP).
     * Caso haja queda de Wi-Fi (IOException), armazena os itens com status 'PENDENTE_SYNC' no Room
     * para sincronização automática assim que a rede retornar.
     */
    suspend fun enviarPedidoDaMesaParaCozinha(
        mesaId: Int,
        itensLocais: List<PedidoEntity>,
        valorTotal: BigDecimal
    ): ResultadoEnvioPedido = withContext(Dispatchers.IO) {
        val itensDto = itensLocais.map { item ->
            ItemPedidoRemotoDTO(
                id = null,
                produtoId = item.produtoId.takeIf { it > 0L },
                nomeProduto = item.nomeProduto.trim(),
                tamanhoEscolhido = item.tamanhoEscolhido,
                tipoGraoEscolhido = item.tipoGraoEscolhido,
                adicionaisEscolhidos = item.adicionaisEscolhidos,
                quantidade = item.quantidade,
                precoTotalItem = item.precoTotalItem.setScale(2, RoundingMode.HALF_UP),
                pagadorItem = item.pagadorItem
            )
        }

        val somaCalculada = itensDto.fold(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) { acc, item ->
            acc.add(item.precoTotalEscala2()).setScale(2, RoundingMode.HALF_UP)
        }
        val totalFinal = if (somaCalculada > BigDecimal.ZERO) {
            somaCalculada
        } else {
            valorTotal.setScale(2, RoundingMode.HALF_UP)
        }

        val requestDto = PedidoRequestDTO(
            numeroMesa = mesaId,
            valorTotal = totalFinal,
            itens = itensDto
        )

        try {
            val response: Response<PedidoResponseDTO> = apiService.enviarPedido(requestDto)
            if (response.isSuccessful) {
                pedidoDao.atualizarStatusParaEnviadoPorMesa(mesaId)
                ResultadoEnvioPedido.EnviadoServidor(response.body())
            } else {
                ResultadoEnvioPedido.ErroRejeitado(
                    codigoHttp = response.code(),
                    mensagem = "Erro no servidor ao registrar pedido (HTTP ${response.code()})."
                )
            }
        } catch (e: IOException) {
            // Queda de Wi-Fi / Servidor inacessível -> salva como PENDENTE_SYNC no Room (Offline-First)
            pedidoDao.marcarComoPendenteSyncPorMesa(mesaId)
            ResultadoEnvioPedido.EnfileiradoOffline(
                numeroMesa = mesaId,
                quantidadeItens = itensLocais.size
            )
        } catch (e: Exception) {
            ResultadoEnvioPedido.ErroRejeitado(
                codigoHttp = 0,
                mensagem = e.message ?: "Erro inesperado ao enviar pedido."
            )
        }
    }

    /**
     * Sincroniza automaticamente com o backend quaisquer pedidos que foram gravados offline ('PENDENTE_SYNC')
     * durante uma oscilação de rede Wi-Fi.
     */
    suspend fun sincronizarPedidosPendentesOffline(): Int = withContext(Dispatchers.IO) {
        val pendentes = pedidoDao.obterItensPendentesSync()
        if (pendentes.isEmpty()) return@withContext 0

        val agrupadosPorMesa = pendentes.groupBy { it.numeroMesa }
        var comandasSincronizadas = 0

        for ((mesaId, itensDaMesa) in agrupadosPorMesa) {
            val itensDto = itensDaMesa.map { item ->
                ItemPedidoRemotoDTO(
                    id = null,
                    produtoId = item.produtoId.takeIf { it > 0L },
                    nomeProduto = item.nomeProduto.trim(),
                    tamanhoEscolhido = item.tamanhoEscolhido,
                    tipoGraoEscolhido = item.tipoGraoEscolhido,
                    adicionaisEscolhidos = item.adicionaisEscolhidos,
                    quantidade = item.quantidade,
                    precoTotalItem = item.precoTotalItem.setScale(2, RoundingMode.HALF_UP),
                    pagadorItem = item.pagadorItem
                )
            }

            val totalMesa = itensDto.fold(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)) { acc, item ->
                acc.add(item.precoTotalEscala2()).setScale(2, RoundingMode.HALF_UP)
            }

            try {
                val response = apiService.enviarPedido(
                    PedidoRequestDTO(
                        numeroMesa = mesaId,
                        valorTotal = totalMesa,
                        itens = itensDto
                    )
                )
                if (response.isSuccessful) {
                    pedidoDao.marcarIdsComoEnviados(itensDaMesa.map { it.id })
                    comandasSincronizadas++
                }
            } catch (_: Exception) {
                // Continua pendente para a próxima tentativa
            }
        }

        comandasSincronizadas
    }
}
