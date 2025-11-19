package com.tdc.nhom6.roomio.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R

class CleaningImageAdapter(
    val images: MutableList<ImageItem>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<CleaningImageAdapter.ImageViewHolder>() {

    data class ImageItem(
        val uri: Uri? = null,
        val url: String? = null,
        val isUploading: Boolean = false
    )

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivCleaningImage)
        val btnRemove: ImageView = view.findViewById(R.id.btnRemoveImage)
        val progressView: View = view.findViewById(R.id.viewUploadProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cleaning_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val item = images[position]
        
        // Load image
        when {
            item.url != null -> {
                Glide.with(holder.imageView.context)
                    .load(item.url)
                    .placeholder(R.drawable.ic_service_roomsvc)
                    .into(holder.imageView)
            }
            item.uri != null -> {
                holder.imageView.setImageURI(item.uri)
            }
            else -> {
                holder.imageView.setImageResource(R.drawable.ic_service_roomsvc)
            }
        }
        
        // Show/hide progress
        holder.progressView.visibility = if (item.isUploading) View.VISIBLE else View.GONE
        
        // Remove button
        holder.btnRemove.setOnClickListener {
            onRemove(position)
        }
    }

    override fun getItemCount() = images.size

    fun addImage(item: ImageItem) {
        images.add(item)
        notifyItemInserted(images.size - 1)
    }

    fun updateImage(position: Int, item: ImageItem) {
        if (position in images.indices) {
            images[position] = item
            notifyItemChanged(position)
        }
    }

    fun removeImage(position: Int) {
        if (position in images.indices) {
            images.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, images.size - position)
        }
    }

    fun getImageUrls(): List<String> = images.mapNotNull { it.url }
}

