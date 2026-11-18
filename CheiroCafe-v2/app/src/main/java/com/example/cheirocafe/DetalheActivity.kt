package com.example.cheirocafe

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.cheirocafe.databinding.ActivityDetalheBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class DetalheActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalheBinding
    private lateinit var viewModel: DetalheViewModel

    private val mapaRadioParaProduto = mutableMapOf<Int, Produto>()
    private var idProdutoRenderizadoNoFormulario: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDetalheBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.getDatabase(this)
        val factory = DetalheViewModel.Factory(PedidoRepository(database.pedidoDao()))
        viewModel = ViewModelProvider(this, factory)[DetalheViewModel::class.java]

        val tituloSubcategoria = intent.getStringExtra("TITULO_TELA") ?: "Detalhes"
        val listaProdutos = IntentCompat.getParcelableArrayListExtra(
            intent,
            "PRODUTOS_LISTA",
            Produto::class.java
        ) ?: arrayListOf()

        val produtoUnitario = IntentCompat.getParcelableExtra(
            intent,
            "PRODUTO_SELECIONADO",
            Produto::class.java
        )
        if (produtoUnitario != null && listaProdutos.isEmpty()) {
            listaProdutos.add(produtoUnitario)
        }

        if (listaProdutos.isEmpty()) {
            Toast.makeText(this, "Nenhum produto encontrado!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.ibVoltarDetalhe.setOnClickListener { finish() }

        // Ocultação inteligente: só exibe a escolha de opção quando há mais de um produto
        val exibirOpcoes = listaProdutos.size > 1
        binding.lblTipoProduto.visibility = if (exibirOpcoes) View.VISIBLE else View.GONE
        binding.radioGroupTipo.visibility = if (exibirOpcoes) View.VISIBLE else View.GONE

        viewModel.inicializarDados(tituloSubcategoria, listaProdutos)
        montarRadioGroupProdutos(listaProdutos)
        configurarListenersBotoes()
        observarViewModel()
    }

    private fun montarRadioGroupProdutos(produtos: List<Produto>) {
        binding.radioGroupTipo.removeAllViews()
        mapaRadioParaProduto.clear()

        val produtoSelecionadoAtual = viewModel.uiState.value.produtoSelecionado
        var radioIdParaMarcar: Int = View.NO_ID

        produtos.forEachIndexed { index, produto ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = produto.nome
                setTextColor(Color.parseColor("#040404"))
            }
            if ((produtoSelecionadoAtual != null && produtoSelecionadoAtual.nome == produto.nome) ||
                (produtoSelecionadoAtual == null && index == 0)
            ) {
                radioIdParaMarcar = rb.id
            }
            mapaRadioParaProduto[rb.id] = produto
            binding.radioGroupTipo.addView(rb)
        }

        if (radioIdParaMarcar != View.NO_ID) {
            binding.radioGroupTipo.check(radioIdParaMarcar)
        }

        binding.radioGroupTipo.setOnCheckedChangeListener { _, checkedId ->
            val produto = mapaRadioParaProduto[checkedId] ?: return@setOnCheckedChangeListener
            viewModel.selecionarProduto(produto)
        }
    }

    private fun configurarListenersBotoes() {
        binding.btnMenos.setOnClickListener {
            viewModel.decrementarQuantidade()
        }

        binding.btnMais.setOnClickListener {
            viewModel.incrementarQuantidade()
        }

        binding.btnAdicionar.setOnClickListener {
            val opcoes = arrayOf(
                "Não dividir (Conta Única)",
                "Dividir Conta (Atribuir a um Cliente)"
            )

            MaterialAlertDialogBuilder(this)
                .setTitle("Organização do Pedido")
                .setSingleChoiceItems(opcoes, 0) { dialog, clickedIndex ->
                    dialog.dismiss()
                    if (clickedIndex == 0) {
                        perguntarNumeroMesaContaUnica()
                    } else {
                        perguntarNumeroMesaDividida()
                    }
                }
                .show()
        }
    }

    private fun observarViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { estado ->
                        renderizarEstado(estado)
                    }
                }
                launch {
                    viewModel.eventos.collect { evento ->
                        when (evento) {
                            is DetalheEvento.PedidoSalvoComSucesso -> {
                                Toast.makeText(
                                    this@DetalheActivity,
                                    "${evento.nomeProduto} adicionado à Mesa %02d!".format(evento.numeroMesa),
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                            is DetalheEvento.MensagemErro -> {
                                Toast.makeText(
                                    this@DetalheActivity,
                                    evento.mensagem,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderizarEstado(estado: DetalheUiState) {
        binding.txtTitulo.text = estado.tituloTela
        binding.txtQuantidade.text = estado.quantidade.toString()
        binding.btnAdicionar.text = "Adicionar • R$ ${"%.2f".format(estado.precoTotalCalculado)}"

        val produto = estado.produtoSelecionado ?: return
        binding.txtNomeProdutoHero.text = produto.nome

        val temVariantes = produto.variantes.isNotEmpty()
        binding.lblTamanho.visibility = if (temVariantes) View.VISIBLE else View.GONE
        binding.containerTamanhos.visibility = if (temVariantes) View.VISIBLE else View.GONE
        val temAdicionais = produto.adicionais.isNotEmpty()
        binding.lblAdicionais.visibility = if (temAdicionais) View.VISIBLE else View.GONE
        binding.containerAdicionais.visibility = if (temAdicionais) View.VISIBLE else View.GONE

        binding.txtDescricaoProduto.text = if (!produto.descricao.isNullOrBlank()) {
            produto.descricao
        } else {
            "Categoria: ${produto.categoria.name}" +
                (produto.subcategoria?.let { " • $it" } ?: "")
        }

        val imagemRes = when (produto.categoria) {
            CategoriaProduto.DOCES -> R.drawable.bolocafe
            CategoriaProduto.SALGADOS -> R.drawable.esfirracarne
            CategoriaProduto.BEBIDAS -> when (produto.subcategoria?.uppercase()) {
                "ESPECIAL" -> R.drawable.cafecremoso
                "SUCOS_IOGURTES" -> R.drawable.sucomanga
                "REFRIGERANTES" -> R.drawable.refrilata
                else -> R.drawable.cafeprensa
            }
        }
        binding.imgProduto.setImageResource(imagemRes)

        val chaveProduto = "${produto.id ?: 0L}_${produto.nome}"
        if (idProdutoRenderizadoNoFormulario != chaveProduto) {
            idProdutoRenderizadoNoFormulario = chaveProduto
            reconstruirVariantesEAdicionais(produto, estado)
        }
    }

    private fun reconstruirVariantesEAdicionais(produto: Produto, estado: DetalheUiState) {
        binding.containerTamanhos.removeAllViews()
        binding.containerAdicionais.removeAllViews()

        val alturaPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            60f,
            resources.displayMetrics
        ).toInt()
        val raioBordaPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            20f,
            resources.displayMetrics
        ).toInt()

        val botoesVariantes = mutableListOf<MaterialButton>()

        produto.variantes.forEachIndexed { index, variante ->
            val btnTamanho = MaterialButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, alturaPx, 1f)
                isCheckable = true
                val nomeFormatado = if (
                    produto.categoria == CategoriaProduto.BEBIDAS &&
                    !variante.nome.contains("ml", ignoreCase = true)
                ) {
                    "${variante.nome}ml"
                } else {
                    variante.nome
                }
                text = "$nomeFormatado (R$ ${"%.2f".format(variante.preco)})"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#432A20"))
                cornerRadius = raioBordaPx
                isChecked = (variante == estado.varianteSelecionada)
            }

            botoesVariantes.add(btnTamanho)
            binding.containerTamanhos.addView(btnTamanho)

            btnTamanho.setOnClickListener {
                botoesVariantes.forEach { outro -> outro.isChecked = false }
                btnTamanho.isChecked = true
                viewModel.selecionarVariante(variante)
            }

            if (index < produto.variantes.size - 1) {
                val space = Space(this)
                val spaceWidthPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    16f,
                    resources.displayMetrics
                ).toInt()
                space.layoutParams = LinearLayout.LayoutParams(
                    spaceWidthPx,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                binding.containerTamanhos.addView(space)
            }
        }

        produto.adicionais.forEach { adicional ->
            val cb = CheckBox(this).apply {
                text = "${adicional.nome} (+ R$ ${"%.2f".format(adicional.preco)})"
                setTextColor(Color.parseColor("#0D0D0D"))
                isChecked = estado.adicionaisSelecionados.contains(adicional)
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.alternarAdicional(adicional, isChecked)
                }
            }
            binding.containerAdicionais.addView(cb)
        }
    }

    private fun perguntarNumeroMesaContaUnica() {
        val inputMesa = EditText(this).apply {
            hint = "Ex: 5, 12, 22..."
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val container = LinearLayout(this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(48, 16, 48, 16)
        inputMesa.layoutParams = params
        container.addView(inputMesa)

        MaterialAlertDialogBuilder(this)
            .setTitle("Número da Mesa (Conta Única)")
            .setMessage("Digite o número da mesa para este pedido:")
            .setView(container)
            .setPositiveButton("Confirmar") { dialog, _ ->
                val mesaDigitada = inputMesa.text.toString().trim()
                val numeroMesa = mesaDigitada.toIntOrNull()?.takeIf { it > 0 } ?: 1
                dialog.dismiss()
                viewModel.salvarPedido(pagador = "Mesa Total", numeroMesa = numeroMesa)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun perguntarNumeroMesaDividida() {
        val inputMesa = EditText(this).apply {
            hint = "Ex: 5, 12, 22..."
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val container = LinearLayout(this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(48, 16, 48, 16)
        inputMesa.layoutParams = params
        container.addView(inputMesa)

        MaterialAlertDialogBuilder(this)
            .setTitle("Número da Mesa")
            .setMessage("Digite o número da mesa para este pedido:")
            .setView(container)
            .setPositiveButton("Confirmar") { dialog, _ ->
                val mesaDigitada = inputMesa.text.toString().trim()
                val numeroMesa = mesaDigitada.toIntOrNull()?.takeIf { it > 0 } ?: 1
                dialog.dismiss()
                abrirPainelClientesMesa(numeroMesa)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirPainelClientesMesa(numeroMesa: Int) {
        val bottomSheetDialog = BottomSheetDialog(this)

        val layoutPainel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 64)
            background = android.graphics.drawable.ColorDrawable(Color.WHITE)
        }

        val txtTitulo = TextView(this).apply {
            text = "Gerenciar Mesa %02d".format(numeroMesa)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
        }
        layoutPainel.addView(txtTitulo)

        val txtSubtitulo = TextView(this).apply {
            text = "Digite o nome do cliente desta mesa que vai pagar este item:"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.GRAY)
            setPadding(0, 8, 0, 24)
        }
        layoutPainel.addView(txtSubtitulo)

        val inputNomeCliente = EditText(this).apply {
            hint = "Nome do Cliente (Ex: Bruno, Mariana...)"
        }
        layoutPainel.addView(inputNomeCliente)

        val btnConfirmar = MaterialButton(this).apply {
            text = "Adicionar à Conta do Cliente"
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 32
            layoutParams = params
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4E342E"))
            cornerRadius = 24
        }

        btnConfirmar.setOnClickListener {
            val nomeCliente = inputNomeCliente.text.toString().trim()
            val pagadorFinal = if (nomeCliente.isNotEmpty()) nomeCliente else "Mesa Única"
            viewModel.salvarPedido(
                pagador = "$pagadorFinal (Mesa $numeroMesa)",
                numeroMesa = numeroMesa
            )
            bottomSheetDialog.dismiss()
        }
        layoutPainel.addView(btnConfirmar)

        bottomSheetDialog.setContentView(layoutPainel)
        bottomSheetDialog.show()
    }
}
