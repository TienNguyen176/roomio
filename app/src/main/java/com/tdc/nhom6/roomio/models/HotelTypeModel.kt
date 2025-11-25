package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp

data class HotelTypeModel(
    var type_id: String? = null,
    var type_name: String = "",
    var description: String = "",
    var created_at: Timestamp? = null
)
