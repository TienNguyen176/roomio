package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.databinding.ItemServiceBinding
import com.tdc.nhom6.roomio.models.ServiceModel

class ServiceAdminAdapter(
    private val items: MutableList<ServiceModel>,
    private val onItemClick: (ServiceModel, Int) -> Unit
) : RecyclerView.Adapter<ServiceAdminAdapter.ServiceViewHolder>() {

    inner class ServiceViewHolder(val binding: ItemServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(service: ServiceModel, position: Int) {
            binding.tvServiceName.text = service.service_name
            binding.tvServiceDesc.text = service.description

            binding.root.setOnClickListener {
                onItemClick(service, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val binding = ItemServiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<ServiceModel>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}