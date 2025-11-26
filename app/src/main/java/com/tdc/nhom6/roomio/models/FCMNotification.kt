package com.tdc.nhom6.roomio.models

data class FCMNotification(
    val user_id: String,
    val title: String,
    val message: String,
    val data: Map<String, String>
)

