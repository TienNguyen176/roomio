package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.databinding.ItemImageLayoutBinding

class ImageListAdapter(
    private val images: MutableList<String>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<ImageListAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemImageLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val url = images[position]
        Glide.with(holder.itemView.context).load(url).into(holder.binding.imgPreview)

        holder.binding.btnDelete.setOnClickListener { onDelete(position) }
    }

    override fun getItemCount() = images.size

    inner class ImageViewHolder(val binding: ItemImageLayoutBinding) :
        RecyclerView.ViewHolder(binding.root)
}
