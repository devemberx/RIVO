package com.monsters.mobimon.core.presentation

import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.WarningSeverity

data class VehicleConditionReason(
    val concern: VehicleConcern,
    val signal: String,
    val value: String,
    val description: String,
)

/** The same evidence drives the pet's appearance and its explanation. Not a vehicle diagnosis. */
fun VehicleSnapshot.vehicleConditionReasons(): List<VehicleConditionReason> {
    if (quality != SignalQuality.VALID) return emptyList()
    return buildList {
        fun hungry(
            signal: String,
            value: String,
            description: String,
        ) {
            add(VehicleConditionReason(VehicleConcern.HUNGRY, signal, value, description))
        }

        fun sick(
            signal: String,
            value: String,
            description: String,
        ) {
            add(VehicleConditionReason(VehicleConcern.SICK, signal, value, description))
        }
        if ((batteryQuality ?: quality) == SignalQuality.VALID && batteryPercent?.let { it in 0..19 } == true) {
            hungry("interpreted.batteryPercent", batteryPercent.toString(), "차량 배터리 잔량이 20% 미만이야")
        }
        if (washerFluidLevel?.let { it in 0..19 } == true) {
            hungry("interpreted.washerFluidLevel", washerFluidLevel.toString(), "워셔액 잔량이 20% 미만이야")
        }
        if (isFuelLevelLow == true || vssCardSignals["Vehicle.Powertrain.FuelSystem.IsFuelLevelLow"] == "true") {
            hungry("Vehicle.Powertrain.FuelSystem.IsFuelLevelLow", "true", "연료 부족 신호가 켜져 있어")
        }
        if (tirePressureStatus == "NG") {
            sick("interpreted.tirePressureStatus", "NG", "타이어 공기압 부족 신호가 있어")
        }
        if (isEmergencyBraking == true) {
            sick("interpreted.isEmergencyBraking", "true", "급제동 감지 신호가 있어")
        }
        if (isDrowsy == true) sick("interpreted.isDrowsy", "true", "운전자 졸음 감지 신호가 있어")
        if (isDistracted == true) sick("interpreted.isDistracted", "true", "운전자 주의 분산 신호가 있어")
        if (isEngineWarning == true) sick("interpreted.isEngineWarning", "true", "차량 진단 경고 신호가 있어")
        warnings.filter { it.quality == SignalQuality.VALID && it.severity != WarningSeverity.NOTICE }.forEach {
            sick("vehicle_warning", it.item.take(100), it.description.take(200))
        }
        vssCardSignals.forEach { (path, value) ->
            val assessment = VehicleSignalConcern.assess(this@vehicleConditionReasons, path)
            if (assessment?.status == VehicleSignalStatus.CAUTION) {
                add(VehicleConditionReason(assessment.concern, path, value.take(200), warningDescription(path)))
            }
        }
    }
}

private fun warningDescription(path: String): String =
    when {
        path.endsWith("WasherFluid.IsLevelLow") -> "워셔액 부족 신호가 켜져 있어"
        path.endsWith("TractionBattery.ErrorCodes") -> "구동 배터리 오류 코드가 보고됐어"
        path.endsWith("IsServiceDue") -> "차량 점검 시기가 됐다는 신호가 있어"
        path.endsWith("Tire.IsPressureLow") -> "타이어 공기압 부족 신호가 있어"
        path.endsWith("Brake.IsFluidLevelLow") -> "브레이크액 부족 신호가 있어"
        path.endsWith("Brake.IsBrakesWorn") -> "브레이크 마모 신호가 있어"
        path.endsWith("ABS.IsError") -> "ABS 오류 신호가 있어"
        path.endsWith("IsBrokenDown") -> "차량 고장 신호가 있어"
        path.endsWith("IsDefect") -> "차량 조명 고장 신호가 있어"
        else -> "차량 경고 신호가 있어"
    }
