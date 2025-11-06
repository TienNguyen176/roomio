package com.tdc.nhom6.roomio.models

data class BankInfo(
    var bankName: String = "",
    var bankAccount: String = "",
    var accountHolder: String = "",
    var linkedAt: String = ""
)
data class User(
    var uid: String = "",
    var avatar: String = "",
    var username: String = "",
    var email: String = "",
    var phone: String = "",
    val gender: String = "",
    val birthDate: String = "",
    var roleId: String = "user",   // mặc định user
    var createdAt: String = "",
    var walletBalance: Double = 0.0,
    var bankInfo: BankInfo? = null   // thêm dòng này
)


data class Bank(
    val id: String = "",
    val name: String = "",
    val logoUrl: String = "",
    val accountNumber: String = "",
    val accountHolder: String = "",
    val isDefault: Boolean = false
)



