package com.tdc.nhom6.roomio.models

data class FCMResponseModel(
    val success: Boolean,
    val message: String? = null,
    val result: Any? = null
)
