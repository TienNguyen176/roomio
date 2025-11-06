package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.Bank

class LinkedBankAdapter(
    private val banks: MutableList<Bank>,
    private val onDelete: (Bank) -> Unit,
    private val onSetDefault: (Bank) -> Unit
) : RecyclerView.Adapter<LinkedBankAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgLogo: ImageView = itemView.findViewById(R.id.imgBankLogo)
        val tvName: TextView = itemView.findViewById(R.id.tvBankName)
        val tvAccount: TextView = itemView.findViewById(R.id.tvAccountNumber)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
        val btnDefault: Button = itemView.findViewById(R.id.btnSetDefault)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_linked_bank, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = banks.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bank = banks[position]
        holder.tvName.text = bank.name
        holder.tvAccount.text = bank.accountNumber
        Glide.with(holder.itemView.context).load(bank.logoUrl).into(holder.imgLogo)

        holder.btnDelete.setOnClickListener { onDelete(bank) }
        holder.btnDefault.setOnClickListener { onSetDefault(bank) }

        if (bank.isDefault) {
            holder.btnDefault.text = "Chính ✓"
            holder.btnDefault.isEnabled = false
        } else {;
            holder.btnDefault.text = "Chọn chính"
            holder.btnDefault.isEnabled = true
        }

        // Reset trạng thái mỗi khi bind lại
        holder.btnDelete.visibility = View.GONE
        holder.imgLogo.animate().scaleX(1f).scaleY(1f).setDuration(200).start()

        // Giữ đè để hiện nút Xóa và co logo
        holder.itemView.setOnLongClickListener {
            holder.btnDelete.visibility = View.VISIBLE
            holder.imgLogo.animate().scaleX(0.83f).scaleY(0.83f).setDuration(200).start()
            true
        }

        // Chạm ra ngoài (nhấn bình thường) để ẩn lại
        holder.itemView.setOnClickListener {
            if (holder.btnDelete.visibility == View.VISIBLE) {
                holder.btnDelete.visibility = View.GONE
                holder.imgLogo.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }
        }
    }

}
