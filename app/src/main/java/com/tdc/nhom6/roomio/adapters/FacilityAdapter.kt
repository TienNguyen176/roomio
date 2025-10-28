package com.tdc.nhom6.roomio.models

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R

class FacilityAdapter(
    private val context: Context,
    private val listFacility:List<Facility>
): BaseAdapter() {
    override fun getCount(): Int =listFacility.size

    override fun getItem(position: Int): Any =listFacility[position]

    override fun getItemId(position: Int): Long =position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val inflater=LayoutInflater.from(context)
        var itemView=inflater.inflate(R.layout.item_facility,parent,false)
        val icon=itemView.findViewById<ImageView>(R.id.iconFacility)
        val facilityName=itemView.findViewById<TextView>(R.id.tvFacilityName)

        val facility=listFacility[position]
        facilityName.text=facility.facilities_name
        Glide.with(context)
            .load(facility.iconUrl)
            .into(icon)
        return itemView
    }
}