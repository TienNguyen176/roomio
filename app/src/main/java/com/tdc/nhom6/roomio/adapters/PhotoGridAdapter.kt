package com.tdc.nhom6.roomio.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.databinding.ItemPhotoBinding

class PhotoGridAdapter(
    private val context: Context,
    private val items: List<String>?
) : RecyclerView.Adapter<PhotoGridAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(val binding: ItemPhotoBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val imageUrl = items?.get(position)

        Glide.with(context)
            .load(imageUrl)
            .into(holder.binding.imgPhoto)
    }

    override fun getItemCount(): Int = items?.size ?: 0
}