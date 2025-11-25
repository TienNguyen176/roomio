package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp

data class PaymentItemModel(
    val invoiceId: String? = null,
    val bookingId: String? = null,
    val checkInDate: Timestamp? = null,
    val checkOutDay: Timestamp? = null,
    val totalPaidAmount: Double? = null,
    val createdAt: Timestamp? = null,
    val status: String? = null
)
