package com.tdc.nhom6.roomio.models

import com.google.firebase.firestore.DocumentId

data class DiscountPaymentMethod(
    @DocumentId
    val discountId: String = "",
    val discountName: String = "",
    val discountDescription:String="",
    val discountPercent: Double? = null,
    val discountAmount: Double? = null,
)

