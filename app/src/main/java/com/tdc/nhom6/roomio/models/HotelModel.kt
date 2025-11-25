package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class HotelModel(
    @DocumentId
    val hotelId: String = "",
    val ownerId: String ="",
    val hotelName: String = "",
    val hotelAddress: String = "",
    val hotelFloors: Int = 0,
    val hotelTotalRooms: Int = 0,
    val pricePerNight: Double = 0.0,
    val images: List<String>? = listOf(),
    val description: String = "",
    val statusId: String = "",
    val typeId: String ="",
    val totalReviews: Int = 0,
    val averageRating: Double = 0.0,
    val createdAt: Timestamp? = null
)