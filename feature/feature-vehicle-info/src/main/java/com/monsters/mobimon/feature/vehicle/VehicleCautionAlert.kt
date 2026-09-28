package com.monsters.mobimon.feature.vehicle

import com.monsters.mobimon.core.domain.VehicleSnapshot

data class VehicleCautionAlert(
    val id: String,
    val title: String,
)

/** Uses the same current card status as the Vehicle screen and retains the selected slot order. */
fun vehicleCautionAlerts(
    snapshot: VehicleSnapshot,
    selectedCards: List<String>,
): List<VehicleCautionAlert> =
    VehicleCardSelectionStore.validOrDefaults(selectedCards).mapNotNull { id ->
        val spec = VehicleCardCatalog.find(id) ?: return@mapNotNull null
        if (VehicleCardCatalog.status(id, snapshot) != VehicleCardStatus.CAUTION) return@mapNotNull null
        val title =
            when (id) {
                "battery" -> "배터리 잔량이 낮아요"
                "tire", "tire-low" -> "타이어 공기압 확인이 필요해요"
                else -> "${spec.title} 확인이 필요해요"
            }
        VehicleCautionAlert(id, title)
    }
