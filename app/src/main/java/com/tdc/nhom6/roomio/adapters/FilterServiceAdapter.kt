package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.databinding.ItemFilterServiceBinding
import com.tdc.nhom6.roomio.models.Facility
import com.tdc.nhom6.roomio.models.Service

class FilterServiceAdapter(
    private val items: List<Service>,
    private val selectedIds: MutableSet<String>
) : RecyclerView.Adapter<FilterServiceAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemFilterServiceBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder{
        val binding = ItemFilterServiceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val facility = items[position]
        val b = holder.binding

        b.tvName.text = facility.service_name
        b.cbSelected.isChecked = selectedIds.contains(facility.id)

        // Toggle khi click vào cả layout
        b.root.setOnClickListener {
            b.cbSelected.isChecked = !b.cbSelected.isChecked
        }

        // Cập nhật lựa chọn
        b.cbSelected.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedIds.add(facility.id)
            else selectedIds.remove(facility.id)
        }
    }
}
