package com.tdc.nhom6.roomio.models

data class MiniItem(
    val name: String,
    val pricePerItem: Double,
    var checked: Boolean = false,
    var quantity: Int = 0
)
