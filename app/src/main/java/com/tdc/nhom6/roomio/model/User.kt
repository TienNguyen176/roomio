package com.tdc.nhom6.roomio.model

import com.google.firebase.firestore.DocumentId

data class Rating(
    @DocumentId
    val ratingId: String = "",
    val hotelId: String = "",
    val userId: String = "",
    val ratingValue: Int = 0, // 1-5 stars
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val userName: String = "",
    val userAvatar: String = ""
)

data class User(
    @DocumentId
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val gender: String = "",
    val dateOfBirth: String = "",
    val roleId: String = "",
    val avatar: String = "",
    val walletAmount: Double = 0.0,
    val accountId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

