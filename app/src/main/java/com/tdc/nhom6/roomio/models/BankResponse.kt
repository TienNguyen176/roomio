package com.tdc.nhom6.roomio.models

data class BankResponse(
    val data: List<BankData>
)

data class BankData(
    val bin: String,
    val name: String,
    val shortName: String,
    val logo: String
)
