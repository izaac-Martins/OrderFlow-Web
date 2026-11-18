package com.example.cheirocafe

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cheirocafe.databinding.ItemPedidoMonitorBinding
import java.util.Locale

class MonitorPedidoAdapter :
    ListAdapter<PedidoResponseDTO, MonitorPedidoAdapter.MonitorViewHolder>(PedidoMonitorDiffCallback) {

    class MonitorViewHolder(val binding: ItemPedidoMonitorBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonitorViewHolder {
        val binding = ItemPedidoMonitorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MonitorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MonitorViewHolder, position: Int) {
        val pedido = getItem(position)
        val statusCanonico = pedido.statusCanonico()

        val idTexto = pedido.id?.let { "#$it" } ?: "#--"
        val mesaTexto = String.format(Locale("pt", "BR"), "Mesa %02d", pedido.numeroMesa)
        holder.binding.txtMesaPill.text = mesaTexto
        holder.binding.txtIdentificacaoPedido.text = "Pedido $idTexto"

        // Destaque operacional: borda verde esmeralda de 2dp quando PRONTO
        val ctx = holder.binding.root.context
        val densidade = ctx.resources.displayMetrics.density
        if (statusCanonico == StatusPedidoCanonico.PRONTO) {
            holder.binding.root.strokeColor = ContextCompat.getColor(ctx, R.color.status_pronto)
            holder.binding.root.strokeWidth = (2 * densidade).toInt()
        } else {
            holder.binding.root.strokeColor = ContextCompat.getColor(ctx, R.color.stroke_subtle)
            holder.binding.root.strokeWidth = (1 * densidade).toInt()
        }

        holder.binding.txtHorarioPedido.text = formatarDataHora(pedido.criadoEm)

        holder.binding.txtBadgeStatus.text = statusCanonico.rotuloExibicao
        holder.binding.txtBadgeStatus.setBackgroundColor(statusCanonico.corFundoHex)
        holder.binding.txtBadgeStatus.setTextColor(statusCanonico.corTextoHex)

        holder.binding.progressCicloPedido.progress = statusCanonico.etapaProgresso
        holder.binding.progressCicloPedido.progressTintList =
            ColorStateList.valueOf(statusCanonico.corTextoHex)
        holder.binding.txtDescricaoEtapa.text = statusCanonico.descricaoEtapa

        val resumoItens = if (pedido.itens.isEmpty()) {
            "Sem itens detalhados"
        } else {
            pedido.itens.joinToString("\n") { item ->
                val sufixoTam = item.tamanhoEscolhido
                    ?.takeIf { it.isNotBlank() && it != "Padrão" }
                    ?.let { " ($it)" }
                    .orEmpty()
                val sufixoAdic = item.adicionaisEscolhidos
                    ?.takeIf { it.isNotBlank() && it != "Nenhum" }
                    ?.let { " [+$it]" }
                    .orEmpty()
                val precoItemStr = String.format(
                    Locale("pt", "BR"),
                    "R$ %.2f",
                    item.precoTotalEscala2()
                )
                "• ${item.quantidade}x ${item.nomeProduto}$sufixoTam$sufixoAdic — $precoItemStr (${item.pagadorItem})"
            }
        }
        holder.binding.txtResumoItensPedido.text = resumoItens

        holder.binding.txtValorTotalMonitor.text = String.format(
            Locale("pt", "BR"),
            "R$ %.2f",
            pedido.valorTotalEscala2()
        )
    }

    private fun formatarDataHora(iso: String?): String {
        if (iso.isNullOrBlank()) return "Sincronizado com a cozinha"
        return try {
            val partes = iso.split("T")
            if (partes.size >= 2) {
                val hora = partes[1].take(8)
                "Atualizado às $hora"
            } else {
                iso
            }
        } catch (_: Exception) {
            iso
        }
    }

    object PedidoMonitorDiffCallback : DiffUtil.ItemCallback<PedidoResponseDTO>() {
        override fun areItemsTheSame(
            oldItem: PedidoResponseDTO,
            newItem: PedidoResponseDTO
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: PedidoResponseDTO,
            newItem: PedidoResponseDTO
        ): Boolean {
            return oldItem == newItem
        }
    }
}
