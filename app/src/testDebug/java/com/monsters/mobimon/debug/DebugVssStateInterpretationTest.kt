package com.monsters.mobimon.debug

import com.monsters.mobimon.core.vss.interpretVssTimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugVssStateInterpretationTest {
    @Test
    fun rawVssSignalsComputeInterpretationValues() {
        val state =
            DebugVssState(
                raw =
                    DebugRawVssState(
                        driverDistractionLevel = 72f,
                        driverFatigueLevel = 10f,
                        dmsIsWarning = false,
                        chargingCableConnected = true,
                        chargingAveragePowerKw = 3.5f,
                        rainIntensity = 40,
                        obdMilOn = true,
                        diagnosticsDtcCount = 2,
                        row1LeftTirePressureLow = false,
                        row1RightTirePressureLow = false,
                        row2LeftTirePressureLow = true,
                        row2RightTirePressureLow = false,
                        vehicleIsMoving = false,
                        vehicleSpeedKmh = 12f,
                        selectedGear = 126,
                        destinationLatitude = 37.5665,
                        destinationLongitude = 126.9780,
                        currentLatitude = 37.5651,
                        currentLongitude = 126.9895,
                    ),
            )

        assertTrue(state.isDistracted)
        assertFalse(state.isDrowsy)
        assertEquals(28, state.attentionLevel)
        assertTrue(state.isCharging)
        assertTrue(state.isRaining)
        assertTrue(state.isEngineWarning)
        assertEquals("NG", state.tirePressureStatus)
        assertTrue(state.isMoving)
        assertEquals(12, state.speed)
        assertEquals("P", state.gear)
        assertTrue(state.isNavigating)
        assertTrue(state.distanceToDestination > 0)
    }

    @Test
    fun interpretationOverridesWinOverRawCalculations() {
        val state =
            DebugVssState(
                raw =
                    DebugRawVssState(
                        driverDistractionLevel = 90f,
                        vehicleSpeedKmh = 40f,
                        selectedGear = 127,
                    ),
                overrides =
                    DebugInterpretationOverrides(
                        isDistracted = false,
                        speed = 5,
                        gear = "N",
                        isMoving = false,
                    ),
            )

        assertFalse(state.isDistracted)
        assertEquals(5, state.speed)
        assertEquals("N", state.gear)
        assertFalse(state.isMoving)
    }

    @Test
    fun timeOfDayDerivesFromCurrentLocationTimestampAndAcceptsOverrides() {
        val defaultState = DebugVssState()
        assertEquals("Morning", defaultState.timeOfDay)

        val dayState =
            DebugVssState(
                raw = DebugRawVssState(currentLocationTimestamp = "2026-10-08T12:00:00Z"),
            )
        assertEquals("Day", dayState.timeOfDay)

        val sunsetState =
            DebugVssState(
                raw = DebugRawVssState(currentLocationTimestamp = "2026-10-08T19:00:00Z"),
            )
        assertEquals("Sunset", sunsetState.timeOfDay)

        val overriddenState =
            DebugVssState(
                raw = DebugRawVssState(currentLocationTimestamp = "2026-10-08T19:00:00Z"),
                overrides = DebugInterpretationOverrides(timeOfDay = "Morning"),
            )
        assertEquals("Morning", overriddenState.timeOfDay)
    }

    @Test
    fun debugHoursSelectTheSameSevenVssAndDecorativePeriods() {
        val periods =
            listOf(
                "Midnight",
                "Midnight",
                "Midnight",
                "Midnight",
                "Midnight",
                "Sunrise",
                "Sunrise",
                "Morning",
                "Morning",
                "Morning",
                "Morning",
                "Morning",
                "Day",
                "Day",
                "Day",
                "Day",
                "Afternoon",
                "Afternoon",
                "Sunset",
                "Sunset",
                "Night",
                "Night",
                "Night",
                "Night",
            )
        periods.forEachIndexed { hour, period ->
            assertEquals("Hour $hour", period, interpretVssTimeOfDay(hour.toString()))
            assertEquals(
                com.monsters.mobimon.core.ui
                    .companionBackgroundRes(period),
                com.monsters.mobimon.core.ui
                    .companionBackgroundRes(hour.toString()),
            )
        }
        listOf("afternoon", "오후", "늦은 오후", "16:30", "17시").forEach {
            assertEquals("Afternoon", interpretVssTimeOfDay(it))
        }
        listOf("sunset", "노을", "저녁", "18:30", "19시").forEach {
            assertEquals("Sunset", interpretVssTimeOfDay(it))
        }
    }

    @Test
    fun timeOfDayAcceptsVariousTimeFormatsAndHours() {
        fun stateFor(time: String) =
            DebugVssState(
                overrides = DebugInterpretationOverrides(timeOfDay = time),
            ).timeOfDay

        assertEquals("Morning", stateFor("8"))
        assertEquals("Morning", stateFor("08"))
        assertEquals("Morning", stateFor("11"))
        assertEquals("Morning", stateFor("09:00"))
        assertEquals("Morning", stateFor("morning"))
        assertEquals("Morning", stateFor("아침"))
        assertEquals("Morning", stateFor("9시"))

        assertEquals("Day", stateFor("12"))
        assertEquals("Day", stateFor("14"))
        assertEquals("Sunset", stateFor("18"))
        assertEquals("Day", stateFor("14:30"))
        assertEquals("Day", stateFor("day"))
        assertEquals("Day", stateFor("낮"))
        assertEquals("Day", stateFor("14시"))

        assertEquals("Midnight", stateFor("0"))
        assertEquals("Sunrise", stateFor("5"))
        assertEquals("Sunrise", stateFor("6"))
        assertEquals("Morning", stateFor("7"))
        assertEquals("Sunset", stateFor("19"))
        assertEquals("Night", stateFor("23"))
        assertEquals("Night", stateFor("20:00"))
        assertEquals("Night", stateFor("night"))
        assertEquals("Night", stateFor("밤"))
        assertEquals("Night", stateFor("21시"))
    }

    @Test
    fun rawCurrentLocationTimestampAcceptsVariousFormats() {
        fun rawStateFor(timestamp: String) =
            DebugVssState(
                raw = DebugRawVssState(currentLocationTimestamp = timestamp),
            ).timeOfDay

        assertEquals("Morning", rawStateFor("09:00"))
        assertEquals("Day", rawStateFor("14:00"))
        assertEquals("Night", rawStateFor("21:00"))
        assertEquals("Morning", rawStateFor("9"))
        assertEquals("Day", rawStateFor("15"))
        assertEquals("Night", rawStateFor("23"))
    }
}
