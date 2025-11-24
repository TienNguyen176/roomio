package com.tdc.nhom6.roomio.models

data class VietQRBankResponse(
    val success: Boolean,
    val data: List<VietQRBank>
)

data class VietQRBank(
    val code: String,   // mã ngân hàng (VD: 970417)
    val name: String,   // tên ngân hàng
    val logo: String?   // URL logo (có thể null)
)
