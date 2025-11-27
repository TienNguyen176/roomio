package com.tdc.nhom6.roomio.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.activities.hotel.HotelDetailActivity
import com.tdc.nhom6.roomio.models.DealItem
import com.tdc.nhom6.roomio.models.Hotel


class DealsAdapter(
    private var hotelDeals: List<Hotel>
) : RecyclerView.Adapter<DealsAdapter.DealsViewHolder>() {

    override fun getItemId(position: Int): Long {
        // Use title hash as stable ID to prevent swap behavior issues
        return hotelDeals[position].hotelId.hashCode().toLong()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DealsViewHolder {
        // Inflate the layout for each item
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deal, parent, false)
        return DealsViewHolder(view)
    }

    override fun onBindViewHolder(holder: DealsViewHolder, position: Int) {
        // Get the hotel deal at this position
        val hotelDeal = hotelDeals[position]
        
        // Put the data into the view holder
        holder.bind(hotelDeal)
    }

    override fun getItemCount(): Int = hotelDeals.size

    fun updateData(newDeals: List<Hotel>) {
        hotelDeals = newDeals
        notifyDataSetChanged()
    }

    class DealsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val dealImage: ImageView = itemView.findViewById(R.id.img)
        private val dealTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val dealSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)


        fun bind(hotelDeal: Hotel) {
            if (hotelDeal.images.isNotEmpty()) {
                Glide.with(itemView.context).load(hotelDeal.images[0]).into(dealImage)
            } else {
                dealImage.setImageResource(R.drawable.caption)
            }
            // Set the hotel name
            dealTitle.text = hotelDeal.hotelName
            
            // Set the location
            dealSubtitle.text = hotelDeal.hotelAddress

            itemView.setOnClickListener(View.OnClickListener {
                val intent = Intent(itemView.context, HotelDetailActivity::class.java)
                intent.putExtra("HOTEL_ID",hotelDeal.hotelId)
                itemView.context.startActivity(intent)
            })
        }
    }
}
