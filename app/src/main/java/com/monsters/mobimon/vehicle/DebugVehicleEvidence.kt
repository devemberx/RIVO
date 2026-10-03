package com.monsters.mobimon.vehicle

import com.monsters.mobimon.core.domain.VehicleChatDependencies
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleDeliveryPolicy
import com.monsters.mobimon.core.domain.VehicleDerivation
import com.monsters.mobimon.core.domain.VehicleEvidenceFrame
import com.monsters.mobimon.core.domain.VehicleObservation
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleValue
import com.monsters.mobimon.core.vss.VehicleObservationRecorder
import com.monsters.mobimon.debug.DebugVssState

/** Simulator state is observed when initialized or changed, never on a publication tick. */
internal class DebugVehicleEvidence(
    sessionId: String,
) {
    private val recorder = VehicleObservationRecorder(VehicleObservationSource.DEBUG_OVERRIDE, sessionId)
    private var previous = emptyMap<String, VehicleValue?>()
    private var previousStamps = emptyMap<String, DebugObservationStamp>()

    fun capture(
        snapshot: VehicleSnapshot,
        receiptAt: Long,
        publishedAt: Long,
        debug: DebugVssState,
    ): VehicleEvidenceFrame {
        val overrides = debug.overrides.chatOverrides()
        val values =
            snapshot.chatValues() + debug.raw.chatRawValues() +
                ("interpreted.isMoving" to VehicleValue.Boolean(debug.isMoving)) +
                ("interpreted.isFuelLevelLow" to VehicleValue.Boolean(debug.raw.fuelLevelLow))
        val changed =
            (values + (previous.keys - values.keys).associateWith { null }).filter { (id, value) ->
                id !in previous ||
                    previous[id] != value ||
                    previousStamps[id] != debug.observationReceipts[id]
            }
        changed.forEach { (id, value) ->
            recorder.receive(
                mapOf(id to value),
                debug.observationReceipts[id]?.receivedAt ?: receiptAt,
                mapOf(
                    id to
                        if (id ==
                            VehicleChatFieldCatalog.TIME
                        ) {
                            VehicleDeliveryPolicy.Periodic(60_000)
                        } else {
                            VehicleDeliveryPolicy.OnChange
                        },
                ),
            )
        }
        previous = values
        previousStamps = debug.observationReceipts
        val frame = recorder.publish(publishedAt)
        val observations = frame.observations.toMutableMap()

        fun resolve(
            id: String,
            visited: Set<String> = emptySet(),
        ): VehicleObservation? {
            val original = observations[id] ?: return null
            if (id in visited) return null
            val deps =
                if (id in
                    overrides
                ) {
                    emptyList()
                } else {
                    VehicleChatDependencies.forField(id)
                }
            val stamp = debug.observationReceipts[id]
            val derived =
                if (deps.isEmpty()) {
                    original.copy(
                        receivedAtElapsedMillis = stamp?.receivedAt ?: original.receivedAtElapsedMillis,
                        changedAtElapsedMillis = stamp?.changedAt ?: original.changedAtElapsedMillis,
                    )
                } else {
                    val inputs = deps.mapNotNull { resolve(it, visited + id) }
                    original.copy(
                        derivation = VehicleDerivation.DERIVED,
                        dependencyIds = deps,
                        changedAtElapsedMillis =
                            stamp?.changedAt ?: inputs.mapNotNull { it.changedAtElapsedMillis }.maxOrNull(),
                        receiptRevision = inputs.maxOfOrNull { it.receiptRevision } ?: original.receiptRevision,
                        receivedAtElapsedMillis =
                            (
                                inputs.mapNotNull { it.receivedAtElapsedMillis } +
                                    listOfNotNull(stamp?.receivedAt)
                            ).maxOrNull(),
                    )
                }
            observations[id] = derived
            return derived
        }
        observations.keys.toList().forEach { resolve(it) }
        return frame.copy(observations = observations.toMap())
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
