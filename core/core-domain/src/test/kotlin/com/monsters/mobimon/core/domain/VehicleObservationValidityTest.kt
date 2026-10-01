package com.monsters.mobimon.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VehicleObservationValidityTest {
    private val subscription = VehicleSubscriptionState("session", true, true, true)
    private val reading =
        VehicleObservation(
            "battery",
            VehicleValue.Number(0.0),
            VehicleObservationSource.VSS_ADAPTER,
            "session",
            1,
            100,
            100,
        )

    @Test fun periodicUsesReceiptAgeAndPreservesZero() {
        assertEquals(SignalQuality.VALID, quality(reading, VehicleDeliveryPolicy.Periodic(15_000), 15_100))
        assertEquals(SignalQuality.STALE, quality(reading, VehicleDeliveryPolicy.Periodic(15_000), 15_101))
        assertEquals(
            SignalQuality.VALID,
            quality(reading.copy(value = VehicleValue.Boolean(false)), VehicleDeliveryPolicy.Periodic(15_000), 100),
        )
    }

    @Test fun changeOnlySurvivesSilenceButRequiresSubscription() {
        assertEquals(SignalQuality.VALID, quality(reading, VehicleDeliveryPolicy.OnChange, 500_000))
        for (state in listOf(
            subscription.copy(connected = false),
            subscription.copy(initialSyncComplete = false),
            subscription.copy(subscriptionValid = false),
            subscription.copy(sessionId = "new"),
            subscription.copy(leaseExpiresAtElapsedMillis = 400_000),
        )) {
            assertNotEquals(
                SignalQuality.VALID,
                VehicleObservationValidity.evaluate(reading, VehicleDeliveryPolicy.OnChange, state, 500_000).quality,
            )
        }
    }

    @Test fun unknownMissingAndFutureReceiptsCannotClaimCurrentValidity() {
        assertEquals(
            "UNVERIFIED_VALIDITY",
            VehicleObservationValidity.evaluate(reading, VehicleDeliveryPolicy.Unknown, subscription, 100).reason,
        )
        assertNotEquals(
            SignalQuality.VALID,
            quality(reading.copy(receivedAtElapsedMillis = null), VehicleDeliveryPolicy.OnChange, 100),
        )
        assertNotEquals(
            SignalQuality.VALID,
            quality(reading.copy(receivedAtElapsedMillis = 101), VehicleDeliveryPolicy.OnChange, 100),
        )
        assertNotEquals(SignalQuality.VALID, quality(reading.copy(value = null), VehicleDeliveryPolicy.OnChange, 100))
    }

    @Test fun derivedFactNeedsItsDeclaredInputsAndCyclesFailClosed() {
        val derived =
            reading.copy(
                fieldId = "condition",
                derivation = VehicleDerivation.DERIVED,
                dependencyIds = listOf("battery"),
            )
        val frame =
            VehicleEvidenceFrame(
                1,
                100,
                mapOf("battery" to reading, "condition" to derived),
                mapOf(
                    "battery" to VehicleDeliveryPolicy.OnChange,
                    "condition" to VehicleDeliveryPolicy.OnChange,
                ),
                subscription,
            )
        assertEquals(SignalQuality.VALID, VehicleObservationValidity.evaluate(frame, "condition", 100).quality)
        assertNotEquals(
            SignalQuality.VALID,
            VehicleObservationValidity
                .evaluate(
                    frame.copy(
                        observations =
                            mapOf(
                                "condition" to derived,
                            ),
                    ),
                    "condition",
                    100,
                ).quality,
        )
        assertNotEquals(
            SignalQuality.VALID,
            VehicleObservationValidity
                .evaluate(
                    frame.copy(
                        observations =
                            mapOf(
                                "condition" to derived.copy(dependencyIds = listOf("condition")),
                            ),
                    ),
                    "condition",
                    100,
                ).quality,
        )
    }

    private fun quality(
        observation: VehicleObservation,
        policy: VehicleDeliveryPolicy,
        now: Long,
    ) = VehicleObservationValidity.evaluate(observation, policy, subscription, now).quality
}
