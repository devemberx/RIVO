package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationContext
import com.monsters.mobimon.core.domain.ConversationContextSource
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import java.time.OffsetDateTime

/** Reads only name and independently fresh VSS time; vehicle telemetry stays outside the prompt. */
class VehicleConversationContext(
    private val userName: () -> String?,
    private val snapshot: () -> VehicleSnapshot,
    private val nowMillis: () -> Long,
    private val allowSimulatedTime: Boolean = false,
) : ConversationContextSource {
    override fun current(): ConversationContext {
        val name = userName()?.trim()?.takeIf { it.isNotEmpty() && it.length <= 100 && it.none(Char::isISOControl) }
        val vehicle = snapshot()
        val age = vehicle.timeObservedAtMillis?.let { nowMillis() - it }
        val time =
            vehicle.vssTimestamp?.takeIf {
                vehicle.quality == SignalQuality.VALID &&
                    (vehicle.source == SignalSource.REAL || allowSimulatedTime) &&
                    age != null &&
                    age in 0..60_000 &&
                    runCatching { OffsetDateTime.parse(it) }.isSuccess
            }
        return ConversationContext(
            name,
            time,
            vehicle.timeOfDay.takeIf { time != null },
            time != null && vehicle.source == SignalSource.SIMULATED,
        )
    }
}
