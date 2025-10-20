package com.tdc.nhom6.roomio.models

import java.util.Date

data class HotelTypeModel(
    val type_id: String = "",
    val type_name: String = "",
    val description: String = "",
    val created_at: Date = Date()
)
