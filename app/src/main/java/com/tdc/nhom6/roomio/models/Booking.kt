package com.tdc.nhom6.roomio.models

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

@Parcelize
data class Booking(
    @DocumentId
    val bookingId: String? = null,

    val customerId: String = "",
    val roomTypeId: String = "",
    var roomId: String? = null,
    var discountId: String? = null,
    var discountPaymentMethodId: String? = null,

    var status: String = "pending",

    val totalOrigin: Double = 0.0,
    var totalFinal: Double = 0.0,

    val checkInDate: Timestamp? = null,
    val checkOutDate: Timestamp? = null,

    val checkInDateActual: Timestamp? = null,
    val checkOutDateActual: Timestamp? = null,

    val numberGuest: Int = 1,
    val note: String? = null,

    val createdAt: Timestamp = Timestamp.now()

) : Parcelable