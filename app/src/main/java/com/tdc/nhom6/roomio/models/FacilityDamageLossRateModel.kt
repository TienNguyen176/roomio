package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp

class FacilityDamageLossRateModel(
    val facilityId: String = "",
    val statusId: String = "",
    val price: Double = 0.0,
    val updateDate: Timestamp? = null
)