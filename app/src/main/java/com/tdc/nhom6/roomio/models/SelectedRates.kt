
package com.tdc.nhom6.roomio.models

data class SelectedRates(
    val facilityRates: List<FacilityPrice>,
    val damageLossRates: List<DamageLossPrice>
)
