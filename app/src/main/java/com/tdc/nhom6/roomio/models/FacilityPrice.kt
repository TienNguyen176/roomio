package com.tdc.nhom6.roomio.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FacilityPrice(
    val facilityId: String = "",
    val facilityName: String = "",
    val price: Long = 0
) : Parcelable