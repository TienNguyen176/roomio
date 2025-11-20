package com.tdc.nhom6.roomio.models

data class BankLookupResponse(
    val code: Int,
    val success: Boolean,
    val status: Boolean,
    val data: BankLookupResponseData?,
    val msg: String?
)
