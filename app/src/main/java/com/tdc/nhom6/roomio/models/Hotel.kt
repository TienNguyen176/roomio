package com.tdc.nhom6.roomio.models

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
    val createdAt: Any = System.currentTimeMillis(), // Flexible type for Firebase
    val images: List<String> = emptyList(),
    val averageRating: Double = 0.0,
    val totalReviews: Int = 0,
    val pricePerNight: Double = 0.0,
    val lowestPricePerNight: Double? = null, // Lowest price from room types
    val highestPricePerNight: Double? = null,  // Highest price from room types
    val serviceIds: List<String> = emptyList(),
    val facilityIds: List<String> = emptyList()
)

//
//data class HotelType(
//    @DocumentId
//    val typeId: String = "",
//    val typeName: String = "",
//    val description: String = "",
//    val createdAt: Any = System.currentTimeMillis() // Flexible type for Firebase
//)
//
//data class HotelImage(
//    @DocumentId
//    val imageId: String = "",
//    val hotelId: String = "",
//    val imageUrl: String = "",
//    val isThumbnail: Boolean = false,
//    val uploadedAt: Any = System.currentTimeMillis() // Flexible type for Firebase
//)
