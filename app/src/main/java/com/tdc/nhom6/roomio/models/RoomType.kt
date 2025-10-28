package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp

class RoomType(
    val typeId: String = "",
    val hotelId: String = "",
    val typeName: String = "",
    val description: String? = null,
    val pricePerNight: Double = 0.0,
    val maxPeople: Int = 0,
    val area: Int = 0,
    val viewId: String? = null,
    // Trường list ảnh nhúng (tên chính xác)
    val roomImages: List<RoomImage> = emptyList()
)


