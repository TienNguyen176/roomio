package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R

class ServiceFeeAdapter(
    private val items: MutableList<ServiceItem>,
    private val onTotalChanged: (Double) -> Unit
) : RecyclerView.Adapter<ServiceFeeAdapter.VH>() {

    data class ServiceItem(
        val iconRes: Int,
        val name: String,
        val price: Double,
        var checked: Boolean = false
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_service_fee, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position]) {
            onTotalChanged(total())
        }
    }

    private fun total(): Double = items.filter { it.checked }.sumOf { it.price }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val name: TextView = itemView.findViewById(R.id.tvName)
        private val price: TextView = itemView.findViewById(R.id.tvPrice)
        private val cb: CheckBox = itemView.findViewById(R.id.cb)

        fun bind(item: ServiceItem, onChange: () -> Unit) {
            icon.setImageResource(item.iconRes)
            name.text = item.name
            price.text = String.format("% ,.0f VND", item.price)
            cb.isChecked = item.checked
            cb.setOnCheckedChangeListener { _, isChecked ->
                item.checked = isChecked
                onChange()
            }
        }
    }
}







