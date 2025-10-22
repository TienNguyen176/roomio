package com.tdc.nhom6.roomio.models

data class User(
    val current_id: String = "",
    val avatar: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val gender: String = "",
    val birthDate: String = "",
    val accountId: String = "",
    val roleId: String = "userRoles/user" // mặc định là user
)
