package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.databinding.ItemDiscountLayoutBinding
import com.tdc.nhom6.roomio.models.Diiiiiscount

class DiscountAdapter(
    private val items: List<Diiiiiscount>,
    private val onEdit: (Diiiiiscount) -> Unit,
    private val onDelete: (Diiiiiscount) -> Unit
) : RecyclerView.Adapter<DiscountAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemDiscountLayoutBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Diiiiiscount) = with(binding) {

            tvCouponName.text = item.discountName
            tvCouponDescription.text = item.description
            tvCouponValue.text = "Giảm: ${item.discountPercent}%"

            tvCouponType.text =
                if (item.type == "infinite")
                    "Vô hạn (reset mỗi ngày: ${item.dailyReset})"
                else
                    "Hữu hạn (${item.availableCount} mã)"

            root.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiscountLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
