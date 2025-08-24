package com.example.aihealth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class FavMenuAdapter(
    private val onClick: (MenuItem) -> Unit
) : ListAdapter<MenuItem, FavMenuAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<MenuItem>() {
        override fun areItemsTheSame(oldItem: MenuItem, newItem: MenuItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MenuItem, newItem: MenuItem) = oldItem == newItem
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val iv: ImageView = view.findViewById(R.id.iv_food)
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_menu, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        // 이미지
        if (!item.imageUrl.isNullOrBlank()) {
            Glide.with(holder.itemView).load(item.imageUrl).thumbnail(0.25f).into(holder.iv)
        } else {
            holder.iv.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // ✅ 레시피 이름(name 필드) 표시
        holder.tvTitle.text = item.name ?: item.id

        // 클릭 → 상세
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
