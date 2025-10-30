package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.GuestDetailActivity
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter.Format
import com.tdc.nhom6.roomio.databinding.ItemPaymentMethodBinding
import com.tdc.nhom6.roomio.models.Discount
import com.tdc.nhom6.roomio.models.PaymentMethod

@Suppress("DEPRECATION")
class PaymentMethodAdapter(
    private val listPaymentMethod: MutableList<PaymentMethod>,
    private var requiredAmount: Double, // Đã chuyển thành var
    private var userWalletBalance: Double // Đã chuyển thành var
) : RecyclerView.Adapter<PaymentMethodAdapter.ViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    var onPaymentMethodSelected: ((PaymentMethod) -> Unit)? = null

    // 1. Hàm cập nhật số tiền yêu cầu
    fun updateRequiredAmount(newAmount: Double) {
        if (this.requiredAmount != newAmount) {
            this.requiredAmount = newAmount
            notifyDataSetChanged()
        }
    }

    // 2. Hàm cập nhật số dư ví
    fun updateWalletBalance(newBalance: Double) {
        if (this.userWalletBalance != newBalance) {
            this.userWalletBalance = newBalance
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPaymentMethodBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    @SuppressLint("ResourceType")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = listPaymentMethod[position]

        val isWalletMethod = item.paymentMethodName.equals("Travel wallet", ignoreCase = true)

        var isSufficientBalance = true

        if (isWalletMethod) {
            isSufficientBalance = userWalletBalance >= requiredAmount

            holder.binding.tvBalance.isVisible = true
            holder.binding.tvBalance.text = "Balance: ${Format.formatCurrency(userWalletBalance)}"

            val colorResId = if (isSufficientBalance) R.color.green else R.color.red
            holder.binding.tvBalance.setTextColor(ContextCompat.getColor(holder.itemView.context, colorResId))

            // Nếu không đủ tiền, hủy chọn phương thức ví nếu nó đang được chọn
            if (!isSufficientBalance && selectedPosition == position) {
                selectedPosition = RecyclerView.NO_POSITION
            }

        } else {
            holder.binding.tvBalance.isVisible = false
        }

        // Vô hiệu Hóa (Disable) Item và Radio Button
        holder.itemView.isEnabled = isSufficientBalance || !isWalletMethod
        holder.itemView.alpha = if (isSufficientBalance || !isWalletMethod) 1.0f else 0.4f
        holder.binding.radId.isEnabled = isSufficientBalance || !isWalletMethod
        
        // Chỉ chọn item nếu nó là item được chọn và không bị disable (hoặc không phải là ví)
        holder.binding.radId.isChecked = position == selectedPosition && (isSufficientBalance || !isWalletMethod)

        holder.onBind(item)

        holder.itemView.setOnClickListener {
            if (holder.itemView.isEnabled) {
                val previousSelectedPosition = selectedPosition

                selectedPosition = holder.adapterPosition

                if (previousSelectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(previousSelectedPosition)
                }

                notifyItemChanged(selectedPosition)

                onPaymentMethodSelected?.invoke(item)
            }
        }
    }

    override fun getItemCount(): Int = listPaymentMethod.size


    class ViewHolder(val binding: ItemPaymentMethodBinding) : RecyclerView.ViewHolder(binding.root) {

        private var discountListener: ListenerRegistration? = null

        @SuppressLint("UseCompatLoadingForDrawables")
        fun onBind(itemMethod: PaymentMethod) {
            binding.radId.text = itemMethod.paymentMethodName

            val drawable = itemMethod.iconId?.let {itemView.context.getDrawable(it)}

            binding.radId.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)

            // ⭐ Dừng listener cũ trước khi thiết lập cái mới (quan trọng cho tái sử dụng ViewHolder)
            discountListener?.remove()

            if(itemMethod.discountId != null){
                itemMethod.discountId?.let { id ->
                    // ⭐ Gọi hàm load Realtime
                    discountListener = loadDiscountDescription(id) { description ->
                        // Cập nhật mô tả nếu có
                        if (!description.isNullOrEmpty()) {
                            binding.tvDiscountDescription.text = description
                        } else {
                            binding.tvDiscountDescription.text = itemMethod.description
                        }
                    }
                }
            }
            else{
                binding.tvDiscountDescription.text = itemMethod.description
            }
        }

        private fun loadDiscountDescription(discountId: String, onResult: (String?) -> Unit): ListenerRegistration {
            // Sử dụng FirebaseFirestore.getInstance() để độc lập với Activity
            return FirebaseFirestore.getInstance().collection("discounts")
                .document(discountId)
                .addSnapshotListener { dataSnapshot, exception ->
                    if (exception != null) {
                        Log.e("Firestore", "Lỗi khi lắng nghe Discount: ", exception)
                        onResult(null)
                        return@addSnapshotListener
                    }

                    var description: String? = null
                    if (dataSnapshot != null && dataSnapshot.exists()) {
                        try {
                            val discount = dataSnapshot.toObject(Discount::class.java)
                            description = discount?.discountDescription
                        } catch (ex: Exception) {
                            Log.e("Firebase", "Lỗi chuyển đổi dữ liệu cho Discount: ${dataSnapshot.id}", ex)
                        }
                    }
                    onResult(description)
                }
        }
    }
}