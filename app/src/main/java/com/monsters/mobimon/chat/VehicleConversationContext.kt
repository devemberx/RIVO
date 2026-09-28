package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationConditionReason
import com.monsters.mobimon.core.domain.ConversationContext
import com.monsters.mobimon.core.domain.ConversationContextSource
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleFreshnessPolicy
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.presentation.vehicleCondition
import com.monsters.mobimon.core.presentation.vehicleConditionReasons
import java.time.OffsetDateTime

/** Reads bounded vehicle context from the explicitly selected runtime source at send time. */
class VehicleConversationContext(
    private val userName: () -> String?,
    private val snapshot: () -> VehicleSnapshot,
    private val nowMillis: () -> Long,
    private val debugModeEnabled: suspend () -> Boolean = { false },
    private val freshness: VehicleFreshnessPolicy = VehicleFreshnessPolicy(15_000),
) : ConversationContextSource {
    override suspend fun current(): ConversationContext {
        val name = userName()?.trim()?.takeIf { it.isNotEmpty() && it.length <= 100 && it.none(Char::isISOControl) }
        val debug = debugModeEnabled()
        val expectedSource = if (debug) SignalSource.SIMULATED else SignalSource.REAL
        val original = snapshot()
        if (original.source != expectedSource ||
            original.isDebuggerOverride != debug ||
            debugModeEnabled() != debug
        ) {
            return ConversationContext(userName = name)
        }
        val now = nowMillis()
        val vehicle = freshness.displaySnapshot(original, expectedSource, now)
        val age = vehicle.timeObservedAtMillis?.let { now - it }
        val time =
            vehicle.vssTimestamp?.takeIf {
                vehicle.quality == SignalQuality.VALID &&
                    age != null &&
                    age in 0..60_000 &&
                    runCatching { OffsetDateTime.parse(it) }.isSuccess
            }
        return ConversationContext(
            name,
            time,
            vehicle.timeOfDay.takeIf { time != null },
            time != null && vehicle.source == SignalSource.SIMULATED,
            vehicle.batteryPercent.takeIf { vehicle.batteryQuality == SignalQuality.VALID },
            vehicle.batteryQuality == SignalQuality.VALID && debug,
            vehicle.vehicleCondition().name,
            vehicle.vehicleConditionReasons().map {
                ConversationConditionReason(it.concern.name, it.signal, it.value, it.description)
            },
            debug,
        )
    }
}
