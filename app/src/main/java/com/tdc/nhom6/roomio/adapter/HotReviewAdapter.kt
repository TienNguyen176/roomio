package com.tdc.nhom6.roomio.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.model.HotReviewItem
import com.tdc.nhom6.roomio.model.Hotel


class HotReviewAdapter(
    private var hotelReviews: List<Hotel>
) : RecyclerView.Adapter<HotReviewAdapter.HotReviewViewHolder>() {

    override fun getItemId(position: Int): Long {
        // Use hotel ID as stable ID to prevent swap behavior issues
        return hotelReviews[position].hotelId.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotReviewViewHolder {
        // Inflate the layout for each item
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hot_review, parent, false)
        return HotReviewViewHolder(view)
    }

    /**
     * Binds data to a view holder
     */
    override fun onBindViewHolder(holder: HotReviewViewHolder, position: Int) {
        // Get the hotel review at this position
        val hotelReview:Hotel = hotelReviews[position]

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
    fun updateData(newReviews: List<Hotel>) {
        // Simple update without DiffUtil to prevent swap behavior issues
        hotelReviews = newReviews
        // Use notifyDataSetChanged() for stable updates
        notifyDataSetChanged()
    }


    inner class HotReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        // Find the views in the layout
        val hotelImage: ImageView = itemView.findViewById(R.id.imgPhoto)
        private val hotelName: TextView = itemView.findViewById(R.id.tvTitle)
        private val hotelRating: TextView = itemView.findViewById(R.id.tvRating)
        private val hotelPrice: TextView = itemView.findViewById(R.id.tvPrice)


        fun bind(hotel: Hotel) {
            // Set the hotel image
            if (hotel.images.isNotEmpty()) {
                Glide.with(hotelImage.context).load(hotel.images[0]).into(hotelImage)
            } else {
                hotelImage.setImageResource(R.drawable.hotel_64260231_1)
            }

            // Set the hotel name
            hotelName.text = hotel.hotelName

            // Set the rating (e.g., "4.5 (234)")
            hotelRating.text = "${hotel.averageRating} (${hotel.totalReviews})"

            // Set the price (e.g., "VND 1,500,000")
            hotelPrice.text = "VND ${String.format("%.0f", hotel.pricePerNight)}"
        }
    }
}
