package com.tdc.nhom6.roomio.models

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class Booking(
    val customerId: String,
    val roomTypeId: String,
    val totalOrigin: Double,
    var totalFinal: Double,

    val statusId: Int? = 0,

    val checkInDate: Long? = null,
    val checkOutDate: Long? = null,

    val checkInDateActual: Long? = null,
    val checkOutDateActual: Long? = null,

    val numberGuest: Int = 1,
    val note: String? = null,
    var discountId: String? = null,

    val createdAt: Timestamp = Timestamp.now()

) : Parcelable