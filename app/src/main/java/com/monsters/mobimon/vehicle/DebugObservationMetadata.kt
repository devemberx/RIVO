package com.monsters.mobimon.vehicle

import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleValue
import com.monsters.mobimon.debug.DebugInterpretationOverrides
import com.monsters.mobimon.debug.DebugVssState

/** Attached before StateFlow publication so conflation cannot move another field's receipt. */
data class DebugObservationStamp(
    val receivedAt: Long,
    val changedAt: Long,
    val revision: Long,
)

internal fun DebugVssState.withObservationReceipts(
    previous: DebugVssState?,
    now: Long,
): DebugVssState {
    val prior = previous?.observationInputs().orEmpty()
    val current = observationInputs()
    val revision = (previous?.observationRevision ?: 0) + 1
    val changed =
        (current.keys + prior.keys).filter { id ->
            (id in current) != (id in prior) ||
                prior[id] != current[id]
        }
    return copy(
        receivedAtElapsedMillis = now,
        observationRevision = revision,
        observationReceipts =
            previous?.observationReceipts.orEmpty() +
                changed.associateWith { DebugObservationStamp(now, now, revision) },
    )
}

internal fun DebugVssState.observationInputs(): Map<String, VehicleValue?> =
    cardExtraSignals.mapValues { (id, value) ->
        VehicleChatFieldCatalog.find(id)?.let { VehicleValue.parse(it.valueType, value) }
    } +
        raw.chatRawValues() + chatInterpretations()

internal fun DebugInterpretationOverrides.chatOverrides(): Map<String, VehicleValue?> =
    mapOf(
        "isDistracted" to isDistracted,
        "isDrowsy" to isDrowsy,
        "attentionLevel" to attentionLevel,
        "isEmergencyBraking" to isEmergencyBraking,
        "distanceToFrontVehicle" to distanceToFrontVehicle,
        "isCharging" to isCharging,
        "batteryPercent" to batteryPercent,
        "outsideTemperature" to outsideTemperature,
        "isRaining" to isRaining,
        "washerFluidLevel" to washerFluidLevel,
        "isEngineWarning" to isEngineWarning,
        "tirePressureStatus" to tirePressureStatus,
        "isMoving" to isMoving,
        "speed" to speed,
        "gear" to gear,
        "isNavigating" to isNavigating,
        "distanceToDestination" to distanceToDestination,
        "isEngineOn" to isEngineOn,
        "timeOfDay" to timeOfDay,
    ).filterValues { it != null }.mapKeys { "interpreted.${it.key}" }.mapValues { (id, value) ->
        VehicleValue.parse(requireNotNull(VehicleChatFieldCatalog.find(id)).valueType, value?.toString())
    }

/** Track derived value changes at the same source mutation, including override transitions. */
internal fun DebugVssState.chatInterpretations(): Map<String, VehicleValue?> =
    mapOf(
        "isDistracted" to isDistracted,
        "isDrowsy" to isDrowsy,
        "attentionLevel" to attentionLevel,
        "isEmergencyBraking" to isEmergencyBraking,
        "distanceToFrontVehicle" to distanceToFrontVehicle,
        "isCharging" to isCharging,
        "batteryPercent" to batteryPercent,
        "outsideTemperature" to outsideTemperature,
        "isRaining" to isRaining,
        "washerFluidLevel" to washerFluidLevel,
        "isEngineWarning" to isEngineWarning,
        "tirePressureStatus" to tirePressureStatus,
        "isMoving" to isMoving,
        "speed" to speed,
        "gear" to gear,
        "isNavigating" to isNavigating,
        "distanceToDestination" to distanceToDestination,
        "isEngineOn" to isEngineOn,
        "timeOfDay" to timeOfDay,
        "isFuelLevelLow" to raw.fuelLevelLow,
    ).mapKeys { "interpreted.${it.key}" }.mapValues { (id, value) ->
        VehicleValue.parse(requireNotNull(VehicleChatFieldCatalog.find(id)).valueType, value.toString())
    }
