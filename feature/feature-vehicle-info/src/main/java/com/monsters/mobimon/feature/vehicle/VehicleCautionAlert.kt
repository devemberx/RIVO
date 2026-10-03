package com.monsters.mobimon.feature.vehicle

import com.monsters.mobimon.core.domain.VehicleSnapshot

data class VehicleCautionAlert(
    val id: String,
    val title: String,
)

/** Uses current card statuses, puts selected cards first, and includes cautions outside visible slots. */
fun vehicleCautionAlerts(
    snapshot: VehicleSnapshot,
    selectedCards: List<String>,
): List<VehicleCautionAlert> {
    val orderedIds =
        (
            VehicleCardSelectionStore.validOrDefaults(
                selectedCards,
            ) + VehicleCardCatalog.allCards.map { it.id }
        ).distinct()
    val alertedConditions = mutableSetOf<String>()
    return orderedIds.mapNotNull { id ->
        if (VehicleCardCatalog.status(id, snapshot) != VehicleCardStatus.CAUTION) return@mapNotNull null
        val conditionId =
            when (id) {
                "tire", "tire-low" -> "tire-pressure"
                "washer", "washer-low" -> "washer-fluid"
                else -> id
            }
        if (!alertedConditions.add(conditionId)) return@mapNotNull null
        val spec = VehicleCardCatalog.find(id) ?: return@mapNotNull null
        val title =
            when (id) {
                "battery" -> "배터리 잔량이 낮아요"
                "tire", "tire-low" -> "타이어 공기압 확인이 필요해요"
                else -> "${spec.title} 확인이 필요해요"
            }
        VehicleCautionAlert(id, title)
    }
}
