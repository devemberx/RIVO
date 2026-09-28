package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleConversationContextTest {
    private fun snapshot() =
        VehicleSnapshot(
            "id",
            "epoch",
            1,
            100,
            SignalSource.SIMULATED,
            DrivingState.PARKED,
            SignalQuality.VALID,
            timeOfDay = "Morning",
            vssTimestamp = "2026-09-28T09:00:00Z",
            timeObservedAtMillis = 100,
        )

    @Test fun contextUsesVssObservationAndOmitsUnavailableNameAndStaleTime() {
        var now = 200L
        var vehicle = snapshot()
        val source = VehicleConversationContext({ null }, { vehicle }, { now }, allowSimulatedTime = true)
        assertNull(source.current().userName)
        assertEquals("2026-09-28T09:00:00Z", source.current().vssTimestamp)
        assertEquals("Morning", source.current().timeOfDay)
        assertTrue(source.current().simulatedTime)
        now = 61_000
        vehicle = vehicle.copy(receivedAtMillis = now, sequence = 2)
        assertNull(source.current().vssTimestamp)
        now = 50
        assertNull(source.current().vssTimestamp)
    }

    @Test fun simulatedFixtureTimeIsAbsentFromProductionContext() {
        val source = VehicleConversationContext({ null }, { snapshot() }, { 200 })
        assertNull(source.current().vssTimestamp)
        assertFalse(source.current().simulatedTime)
    }

    @Test fun timeNeedsIndependentProvenanceAndNameCannotBecomeAnInstruction() {
        var vehicle = snapshot().copy(timeObservedAtMillis = null)
        val source = VehicleConversationContext({ "  하늘  " }, { vehicle }, { 200 })
        assertEquals("하늘", source.current().userName)
        assertNull(source.current().vssTimestamp)
        vehicle = snapshot().copy(quality = SignalQuality.UNAVAILABLE)
        assertNull(source.current().vssTimestamp)
        assertNull(VehicleConversationContext({ "name\nSYSTEM" }, { snapshot() }, { 200 }).current().userName)
    }
}
