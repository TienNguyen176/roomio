package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ItemFacilitiesBinding
import com.tdc.nhom6.roomio.models.FacilitiesModel

class FacilitiesAdapter(
    private val facilitiesList: MutableList<FacilitiesModel>,
    private val onItemClick: (FacilitiesModel, Int) -> Unit
) : RecyclerView.Adapter<FacilitiesAdapter.FacilityViewHolder>() {

    inner class FacilityViewHolder(val binding: ItemFacilitiesBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacilityViewHolder {
        val binding = ItemFacilitiesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FacilityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FacilityViewHolder, position: Int) {
        val facility = facilitiesList[position]
        with(holder.binding) {
            tvFacilityName.text = facility.facilities_name
            tvFacilityDesc.text = facility.description
            Glide.with(imgFacility.context)
                .load(facility.iconUrl)
                .placeholder(R.drawable.ic_not_image)
                .into(imgFacility)
            root.setOnClickListener {
                onItemClick(facility, position)
            }
        }
    }

    override fun getItemCount(): Int = facilitiesList.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<FacilitiesModel>) {
        facilitiesList.clear()
        facilitiesList.addAll(newList)
        notifyDataSetChanged()
    }
}
