package com.tdc.nhom6.roomio.models

import com.tdc.nhom6.roomio.R

data class Room(
    val floor: Int = 0,
    val room_id: String = "",
    val room_number: String = "",       // có thể là "A01" (cũ) hoặc "01" (mới)
    val room_type_id: String = "",
    val room_type_name: String = "",
    val status_id: String = "room_available"
) {

    companion object {
        // Prefix chung, mặc định là "F"
        var prefix: String = "F"
    }

    val displayCode: String
        get() {
            val floorStr = floor.toString().padStart(2, '0')
            val p = prefix.ifBlank { "F" }
            val patternWithBlock = Regex("^[A-Z]\\d{2}$")
            return if (patternWithBlock.matches(room_number)) {
                "$p${floorStr}${room_number}"
            } else {
                val blockChar = ('A'.code + (floor - 1)).toChar()
                val roomStr = room_number.padStart(2, '0')
                "$p${floorStr}${blockChar}${roomStr}"
            }
        }

    fun getBackgroundColorResId(): Int {
        return when (status_id) {
            "room_occupied" -> R.drawable.bg_chip_red
            "room_available" -> R.drawable.bg_chip_gray
            "room_pending"   -> R.drawable.bg_chip_yellow
            "room_fixed"     -> R.drawable.bg_chip_blue
            else             -> R.drawable.bg_chip_gray
        }
    }
}
