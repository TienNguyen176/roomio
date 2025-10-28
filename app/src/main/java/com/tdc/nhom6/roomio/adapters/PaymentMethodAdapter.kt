package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ItemPaymentMethodBinding
import com.tdc.nhom6.roomio.models.PaymentMethod

class PaymentMethodAdapter(
    private val listPaymentMethod: MutableList<PaymentMethod>
): RecyclerView.Adapter<PaymentMethodAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentMethodAdapter.ViewHolder {
        val binding= ItemPaymentMethodBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PaymentMethodAdapter.ViewHolder, position: Int) {
        holder.onbind(listPaymentMethod[position])
    }

    override fun getItemCount(): Int =listPaymentMethod.size

    class ViewHolder(private val binding: ItemPaymentMethodBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("UseCompatLoadingForDrawables")
        fun onbind(itemMethod: PaymentMethod){
            binding.radId.text=itemMethod.paymentMethodName
            val drawable= itemMethod.iconId?.let { itemView.context.getDrawable(it) }
            binding.radId.setCompoundDrawablesWithIntrinsicBounds(drawable,null,null,null)
            binding.tvDiscountDescription.text=itemMethod.description
        }
    }
}