package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ItemPaymentApproverBinding
import com.tdc.nhom6.roomio.models.PaymentItemModel
import java.text.SimpleDateFormat
import java.util.*

class PaymentApproverAdapter(
    private val list: MutableList<PaymentItemModel>,
    private val onItemClick: ((PaymentItemModel) -> Unit)? = null
) : RecyclerView.Adapter<PaymentApproverAdapter.PaymentViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy > HH:mm", Locale.getDefault())

    // Map trạng thái -> màu
    private val statusColorMap = mapOf(
        "paid" to R.color.green,
        "cancelled" to R.color.red_500,
        "pending" to R.color.yellow
    )

    // Map trạng thái -> tên hiển thị từ Firestore
    var statusNameMap: Map<String, String> = mapOf()

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
            tvBookingId.text = "ID: ${item.invoiceId}"
            tvCheckInDay.text =
                "CheckIn: ${item.checkInDate?.toDate()?.let { dateFormat.format(it) } ?: ""}"
            tvCheckOutDay.text =
                "CheckOut: ${item.checkOutDay?.toDate()?.let { dateFormat.format(it) } ?: ""}"
            tvNgayThanhToan.text =
                "Ngày thanh toán: ${item.createdAt?.toDate()?.let { dateFormat.format(it) } ?: ""}"
            tvTotalPaidAmount.text = "Số tiền đã thanh toán: ${item.totalPaidAmount ?: 0}"

            // Hiển thị tên status đẹp
            val displayStatus = statusNameMap[item.status] ?: item.status
            tvStatus.text = displayStatus

            // Đổi màu status
            val colorRes = statusColorMap[item.status] ?: R.color.gray_700
            tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, colorRes))
        }
    }

    override fun getItemCount(): Int = list.size

    fun setData(newList: List<PaymentItemModel>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
}
