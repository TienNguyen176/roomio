package com.tdc.nhom6.roomio.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ItemFacilityLayoutBinding
import com.tdc.nhom6.roomio.models.Facility

class FacilityAdapter(
    private val context: Context,
    private val facilities: List<Facility>
) : RecyclerView.Adapter<FacilityAdapter.FacilityViewHolder>() {

    private val selectedFacilityIds: MutableSet<String> = mutableSetOf()

    class FacilityViewHolder(val binding: ItemFacilityLayoutBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacilityViewHolder {
        val binding = ItemFacilityLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FacilityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FacilityViewHolder, position: Int) {
        val facility = facilities[position]
        val isSelected = selectedFacilityIds.contains(facility.id)

        holder.binding.tvFacilityName.text = facility.facilities_name
        holder.binding.cbSelected.isChecked = isSelected

        // Set CardView background color based on selection
        val colorRes = if (isSelected) R.color.light_blue else R.color.white
        holder.binding.cardFacility.setCardBackgroundColor(context.getColor(colorRes))

        holder.binding.root.setOnClickListener {
            // Toggle selection
            if (selectedFacilityIds.contains(facility.id)) {
                selectedFacilityIds.remove(facility.id)
            } else {
                selectedFacilityIds.add(facility.id)
            }
            notifyItemChanged(position) // Update UI for this item
        }
    }

    override fun getItemCount() = facilities.size

    fun getSelectedFacilityIds(): Set<String> = selectedFacilityIds.toSet()
}
