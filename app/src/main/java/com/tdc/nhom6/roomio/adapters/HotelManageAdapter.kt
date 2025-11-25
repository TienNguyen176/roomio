package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ItemAdminHotelBinding
import com.tdc.nhom6.roomio.models.Hotel

class HotelManageAdapter(
    private var hotelList: List<Pair<Hotel, String>>, // Pair<Hotel, ownerName>
    private val onClick: (Hotel) -> Unit
) : RecyclerView.Adapter<HotelManageAdapter.HotelViewHolder>() {

    inner class HotelViewHolder(private val binding: ItemAdminHotelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(hotel: Hotel, ownerName: String) {
            binding.tvHotelName.text = hotel.hotelName
            binding.tvHotelOwner.text = ownerName
            Glide.with(binding.imgHotel.context)
                .load(hotel.images.getOrNull(0))
                .placeholder(R.drawable.ic_not_image)
                .into(binding.imgHotel)

            // Hiển thị rating
            val stars = listOf(binding.ivStar1, binding.ivStar2, binding.ivStar3, binding.ivStar4, binding.ivStar5)
            val fullStars = hotel.averageRating.toInt()
            val halfStar = (hotel.averageRating - fullStars) >= 0.5

            stars.forEachIndexed { index, imageView ->
                when {
                    index < fullStars -> imageView.setImageResource(R.drawable.ic_star)
                    index == fullStars && halfStar -> imageView.setImageResource(R.drawable.ic_star)
                    else -> imageView.setImageResource(R.drawable.ic_star_border)
                }
            }

            binding.root.setOnClickListener { onClick(hotel) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotelViewHolder {
        val binding = ItemAdminHotelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HotelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HotelViewHolder, position: Int) {
        val (hotel, ownerName) = hotelList[position]
        holder.bind(hotel, ownerName)
    }

    override fun getItemCount(): Int = hotelList.size

    fun updateDataWithOwner(newList: List<Pair<Hotel, String>>) {
        hotelList = newList
        notifyDataSetChanged()
    }
}
