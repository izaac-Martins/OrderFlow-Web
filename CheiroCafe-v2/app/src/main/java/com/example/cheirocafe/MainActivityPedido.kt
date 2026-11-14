package com.example.cheirocafe

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cheirocafe.databinding.ActivityMainPedidoBinding
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

class MainActivityPedido : AppCompatActivity() {

    private lateinit var binding: ActivityMainPedidoBinding
    private lateinit var viewModel: CardapioViewModel

    private val subcategoriaAdapter = SubcategoriaAdapter { subcategoriaClicada ->
        val produtosFiltrados = viewModel.filtrarProdutosPorSubcategoria(subcategoriaClicada.nome)
        if (produtosFiltrados.isEmpty()) {
            Toast.makeText(
                this,
                "Nenhum produto cadastrado em ${subcategoriaClicada.nome}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            val intent = Intent(this, ListaProdutosFiltradosActivity::class.java).apply {
                putExtra("TITULO_TELA", subcategoriaClicada.nome)
                putParcelableArrayListExtra("PRODUTOS_LISTA", ArrayList(produtosFiltrados))
            }
            startActivity(intent)
        }
    }

    private val produtoAdapter = ProdutoAdapter { produtoSelecionado ->
        val intent = Intent(this, DetalheActivity::class.java).apply {
            putExtra("TITULO_TELA", produtoSelecionado.nome)
            putExtra("PRODUTO_SELECIONADO", produtoSelecionado)
        }
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainPedidoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Grade simétrica permanente de 2 colunas
        binding.rvProdutos.layoutManager = GridLayoutManager(this, 2)

        val database = AppDatabase.getDatabase(this)
        val factory = CardapioViewModel.Factory(
            produtoRepository = ProdutoRepository(database.produtoDao()),
            pedidoRepository = PedidoRepository(database.pedidoDao())
        )
        viewModel = ViewModelProvider(this, factory)[CardapioViewModel::class.java]

        configurarEventosDeClique()
        observarEstadoViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.carregarProdutos()
    }

    private fun configurarEventosDeClique() {
        binding.btnMonitorPedidosHeader.setOnClickListener {
            startActivity(Intent(this, MonitorPedidosActivity::class.java))
        }

        binding.btnMenuOpcoes.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menuInflater.inflate(R.menu.menu_principal, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_monitor_pedidos -> {
                        startActivity(Intent(this, MonitorPedidosActivity::class.java))
                        true
                    }
                    R.id.menu_sincronizar_cardapio -> {
                        viewModel.carregarProdutos()
                        Toast.makeText(
                            this,
                            "Sincronizando cardápio e fila com o servidor...",
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        binding.buttonDoces.setOnClickListener {
            viewModel.selecionarCategoria(CategoriaProduto.DOCES)
        }
        binding.buttonSalgados.setOnClickListener {
            viewModel.selecionarCategoria(CategoriaProduto.SALGADOS)
        }
        binding.buttonBebidas.setOnClickListener {
            viewModel.selecionarCategoria(CategoriaProduto.BEBIDAS)
        }

        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.atualizarTermoBusca(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnVerSacola.setOnClickListener {
            lifecycleScope.launch {
                val numeroMesa = viewModel.obterNumeroMesaAtiva()
                val intent = Intent(this@MainActivityPedido, CarrinhoActivity::class.java).apply {
                    putExtra("NUMERO_MESA", numeroMesa)
                }
                startActivity(intent)
            }
        }
    }

    private fun observarEstadoViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { estado ->
                    renderizarBotoesCategoria(estado.categoriaSelecionada)
                    renderizarCardapio(estado.modoExibicao)
                    renderizarPainelCarrinho(estado.resumoPainel)
                }
            }
        }
    }

    private fun renderizarBotoesCategoria(selecionada: CategoriaProduto) {
        val botoes = mapOf(
            CategoriaProduto.DOCES to binding.buttonDoces,
            CategoriaProduto.SALGADOS to binding.buttonSalgados,
            CategoriaProduto.BEBIDAS to binding.buttonBebidas
        )
        botoes.forEach { (cat, botao) ->
            if (cat == selecionada) {
                botao.setBackgroundResource(R.drawable.btn_fundo_selecionado)
                botao.setTextColor(Color.WHITE)
            } else {
                botao.setBackgroundResource(R.drawable.btn_fundo_cinza)
                botao.setTextColor(Color.parseColor("#571D22"))
            }
        }
    }

    private fun renderizarCardapio(modo: ModoExibicaoCardapio) {
        when (modo) {
            is ModoExibicaoCardapio.Subcategorias -> {
                binding.textViewLabel.text = modo.titulo
                if (binding.rvProdutos.adapter !== subcategoriaAdapter) {
                    binding.rvProdutos.adapter = subcategoriaAdapter
                }
                subcategoriaAdapter.submitList(modo.itens)
            }

            is ModoExibicaoCardapio.GradeProdutos -> {
                binding.textViewLabel.text = modo.titulo
                if (binding.rvProdutos.adapter !== produtoAdapter) {
                    binding.rvProdutos.adapter = produtoAdapter
                }
                produtoAdapter.submitList(modo.produtos)
            }
        }
    }

    private fun renderizarPainelCarrinho(resumo: ResumoPainel?) {
        val quantidade = resumo?.totalItens ?: 0
        val totalPreco: BigDecimal =
            (resumo?.totalPreco ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

        if (quantidade > 0) {
            binding.painelCarrinhoIFood.visibility = View.VISIBLE
            binding.txtTotalItensPainel.text = if (quantidade == 1) "1 item" else "$quantidade itens"
            binding.txtPrecoTotalPainel.text =
                String.format(Locale("pt", "BR"), "R$ %.2f", totalPreco)
        } else {
            binding.painelCarrinhoIFood.visibility = View.GONE
        }
    }
}
