package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import com.tdc.nhom6.roomio.adapters.ImageListAdapter.*
import com.tdc.nhom6.roomio.databinding.DialogImageListBinding
import com.tdc.nhom6.roomio.databinding.ItemRoomImageBinding
import com.tdc.nhom6.roomio.models.RoomImage

class ImageListAdapter(
    private var images: List<RoomImage>,
    private val onNextClick: (Int) -> Unit
    ): RecyclerView.Adapter<ImageListAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ImageViewHolder {
        val binding = ItemRoomImageBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ImageViewHolder,
        position: Int
    ) {
        holder.bind(images[position], position, onNextClick)
    }

    override fun getItemCount(): Int =images.size

    class ImageViewHolder(private val binding: ItemRoomImageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(imgage: RoomImage, position: Int, onNextClick: (Int) -> Unit){
            Glide.with(itemView.context).load(imgage.imageUrl).into(binding.imgRoomType)
            binding.btnNextImage.setOnClickListener { onNextClick(position) }
        }
    }

}