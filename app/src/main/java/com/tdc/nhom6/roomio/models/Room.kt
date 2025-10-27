
package com.tdc.nhom6.roomio.models

import com.tdc.nhom6.roomio.R


data class Room(
    val floor: Long = 0,
    val room_id: String = "",
    val room_number: String = "",
    val room_type_id: String = "",
    val status_id: String = "room_available"
) {
    val displayCode: String
        get() = "F${floor}-${room_number}"

    // Hàm lấy ID Drawable cho màu nền dựa trên trạng thái
    fun getBackgroundColorResId(): Int {
        return when (status_id) {
            "room_occupied" -> R.drawable.bg_chip_red     // Occupied
            "room_available" -> R.drawable.bg_chip_gray  // Available
            "room_pending" -> R.drawable.bg_chip_yellow  // Pending
            "room_fixed" -> R.drawable.bg_chip_blue      // Fixed
            else -> R.drawable.bg_chip_gray
        }
    }
}