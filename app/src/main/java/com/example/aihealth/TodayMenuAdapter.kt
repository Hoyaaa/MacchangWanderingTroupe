package com.example.aihealth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class TodayMenuAdapter(
    private val onClick: (MenuItem) -> Unit,
    private val onToggleFav: (MenuItem) -> Unit
) : ListAdapter<MenuItem, TodayMenuAdapter.VH>(DIFF) {

    var favoriteIds: Set<String> = emptySet()
        set(value) { field = value; notifyDataSetChanged() }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MenuItem>() {
            override fun areItemsTheSame(old: MenuItem, new: MenuItem) = old.id == new.id
            override fun areContentsTheSame(old: MenuItem, new: MenuItem) = old == new
        }
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivFood: ImageView = v.findViewById(R.id.iv_food)
        val tvName: TextView = v.findViewById(R.id.tv_food_name)
        val btnFav: ImageButton = v.findViewById(R.id.btn_fav)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_today_menu, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvName.text = item.name

        if (!item.imageUrl.isNullOrBlank()) {
            Glide.with(holder.itemView).load(item.imageUrl).thumbnail(0.25f).into(holder.ivFood)
        } else {
            holder.ivFood.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        val isFav = favoriteIds.contains(item.id)
        holder.btnFav.setImageResource(if (isFav) R.drawable.ic_star_border_24 else R.drawable.ic_star_border_24)
        holder.btnFav.alpha = if (isFav) 1.0f else 0.7f

        holder.itemView.setOnClickListener { onClick(item) }
        holder.btnFav.setOnClickListener { onToggleFav(item) }
    }
}
