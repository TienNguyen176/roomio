package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp
import java.math.BigDecimal

class RoomType(
data class RoomType(
    val roomTypeId: String = "",
    val hotelId: String = "" ,
    val typeName: String = "",
    val area: Int = 0,
    val description: String? = null,
    val pricePerNight: Double = 0.0,
    val hotelId: String = "",
    val maxPeople: Int = 0,
    val area: Int = 0,
    val viewId: String? = null,
    val roomImages: List<RoomImage> = emptyList()
    val pricePerNight: Long = 0,
    val roomImages: List<RoomImage> = emptyList(),
    val facilityPrices: List<FacilityPrice> = emptyList(),
    val viewId: String = "0"
)

data class RoomImage(

    val imageUrl: String = "",
    val thumbnail: Boolean = false,
    val uploadedAt:  Any? = null
)
