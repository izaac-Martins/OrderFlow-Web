package com.example.cheirocafe

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SubcategoriaItem(
    val nome: String,
    val imagemResId: Int
) : Parcelable
