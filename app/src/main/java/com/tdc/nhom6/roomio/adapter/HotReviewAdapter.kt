package com.tdc.nhom6.roomio.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.model.HotReviewItem


class HotReviewAdapter(
    private var hotelReviews: List<HotReviewItem>
) : RecyclerView.Adapter<HotReviewAdapter.HotReviewViewHolder>() {

    /**
     * Creates a new view holder for each item
     * This is called when RecyclerView needs a new item to display
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotReviewViewHolder {
        // Inflate the layout for each item
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hot_review, parent, false)
        return HotReviewViewHolder(view)
    }

    /**
     * Binds data to a view holder
     * This is called when RecyclerView wants to show data in an item
     */
    override fun onBindViewHolder(holder: HotReviewViewHolder, position: Int) {
        // Get the hotel review at this position
        val hotelReview = hotelReviews[position]
        
        // Put the data into the view holder
        holder.bind(hotelReview)
    }

    /**
     * Returns the number of items in the list
     */
    override fun getItemCount(): Int = hotelReviews.size

    /**
     * Updates the data and refreshes the display
     * Call this when you have new data to show
     */
    fun updateData(newReviews: List<HotReviewItem>) {
        // Simple update without DiffUtil to prevent swap behavior issues
        hotelReviews = newReviews
        notifyDataSetChanged()
    }

    /**
     * ViewHolder - Holds references to the views in each item
     * This makes scrolling faster because we don't have to find views every time
     */
    class HotReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        // Find the views in the layout
        private val hotelImage: ImageView = itemView.findViewById(R.id.imgPhoto)
        private val hotelName: TextView = itemView.findViewById(R.id.tvTitle)
        private val hotelRating: TextView = itemView.findViewById(R.id.tvRating)
        private val hotelPrice: TextView = itemView.findViewById(R.id.tvPrice)

        /**
         * Binds hotel review data to the views
         * This is where we put the actual data into the UI elements
         */
        fun bind(hotelReview: HotReviewItem) {
            // Set the hotel image
            hotelImage.setImageResource(hotelReview.imageRes)
            
            // Set the hotel name
            hotelName.text = hotelReview.title
            
            // Set the rating (e.g., "4.5 (234)")
            hotelRating.text = hotelReview.ratingText
            
            // Set the price (e.g., "VND 1,500,000")
            hotelPrice.text = hotelReview.priceText
        }
    }
}
