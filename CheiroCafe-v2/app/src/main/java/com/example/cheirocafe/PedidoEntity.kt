package com.example.cheirocafe

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
@Entity(tableName = "itens_pedido")
data class PedidoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val produtoId: Long,
    val nomeProduto: String,
    val tamanhoEscolhido: String?,
    val tipoGraoEscolhido: String?,
    val adicionaisEscolhidos: String,
    val quantidade: Int,
    val precoTotalItem: BigDecimal,
    val pagadorItem: String = "Mesa Total",
    val numeroMesa: Int,
    val statusPedido: String = "RASCUNHO"
) : Parcelable
