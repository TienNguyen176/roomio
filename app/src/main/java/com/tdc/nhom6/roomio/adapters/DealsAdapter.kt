package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.DealItem


class DealsAdapter(
    private var hotelDeals: List<DealItem>
) : RecyclerView.Adapter<DealsAdapter.DealsViewHolder>() {

    override fun getItemId(position: Int): Long {
        // Use title hash as stable ID to prevent swap behavior issues
        return hotelDeals[position].title.hashCode().toLong()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DealsViewHolder {
        // Inflate the layout for each item
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deal, parent, false)
        return DealsViewHolder(view)
    }

    /**
     * Binds data to a view holder
     * This is called when RecyclerView wants to show data in an item
     */
    override fun onBindViewHolder(holder: DealsViewHolder, position: Int) {
        // Get the hotel deal at this position
        val hotelDeal = hotelDeals[position]
        
        // Put the data into the view holder
        holder.bind(hotelDeal)
    }

    /**
     * Returns the number of items in the list
     */
    override fun getItemCount(): Int = hotelDeals.size

    /**
     * Updates the data and refreshes the display
     * Call this when you have new data to show
     */
    fun updateData(newDeals: List<DealItem>) {
        // Simple update without DiffUtil to prevent swap behavior issues
        hotelDeals = newDeals
        // Use notifyDataSetChanged() for stable updates
        notifyDataSetChanged()
    }

    /**
     * ViewHolder - Holds references to the views in each item
     * This makes scrolling faster because we don't have to find views every time
     */
    class DealsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        // Find the views in the layout
        private val dealImage: ImageView = itemView.findViewById(R.id.img)
        private val dealTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val dealSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)

        /**
         * Binds hotel deal data to the views
         * This is where we put the actual data into the UI elements
         */
        fun bind(hotelDeal: DealItem) {
            try {
                dealImage.setImageResource(hotelDeal.imageRes)
            } catch (_: Exception) {
                // As a safety, keep a placeholder
                dealImage.setImageResource(R.drawable.caption)
            }

            // Set the hotel name
            dealTitle.text = hotelDeal.title
            
            // Set the location
            dealSubtitle.text = hotelDeal.subtitle
        }
    }
}
