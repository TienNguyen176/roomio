package com.tdc.nhom6.roomio.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.databinding.ItemFacilityBinding
import com.tdc.nhom6.roomio.models.Facility

class FacilityHotelAdapter(
    private val context: Context,
    private val listFacility: List<Facility>
) : RecyclerView.Adapter<FacilityHotelAdapter.FacilityViewHolder>() {

    class FacilityViewHolder(val binding: ItemFacilityBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacilityViewHolder {
        val binding = ItemFacilityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FacilityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FacilityViewHolder, position: Int) {
        val facility = listFacility[position]

        holder.binding.tvFacilityName.text = facility.facilities_name

        Glide.with(context)
            .load(facility.iconUrl)
            .into(holder.binding.iconFacility)
    }

    override fun getItemCount(): Int = listFacility.size
}