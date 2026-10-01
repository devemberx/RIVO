package com.monsters.mobimon.vehicle

import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleDeliveryPolicy
import com.monsters.mobimon.core.domain.VehicleEvidenceFrame
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleValue
import com.monsters.mobimon.core.vss.VehicleObservationRecorder

/** Simulator state is observed when initialized or changed, never on a publication tick. */
internal class DebugVehicleEvidence(
    sessionId: String,
) {
    private val recorder = VehicleObservationRecorder(VehicleObservationSource.DEBUG_OVERRIDE, sessionId)
    private var previous = emptyMap<String, VehicleValue?>()

    fun capture(
        snapshot: VehicleSnapshot,
        receiptAt: Long,
        publishedAt: Long,
    ): VehicleEvidenceFrame {
        val values = snapshot.chatValues()
        val changed = values.filter { (id, value) -> id !in previous || previous[id] != value }
        if (changed.isNotEmpty()) {
            recorder.receive(
                changed,
                receiptAt,
                changed.keys.associateWith {
                    if (it ==
                        VehicleChatFieldCatalog.TIME
                    ) {
                        VehicleDeliveryPolicy.Periodic(60_000)
                    } else {
                        VehicleDeliveryPolicy.OnChange
                    }
                },
            )
        }
        previous = values
        return recorder.publish(publishedAt)
    }
}

internal fun VehicleSnapshot.chatValues(): Map<String, VehicleValue?> {
    val interpreted: Map<String, Any?> =
        mapOf(
            "batteryPercent" to batteryPercent,
            "washerFluidLevel" to washerFluidLevel,
            "tirePressureStatus" to tirePressureStatus,
            "speed" to speed,
            "gear" to gear,
            "drivingState" to drivingState.name,
            "isEngineOn" to isEngineOn,
            "isCharging" to isCharging,
            "outsideTemperature" to outsideTemperature,
            "isRaining" to isRaining,
            "attentionLevel" to attentionLevel,
            "isDistracted" to isDistracted,
            "isDrowsy" to isDrowsy,
            "isEmergencyBraking" to isEmergencyBraking,
            "distanceToFrontVehicle" to distanceToFrontVehicle,
            "isFuelLevelLow" to isFuelLevelLow,
            "isEngineWarning" to isEngineWarning,
            "isNavigating" to isNavigating,
            "distanceToDestination" to distanceToDestination,
            "timeOfDay" to timeOfDay,
        )
    val strings =
        vssCardSignals + interpreted.mapKeys { "interpreted.${it.key}" }.mapValues { it.value?.toString() } +
            (VehicleChatFieldCatalog.TIME to vssTimestamp)
    return strings
        .mapNotNull { (id, value) ->
            VehicleChatFieldCatalog.find(id)?.let { id to VehicleValue.parse(it.valueType, value) }
        }.toMap()
}
