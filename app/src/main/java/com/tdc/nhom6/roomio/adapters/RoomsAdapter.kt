package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.models.Room
import com.tdc.nhom6.roomio.databinding.ItemRoomCellBinding

// Giả sử layout cho mỗi item là item_room.xml, tương ứng với FrameLayout đã cung cấp
// Trong file này, bạn cần thay đổi xmlns:android="http://schemas.android.com/apk/res/android"
// thành androidx.constraintlayout.widget.ConstraintLayout hoặc giữ FrameLayout
// và đặt tên file layout là item_room.xml.

class RoomsAdapter(private val onItemClicked: (Room) -> Unit) :
    ListAdapter<Room, RoomsAdapter.RoomViewHolder>(RoomDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val binding = ItemRoomCellBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RoomViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = getItem(position)
        holder.bind(room, onItemClicked)
    }

    class RoomViewHolder(private val binding: ItemRoomCellBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(room: Room, onItemClicked: (Room) -> Unit) {
            // Ánh xạ dữ liệu vào layout item_room.xml
            binding.tvRoomCode.text = room.displayCode
            binding.tvRoomCode.setBackgroundResource(room.getBackgroundColorResId())

            binding.root.setOnClickListener { onItemClicked(room) }
        }
    }

    // Sử dụng DiffUtil để tối ưu hóa hiệu năng
    private class RoomDiffCallback : DiffUtil.ItemCallback<Room>() {
        override fun areItemsTheSame(oldItem: Room, newItem: Room): Boolean {
            return oldItem.room_id == newItem.room_id
        }

        override fun areContentsTheSame(oldItem: Room, newItem: Room): Boolean {
            return oldItem == newItem
        }
    }
}