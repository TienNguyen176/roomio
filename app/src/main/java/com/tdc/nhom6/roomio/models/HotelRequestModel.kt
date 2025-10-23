package com.tdc.nhom6.roomio.models

import java.util.Date

data class HotelRequestModel(
    val user_id: String = "",
    val username: String = "",
    val birth_date: String = "",
    val cccd_number: String = "",
    val cccd_image: List<String>? = listOf(),
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val gender: String = "",
    val hotel_name: String = "",
    val hotel_address: String = "",
    val hotel_floors: Int = 0,
    val hotel_total_rooms: Int = 0,
    val hotel_type_id: String = "",
    val license: List<String>? = listOf(),
    val status_id: String = "",
    val reason_rejected: String = "",
    val created_at: String = "",
    val updated_at: String = ""
)
