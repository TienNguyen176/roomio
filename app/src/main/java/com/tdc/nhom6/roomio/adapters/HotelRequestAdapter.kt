package com.tdc.nhom6.roomio.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.databinding.ItemHotelRequestBinding
import com.tdc.nhom6.roomio.models.HotelRequestModel

class HotelRequestAdapter(
    private val list: ArrayList<Pair<HotelRequestModel, String>>,
    private val onItemClick: (HotelRequestModel) -> Unit
) : RecyclerView.Adapter<HotelRequestAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemHotelRequestBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHotelRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = list.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (item, avatar) = list[position]

        val context = holder.itemView.context
        val phone = context.getString(R.string.text_phone_number)
        val address = context.getString(R.string.text_address)

        when(item.status_id) {
            "hotel_request_pending" -> {
                holder.binding.layoutContainer.setBackgroundResource(R.drawable.bg_hotel_request_status_peding)
            }
            "hotel_request_approved" -> {
                holder.binding.layoutContainer.setBackgroundResource(R.drawable.bg_hotel_request_status_approved)
            }
            "hotel_request_rejected" -> {
                holder.binding.layoutContainer.setBackgroundResource(R.drawable.bg_hotel_request_status_rejected)
            }
            else -> {
                holder.binding.layoutContainer.setBackgroundResource(R.drawable.bg_hotel_request_status_peding)
            }
        }

        holder.binding.apply {
            tvUsername.text = item.username
            tvPhone.text = "$phone ${item.phone}"
            tvAddress.text = "$address ${item.address}"

            Glide.with(imgAvatar.context)
                .load(avatar)
                .placeholder(R.drawable.ic_user_default)
                .error(R.drawable.ic_not_image)
                .apply(RequestOptions().skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE))
                .into(imgAvatar)

            imgProfile.setOnClickListener {
                onItemClick(item)
            }

            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
