package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp

data class Reply(
    val userId: String = "",
    val userName: String = "",
    val message: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
