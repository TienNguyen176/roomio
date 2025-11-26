package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.databinding.ItemHotelTypesAdminBinding
import com.tdc.nhom6.roomio.models.HotelTypeModel

class HotelTypeAdminAdapter(
    private val items: MutableList<HotelTypeModel>,
    private val onItemClick: (HotelTypeModel, Int) -> Unit
) : RecyclerView.Adapter<HotelTypeAdminAdapter.TypeViewHolder>() {

    inner class TypeViewHolder(val binding: ItemHotelTypesAdminBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(type: HotelTypeModel, position: Int) {
            binding.tvHotelTypeName.text = type.typeName
            binding.tvHotelTypeDesc.text = type.description
            binding.root.setOnClickListener { onItemClick(type, position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TypeViewHolder {
        val binding = ItemHotelTypesAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TypeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TypeViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<HotelTypeModel>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}