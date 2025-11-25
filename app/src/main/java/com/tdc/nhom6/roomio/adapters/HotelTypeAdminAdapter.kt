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
) : RecyclerView.Adapter<HotelTypeAdminAdapter.HotelTypeAdminViewHolder>() {

    inner class HotelTypeAdminViewHolder(val binding: ItemHotelTypesAdminBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(hotelType: HotelTypeModel, position: Int) {
            binding.tvHotelTypeName.text = hotelType.type_name
            binding.tvHotelTypeDesc.text = hotelType.description

            binding.root.setOnClickListener {
                onItemClick(hotelType, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotelTypeAdminViewHolder {
        val binding = ItemHotelTypesAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HotelTypeAdminViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HotelTypeAdminViewHolder, position: Int) {
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