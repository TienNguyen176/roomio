package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp

data class RoomImage(
    val imageUrl: String = "",
    val isThumbnail: Boolean = false,
    val uploadedAt: Timestamp? = null
)