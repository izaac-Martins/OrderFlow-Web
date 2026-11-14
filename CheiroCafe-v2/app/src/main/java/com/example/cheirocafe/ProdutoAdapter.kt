package com.example.cheirocafe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cheirocafe.databinding.ItemProdutoBinding
import java.util.Locale

class ProdutoAdapter(
    private val onItemClick: (Produto) -> Unit
) : ListAdapter<Produto, ProdutoAdapter.ProdutoViewHolder>(ProdutoDiffCallback) {

    class ProdutoViewHolder(val binding: ItemProdutoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProdutoViewHolder {
        val binding = ItemProdutoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProdutoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProdutoViewHolder, position: Int) {
        val produto = getItem(position)

        holder.binding.tvNomeProduto.text = produto.nome

        val subcatFormatada = produto.subcategoria?.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
        holder.binding.tvCategoriaBadge.visibility = View.VISIBLE
        holder.binding.tvCategoriaBadge.text = "${produto.categoria.name}$subcatFormatada"

        holder.binding.tvVariantesResumo.visibility = View.VISIBLE
        if (produto.variantes.isNotEmpty()) {
            val opcoes = produto.variantes.joinToString(", ") { it.nome }
            holder.binding.tvVariantesResumo.text = "Opções: $opcoes"
        } else {
            holder.binding.tvVariantesResumo.text = "Porção / Tamanho padrão"
        }

        holder.binding.tvPrecoProduto.visibility = View.VISIBLE
        val menorPreco = produto.menorPrecoExibicao()
        val precoFormatado = String.format(Locale("pt", "BR"), "R$ %.2f", menorPreco)
        holder.binding.tvPrecoProduto.text = if (produto.variantes.size > 1) {
            "A partir de $precoFormatado"
        } else {
            precoFormatado
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
        holder.binding.ivProduto.setImageResource(imagemRes)

        holder.binding.root.setOnClickListener {
            onItemClick(produto)
        }
    }

    object ProdutoDiffCallback : DiffUtil.ItemCallback<Produto>() {
        override fun areItemsTheSame(oldItem: Produto, newItem: Produto): Boolean {
            return if (oldItem.id != null && newItem.id != null) {
                oldItem.id == newItem.id
            } else {
                oldItem.nome == newItem.nome && oldItem.categoria == newItem.categoria
            }
        }

        override fun areContentsTheSame(oldItem: Produto, newItem: Produto): Boolean {
            return oldItem == newItem
        }
    }
}
