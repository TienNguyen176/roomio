package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.databinding.ItemFacilitySelectorBinding
import com.tdc.nhom6.roomio.models.FacilityModel
import com.tdc.nhom6.roomio.models.FacilityPrice

class FacilitySelectorAdapter(
    private val facilities: List<FacilityModel>,
    private val preselected: MutableList<FacilityPrice>,
    private val onEnterPrice: (FacilityModel, () -> Unit) -> Unit
) : RecyclerView.Adapter<FacilitySelectorAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemFacilitySelectorBinding) :
        RecyclerView.ViewHolder(binding.root)

    private val selectedMap = mutableMapOf<String, Boolean>()

    init {
        preselected.forEach { selectedMap[it.facilityId] = true }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFacilitySelectorBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = facilities.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val facility = facilities[position]
        val binding = holder.binding

        binding.cbSelected.setOnCheckedChangeListener(null)

        binding.cbSelected.text = facility.facilities_name
        binding.cbSelected.isChecked = selectedMap[facility.id] == true

        binding.cbSelected.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                selectedMap[facility.id] = true

                onEnterPrice(facility) {
                    selectedMap[facility.id] = false
                    binding.cbSelected.isChecked = false
                }

            } else {
                selectedMap[facility.id] = false
                preselected.removeAll { it.facilityId == facility.id }
            }
        }
    }

    fun getSelectedFacilities(): List<FacilityModel> =
        facilities.filter { selectedMap[it.id] == true }
}
