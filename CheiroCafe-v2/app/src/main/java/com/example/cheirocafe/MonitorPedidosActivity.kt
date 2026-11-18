package com.example.cheirocafe

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cheirocafe.databinding.ActivityMonitorPedidosBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitorPedidosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitorPedidosBinding
    private lateinit var viewModel: MonitorPedidosViewModel
    private val adapter = MonitorPedidoAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMonitorPedidosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootMonitorPedidos) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.rvPedidosMonitor.layoutManager = LinearLayoutManager(this)
        binding.rvPedidosMonitor.adapter = adapter
        // Evita flickering durante o polling de atualização
        binding.rvPedidosMonitor.itemAnimator = null

        val database = AppDatabase.getDatabase(this)
        val factory = MonitorPedidosViewModel.Factory(
            pedidoRepository = PedidoRepository(database.pedidoDao())
        )
        viewModel = ViewModelProvider(this, factory)[MonitorPedidosViewModel::class.java]

        configurarListeners()
        observarEstadoERefreshAutomatico()
    }

    private fun configurarListeners() {
        binding.btnVoltarMonitor.setOnClickListener {
            finish()
        }

        binding.btnAtualizarAgora.setOnClickListener {
            viewModel.sincronizarPedidos()
        }

        binding.btnSincronizarOffline.setOnClickListener {
            viewModel.sincronizarPedidos()
        }

        binding.btnFiltroTodos.setOnClickListener {
            viewModel.selecionarFiltro(null)
        }
        binding.btnFiltroRecebido.setOnClickListener {
            viewModel.selecionarFiltro(StatusPedidoCanonico.RECEBIDO)
        }
        binding.btnFiltroEmPreparo.setOnClickListener {
            viewModel.selecionarFiltro(StatusPedidoCanonico.EM_PREPARO)
        }
        binding.btnFiltroPronto.setOnClickListener {
            viewModel.selecionarFiltro(StatusPedidoCanonico.PRONTO)
        }
        binding.btnFiltroEntregue.setOnClickListener {
            viewModel.selecionarFiltro(StatusPedidoCanonico.ENTREGUE)
        }
        binding.btnFiltroCancelado.setOnClickListener {
            viewModel.selecionarFiltro(StatusPedidoCanonico.CANCELADO)
        }
    }

    private fun observarEstadoERefreshAutomatico() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { estado ->
                        renderizarEstado(estado)
                    }
                }
                // Polling automático a cada 4 segundos enquanto a tela está visível
                launch {
                    while (isActive) {
                        delay(4000L)
                        viewModel.sincronizarPedidos()
                    }
                }
            }
        }
    }

    private fun renderizarEstado(estado: MonitorPedidosUiState) {
        binding.txtSubtituloSincronizacao.text = estado.statusConexaoTexto

        if (estado.itensPendentesOffline > 0) {
            binding.bannerOfflinePendente.visibility = View.VISIBLE
            binding.txtMensagemOfflinePendente.text =
                "${estado.itensPendentesOffline} item(ns) salvo(s) offline aguardando sincronização."
        } else {
            binding.bannerOfflinePendente.visibility = View.GONE
        }

        atualizarBotoesFiltro(estado.filtroSelecionado)

        adapter.submitList(estado.pedidosFiltrados)

        if (estado.pedidosFiltrados.isEmpty() && !estado.isLoading) {
            binding.txtMonitorVazio.visibility = View.VISIBLE
        } else {
            binding.txtMonitorVazio.visibility = View.GONE
        }
    }

    private fun atualizarBotoesFiltro(selecionado: StatusPedidoCanonico?) {
        val mapa = mapOf(
            null to binding.btnFiltroTodos,
            StatusPedidoCanonico.RECEBIDO to binding.btnFiltroRecebido,
            StatusPedidoCanonico.EM_PREPARO to binding.btnFiltroEmPreparo,
            StatusPedidoCanonico.PRONTO to binding.btnFiltroPronto,
            StatusPedidoCanonico.ENTREGUE to binding.btnFiltroEntregue,
            StatusPedidoCanonico.CANCELADO to binding.btnFiltroCancelado
        )

        mapa.forEach { (status, botao) ->
            if (status == selecionado) {
                botao.setBackgroundResource(R.drawable.btn_fundo_selecionado)
                botao.setTextColor(Color.WHITE)
            } else {
                botao.setBackgroundResource(R.drawable.btn_fundo_cinza)
                botao.setTextColor(Color.parseColor("#571D22"))
            }
        }
    }
}
