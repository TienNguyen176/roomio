package com.tdc.nhom6.roomio.models

data class BrokenItem(
    val name: String,
    val pricePerItem: Double,
    var checked: Boolean = false,
    var description: String = "",
    val facilityId: String = "" // Store facilityId for saving to facilitiesUsed
)
