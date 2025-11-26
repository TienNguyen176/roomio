package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.databinding.ItemPaymentApproverBinding
import com.tdc.nhom6.roomio.models.PaymentItemModel

class PaymentApproverAdapter(
    private val list: MutableList<PaymentItemModel>,
    private val onItemClick: ((PaymentItemModel) -> Unit)? = null
) : RecyclerView.Adapter<PaymentApproverAdapter.PaymentViewHolder>() {

    inner class PaymentViewHolder(val binding: ItemPaymentApproverBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(list[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val binding = ItemPaymentApproverBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PaymentViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val item = list[position]
        holder.binding.apply {
            tvBookingId.text = "ID: ${item.bookingId}"
            tvCheckInDay.text = "CheckIn: ${item.checkInDate}"
            tvCheckOutDay.text = "CheckOut: ${item.checkOutDay}"
            tvTotalPaidAmount.text = "Số tiền đã thanh toán: ${item.totalPaidAmount ?: 0}"
            tvStatus.text = item.status
        }
    }

    override fun getItemCount(): Int = list.size

    // Cập nhật dữ liệu
    fun setData(newList: List<PaymentItemModel>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
}