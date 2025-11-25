package com.tdc.nhom6.roomio.models

data class LostItem(
    val name: String,
    val pricePerItem: Double,
    var checked: Boolean = false,
    var quantity: Int = 0,
    val facilityId: String = "" // Store facilityId for saving to facilitiesUsed
)
