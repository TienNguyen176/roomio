package com.tdc.nhom6.roomio.models

import com.google.firebase.firestore.DocumentId

data class Discount(
    @DocumentId
    val discountId: String = "",
    val discountName: String = "",
    val discountDescription:String="",
    val discountValue: Int = 0,
)