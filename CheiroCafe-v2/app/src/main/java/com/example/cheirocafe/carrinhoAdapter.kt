package com.example.cheirocafe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cheirocafe.databinding.ItemCarrinhoBinding
import java.util.Locale

class CarrinhoAdapter(
    private val cliqueRemover: (PedidoEntity) -> Unit
) : ListAdapter<PedidoEntity, CarrinhoAdapter.CarrinhoViewHolder>(PedidoDiffCallback) {

    class CarrinhoViewHolder(val binding: ItemCarrinhoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarrinhoViewHolder {
        val binding = ItemCarrinhoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CarrinhoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CarrinhoViewHolder, position: Int) {
        val item = getItem(position)

        val sufixoTamanho = if (!item.tamanhoEscolhido.isNullOrBlank() && item.tamanhoEscolhido != "Padrão") {
            " (${item.tamanhoEscolhido})"
        } else {
            ""
        }
        holder.binding.txtNomeProduto.text = "${item.nomeProduto}$sufixoTamanho"
        holder.binding.txtQuantidade.text = "Qtd: ${item.quantidade}x • ${item.pagadorItem}"
        holder.binding.txtPrecoTotal.text = String.format(Locale("pt", "BR"), "R$ %.2f", item.precoTotalItem)

        if (item.adicionaisEscolhidos.isNotBlank() && item.adicionaisEscolhidos != "Nenhum") {
            holder.binding.txtAdicionais.visibility = View.VISIBLE
            holder.binding.txtAdicionais.text = "Adicionais: ${item.adicionaisEscolhidos}"
        } else {
            holder.binding.txtAdicionais.visibility = View.GONE
        }

        holder.binding.root.setOnLongClickListener {
            cliqueRemover(item)
            true
        }
    }

    object PedidoDiffCallback : DiffUtil.ItemCallback<PedidoEntity>() {
        override fun areItemsTheSame(oldItem: PedidoEntity, newItem: PedidoEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PedidoEntity, newItem: PedidoEntity): Boolean {
            return oldItem == newItem
        }
    }
}
