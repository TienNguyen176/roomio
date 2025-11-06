package com.tdc.nhom6.roomio.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Invoice(
    @DocumentId
    var invoiceId: String? = null,

    var bookingId: String? = null,

    var totalAmount: Double? = 0.0,

    var paymentMethodId: String? = null,

    var createdAt: Timestamp = Timestamp.now(),

    var issuedById: Int? = null,

    var issuedDate: Timestamp? = null
)