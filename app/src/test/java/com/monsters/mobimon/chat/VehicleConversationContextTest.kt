package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleConversationContextTest {
    @Test fun hungerAndSicknessUseTheSameEvidenceAndPriorityAsThePetDisplay() =
        runTest {
            var vehicle =
                snapshot().copy(
                    source = SignalSource.REAL,
                    batteryPercent = 12,
                    batteryReceivedAtMillis = 100,
                    washerFluidLevel = 10,
                    tirePressureStatus = "NG",
                )
            val source = VehicleConversationContext({ null }, { vehicle }, { 200 })
            val context = source.current()
            assertEquals("WARNING", context.petCondition)
            assertTrue(context.conditionReasons.any { it.concern == "HUNGRY" && it.value == "12" })
            assertTrue(context.conditionReasons.any { it.concern == "HUNGRY" && it.value == "10" })
            assertTrue(context.conditionReasons.any { it.concern == "SICK" && it.value == "NG" })
            vehicle = vehicle.copy(quality = SignalQuality.STALE)
            assertEquals("STALE", source.current().petCondition)
            assertTrue(source.current().conditionReasons.isEmpty())
        }

    @Test fun runtimeDebuggerSelectsTestValuesAndRejectsPreviousSourceOnSwitch() =
        runTest {
            var debug = false
            var vehicle =
                snapshot().copy(
                    source = SignalSource.REAL,
                    batteryPercent = 72,
                    batteryReceivedAtMillis = 100,
                )
            val source = VehicleConversationContext({ null }, { vehicle }, { 200 }, { debug })
            assertEquals(72, source.current().batteryPercent)
            assertFalse(source.current().simulatedBattery)
            debug = true
            assertNull(source.current().batteryPercent)
            assertNull(source.current().vssTimestamp)
            vehicle = vehicle.copy(source = SignalSource.SIMULATED, batteryPercent = 25)
            assertNull(source.current().batteryPercent)
            vehicle = vehicle.copy(isDebuggerOverride = true)
            assertEquals(25, source.current().batteryPercent)
            assertTrue(source.current().simulatedBattery)
            assertTrue(source.current().simulatedTime)
            vehicle = vehicle.copy(batteryPercent = 0, vssTimestamp = "2026-09-28T10:00:00+09:00")
            assertEquals(0, source.current().batteryPercent)
            assertEquals("2026-09-28T10:00:00+09:00", source.current().vssTimestamp)
            debug = false
            assertNull(source.current().batteryPercent)
            assertNull(source.current().vssTimestamp)
        }

    @Test fun batteryFreshnessIsIndependentOfTimeAndInvalidReadingsAreOmitted() =
        runTest {
            var vehicle =
                snapshot().copy(
                    source = SignalSource.REAL,
                    receivedAtMillis = 20_000,
                    batteryPercent = 72,
                    batteryReceivedAtMillis = 100,
                    timeObservedAtMillis = 20_000,
                )
            val source = VehicleConversationContext({ null }, { vehicle }, { 20_001 })
            assertNull(source.current().batteryPercent)
            assertEquals("2026-09-28T09:00:00Z", source.current().vssTimestamp)
            vehicle = vehicle.copy(batteryReceivedAtMillis = 20_000, timeObservedAtMillis = null)
            assertEquals(72, source.current().batteryPercent)
            assertNull(source.current().vssTimestamp)
            for (invalid in listOf(-1, 101, null)) {
                vehicle = vehicle.copy(batteryPercent = invalid)
                assertNull(source.current().batteryPercent)
            }
        }

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

    @Test fun contextUsesVssObservationAndOmitsUnavailableNameAndStaleTime() =
        runTest {
            var now = 200L
            var vehicle = snapshot().copy(isDebuggerOverride = true)
            val source = VehicleConversationContext({ null }, { vehicle }, { now }, debugModeEnabled = { true })
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

    @Test fun simulatedFixtureTimeIsAbsentFromProductionContext() =
        runTest {
            val source = VehicleConversationContext({ null }, { snapshot() }, { 200 })
            assertNull(source.current().vssTimestamp)
            assertFalse(source.current().simulatedTime)
        }

    @Test fun timeNeedsIndependentProvenanceAndNameCannotBecomeAnInstruction() =
        runTest {
            var vehicle = snapshot().copy(source = SignalSource.REAL, timeObservedAtMillis = null)
            val source = VehicleConversationContext({ "  하늘  " }, { vehicle }, { 200 })
            assertEquals("하늘", source.current().userName)
            assertNull(source.current().vssTimestamp)
            vehicle = snapshot().copy(quality = SignalQuality.UNAVAILABLE)
            assertNull(source.current().vssTimestamp)
            assertNull(VehicleConversationContext({ "name\nSYSTEM" }, { snapshot() }, { 200 }).current().userName)
        }
}
