package com.tdc.nhom6.roomio.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tdc.nhom6.roomio.R
import java.io.File

class CleaningImageAdapter(
    val images: MutableList<ImageItem>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<CleaningImageAdapter.ImageViewHolder>() {

    data class ImageItem(
        val uri: Uri? = null,
        val url: String? = null,
        val isUploading: Boolean = false,
        val file: File? = null
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
        if (position !in images.indices) {
            android.util.Log.e("CleaningImageAdapter", "Position $position out of bounds (size: ${images.size})")
            return
        }

        val item = images[position]
        android.util.Log.d("CleaningImageAdapter", "Binding position $position: uri=${item.uri}, url=${item.url}")

        // Load image - use Glide for both URI and URL for better compatibility
        when {
            item.url != null -> {
                android.util.Log.d("CleaningImageAdapter", "Loading image from URL: ${item.url}")
                Glide.with(holder.imageView.context)
                    .load(item.url)
                    .placeholder(R.drawable.ic_service_roomsvc)
                    .error(R.drawable.ic_service_roomsvc)
                    .into(holder.imageView)
            }
            item.uri != null -> {
                android.util.Log.d("CleaningImageAdapter", "Loading image from URI: ${item.uri}")
                // Use Glide for URI loading instead of setImageURI for better compatibility
                Glide.with(holder.imageView.context)
                    .load(item.uri)
                    .placeholder(R.drawable.ic_service_roomsvc)
                    .error(R.drawable.ic_service_roomsvc)
                    .into(holder.imageView)
            }
            else -> {
                android.util.Log.w("CleaningImageAdapter", "No URI or URL for position $position")
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
        val position = images.size
        images.add(item)
        android.util.Log.d("CleaningImageAdapter", "Added image at position $position, total: ${images.size}")
        notifyItemInserted(position)
        // Also notify data set changed to ensure RecyclerView refreshes
        notifyDataSetChanged()
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

