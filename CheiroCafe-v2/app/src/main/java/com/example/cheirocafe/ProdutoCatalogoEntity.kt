package com.example.cheirocafe

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.reflect.TypeToken
import java.math.BigDecimal
import java.math.RoundingMode

@Entity(tableName = "catalogo_produtos")
data class ProdutoCatalogoEntity(
    @PrimaryKey
    val id: Long,
    val nome: String,
    val descricao: String?,
    val preco: BigDecimal,
    val categoria: String,
    val subcategoria: String?,
    val variantesJson: String,
    val adicionaisJson: String,
    val atualizadoEm: Long = System.currentTimeMillis()
) {

    fun toDomain(): Produto {
        val tipoVariantes = object : TypeToken<List<Variante>>() {}.type
        val tipoAdicionais = object : TypeToken<List<Adicional>>() {}.type

        val listaVariantes: List<Variante> = try {
            RetrofitClient.gson.fromJson<List<Variante>>(variantesJson, tipoVariantes)
                ?.map { it.copy(preco = it.preco.setScale(2, RoundingMode.HALF_UP)) }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val listaAdicionais: List<Adicional> = try {
            RetrofitClient.gson.fromJson<List<Adicional>>(adicionaisJson, tipoAdicionais)
                ?.map { it.copy(preco = it.preco.setScale(2, RoundingMode.HALF_UP)) }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return Produto(
            id = id,
            nome = nome,
            descricao = descricao,
            preco = preco.setScale(2, RoundingMode.HALF_UP),
            categoria = CategoriaProduto.fromString(categoria),
            subcategoria = subcategoria,
            variantes = listaVariantes,
            adicionais = listaAdicionais
        )
    }

    companion object {
        fun fromDomain(produto: Produto, fallbackId: Long): ProdutoCatalogoEntity {
            val idSeguro = produto.id ?: fallbackId
            val variantesNormalizadas = produto.variantes.map {
                it.copy(preco = it.preco.setScale(2, RoundingMode.HALF_UP))
            }
            val adicionaisNormalizados = produto.adicionais.map {
                it.copy(preco = it.preco.setScale(2, RoundingMode.HALF_UP))
            }

            return ProdutoCatalogoEntity(
                id = idSeguro,
                nome = produto.nome.trim(),
                descricao = produto.descricao,
                preco = produto.preco.setScale(2, RoundingMode.HALF_UP),
                categoria = produto.categoria.name,
                subcategoria = produto.subcategoria,
                variantesJson = RetrofitClient.gson.toJson(variantesNormalizadas),
                adicionaisJson = RetrofitClient.gson.toJson(adicionaisNormalizados),
                atualizadoEm = System.currentTimeMillis()
            )
        }
    }
}
