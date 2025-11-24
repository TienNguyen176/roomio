package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.databinding.ItemRoomImageBinding
import com.tdc.nhom6.roomio.models.RoomImage

class RoomImageListAdapter(
    private val images: List<RoomImage>,
    private val onNextClick: (Int) -> Unit
) : RecyclerView.Adapter<RoomImageListAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemRoomImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val image = images[position]
        Glide.with(holder.itemView.context)
            .load(image.imageUrl)
            .into(holder.binding.imgRoomType)
        
        holder.binding.btnNextImage.setOnClickListener { onNextClick(position) }
    }

    override fun getItemCount() = images.size

    class ImageViewHolder(val binding: ItemRoomImageBinding) :
        RecyclerView.ViewHolder(binding.root)
}


