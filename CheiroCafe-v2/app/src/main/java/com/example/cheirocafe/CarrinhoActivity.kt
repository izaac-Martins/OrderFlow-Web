package com.example.cheirocafe

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cheirocafe.databinding.ActivityCarrinhoBinding
import kotlinx.coroutines.launch
import java.util.Locale

class CarrinhoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCarrinhoBinding
    private lateinit var viewModel: CarrinhoViewModel

    private val carrinhoAdapter = CarrinhoAdapter { pedidoParaRemover ->
        viewModel.removerItem(pedidoParaRemover)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCarrinhoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.containerHeaderCarrinho.parent as View) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.rvItensCarrinho.layoutManager = LinearLayoutManager(this)
        binding.rvItensCarrinho.adapter = carrinhoAdapter

        val mesaSelecionada = intent.getIntExtra("NUMERO_MESA", 1)
        val database = AppDatabase.getDatabase(this)
        val factory = CarrinhoViewModel.Factory(
            pedidoRepository = PedidoRepository(database.pedidoDao()),
            numeroMesa = mesaSelecionada
        )
        viewModel = ViewModelProvider(this, factory)[CarrinhoViewModel::class.java]

        binding.btnEnviarPedidoCozinha.setOnClickListener {
            viewModel.enviarPedidoParaCozinha()
        }

        observarViewModel()
    }

    private fun observarViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { estado ->
                        binding.txtNumeroMesaCarrinho.text =
                            String.format(Locale("pt", "BR"), "Mesa %02d", estado.numeroMesa)
                        binding.txtTotalCarrinho.text =
                            String.format(Locale("pt", "BR"), "R$ %.2f", estado.valorTotalGeral)
                        binding.btnEnviarPedidoCozinha.isEnabled =
                            !estado.isSending && estado.itens.isNotEmpty()
                        binding.btnEnviarPedidoCozinha.text = if (estado.isSending) {
                            "Enviando para a Cozinha..."
                        } else {
                            "Enviar para a Cozinha"
                        }
                        carrinhoAdapter.submitList(estado.itens)
                    }
                }
                launch {
                    viewModel.eventos.collect { evento ->
                        when (evento) {
                            is CarrinhoEvento.ItemRemovido -> {
                                Toast.makeText(
                                    this@CarrinhoActivity,
                                    "${evento.nomeProduto} removido do carrinho",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is CarrinhoEvento.PedidoEnviadoCozinha -> {
                                val sufixoComanda = evento.pedidoRemotoId?.let { " (Pedido #$it)" }.orEmpty()
                                Toast.makeText(
                                    this@CarrinhoActivity,
                                    "Pedido da Mesa %02d recebido na cozinha!$sufixoComanda".format(evento.numeroMesa),
                                    Toast.LENGTH_LONG
                                ).show()
                                startActivity(Intent(this@CarrinhoActivity, MonitorPedidosActivity::class.java))
                                finish()
                            }
                            is CarrinhoEvento.PedidoSalvoOffline -> {
                                Toast.makeText(
                                    this@CarrinhoActivity,
                                    "Sem Wi-Fi no momento: Pedido da Mesa %02d salvo no dispositivo e será enviado automaticamente!".format(evento.numeroMesa),
                                    Toast.LENGTH_LONG
                                ).show()
                                startActivity(Intent(this@CarrinhoActivity, MonitorPedidosActivity::class.java))
                                finish()
                            }
                            is CarrinhoEvento.MensagemErro -> {
                                Toast.makeText(
                                    this@CarrinhoActivity,
                                    evento.mensagem,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }
}
