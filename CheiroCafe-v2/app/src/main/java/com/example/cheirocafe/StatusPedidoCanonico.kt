package com.example.cheirocafe

import android.graphics.Color
import com.google.gson.annotations.SerializedName

enum class StatusPedidoCanonico(
    val rotuloExibicao: String,
    val descricaoEtapa: String,
    val etapaProgresso: Int,
    val corFundoHex: Int,
    val corTextoHex: Int
) {
    @SerializedName("RECEBIDO")
    RECEBIDO(
        rotuloExibicao = "RECEBIDO",
        descricaoEtapa = "Na fila da cozinha (Etapa 1/4)",
        etapaProgresso = 25,
        corFundoHex = Color.parseColor("#E0F2FE"),
        corTextoHex = Color.parseColor("#0369A1")
    ),

    @SerializedName("EM_PREPARO")
    EM_PREPARO(
        rotuloExibicao = "EM PREPARO",
        descricaoEtapa = "Em produção pelo barista/cozinha (Etapa 2/4)",
        etapaProgresso = 55,
        corFundoHex = Color.parseColor("#FEF3C7"),
        corTextoHex = Color.parseColor("#B45309")
    ),

    @SerializedName("PRONTO")
    PRONTO(
        rotuloExibicao = "PRONTO P/ RETIRADA",
        descricaoEtapa = "Aguardando entrega na mesa/balcão (Etapa 3/4)",
        etapaProgresso = 85,
        corFundoHex = Color.parseColor("#D1FAE5"),
        corTextoHex = Color.parseColor("#047857")
    ),

    @SerializedName("ENTREGUE")
    ENTREGUE(
        rotuloExibicao = "ENTREGUE",
        descricaoEtapa = "Pedido finalizado e entregue (Etapa 4/4)",
        etapaProgresso = 100,
        corFundoHex = Color.parseColor("#F1F5F9"),
        corTextoHex = Color.parseColor("#334155")
    ),

    @SerializedName("CANCELADO")
    CANCELADO(
        rotuloExibicao = "CANCELADO",
        descricaoEtapa = "Pedido cancelado",
        etapaProgresso = 100,
        corFundoHex = Color.parseColor("#FFE4E6"),
        corTextoHex = Color.parseColor("#BE123C")
    );

    companion object {
        fun fromString(valor: String?): StatusPedidoCanonico {
            val normalizado = valor?.trim()?.uppercase()
            return entries.firstOrNull { it.name == normalizado } ?: RECEBIDO
        }
    }
}
