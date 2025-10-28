package com.tdc.nhom6.roomio.models

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class Booking(

    val customerId: Int,
    val roomTypeId: Int,

    val statusId: Int? = null,

    val checkInDate: LocalDateTime? = null,
    val checkOutDate: LocalDateTime? = null,

    val checkInDateActual: LocalDateTime? = null,
    val checkOutDateActual: LocalDateTime? = null,

    val numberGuest: Int = 1,
    val note: String? = null,
    val totalOrigin: BigDecimal,
    val discountId: String? = null,

    val totalFinal: BigDecimal,

    val createdAt: LocalDateTime = LocalDateTime.now()
)