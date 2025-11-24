package com.tdc.nhom6.roomio.models

data class AccountModel(
    val userId: String = "",
    val fullName: String = "",
    val avatarUrl: String = "",
    var roleId: String = "user"
)
