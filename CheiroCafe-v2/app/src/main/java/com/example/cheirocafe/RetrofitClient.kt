package com.example.cheirocafe

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.math.RoundingMode

object RetrofitClient {
    //private const val BASE_URL = BuildConfig.BASE_URL
    private const val BASE_URL = "http://192.168.100.36:8080/api/v1/"
    private val bigDecimalSerializer = JsonSerializer<BigDecimal> { src, _, _ ->
        JsonPrimitive(src?.setScale(2, RoundingMode.HALF_UP) ?: BigDecimal.ZERO)
    }

    private val bigDecimalDeserializer = JsonDeserializer<BigDecimal> { json, _, _ ->
        try {
            if (json == null || json.isJsonNull) {
                BigDecimal.ZERO
            } else {
                json.asBigDecimal
            }
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }

    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(BigDecimal::class.java, bigDecimalSerializer)
        .registerTypeAdapter(BigDecimal::class.java, bigDecimalDeserializer)
        .create()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()
            .create(ApiService::class.java)
    }
}
