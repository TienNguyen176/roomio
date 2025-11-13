package com.tdc.nhom6.roomio.models

import com.google.firebase.firestore.DocumentId

data class DiscountHotel(
    @DocumentId
    val discountHotelId: String = "",
    val hotelId: String = "",
    val roomTypeId: String? = null,
    val title: String = "",
    val description: String = "",
    val discountPercent: Double? = null, // 0..100
    val discountAmount: Double? = null, // absolute amount
    val startDate: Any = System.currentTimeMillis(),
    val endDate: Any = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val createdAt: Any = System.currentTimeMillis()
)


