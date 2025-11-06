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

class BankAdapter(
    private var banks: List<Bank>,
    private val onBankSelected: (Bank) -> Unit
) : RecyclerView.Adapter<BankAdapter.BankViewHolder>(), Filterable {

    private var filteredBanks = banks.toMutableList()

    inner class BankViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.imgBankLogo)
        val name: TextView = view.findViewById(R.id.tvBankName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BankViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bank, parent, false)
        return BankViewHolder(view)
    }

    override fun onBindViewHolder(holder: BankViewHolder, position: Int) {
        val bank = filteredBanks[position]
        holder.name.text = bank.name
        Glide.with(holder.itemView.context)
            .load(bank.logoUrl)
            .into(holder.logo)

        holder.itemView.setOnClickListener { onBankSelected(bank) }
    }

    override fun getItemCount() = filteredBanks.size

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val keyword = constraint?.toString()?.lowercase() ?: ""
                filteredBanks = if (keyword.isEmpty()) {
                    banks.toMutableList()
                } else {
                    banks.filter {
                        it.name.lowercase().contains(keyword) ||
                                it.id.lowercase().contains(keyword)
                    }.toMutableList()
                }
                return FilterResults().apply { values = filteredBanks }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredBanks = results?.values as MutableList<Bank>
                notifyDataSetChanged()
            }
        }
    }
}
