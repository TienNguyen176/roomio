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

    private var editingPosition: Int? = null

    fun setEditingPosition(position: Int?) {
        editingPosition = position
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgLogo: ImageView = itemView.findViewById(R.id.imgBankLogo)
        val tvName: TextView = itemView.findViewById(R.id.tvBankName)
        val tvAccount: TextView = itemView.findViewById(R.id.tvAccountNumber)
        //val edtName: EditText = itemView.findViewById(R.id.edtBankName)
        val edtAccount: EditText = itemView.findViewById(R.id.edtAccountNumber)
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
        Glide.with(holder.itemView.context).load(bank.logoUrl).into(holder.imgLogo)

        if (editingPosition == position) {
            // Đang chỉnh item này
            holder.tvName.visibility = View.GONE
            holder.tvAccount.visibility = View.GONE
           // holder.edtName.visibility = View.VISIBLE
            holder.edtAccount.visibility = View.VISIBLE

           // holder.edtName.setText(bank.name)
            holder.edtAccount.setText(bank.accountNumber)

//            holder.edtName.addTextChangedListener(object : TextWatcher {
//                override fun afterTextChanged(s: Editable?) {
//                    bank.name = s.toString()
//                }
//                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//            })

            holder.edtAccount.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    bank.accountNumber = s.toString()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        } else {
            // Các item khác
            holder.tvName.visibility = View.VISIBLE
            holder.tvAccount.visibility = View.VISIBLE
            //holder.edtName.visibility = View.GONE
            holder.edtAccount.visibility = View.GONE

            holder.tvName.text = bank.name
            holder.tvAccount.text = bank.accountNumber
        }

        // Khi click vào item
        holder.itemView.setOnClickListener {
            if (editingPosition == position) {
                setEditingPosition(null) // tắt edit nếu bấm lại item đang chỉnh
            } else {
                setEditingPosition(position) // bật edit cho item này
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
}
