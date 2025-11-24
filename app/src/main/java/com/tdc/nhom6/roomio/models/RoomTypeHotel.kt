package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp

data class RoomTypeHotel(
    val roomTypeId: String = "",
    val typeName: String = "",
    val area: Int = 0,
    val description: String? = null,
    val hotelId: String = "",
    val maxPeople: Int = 0,
    val pricePerNight: Long = 0,
    val roomImages: List<RoomImageHotel> = emptyList(),
    val facilityPrices: List<FacilityPrice> = emptyList(),
    val viewId: String = "0"
)

data class RoomImageHotel(

    val imageUrl: String = "",
    val thumbnail: Boolean = false,
    val uploadedAt:  Any? = null
)
