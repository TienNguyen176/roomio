package com.tdc.nhom6.roomio.model

import com.google.firebase.firestore.DocumentId

data class RoomType(
    @DocumentId
    val roomTypeId: String = "",
    val hotelId: String = "",
    val typeName: String = "",
    val description: String = "",
    val pricePerNight: Double = 0.0,
    val maxPeople: Int = 0,
    val viewId: String = "",
    val area: Int = 0
)


