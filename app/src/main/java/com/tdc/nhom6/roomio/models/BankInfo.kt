package com.tdc.nhom6.roomio.models

import com.google.gson.annotations.SerializedName

data class BankInfo(
    var account_holder: String? = null,

    @field:SerializedName("account_number")
    var account_number: String? = null,

    var bank_code: String? = null,
    var bank_name: String? = null,
    var chi_nhanh: String? = null,
    @field:SerializedName("default")
    var default: Boolean = false,

    var linked_at: String? = null,

    var logo_url: String? = null
)
