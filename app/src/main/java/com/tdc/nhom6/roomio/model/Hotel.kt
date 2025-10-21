package com.tdc.nhom6.roomio.model

import com.google.firebase.firestore.DocumentId

data class Hotel(
    @DocumentId
    val hotelId: String = "",
    val ownerId: String = "",
    val hotelName: String = "",
    val hotelAddress: String = "",
    val hotelFloors: Int = 0,
    val hotelTotalRooms: Int = 0,
    val typeId: String = "",
    val description: String = "",
    val statusId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val images: List<String> = emptyList(),
    val averageRating: Double = 0.0,
    val totalReviews: Int = 0,
    val pricePerNight: Double = 0.0
)

data class HotelType(
    @DocumentId
    val typeId: String = "",
    val typeName: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class HotelImage(
    @DocumentId
    val imageId: String = "",
    val hotelId: String = "",
    val imageUrl: String = "",
    val isThumbnail: Boolean = false,
    val uploadedAt: Long = System.currentTimeMillis()
)
