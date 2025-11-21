package com.tdc.nhom6.roomio.models

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class RoomImage(
    val imageUrl: String = "",
    val thumbnail: Boolean = false,
    val uploadedAt: Timestamp? = null
) : Parcelable

