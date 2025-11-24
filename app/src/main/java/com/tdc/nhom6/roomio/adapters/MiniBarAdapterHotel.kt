package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.databinding.ItemMinibarLayoutBinding
import com.tdc.nhom6.roomio.models.MinibarItem

class MinibarAdapterHotel(
    private val items: MutableList<MinibarItem>,
    private val listener: MinibarListener
) : RecyclerView.Adapter<MinibarAdapterHotel.MVH>() {

    interface MinibarListener {
        fun onEdit(item: MinibarItem)
        fun onDelete(item: MinibarItem, position: Int)
    }

    inner class MVH(val binding: ItemMinibarLayoutBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MVH {
        val binding = ItemMinibarLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MVH(binding)
    }

    override fun onBindViewHolder(holder: MVH, position: Int) {
        val item = items[position]

        holder.binding.tvName.text = item.name
        holder.binding.tvPrice.text = "${item.price} đ"

        // Sửa
        holder.binding.btnEdit.setOnClickListener {
            listener.onEdit(item)
        }

        // Xóa
        holder.binding.btnDelete.setOnClickListener {
            listener.onDelete(item, position)
        }
    }

    override fun getItemCount(): Int = items.size

    fun removeItem(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun updateItem(position: Int, newItem: MinibarItem) {
        items[position] = newItem
        notifyItemChanged(position)
    }
}
