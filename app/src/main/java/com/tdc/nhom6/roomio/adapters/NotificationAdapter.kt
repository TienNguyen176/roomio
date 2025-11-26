package com.tdc.nhom6.roomio.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.DocumentSnapshot
import com.tdc.nhom6.roomio.databinding.ItemNotificationBinding

class NotificationAdapter(
    private val notifications: MutableList<DocumentSnapshot>,
    private val onClick: (DocumentSnapshot) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = notifications.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val doc = notifications[position]
        val title = doc.getString("title") ?: ""
        val message = doc.getString("message") ?: ""
        val read = doc.getBoolean("read") ?: false

        holder.binding.tvTitle.text = title
        holder.binding.tvMessage.text = message
        holder.binding.tvTitle.setTextColor(if (read) Color.GRAY else Color.BLACK)
        holder.binding.tvMessage.setTextColor(if (read) Color.GRAY else Color.DKGRAY)
        holder.binding.redDot.visibility = if (read) android.view.View.GONE else android.view.View.VISIBLE

        holder.binding.root.setOnClickListener { onClick(doc) }
    }
}
