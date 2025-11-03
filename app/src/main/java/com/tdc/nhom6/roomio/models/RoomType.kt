package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp
import java.math.BigDecimal

class RoomType(
    val roomTypeId: String = "",
    val hotelId: String = "" ,
    val typeName: String = "",
    val description: String? = null,
    val pricePerNight: Double = 0.0,
    val maxPeople: Int = 0,
    val area: Int = 0,
    val viewId: String? = null,
    val roomImages: List<RoomImage> = emptyList()
)


