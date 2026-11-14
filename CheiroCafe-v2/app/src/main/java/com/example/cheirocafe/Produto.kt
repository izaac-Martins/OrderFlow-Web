package com.example.cheirocafe

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.math.RoundingMode

@Parcelize
data class Variante(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName(value = "nome", alternate = ["tamanho"])
    val nome: String,

    @SerializedName("preco")
    val preco: BigDecimal
) : Parcelable {
    fun precoEscala2(): BigDecimal = preco.setScale(2, RoundingMode.HALF_UP)
}

@Parcelize
data class Adicional(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("nome")
    val nome: String,

    @SerializedName("preco")
    val preco: BigDecimal,

    @SerializedName(value = "vinculo", alternate = ["escopo"])
    val vinculo: String = "PRODUTO"
) : Parcelable {
    fun precoEscala2(): BigDecimal = preco.setScale(2, RoundingMode.HALF_UP)
}

@Parcelize
data class Produto(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("nome")
    val nome: String,

    @SerializedName("descricao")
    val descricao: String? = null,

    @SerializedName(value = "preco", alternate = ["precoBase"])
    val preco: BigDecimal,

    @SerializedName("categoria")
    val categoria: CategoriaProduto,

    @SerializedName("subcategoria")
    val subcategoria: String? = null,

    @SerializedName("variantes")
    val variantes: List<Variante> = emptyList(),

    @SerializedName("adicionais")
    val adicionais: List<Adicional> = emptyList()
) : Parcelable {

    fun precoBaseEscala2(): BigDecimal = preco.setScale(2, RoundingMode.HALF_UP)

    fun menorPrecoExibicao(): BigDecimal {
        val menorVariante = variantes.minByOrNull { it.preco }?.preco
        return (menorVariante ?: preco).setScale(2, RoundingMode.HALF_UP)
    }

    fun possuiVariantes(): Boolean = variantes.isNotEmpty()
}
