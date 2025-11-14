package com.tdc.nhom6.roomio.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.Bank

class LinkedBankAdapter(
    private val banks: MutableList<Bank>,
    private val onDelete: (Bank) -> Unit,
    private val onSetDefault: (Bank) -> Unit
) : RecyclerView.Adapter<LinkedBankAdapter.ViewHolder>() {

    private var isEditing = false
    private var deletePosition: Int? = null

    fun setEditing(value: Boolean) {
        isEditing = value
        deletePosition = null
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgLogo: ImageView = itemView.findViewById(R.id.imgBankLogo)
        val tvName: TextView = itemView.findViewById(R.id.tvBankName)
        val tvAccount: TextView = itemView.findViewById(R.id.tvAccountNumber)
        val edtAccount: EditText = itemView.findViewById(R.id.edtAccountNumber)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        val btnDefault: Button = itemView.findViewById(R.id.btnSetDefault)
    }

    //
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_linked_bank, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bank = banks[position]

        Glide.with(holder.itemView.context).load(bank.logoUrl).into(holder.imgLogo)

        if (isEditing) {
            holder.tvName.visibility = View.VISIBLE
            holder.tvAccount.visibility = View.GONE
            holder.edtAccount.visibility = View.VISIBLE

            holder.tvName.text = bank.name
            holder.edtAccount.setText(bank.accountNumber)

            holder.btnDelete.visibility = View.GONE
            holder.btnDefault.visibility = View.VISIBLE

            holder.edtAccount.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    bank.accountNumber = s.toString()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

        } else {

            holder.tvName.visibility = View.VISIBLE
            holder.tvAccount.visibility = View.VISIBLE
            holder.edtAccount.visibility = View.GONE

            holder.tvName.text = bank.name
            holder.tvAccount.text = bank.accountNumber

            if (deletePosition == position) {
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnDefault.visibility = View.GONE
            } else {
                holder.btnDelete.visibility = View.GONE
                holder.btnDefault.visibility = View.VISIBLE
            }

            holder.itemView.setOnLongClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    deletePosition = pos
                    notifyDataSetChanged()
                }
                true
            }

            holder.itemView.setOnClickListener {
                if (deletePosition != null) {
                    deletePosition = null
                    notifyDataSetChanged()
                }
            }
        }

        holder.btnDelete.setOnClickListener { onDelete(bank) }
        holder.btnDefault.setOnClickListener { onSetDefault(bank) }

        if (bank.isDefault) {
            holder.btnDefault.text = "Chính ✓"
            holder.btnDefault.isEnabled = false
        } else {
            holder.btnDefault.text = "Chọn chính"
            holder.btnDefault.isEnabled = true
        }
    }

    override fun getItemCount() = banks.size
}
