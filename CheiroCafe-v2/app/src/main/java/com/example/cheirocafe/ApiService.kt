package com.example.cheirocafe

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @GET("produtos")
    suspend fun getProdutos(): List<Produto>

    @GET("produtos/adicionais-globais")
    suspend fun getAdicionaisGlobais(): List<Adicional>

    @GET("produtos/{id}")
    suspend fun getProdutoPorId(@Path("id") id: Long): Response<Produto>

    @POST("pedidos")
    suspend fun enviarPedido(@Body pedido: PedidoRequestDTO): Response<PedidoResponseDTO>

    @GET("pedidos")
    suspend fun listarPedidos(): List<PedidoResponseDTO>
}
