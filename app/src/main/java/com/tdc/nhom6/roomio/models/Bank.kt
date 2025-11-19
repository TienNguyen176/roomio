package com.tdc.nhom6.roomio.models

data class Bank(
    val id: String = "",
    var name: String = "",
    val logoUrl: String = "",
    var accountNumber: String = "",
    val accountHolder: String = "",
    val isDefault: Boolean = false
)
