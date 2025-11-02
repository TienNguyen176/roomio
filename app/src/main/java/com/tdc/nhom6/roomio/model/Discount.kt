package com.tdc.nhom6.roomio.model

import com.google.firebase.firestore.DocumentId

data class Discount(
    @DocumentId
    val discountId: String = "",
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


