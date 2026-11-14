package com.example.cheirocafe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cheirocafe.databinding.ItemProdutoBinding

class SubcategoriaAdapter(
    private val cliqueItem: (SubcategoriaItem) -> Unit
) : ListAdapter<SubcategoriaItem, SubcategoriaAdapter.SubcategoriaViewHolder>(SubcategoriaDiffCallback) {

    class SubcategoriaViewHolder(val binding: ItemProdutoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubcategoriaViewHolder {
        val binding = ItemProdutoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SubcategoriaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubcategoriaViewHolder, position: Int) {
        val item = getItem(position)

        holder.binding.tvCategoriaBadge.visibility = View.VISIBLE
        holder.binding.tvCategoriaBadge.text = "BEBIDAS"
        holder.binding.tvNomeProduto.text = item.nome
        holder.binding.ivProduto.setImageResource(item.imagemResId)
        holder.binding.tvVariantesResumo.visibility = View.VISIBLE
        holder.binding.tvVariantesResumo.text = "Toque para explorar"
        holder.binding.tvPrecoProduto.visibility = View.GONE

        holder.binding.root.setOnClickListener {
            cliqueItem(item)
        }
    }

    object SubcategoriaDiffCallback : DiffUtil.ItemCallback<SubcategoriaItem>() {
        override fun areItemsTheSame(oldItem: SubcategoriaItem, newItem: SubcategoriaItem): Boolean {
            return oldItem.nome == newItem.nome
        }

        override fun areContentsTheSame(oldItem: SubcategoriaItem, newItem: SubcategoriaItem): Boolean {
            return oldItem == newItem
        }
    }
}
