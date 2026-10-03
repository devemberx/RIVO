package com.monsters.mobimon.feature.vehicle

import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleWarning
import com.monsters.mobimon.core.domain.WarningSeverity
import com.monsters.mobimon.core.presentation.vehicleCondition

internal typealias VehicleCondition = com.monsters.mobimon.core.presentation.VehicleCondition

internal data class VehicleInfoUiState(
    val condition: VehicleCondition,
    val batteryPercent: Int?,
    val tireStatus: String?,
    val tireWarning: VehicleWarning?,
)

internal fun VehicleSnapshot.toVehicleInfoUiState(): VehicleInfoUiState {
    val battery = batteryPercent?.takeIf { (batteryQuality ?: quality) == SignalQuality.VALID && it in 0..100 }
    // These legacy optional readings share the snapshot timestamp until the adapter supplies per-signal metadata.
    val current = takeIf { quality == SignalQuality.VALID }
    val tire = current?.tirePressureStatus?.takeIf(String::isNotBlank)
    val tireWarning =
        current?.warnings?.firstOrNull {
            it.quality == SignalQuality.VALID &&
                it.severity != WarningSeverity.NOTICE &&
                (it.item.contains("타이어") || it.item.contains("바퀴"))
        }
    return VehicleInfoUiState(
        condition = vehicleCondition(),
        batteryPercent = battery,
        tireStatus = tire,
        tireWarning = tireWarning,
    )
}
