
package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.databinding.ItemFilterFacilityBinding
import com.tdc.nhom6.roomio.models.Facility
import com.tdc.nhom6.roomio.models.Service

class FilterTienIchAdapter(
    private val items: List<Facility>,
    private val selectedIds: MutableSet<String>
) : RecyclerView.Adapter<FilterTienIchAdapter.FacilityViewHolder>() {

    inner class FacilityViewHolder(val binding: ItemFilterFacilityBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacilityViewHolder {
        val binding = ItemFilterFacilityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FacilityViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: FacilityViewHolder, position: Int) {
        val service = items[position]
        val b = holder.binding

        b.tvName.text = service.facilities_name
        b.cbSelected.isChecked = selectedIds.contains(service.id)

        // Toggle khi click vào cả layout
        b.root.setOnClickListener {
            b.cbSelected.isChecked = !b.cbSelected.isChecked
        }

        // Cập nhật lựa chọn
        b.cbSelected.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedIds.add(service.id)
            else selectedIds.remove(service.id)
        }
    }
}


