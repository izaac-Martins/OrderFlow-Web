package com.example.cheirocafe

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.math.RoundingMode

class BigDecimalConverters {

    @TypeConverter
    fun fromBigDecimal(valor: BigDecimal?): String? {
        return valor?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()
    }

    @TypeConverter
    fun toBigDecimal(valor: String?): BigDecimal? {
        if (valor.isNullOrBlank()) return null
        return try {
            BigDecimal(valor.trim()).setScale(2, RoundingMode.HALF_UP)
        } catch (e: Exception) {
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        }
    }
}
