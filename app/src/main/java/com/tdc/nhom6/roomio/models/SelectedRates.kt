
package com.tdc.nhom6.roomio.models

data class SelectedRates(
    val facilityRates: List<FacilityPrice>,      // giá sử dụng tiện ích
    val damageLossRates: List<DamageLossPrice>   // giá bồi thường hư hỏng
)
