package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp

data class HotelTypeModel(
    var id: String? = null,
    var typeName: String = "",
    var description: String = "",
    var createdAt: Timestamp? = null
)
