package com.monsters.mobimon.core.presentation

import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.VehicleSnapshot

enum class VehicleConcern { HUNGRY, SICK }

enum class VehicleSignalStatus { NORMAL, CAUTION, UNAVAILABLE }

data class VehicleSignalAssessment(
    val status: VehicleSignalStatus,
    val concern: VehicleConcern,
)

/** Interprets only explicit, current warning signals used for display conditions. */
object VehicleSignalConcern {
    private const val BATTERY_ERROR = "Vehicle.Powertrain.TractionBattery.ErrorCodes"
    private const val WASHER_LOW = "Vehicle.Body.Windshield.Front.WasherFluid.IsLevelLow"
    private val wheels = listOf("Row1.Wheel.Left", "Row1.Wheel.Right", "Row2.Wheel.Left", "Row2.Wheel.Right")
    private val sickBooleanPaths =
        setOf(
            "Vehicle.Service.IsServiceDue",
            "Vehicle.Body.Lights.Beam.Low.IsDefect",
            "Vehicle.Body.Lights.Brake.IsDefect",
            "Vehicle.ADAS.ABS.IsError",
            "Vehicle.IsBrokenDown",
        ) +
            wheels.flatMap { wheel ->
                listOf(
                    "Vehicle.Chassis.Axle.$wheel.Brake.IsFluidLevelLow",
                    "Vehicle.Chassis.Axle.$wheel.Tire.IsPressureLow",
                    "Vehicle.Chassis.Axle.$wheel.Brake.IsBrakesWorn",
                )
            }

    fun assess(
        snapshot: VehicleSnapshot,
        path: String,
    ): VehicleSignalAssessment? {
        val concern =
            when (path) {
                BATTERY_ERROR -> VehicleConcern.SICK
                WASHER_LOW -> VehicleConcern.HUNGRY
                in sickBooleanPaths -> VehicleConcern.SICK
                else -> return null
            }
        if (snapshot.quality != SignalQuality.VALID ||
            (path == BATTERY_ERROR && (snapshot.batteryQuality ?: snapshot.quality) != SignalQuality.VALID)
        ) {
            return VehicleSignalAssessment(VehicleSignalStatus.UNAVAILABLE, concern)
        }
        val raw = snapshot.vssCardSignals[path]
        val status =
            if (path == BATTERY_ERROR) {
                when {
                    raw == null -> VehicleSignalStatus.UNAVAILABLE
                    raw.isBlank() -> VehicleSignalStatus.NORMAL
                    else -> VehicleSignalStatus.CAUTION
                }
            } else {
                when (raw) {
                    "true" -> VehicleSignalStatus.CAUTION
                    "false" -> VehicleSignalStatus.NORMAL
                    else -> VehicleSignalStatus.UNAVAILABLE
                }
            }
        return VehicleSignalAssessment(status, concern)
    }
}
