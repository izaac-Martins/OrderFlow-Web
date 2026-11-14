package com.example.cheirocafe

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProdutoDao {

    @Query("SELECT * FROM catalogo_produtos ORDER BY categoria ASC, nome ASC")
    fun observarTodos(): Flow<List<ProdutoCatalogoEntity>>

    @Query("SELECT * FROM catalogo_produtos ORDER BY categoria ASC, nome ASC")
    suspend fun listarTodosSnapshot(): List<ProdutoCatalogoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(produtos: List<ProdutoCatalogoEntity>)

    @Query("DELETE FROM catalogo_produtos")
    suspend fun limparCatalogo()

    @Transaction
    suspend fun substituirCatalogo(produtos: List<ProdutoCatalogoEntity>) {
        limparCatalogo()
        inserirTodos(produtos)
    }
}
