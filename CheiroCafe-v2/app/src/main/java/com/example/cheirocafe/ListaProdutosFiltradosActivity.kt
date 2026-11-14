package com.example.cheirocafe

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cheirocafe.databinding.ActivityMainPedidoBinding

class ListaProdutosFiltradosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainPedidoBinding

    private val produtoAdapter = ProdutoAdapter { produtoSelecionado ->
        val intentDet = Intent(this, DetalheActivity::class.java).apply {
            putExtra("TITULO_TELA", produtoSelecionado.nome)
            putExtra("PRODUTO_SELECIONADO", produtoSelecionado)
        }
        startActivity(intentDet)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainPedidoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tituloTela = intent.getStringExtra("TITULO_TELA") ?: "Produtos"
        val produtosFiltrados = IntentCompat.getParcelableArrayListExtra(
            intent,
            "PRODUTOS_LISTA",
            Produto::class.java
        ) ?: arrayListOf()

        binding.linearLayoutButtons.visibility = View.GONE
        binding.editTextSearch.visibility = View.GONE
        binding.textViewLabel.text = tituloTela

        binding.btnMonitorPedidosHeader.setOnClickListener {
            startActivity(Intent(this, MonitorPedidosActivity::class.java))
        }

        binding.rvProdutos.layoutManager = GridLayoutManager(this, 2)
        binding.rvProdutos.adapter = produtoAdapter
        produtoAdapter.submitList(produtosFiltrados)
    }
}
