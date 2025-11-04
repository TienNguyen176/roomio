package com.tdc.nhom6.roomio.models

data class RoomType(
    val roomTypeId: String = "",
    val typeName: String = "",
    val area: Int = 0,
    val description: String? = null,
    val hotelId: String = "",
    val maxPeople: Int = 0,
    val pricePerNight: Long = 0,
    val roomImages: List<RoomImage> = emptyList(),
    val viewId: String = "0"
)

data class RoomImage(
    val imageUrl: String = "",
    val thumbnail: Boolean = false,
    val uploadedAt: String = ""
)
