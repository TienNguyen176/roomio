
package com.tdc.nhom6.roomio.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tdc.nhom6.roomio.databinding.ItemRoomCellBinding
import com.tdc.nhom6.roomio.models.Room

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
            binding.tvRoomCode.text = room.displayCode
            binding.tvRoomCode.setBackgroundResource(room.getBackgroundColorResId())

            binding.root.setOnClickListener { onItemClicked(room) }
        }
    }

    private class RoomDiffCallback : DiffUtil.ItemCallback<Room>() {
        override fun areItemsTheSame(oldItem: Room, newItem: Room): Boolean {
            return oldItem.room_id == newItem.room_id
        }

        override fun areContentsTheSame(oldItem: Room, newItem: Room): Boolean {
            return oldItem == newItem
        }
    }
}