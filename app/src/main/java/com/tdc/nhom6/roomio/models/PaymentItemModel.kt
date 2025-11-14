package com.tdc.nhom6.roomio.models

data class PaymentItemModel(
    val bookingId: String? = null,
    val checkInDate: String? = null,
    val checkOutDay: String? = null,
    val totalPaidAmount: Double? = null,
    val status: String? = null
)
