package com.tdc.nhom6.roomio.models

data class BankInfo(
    val account_holder: String? = null,

    val account_number: String? = null,

    val bank_code: String? = null,

    val bank_name: String? = null,

    val default: Boolean = false,

    val linked_at: String? = null,

    val logo_url: String? = null
)