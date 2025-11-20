package com.tdc.nhom6.roomio.models

data class Bank(
    var id: String = "",      // bin
    var name: String = "",
    var accountNumber: String = "",
    var logoUrl: String = "",
    var isDefault: Boolean = false
)
