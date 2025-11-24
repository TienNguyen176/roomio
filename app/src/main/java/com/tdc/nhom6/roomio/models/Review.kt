package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Review(
    @DocumentId
    val id: String = "",
    val hotelId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatarUrl: String? = null,
    val rating: Int = 0,
    val comment: String = "",
    val createdAt: Timestamp? = null
)