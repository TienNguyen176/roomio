package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp
import java.math.BigDecimal
import java.time.LocalDateTime

data class Invoice(

    val bookingId: Int,

    val totalAmount: BigDecimal,

    val issuedById: Int? = null,
    val paymentMethodId: Int? = null,

    val issuedDate: Timestamp = Timestamp.now()
)