package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.Bank
import java.util.*
import kotlin.collections.ArrayList

class BankAdapter(
    private val banks: List<Bank>,
    private val onClick: (Bank) -> Unit
) : RecyclerView.Adapter<BankAdapter.BankViewHolder>(), Filterable {

    private val banksFiltered = ArrayList(banks)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BankViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bank, parent, false)
        return BankViewHolder(view)
    }

    override fun onBindViewHolder(holder: BankViewHolder, position: Int) {
        val bank = banksFiltered[position]
        holder.bind(bank)
    }

    override fun getItemCount(): Int = banksFiltered.size

    inner class BankViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvBankName)
        private val imgLogo: ImageView = itemView.findViewById(R.id.imgBankLogo)

        fun bind(bank: Bank) {
            tvName.text = bank.name
            Glide.with(itemView.context).load(bank.logoUrl).into(imgLogo)

            itemView.setOnClickListener { onClick(bank) }
        }
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterString = constraint?.toString()?.lowercase(Locale.getDefault()) ?: ""
                val results = FilterResults()
                results.values = if (filterString.isEmpty()) {
                    banks
                } else {
                    banks.filter { it.name.lowercase(Locale.getDefault()).contains(filterString) }
                }
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                banksFiltered.clear()
                banksFiltered.addAll(results?.values as List<Bank>)
                notifyDataSetChanged()
            }
        }
    }
}
