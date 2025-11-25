package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.LostItem

class LostItemAdapter(
    private val items: MutableList<LostItem>,
    private val onChange: () -> Unit
) : RecyclerView.Adapter<LostItemAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lost_item, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position]) { onChange() }
    }

    fun replaceItems(newItems: List<LostItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        onChange()
    }

    fun totalCharges(): Double = items.sumOf { (if (it.checked) it.quantity else 0) * it.pricePerItem }

    fun getCheckedItems(): List<LostItem> = items.filter { it.checked }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cb: CheckBox = itemView.findViewById(R.id.cbItem)
        private val btnMinus: Button = itemView.findViewById(R.id.btnMinus)
        private val btnPlus: Button = itemView.findViewById(R.id.btnPlus)
        private val tvQty: TextView = itemView.findViewById(R.id.tvQty)

        fun bind(item: LostItem, onChange: () -> Unit) {
            cb.text = item.name
            cb.isChecked = item.checked
            tvQty.text = item.quantity.toString()
            
            // Update button states based on checked state
            updateButtonStates(item.checked, item.quantity)

            cb.setOnCheckedChangeListener { _, isChecked ->
                item.checked = isChecked
                if (isChecked) {
                    // When checked, set quantity to 1 if it was 0
                    if (item.quantity == 0) {
                        item.quantity = 1
                        tvQty.text = item.quantity.toString()
                    }
                } else {
                    // When unchecked, reset quantity to 0
                    item.quantity = 0
                    tvQty.text = item.quantity.toString()
                }
                // Update button states
                updateButtonStates(isChecked, item.quantity)
                onChange()
            }

            btnMinus.setOnClickListener {
                if (item.checked && item.quantity > 1) {
                    item.quantity -= 1
                    tvQty.text = item.quantity.toString()
                    updateButtonStates(item.checked, item.quantity)
                    onChange()
                }
            }
            btnPlus.setOnClickListener {
                if (item.checked) {
                    item.quantity += 1
                    tvQty.text = item.quantity.toString()
                    updateButtonStates(item.checked, item.quantity)
                    onChange()
                }
            }
        }
        
        private fun updateButtonStates(isChecked: Boolean, quantity: Int) {
            // Enable/disable buttons based on checkbox state
            btnMinus.isEnabled = isChecked && quantity > 1
            btnPlus.isEnabled = isChecked
            
            // Set alpha for visual feedback (blurred when disabled)
            btnMinus.alpha = if (isChecked && quantity > 1) 1.0f else 0.5f
            btnPlus.alpha = if (isChecked) 1.0f else 0.5f
        }
    }
}





