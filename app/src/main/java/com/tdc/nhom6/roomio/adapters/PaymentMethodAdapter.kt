package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.RoomTypeAdapter.Format
import com.tdc.nhom6.roomio.databinding.ItemPaymentMethodBinding
import com.tdc.nhom6.roomio.models.Discount
import com.tdc.nhom6.roomio.models.DiscountPaymentMethod
import com.tdc.nhom6.roomio.models.PaymentMethod

@Suppress("DEPRECATION")
class PaymentMethodAdapter(
    private val listPaymentMethod: MutableList<PaymentMethod>,
    private var requiredAmount: Double,
    private var userWalletBalance: Double
) : RecyclerView.Adapter<PaymentMethodAdapter.ViewHolder>() {

    var selectedPosition = RecyclerView.NO_POSITION
    var onPaymentMethodSelected: ((PaymentMethod) -> Unit)? = null

    private object Payload {
        const val CHECK_STATE = "check_state_changed"
    }

    // Cập nhật số tiền yêu cầu
    fun updateRequiredAmount(newAmount: Double) {
        if (this.requiredAmount != newAmount) {
            this.requiredAmount = newAmount
            notifyDataSetChanged()
        }
    }

    // Cập nhật số dư ví
    fun updateWalletBalance(newBalance: Double) {
        if (this.userWalletBalance != newBalance) {
            this.userWalletBalance = newBalance

            // Hủy chọn nếu số dư không đủ sau khi cập nhật
            if (selectedPosition != RecyclerView.NO_POSITION) {
                val selectedItem = listPaymentMethod.getOrNull(selectedPosition)
                val isWalletMethod = selectedItem?.paymentMethodName.equals("Travel wallet", ignoreCase = true)

                if (isWalletMethod && newBalance < this.requiredAmount) {
                    selectedPosition = RecyclerView.NO_POSITION
                }
            }
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

        // 1. Tính trạng thái
        val isWalletMethod = item.paymentMethodName.equals("Travel wallet", ignoreCase = true)
        val isSufficientBalance = userWalletBalance >= requiredAmount
        val isEnabled = isSufficientBalance || !isWalletMethod // Chỉ disable khi là Ví và không đủ tiền

        // 2. Truyền dữ liệu vào onBind
        holder.onBind(
            item,
            requiredAmount,
            userWalletBalance,
            position == selectedPosition,
            isEnabled
        )

        holder.itemView.setOnClickListener {
            if (isEnabled) {
                val previousSelectedPosition = selectedPosition

                selectedPosition = holder.adapterPosition

                if (previousSelectedPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(previousSelectedPosition, Payload.CHECK_STATE)
                }

                notifyItemChanged(selectedPosition, Payload.CHECK_STATE)

                onPaymentMethodSelected?.invoke(item)
            }
        }
    }

    override fun getItemCount(): Int = listPaymentMethod.size


    class ViewHolder(val binding: ItemPaymentMethodBinding) : RecyclerView.ViewHolder(binding.root) {

        private var discountListener: ListenerRegistration? = null

        fun updateCheckStateOnly(isChecked: Boolean) {
            binding.radId.isChecked = isChecked
        }

        @SuppressLint("UseCompatLoadingForDrawables")
        fun onBind(
            item: PaymentMethod,
            requiredAmount: Double,
            userWalletBalance: Double,
            isSelected: Boolean,
            isEnabled: Boolean
        ) {
            binding.radId.text = item.paymentMethodName

            // --- Logic Load Icon (Giữ nguyên) ---
            Glide.with(itemView.context).asDrawable().load(item.iconId).into(
                object :CustomTarget<Drawable>(){
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        binding.radId.setCompoundDrawablesWithIntrinsicBounds(resource, null, null, null)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        binding.radId.setCompoundDrawablesWithIntrinsicBounds(placeholder, null, null, null)
                    }
                }
            )

            discountListener?.remove()

            if(item.discountId != null){
                item.discountId?.let { id ->
                    discountListener = loadDiscountDescription(id) { description ->
                        if (!description.isNullOrEmpty()) {
                            binding.tvDiscountDescription.text = description
                        } else {
                            binding.tvDiscountDescription.text = item.description
                        }
                    }
                }
            } else {
                binding.tvDiscountDescription.text = item.description
            }

            val isWalletMethod = item.paymentMethodName.equals("Travel wallet", ignoreCase = true)

            if (isWalletMethod) {
                val isSufficientBalance = userWalletBalance >= requiredAmount

                binding.tvBalance.isVisible = true
                binding.tvBalance.text = "Balance: ${Format.formatCurrency(userWalletBalance)}"

                val colorResId = if (isSufficientBalance) R.color.green else R.color.red
                binding.tvBalance.setTextColor(ContextCompat.getColor(itemView.context, colorResId))

            } else {
                binding.tvBalance.isVisible = false
            }

            itemView.isEnabled = isEnabled
            itemView.alpha = if (isEnabled) 1.0f else 0.4f
            binding.radId.isEnabled = isEnabled

            binding.radId.isChecked = isSelected
        }

        private fun loadDiscountDescription(discountId: String, onResult: (String?) -> Unit): ListenerRegistration {
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
                            val discount = dataSnapshot.toObject(DiscountPaymentMethod::class.java)
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