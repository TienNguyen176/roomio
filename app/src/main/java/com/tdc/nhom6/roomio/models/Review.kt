package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Review(
    @DocumentId
    var reviewId: String? = null,
    val hotelId: String = "",
    val userId: String = "",
    val userName: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val createdAt: Timestamp = Timestamp.now()
)