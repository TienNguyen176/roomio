package com.tdc.nhom6.roomio.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// It's a good practice to make model classes Parcelable in Android.
@Parcelize
data class RoomType(
    val roomTypeId: String = "",
    val hotelId: String = "",
    val typeName: String = "",
    val area: Int = 0,
    val description: String? = null,
    val pricePerNight: Double = 0.0, // Kept the Double version, removed the Long duplicate.
    val maxPeople: Int = 0,
    val viewId: String? = null,
    val roomImages: List<RoomImage> = emptyList(), // Added a comma here.
    val facilityPrices: List<FacilityPrice> = emptyList()
) : Parcelable

@Parcelize
data class RoomImage(
    val imageUrl: String = "",
    val thumbnail: Boolean = false,
    // Using a Parcelable type is better than 'Any?'. String or Long for timestamp is common.
    val uploadedAt: Long? = null
) : Parcelable

// You will also need to define the FacilityPrice data class.
// Assuming it's simple, it might look like this.
@Parcelize
data class FacilityPrice(
    val facilityId: String = "",
    val price: Double = 0.0
) : Parcelable
