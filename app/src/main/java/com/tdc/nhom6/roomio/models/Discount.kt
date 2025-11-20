package com.tdc.nhom6.roomio.models

import com.google.firebase.firestore.DocumentId

data class Discount(
    @DocumentId
    var id: String? = null,
    var hotelId: String? = null,

    var discountName: String = "",
    var description: String = "",
    var discountPercent: Int = 0,
    var maxDiscount: Long = 0,
    var minOrder: Long = 0,

    var type: String = "infinite",
    var dailyReset: Int = 0,
    var availableCount: Int = 0,

    var lastResetDate: String = ""
)


