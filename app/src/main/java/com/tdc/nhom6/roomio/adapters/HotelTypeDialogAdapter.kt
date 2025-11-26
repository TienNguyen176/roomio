package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.tdc.nhom6.roomio.databinding.ItemHotelTypeDialogBinding
import com.tdc.nhom6.roomio.models.HotelTypeModel

class HotelTypeDialogAdapter(
    private val context: Context,
    private val hotelTypes: List<HotelTypeModel>
) : BaseAdapter() {

    override fun getCount(): Int = hotelTypes.size
    override fun getItem(position: Int): Any = hotelTypes[position]
    override fun getItemId(position: Int): Long = position.toLong()

    @SuppressLint("SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding: ItemHotelTypeDialogBinding

        if (convertView == null) {
            binding = ItemHotelTypeDialogBinding.inflate(LayoutInflater.from(context), parent, false)
            binding.root.tag = binding
        } else {
            binding = convertView.tag as ItemHotelTypeDialogBinding
        }

        val item = hotelTypes[position]
        binding.tvIndex.text = "${position + 1}."
        binding.tvHotelTypeName.text = item.typeName

        return binding.root
    }
}
