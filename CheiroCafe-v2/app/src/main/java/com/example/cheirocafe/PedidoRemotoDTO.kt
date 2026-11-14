package com.example.cheirocafe

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.math.RoundingMode

@Parcelize
data class ItemPedidoRemotoDTO(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("produtoId")
    val produtoId: Long? = null,

    @SerializedName("nomeProduto")
    val nomeProduto: String,

    @SerializedName("tamanhoEscolhido")
    val tamanhoEscolhido: String? = null,

    @SerializedName("tipoGraoEscolhido")
    val tipoGraoEscolhido: String? = null,

    @SerializedName("adicionaisEscolhidos")
    val adicionaisEscolhidos: String? = null,

    @SerializedName("quantidade")
    val quantidade: Int,

    @SerializedName(value = "precoTotalItem", alternate = ["precoUnitario"])
    val precoTotalItem: BigDecimal,

    @SerializedName("pagadorItem")
    val pagadorItem: String = "Mesa Total"
) : Parcelable {
    fun precoTotalEscala2(): BigDecimal =
        precoTotalItem.setScale(2, RoundingMode.HALF_UP)
}

@Parcelize
data class PedidoRequestDTO(
    @SerializedName("numeroMesa")
    val numeroMesa: Int,

    @SerializedName("valorTotal")
    val valorTotal: BigDecimal,

    @SerializedName("itens")
    val itens: List<ItemPedidoRemotoDTO>
) : Parcelable

@Parcelize
data class PedidoResponseDTO(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("versao")
    val versao: Long? = 0L,

    @SerializedName("numeroMesa")
    val numeroMesa: Int,

    @SerializedName("status")
    val status: String? = "RECEBIDO",

    @SerializedName("valorTotal")
    val valorTotal: BigDecimal,

    @SerializedName(value = "criadoEm", alternate = ["dataHora"])
    val criadoEm: String? = null,

    @SerializedName("itens")
    val itens: List<ItemPedidoRemotoDTO> = emptyList()
) : Parcelable {

    fun statusCanonico(): StatusPedidoCanonico =
        StatusPedidoCanonico.fromString(status)

    fun valorTotalEscala2(): BigDecimal =
        valorTotal.setScale(2, RoundingMode.HALF_UP)
}
