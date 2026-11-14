package com.example.cheirocafe

import com.google.gson.annotations.SerializedName

enum class CategoriaProduto {
    @SerializedName("BEBIDAS")
    BEBIDAS,

    @SerializedName("DOCES")
    DOCES,

    @SerializedName("SALGADOS")
    SALGADOS;

    companion object {
        fun fromString(valor: String?): CategoriaProduto {
            return entries.firstOrNull { it.name.equals(valor?.trim(), ignoreCase = true) } ?: BEBIDAS
        }
    }
}
