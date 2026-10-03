package com.monsters.mobimon.core.vss

import com.monsters.mobimon.core.domain.VehicleDeliveryPolicy
import com.monsters.mobimon.core.domain.VehicleEvidenceFrame
import com.monsters.mobimon.core.domain.VehicleObservation
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleSubscriptionState
import com.monsters.mobimon.core.domain.VehicleValue

/** Call receive only for source events. Publishing a frame never records a receipt. */
class VehicleObservationRecorder(
    private val source: VehicleObservationSource,
    private val sessionId: String,
) {
    private var revision = 0L
    private var observations = emptyMap<String, VehicleObservation>()
    private var policies = emptyMap<String, VehicleDeliveryPolicy>()
    private var connected = true

    @Synchronized
    fun receive(
        values: Map<String, VehicleValue?>,
        now: Long,
        delivery: Map<String, VehicleDeliveryPolicy> = emptyMap(),
    ): VehicleEvidenceFrame {
        require(now >= 0)
        check(connected) { "A disconnected session cannot receive again" }
        revision++
        observations = observations +
            values.mapValues { (id, value) ->
                val prior = observations[id]
                VehicleObservation(
                    id,
                    value,
                    source,
                    sessionId,
                    revision,
                    now,
                    prior?.changedAtElapsedMillis.takeIf { prior?.value == value } ?: now,
                )
            }
        policies =
            policies + values.keys.associateWith { delivery[it] ?: policies[it] ?: VehicleDeliveryPolicy.Unknown }
        return publish(now)
    }

    @Synchronized
    fun publish(now: Long): VehicleEvidenceFrame =
        VehicleEvidenceFrame(
            revision,
            now,
            observations.toMap(),
            policies.toMap(),
            VehicleSubscriptionState(sessionId, observations.isNotEmpty(), connected, connected),
        )

    @Synchronized
    fun disconnect(now: Long): VehicleEvidenceFrame {
        connected = false
        revision++
        return publish(now)
    }
}
