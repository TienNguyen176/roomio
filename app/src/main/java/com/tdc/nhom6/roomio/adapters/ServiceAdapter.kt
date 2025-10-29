package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.ServiceModel

class ServiceAdapter(
    private val items: MutableList<ServiceModel>,
    private val onItemClick: (ServiceModel, Int) -> Unit
) : RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder>() {

    inner class ServiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvServiceName)
        val tvDesc: TextView = itemView.findViewById(R.id.tvServiceDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service, parent, false)
        return ServiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val service = items[position]
        holder.tvName.text = service.service_name
        holder.tvDesc.text = service.description

        holder.itemView.setOnClickListener {
            onItemClick(service, position)
        }
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<ServiceModel>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}
