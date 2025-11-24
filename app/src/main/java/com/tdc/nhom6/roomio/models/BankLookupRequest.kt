package com.tdc.nhom6.roomio.models

// Request
data class BankLookupRequest(
    val bank: String,          // mã ngân hàng: VCB, MB, ...
    val account: String        // số tài khoản
)

