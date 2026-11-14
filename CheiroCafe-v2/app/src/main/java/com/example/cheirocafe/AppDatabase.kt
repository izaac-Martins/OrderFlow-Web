package com.example.cheirocafe

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PedidoEntity::class, ProdutoCatalogoEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(BigDecimalConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pedidoDao(): PedidoDao
    abstract fun produtoDao(): ProdutoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cheiro_cafe_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
