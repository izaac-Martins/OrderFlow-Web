package com.example.cheirocafe

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
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

    private val produtoAdapter = ProdutoAdapter { produtoSelecionado ->
        val intent = Intent(this, DetalheActivity::class.java).apply {
            putExtra("TITULO_TELA", produtoSelecionado.nome)
            putExtra("PRODUTO_SELECIONADO", produtoSelecionado)
        }
        startActivity(intent)
    }

    private var assinaturaChipsRenderizados: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainPedidoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Grade simétrica permanente de 2 colunas exibindo sempre ProdutoAdapter
        binding.rvProdutos.layoutManager = GridLayoutManager(this, 2)
        binding.rvProdutos.adapter = produtoAdapter

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
                    renderizarChipsSubcategoria(
                        subcategorias = estado.subcategoriasDisponiveis,
                        selecionada = estado.subcategoriaSelecionada,
                        ocultarPorBusca = estado.termoBusca.isNotBlank()
                    )
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
                botao.setBackgroundResource(R.drawable.bg_pill_selected)
                botao.setTextColor(Color.WHITE)
            } else {
                botao.setBackgroundResource(R.drawable.bg_pill_unselected)
                botao.setTextColor(ContextCompat.getColor(this, R.color.coffee_primary))
            }
        }
    }

    private fun renderizarChipsSubcategoria(
        subcategorias: List<String>,
        selecionada: String?,
        ocultarPorBusca: Boolean
    ) {
        if (ocultarPorBusca || subcategorias.isEmpty()) {
            binding.scrollSubcategorias.visibility = View.GONE
            assinaturaChipsRenderizados = ""
            return
        }

        binding.scrollSubcategorias.visibility = View.VISIBLE

        val novaAssinatura = "${subcategorias.joinToString("|")}::${selecionada.orEmpty()}"
        if (novaAssinatura == assinaturaChipsRenderizados) return
        assinaturaChipsRenderizados = novaAssinatura

        binding.containerChipsSubcategoria.removeAllViews()

        val opcoes = listOf<String?>(null) + subcategorias
        opcoes.forEachIndexed { index, subcat ->
            val ativo = (subcat == null && selecionada == null) ||
                (subcat != null && subcat.equals(selecionada, ignoreCase = true))

            val chip = criarChipSubcategoria(
                rotulo = formatarNomeSubcategoria(subcat),
                ativo = ativo,
                temMargemDireita = index < opcoes.lastIndex
            ) {
                viewModel.selecionarSubcategoria(subcat)
            }
            binding.containerChipsSubcategoria.addView(chip)
        }
    }

    private fun criarChipSubcategoria(
        rotulo: String,
        ativo: Boolean,
        temMargemDireita: Boolean,
        aoClicar: () -> Unit
    ): AppCompatButton {
        val densidade = resources.displayMetrics.density
        val alturaPx = (34 * densidade).toInt()
        val padHorizontalPx = (14 * densidade).toInt()
        val margemDireitaPx = if (temMargemDireita) (8 * densidade).toInt() else 0

        return AppCompatButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                alturaPx
            ).apply {
                marginEnd = margemDireitaPx
            }
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(padHorizontalPx, 0, padHorizontalPx, 0)
            gravity = Gravity.CENTER
            isAllCaps = false
            stateListAnimator = null
            text = rotulo
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            if (ativo) {
                setBackgroundResource(R.drawable.bg_pill_selected)
                setTextColor(Color.WHITE)
            } else {
                setBackgroundResource(R.drawable.bg_pill_unselected)
                setTextColor(ContextCompat.getColor(context, R.color.coffee_primary))
            }
            setOnClickListener { aoClicar() }
        }
    }

    private fun formatarNomeSubcategoria(tag: String?): String {
        if (tag.isNullOrBlank()) return "Todas"
        return when (tag.trim().uppercase()) {
            "TRADICIONAL" -> "Tradicionais"
            "ESPECIAL" -> "Especiais"
            "SUCOS_IOGURTES" -> "Sucos & Iogurtes"
            "REFRIGERANTES" -> "Refrigerantes"
            else -> tag.trim().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString()
            }
        }
    }

    private fun renderizarCardapio(modo: ModoExibicaoCardapio) {
        when (modo) {
            is ModoExibicaoCardapio.GradeProdutos -> {
                binding.textViewLabel.text = modo.titulo
                produtoAdapter.submitList(modo.produtos)
            }
            is ModoExibicaoCardapio.Subcategorias -> {
                binding.textViewLabel.text = modo.titulo
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
