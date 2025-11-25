package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.SearchResultItem
import com.tdc.nhom6.roomio.models.SearchResultType
import com.tdc.nhom6.roomio.utils.FormatUtils

class SearchResultsAdapter(var items: List<SearchResultItem>) :
    RecyclerView.Adapter<SearchResultsAdapter.SearchResultsViewHolder>() {

    var onHotelClick: ((String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_hotel, parent, false)
        return SearchResultsViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SearchResultsViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun updateData(newItems: List<SearchResultItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class SearchResultsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val img: ImageView = itemView.findViewById(R.id.img)
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val location: TextView = itemView.findViewById(R.id.tvLocation)
        private val price: TextView = itemView.findViewById(R.id.tvPrice)
        private val layoutRating: LinearLayout = itemView.findViewById(R.id.layoutRating)

        @SuppressLint("SetTextI18n")
        fun bind(item: SearchResultItem) {
            when (item.type) {
                SearchResultType.HOTEL -> item.hotel?.let { hotel ->
                    loadImage(hotel.images.firstOrNull() ?: "", img)
                    title.text = hotel.hotelName
                    location.text = extractLocationFromAddress(hotel.hotelAddress)
                    price.text = formatPrice(hotel.pricePerNight)
                    setStarRating(hotel.averageRating)

                    itemView.setOnClickListener {
                        onHotelClick?.invoke(hotel.hotelId)
                    }
                }
                SearchResultType.DEAL -> item.deal?.let { deal ->
                    loadImage(deal.imageUrl, img)
                    title.text = deal.hotelName
                    location.text = deal.hotelLocation
                    price.text = formatPrice(deal.discountPricePerNight)
                    setStarRating(deal.rating)
                }
                SearchResultType.REVIEW -> item.review?.let { review ->
                    loadImage(review.hotelImage, img)
                    title.text = review.hotelName
                    location.text = review.location
                    price.text = formatPrice(review.pricePerNight)
                    setStarRating(review.rating)
                }
            }
        }

        private fun extractLocationFromAddress(address: String): String {
            // Return the address as-is, or extract the last part (typically city) if address contains commas
            return if (address.contains(",")) {
                address.split(",").lastOrNull()?.trim() ?: address
            } else {
                address
            }
        }

        private fun formatPrice(price: Double): String {
            return "VND ${FormatUtils.formatCurrency(price).replace("VND", "").trim()}"
        }

        private fun setStarRating(rating: Double) {
            val stars = listOf(
                itemView.findViewById<ImageView>(R.id.ivStar1),
                itemView.findViewById(R.id.ivStar2),
                itemView.findViewById(R.id.ivStar3),
                itemView.findViewById(R.id.ivStar4),
                itemView.findViewById(R.id.ivStar5)
            )

            val fullStars = rating.toInt()
            val hasHalfStar = rating - fullStars >= 0.5

            stars.forEachIndexed { index, star ->
                when {
                    index < fullStars -> {
                        star.setImageResource(R.drawable.ic_star_filled)
                        star.setColorFilter(itemView.context.getColor(android.R.color.holo_orange_dark))
                    }
                    index == fullStars && hasHalfStar -> {
                        star.setImageResource(R.drawable.ic_star_half)
                        star.setColorFilter(itemView.context.getColor(android.R.color.holo_orange_dark))
                    }
                    else -> {
                        star.setImageResource(R.drawable.ic_star)
                        star.setColorFilter(itemView.context.getColor(android.R.color.darker_gray))
                    }
                }
            }
        }

        private fun loadImage(nameOrUrl: String, target: ImageView) {
            if (nameOrUrl.isBlank()) {
                target.setImageResource(R.drawable.caption)
                return
            }

            // If it's a URL (starts with http/https or contains /), load with Glide
            if (nameOrUrl.startsWith("http", true) || nameOrUrl.contains("/")) {
                Glide.with(target.context)
                    .load(nameOrUrl)
                    .placeholder(R.drawable.caption)
                    .error(R.drawable.caption)
                    .into(target)
            } else {
                // Fallback to default placeholder for non-URL image names
                target.setImageResource(R.drawable.caption)
            }
        }
    }
}

