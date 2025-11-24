package com.tdc.nhom6.roomio.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.Timestamp

data class Reply(
    @DocumentId
    val id: String = "",

    val reviewId: String = "",

    val userId: String = "",

    val message: String = "",

    @ServerTimestamp
    val createdAt: Timestamp? = null
)