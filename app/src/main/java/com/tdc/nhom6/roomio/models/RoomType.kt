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

