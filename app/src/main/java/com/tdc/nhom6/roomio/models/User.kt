package com.tdc.nhom6.roomio.models

data class User(
    var uid: String = "",
    var avatar: String = "",
    var username: String = "",
    var email: String = "",
    var phone: String = "",
    val genderId: String = "",
    val birthDate: String = "",
    var roleId: String = "user",   // mặc định user
    var createdAt: String = "",
    var walletBalance: Double = 0.0
)




