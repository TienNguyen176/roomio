package com.tdc.nhom6.roomio.models

data class ReservationUi(
    val documentId: String,
    val reservationId: String,
    val displayReservationCode: String,
    val badge: String,
    val line1: String,
    val line2: String,
    val line3: String,
    val action: String,
    val headerColor: HeaderColor,
    val status: ReservationStatus = ReservationStatus.UNCOMPLETED,
    val numberGuest: Int = 1,
    val roomType: String = "",
    val roomTypeId: String? = null,
    val hotelId: String? = null,
    val guestPhone: String? = null,
    val guestEmail: String? = null,
    val totalFinalAmount: Double = 0.0,
    val checkInText: String = "",
    val checkOutText: String = "",
    val checkInMillis: Long? = null,
    val checkOutMillis: Long? = null,
    val discountLabel: String = "-",
    val cleaningCompletedAtMillis: Long? = null
)

