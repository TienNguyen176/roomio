package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.models.SearchResultItem
import com.tdc.nhom6.roomio.models.SearchResultType

class SearchResultsAdapter(var items: List<SearchResultItem>) : RecyclerView.Adapter<SearchResultsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_hotel, parent, false)
        return SearchResultsViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: SearchResultsViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun updateData(newItems: List<SearchResultItem>) {
        // Simple update without complex notifications to prevent swap behavior issues
        items = newItems
        // Use notifyDataSetChanged() for stable updates
        notifyDataSetChanged()
    }
}

// ViewHolder for search results
class SearchResultsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val img = itemView.findViewById<android.widget.ImageView>(R.id.img)
    private val title = itemView.findViewById<android.widget.TextView>(R.id.tvTitle)
    private val location = itemView.findViewById<android.widget.TextView>(R.id.tvLocation)
    private val price = itemView.findViewById<android.widget.TextView>(R.id.tvPrice)

    private val layoutRating = itemView.findViewById<android.widget.LinearLayout>(R.id.layoutRating)

    @SuppressLint("SetTextI18n")
    fun bind(item: SearchResultItem) {
        when (item.type) {
            SearchResultType.HOTEL -> {
                item.hotel?.let { hotel ->
                    loadImage(hotel.images.firstOrNull() ?: "hotel_64260231_1", img)
                    title.text = hotel.hotelName
                    location.text = extractLocationFromAddress(hotel.hotelAddress)
                    price.text = formatPrice(hotel.pricePerNight)

                    setStarRating(hotel.averageRating)
                }
            }
            SearchResultType.DEAL -> {
                item.deal?.let { deal ->
                    loadImage(deal.imageUrl, img)
                    title.text = deal.hotelName
                    location.text = deal.hotelLocation
                    price.text = formatPrice(deal.discountPricePerNight)

                    setStarRating(deal.rating)
                }
            }
            SearchResultType.REVIEW -> {
                item.review?.let { review ->
                    loadImage(review.hotelImage, img)
                    title.text = review.hotelName
                    location.text = review.location
                    price.text = formatPrice(review.pricePerNight)

                    setStarRating(review.rating)
                }
            }
        }
    }

    private fun extractLocationFromAddress(address: String): String {
        // Extract city name from address (e.g., "123 Nguyen Hue, Quận 1, TP. Hồ Chí Minh" -> "Ho Chi Minh City")
        return when {
            address.contains("TP. Hồ Chí Minh") || address.contains("Ho Chi Minh") -> "Ho Chi Minh City"
            address.contains("Vũng Tàu") || address.contains("Vung Tau") -> "Vung Tau"
            address.contains("Đà Nẵng") || address.contains("Da Nang") -> "Da Nang"
            address.contains("Hà Nội") || address.contains("Hanoi") -> "Hanoi"
            address.contains("Nha Trang") -> "Nha Trang"
            address.contains("Phú Quốc") || address.contains("Phu Quoc") -> "Phu Quoc"
            else -> "Vietnam"
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatPrice(price: Double): String {
        return "VND ${String.format("%,.0f", price)}"
    }

    private fun setStarRating(rating: Double) {
        val stars = listOf(
            itemView.findViewById<android.widget.ImageView>(R.id.ivStar1),
            itemView.findViewById<android.widget.ImageView>(R.id.ivStar2),
            itemView.findViewById<android.widget.ImageView>(R.id.ivStar3),
            itemView.findViewById<android.widget.ImageView>(R.id.ivStar4),
            itemView.findViewById<android.widget.ImageView>(R.id.ivStar5)
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

    private fun loadImage(nameOrUrl: String, target: android.widget.ImageView) {
        if (nameOrUrl.startsWith("http", ignoreCase = true) || nameOrUrl.contains("/")) {
            Glide.with(target.context)
                .load(nameOrUrl)
                .placeholder(R.drawable.ic_not_image)
                .error(R.drawable.ic_not_image)
                .into(target)
        } else {
            val resId = when (nameOrUrl) {
                "hotel_64260231_1" -> R.drawable.ic_not_image
                else -> R.drawable.ic_not_image
            }
            target.setImageResource(resId)
        }
    }
}