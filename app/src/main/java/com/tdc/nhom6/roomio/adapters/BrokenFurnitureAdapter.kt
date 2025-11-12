package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout
import com.tdc.nhom6.roomio.R

class BrokenFurnitureAdapter(
    private val items: MutableList<BrokenItem>,
    private val onChange: () -> Unit
) : RecyclerView.Adapter<BrokenFurnitureAdapter.VH>() {

    data class BrokenItem(
        val name: String,
        val pricePerItem: Double,
        var checked: Boolean = false,
        var description: String = ""
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_broken_furniture, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position]) { onChange() }
    }

    fun totalCharges(): Double {
        // If prices are not set, broken items contribute 0 by default
        return items.filter { it.checked }.sumOf { it.pricePerItem }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cb: CheckBox = itemView.findViewById(R.id.cbItem)
        private val til: TextInputLayout = itemView.findViewById(R.id.tilDesc)
        private val et by lazy { til.editText }

        fun bind(item: BrokenItem, onChange: () -> Unit) {
            cb.text = item.name
            cb.isChecked = item.checked
            til.visibility = if (item.checked) View.VISIBLE else View.GONE
            et?.setText(item.description)

            cb.setOnCheckedChangeListener { _, isChecked ->
                item.checked = isChecked
                til.visibility = if (isChecked) View.VISIBLE else View.GONE
                onChange()
            }
            et?.setOnFocusChangeListener { _, _ ->
                item.description = et?.text?.toString() ?: ""
            }
        }
    }
}







