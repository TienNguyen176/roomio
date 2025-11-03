package com.tdc.nhom6.roomio.models

import android.net.Uri

data class PaymentMethod(

    val paymentMethodName: String = "",

    val description: String? = null,

    val iconId: String? = null,
    val discountId: String? = null

)
