package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationConditionReason
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleChatCapture
import com.monsters.mobimon.core.domain.VehicleChatEvidenceSource
import com.monsters.mobimon.core.domain.VehicleChatField
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleChatTopic
import com.monsters.mobimon.core.domain.VehicleDeliveryPolicy
import com.monsters.mobimon.core.domain.VehicleDerivation
import com.monsters.mobimon.core.domain.VehicleEvidenceFrame
import com.monsters.mobimon.core.domain.VehicleFieldValidity
import com.monsters.mobimon.core.domain.VehicleObservation
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleObservationValidity
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleSubscriptionState
import com.monsters.mobimon.core.domain.VehicleValue
import com.monsters.mobimon.core.presentation.VehicleConcern
import com.monsters.mobimon.core.presentation.vehicleConditionReasons
import java.time.OffsetDateTime
import java.util.UUID

/** One immutable capture feeds both basic context and read-only detailed queries. */
class VehicleChatEvidenceReader(
    private val snapshot: () -> VehicleSnapshot,
    private val nowMillis: () -> Long,
    private val debugModeEnabled: suspend () -> Boolean,
) : VehicleChatEvidenceSource {
    override suspend fun capture(topic: VehicleChatTopic): VehicleChatCapture {
        val debug = debugModeEnabled()
        val vehicle = snapshot()
        val now = nowMillis()
        val source = if (debug) VehicleObservationSource.DEBUG_OVERRIDE else VehicleObservationSource.VSS_ADAPTER
        val matches =
            vehicle.source == (if (debug) SignalSource.SIMULATED else SignalSource.REAL) &&
                vehicle.isDebuggerOverride == debug &&
                debugModeEnabled() == debug
        var frame =
            vehicle.evidenceFrame.takeIf { matches } ?: VehicleEvidenceFrame(
                0,
                now,
                emptyMap(),
                emptyMap(),
                VehicleSubscriptionState("unavailable-${vehicle.epoch}", false, false, false),
            )
        // A supplied frame is not permission to mix adapter and debugger observations.
        frame = frame.copy(observations = frame.observations.filterValues { it.sourceKind == source })
        frame = batteryAlias(frame)

        fun field(id: String): VehicleChatField {
            val spec = requireNotNull(VehicleChatFieldCatalog.find(id))
            val observation = frame.observations[id]
            var validity = VehicleObservationValidity.evaluate(frame, id, now)
            val value = observation?.value
            if (value != null &&
                (
                    VehicleValue.parse(spec.valueType, value.canonical()) != value ||
                        spec.unit == "%" &&
                        (value as? VehicleValue.Number)?.value?.let { it !in 0.0..100.0 } == true ||
                        id == VehicleChatFieldCatalog.TIME &&
                        runCatching { OffsetDateTime.parse(value.canonical()) }.isFailure
                )
            ) {
                validity =
                    VehicleFieldValidity(
                        SignalQuality.UNAVAILABLE,
                        "INVALID_VALUE",
                        validity.receiptAgeMillis,
                        validity.validityBasis,
                    )
            }
            val mode =
                when (frame.policies[id]) {
                    is VehicleDeliveryPolicy.Periodic -> "PERIODIC"
                    VehicleDeliveryPolicy.OnChange -> "ON_CHANGE"
                    else -> "UNKNOWN"
                }
            return VehicleChatField(spec, observation, validity, mode)
        }
        val reasons =
            vehicle.vehicleConditionReasons().filter { reason ->
                VehicleChatFieldCatalog.find(reason.signal) != null &&
                    field(reason.signal).value?.canonical() == reason.value
            }
        val essentials =
            listOf(
                "interpreted.batteryPercent",
                "interpreted.washerFluidLevel",
                "interpreted.tirePressureStatus",
                "interpreted.isEmergencyBraking",
                "interpreted.isDrowsy",
                "interpreted.isDistracted",
                "interpreted.isEngineWarning",
                "interpreted.isFuelLevelLow",
            ) +
                VehicleChatFieldCatalog.fields
                    .filter {
                        it.id.endsWith("IsServiceDue") ||
                            it.id.endsWith("IsDefect") ||
                            it.id.endsWith("IsError") ||
                            it.id.endsWith("IsBrokenDown") ||
                            it.id.endsWith("IsBrakesWorn") ||
                            it.id.endsWith("IsFluidLevelLow") ||
                            it.id.endsWith("IsPressureLow") ||
                            it.id.endsWith("ErrorCodes")
                    }.map { it.id }
        val validInputs = essentials.filter { field(it).value != null }
        val condition =
            when {
                reasons.any { it.concern == VehicleConcern.SICK } -> "WARNING"
                reasons.any { it.concern == VehicleConcern.HUNGRY } -> "LOW_BATTERY"
                validInputs.size == essentials.size -> "CHECKED"
                validInputs.isNotEmpty() -> "PARTIAL"
                else -> null
            }
        val deps = if (reasons.isNotEmpty()) reasons.map { it.signal }.distinct() else validInputs
        if (condition != null && deps.isNotEmpty()) {
            val observations = deps.mapNotNull { frame.observations[it] }
            val observation =
                VehicleObservation(
                    VehicleChatFieldCatalog.CONDITION,
                    VehicleValue.Text(condition),
                    source,
                    frame.subscription.sessionId,
                    observations.maxOf { it.receiptRevision },
                    observations.mapNotNull { it.receivedAtElapsedMillis }.minOrNull(),
                    observations.mapNotNull { it.changedAtElapsedMillis }.minOrNull(),
                    derivation = VehicleDerivation.DERIVED,
                    dependencyIds = deps,
                )
            frame =
                frame.copy(
                    observations = frame.observations + (observation.fieldId to observation),
                    policies = frame.policies + (observation.fieldId to VehicleDeliveryPolicy.OnChange),
                )
        }
        val basic =
            setOf(VehicleChatFieldCatalog.BATTERY, VehicleChatFieldCatalog.TIME, VehicleChatFieldCatalog.CONDITION) +
                reasons.take(32).map { it.signal }
        val selected =
            VehicleChatFieldCatalog.fields.filter {
                when (topic) {
                    VehicleChatTopic.BASIC -> it.id in basic
                    VehicleChatTopic.OVERVIEW -> true
                    VehicleChatTopic.CONDITION -> it.id in basic || it.id in essentials
                    else -> it.topic == topic
                }
            }
        return VehicleChatCapture(
            UUID.randomUUID().toString(),
            now,
            source,
            frame.subscription.sessionId,
            selected.map {
                field(it.id)
            },
            reasons.take(32).map { ConversationConditionReason(it.concern.name, it.signal, it.value, it.description) },
        )
    }

    private fun batteryAlias(frame: VehicleEvidenceFrame): VehicleEvidenceFrame {
        val id = VehicleChatFieldCatalog.BATTERY
        if (id in frame.observations) return frame
        val rawId = "Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed"
        val raw = frame.observations[rawId] ?: return frame
        val value = (raw.value as? VehicleValue.Number)?.value ?: return frame
        val interpreted =
            raw.copy(
                fieldId = id,
                value = VehicleValue.Number(kotlin.math.round(value)),
                derivation = VehicleDerivation.DERIVED,
                dependencyIds = listOf(rawId),
            )
        return frame.copy(
            observations = frame.observations + (id to interpreted),
            policies = frame.policies + (id to VehicleDeliveryPolicy.OnChange),
        )
    }
}
