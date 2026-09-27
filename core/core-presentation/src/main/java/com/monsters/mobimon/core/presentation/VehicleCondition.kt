package com.monsters.mobimon.core.presentation

import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.WarningSeverity

/** Shared display classification; does not authorize vehicle actions. */
enum class VehicleCondition { CHECKED, PARTIAL, LOW_BATTERY, WARNING, STALE, UNAVAILABLE }

fun VehicleSnapshot.vehicleCondition(): VehicleCondition {
    val battery = batteryPercent?.takeIf { (batteryQuality ?: quality) == SignalQuality.VALID && it in 0..100 }
    val cardConcerns = vssCardSignals.keys.mapNotNull { VehicleSignalConcern.assess(this, it) }
    return when {
        quality == SignalQuality.UNAVAILABLE -> VehicleCondition.UNAVAILABLE
        quality == SignalQuality.STALE -> VehicleCondition.STALE
        tirePressureStatus == "NG" -> VehicleCondition.WARNING
        isEmergencyBraking == true ||
            isDrowsy == true ||
            isDistracted == true ||
            isEngineWarning == true ||
            warnings.any {
                it.quality == SignalQuality.VALID && it.severity != WarningSeverity.NOTICE
            } ||
            cardConcerns.any { it.status == VehicleSignalStatus.CAUTION && it.concern == VehicleConcern.SICK } ->
            VehicleCondition.WARNING
        (battery != null && battery < 20) ||
            washerFluidLevel?.let { it in 0..19 } == true ||
            isFuelLevelLow == true ||
            vssCardSignals["Vehicle.Powertrain.FuelSystem.IsFuelLevelLow"]?.toBoolean() == true ||
            cardConcerns.any { it.status == VehicleSignalStatus.CAUTION && it.concern == VehicleConcern.HUNGRY } ->
            VehicleCondition.LOW_BATTERY
        battery == null ||
            tirePressureStatus.isNullOrBlank() ||
            isEmergencyBraking == null ||
            isDrowsy == null ||
            isDistracted == null -> VehicleCondition.PARTIAL
        else -> VehicleCondition.CHECKED
    }
}
