package com.example.cheirocafe

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PedidoDao {

    @Insert
    suspend fun inserirItem(item: PedidoEntity)

    @Query("SELECT * FROM itens_pedido")
    suspend fun obterTodosOsItens(): List<PedidoEntity>

    @Query("DELETE FROM itens_pedido")
    suspend fun limparComanda()

    @Query("DELETE FROM itens_pedido WHERE id = :itemId")
    suspend fun deletarItem(itemId: Long)

    @Query("SELECT * FROM itens_pedido WHERE numeroMesa = :mesaId AND statusPedido = 'RASCUNHO'")
    fun obterCarrinhoDaMesa(mesaId: Int): Flow<List<PedidoEntity>>

    @Query("SELECT * FROM itens_pedido WHERE statusPedido = 'RASCUNHO' ORDER BY id DESC LIMIT 1")
    suspend fun obterUltimoItemRascunho(): PedidoEntity?

    @Query("SELECT SUM(precoTotalItem) as totalPreco, COUNT(*) as totalItens FROM itens_pedido WHERE statusPedido = 'RASCUNHO'")
    fun obterResumoPainel(): Flow<ResumoPainel?>

    @Query("UPDATE itens_pedido SET statusPedido = 'ENVIADO' WHERE statusPedido = 'RASCUNHO'")
    suspend fun atualizarStatusParaEnviado()

    @Query("UPDATE itens_pedido SET statusPedido = 'ENVIADO' WHERE numeroMesa = :mesaId AND statusPedido = 'RASCUNHO'")
    suspend fun atualizarStatusParaEnviadoPorMesa(mesaId: Int)

    @Query("UPDATE itens_pedido SET statusPedido = 'PENDENTE_SYNC' WHERE numeroMesa = :mesaId AND statusPedido = 'RASCUNHO'")
    suspend fun marcarComoPendenteSyncPorMesa(mesaId: Int)

    @Query("SELECT * FROM itens_pedido WHERE statusPedido = 'PENDENTE_SYNC' ORDER BY id ASC")
    suspend fun obterItensPendentesSync(): List<PedidoEntity>

    @Query("SELECT COUNT(*) FROM itens_pedido WHERE statusPedido = 'PENDENTE_SYNC'")
    fun observarQuantidadePendenteSync(): Flow<Int>

    @Query("UPDATE itens_pedido SET statusPedido = 'ENVIADO' WHERE id IN (:ids)")
    suspend fun marcarIdsComoEnviados(ids: List<Long>)
}
