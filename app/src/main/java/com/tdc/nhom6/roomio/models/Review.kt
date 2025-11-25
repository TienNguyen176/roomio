package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp

data class Review(
    var reviewId: String? = null,
    val userId: String = "",
    val userName: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
