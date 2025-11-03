package com.tdc.nhom6.roomio.models

import com.google.firebase.firestore.DocumentId

data class Deal(
    @DocumentId
    val dealId: String = "",
    val hotelName: String = "",
    val hotelLocation: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val originalPricePerNight: Double = 0.0,
    val discountPricePerNight: Double = 0.0,
    val discountPercentage: Int = 0,
    val validFrom: Long = System.currentTimeMillis(),
    val validTo: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val hotelId: String = "",
    val roomType: String = "",
    val amenities: Any = emptyList<String>(), // Flexible type for Firebase
    val rating: Double = 0.0,
    val totalReviews: Int = 0,
    val createdAt: Any = System.currentTimeMillis() // Flexible type for Firebase
)

data class HotReview(
    @DocumentId
    val reviewId: String = "",
    val hotelId: String = "",
    val hotelName: String = "",
    val hotelImage: String = "",
    val rating: Double = 0.0,
    val totalReviews: Int = 0,
    val pricePerNight: Double = 0.0,
    val location: String = "",
    val isHot: Boolean = false,
    val createdAt: Any = System.currentTimeMillis() // Flexible type for Firebase
)
