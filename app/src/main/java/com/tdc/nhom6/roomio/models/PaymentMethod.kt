package com.tdc.nhom6.roomio.models

import android.net.Uri
import com.google.firebase.firestore.DocumentId

 class PaymentMethod(
    val paymentMethodId: String? = null,

    val paymentMethodName: String = "",

    val description: String? = null,

    val iconId: String? = null,
    val discountId: String? = null

)
