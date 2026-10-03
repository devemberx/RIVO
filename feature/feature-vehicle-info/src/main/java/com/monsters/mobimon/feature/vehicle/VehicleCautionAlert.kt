package com.monsters.mobimon.feature.vehicle

import com.monsters.mobimon.core.domain.SignalQuality
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
    val cardAlerts =
        orderedIds.mapNotNull { id ->
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
    val driverAlert =
        if (snapshot.quality == SignalQuality.VALID &&
            (snapshot.isEmergencyBraking == true || snapshot.isDrowsy == true || snapshot.isDistracted == true)
        ) {
            val title =
                when {
                    snapshot.isEmergencyBraking == true -> "급제동 감지 신호가 있어요"
                    snapshot.isDrowsy == true -> "운전자 졸음 감지 신호가 있어요"
                    else -> "운전자 주의 분산 신호가 있어요"
                }
            listOf(VehicleCautionAlert("driver-state", title))
        } else {
            emptyList()
        }
    return cardAlerts + driverAlert
}
