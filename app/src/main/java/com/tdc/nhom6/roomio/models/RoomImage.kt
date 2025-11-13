package com.tdc.nhom6.roomio.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RoomImage(
    val imageUrl: String = "",
    val thumbnail: Boolean = false,
    val uploadedAt: Long? = null
) : Parcelable

