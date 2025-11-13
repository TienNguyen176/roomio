package com.tdc.nhom6.roomio.models

import kotlinx.parcelize.Parcelize
import android.os.Parcelable

// The Parcelize annotation allows this object to be passed between Android components (e.g., in an Intent)
@Parcelize
data class Facility(
    var id: String = "",
    val facilities_name: String = "",
    val iconUrl: String = "",
    val description: String = ""
) : Parcelable
