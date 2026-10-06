package com.monsters.mobimon.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveEvidenceAccumulatorTest {
    private var nextId = 0
    private val accumulator = DriveEvidenceAccumulator(IdGenerator { "drive-${++nextId}" })
    private val evaluator = DrivingQuestEvaluator()
    private var elapsed = 1_000L
    private var utc = 1_800_000_000_000L

    private fun snapshot(
        state: DrivingState,
        values: Map<String, VehicleValue>,
        source: SignalSource = SignalSource.REAL,
        kind: VehicleObservationSource = VehicleObservationSource.VSS_ADAPTER,
        debugger: Boolean = false,
    ): VehicleSnapshot {
        elapsed += 2_000
        return VehicleSnapshot(
            id = "s$elapsed",
            epoch = "e",
            sequence = elapsed,
            receivedAtMillis = elapsed,
            source = source,
            drivingState = state,
            quality = SignalQuality.VALID,
            isDebuggerOverride = debugger,
            evidenceFrame =
                VehicleEvidenceFrame(
                    elapsed,
                    elapsed,
                    values.mapValues { (id, value) ->
                        VehicleObservation(id, value, kind, "session", 1, elapsed, elapsed)
                    },
                    emptyMap(),
                    VehicleSubscriptionState("session", true, true, true),
                ),
        )
    }

    private fun drivingValues(
        odometer: Double,
        belted: Boolean = true,
        acceleration: Double = 0.0,
        left: Boolean = false,
        lane: Boolean = false,
    ) = mapOf(
        "Vehicle.TraveledDistance" to VehicleValue.Number(odometer),
        "Vehicle.Speed" to VehicleValue.Number(60.0),
        "Vehicle.Acceleration.Longitudinal" to VehicleValue.Number(acceleration),
        "Vehicle.Cabin.Seat.Row1.DriverSide.IsBelted" to VehicleValue.Boolean(belted),
        "Vehicle.ADAS.LaneDepartureDetection.IsWarning" to VehicleValue.Boolean(lane),
        "Vehicle.Driver.DistractionLevel" to VehicleValue.Number(10.0),
        "Vehicle.Body.Lights.DirectionIndicator.Left.IsSignaling" to VehicleValue.Boolean(left),
    )

    private fun drive(
        start: DriveEvidenceState,
        frames: List<Map<String, VehicleValue>>,
    ): DriveEvidenceState {
        var state = start
        frames.forEach { state = requireNotNull(accumulator.accept(state, snapshot(DrivingState.MOVING, it), utc)) }
        return requireNotNull(
            accumulator.accept(
                state,
                snapshot(
                    DrivingState.PARKED,
                    mapOf(
                        "Vehicle.Speed" to VehicleValue.Number(0.0),
                    ),
                ),
                utc,
            ),
        )
    }

    @Test
    fun simulatedDebugOrFallbackObservationsNeverAdvanceEvidence() {
        val values = drivingValues(0.0)
        val state = DriveEvidenceState()
        assertNull(
            accumulator.accept(state, snapshot(DrivingState.MOVING, values, source = SignalSource.SIMULATED), utc),
        )
        assertNull(accumulator.accept(state, snapshot(DrivingState.MOVING, values, debugger = true), utc))
        assertNull(
            accumulator.accept(
                state,
                snapshot(DrivingState.MOVING, values, kind = VehicleObservationSource.FALLBACK),
                utc,
            ),
        )
        assertNull(accumulator.accept(state, snapshot(DrivingState.MOVING, values).copy(evidenceFrame = null), utc))
    }

    @Test
    fun verifiedCleanDriveSatisfiesPerDriveQuestsAndKeepsItsIdWhileParked() {
        // 3,000 belted moving frames at 2 s cover 100 minutes and 30 km.
        val frames = (0..3_000).map { drivingValues(odometer = it * 10.0) }
        val parked = drive(DriveEvidenceState(), frames)
        val data = accumulator.evaluation(parked, utc)

        assertEquals("drive-1", data.driveId)
        assertEquals(SignalSource.REAL, data.source)
        assertEquals(30f, data.distanceKm, 0.01f)
        assertTrue(data.safeBeltMinutes >= 99)
        assertTrue(evaluator.evaluateSeatbelt(data).isSatisfied)
        assertTrue(evaluator.evaluateCleanDriveBonus(data).isSatisfied)
        assertTrue(evaluator.evaluateLaneKeeping(data).isSatisfied)
        assertTrue(evaluator.evaluateFocusedDrive(data).isSatisfied)
        assertEquals(1, data.safeDriveCount)

        val next = drive(parked, listOf(drivingValues(odometer = 30_000.0), drivingValues(odometer = 31_000.0)))
        assertNotEquals("drive-1", accumulator.evaluation(next, utc).driveId)
        assertEquals(31f, accumulator.evaluation(next, utc).totalDistanceKm, 0.01f)
    }

    @Test
    fun unobservedNegativeSignalsCannotProveACleanDrive() {
        val frames =
            (0..3_000).map {
                mapOf(
                    "Vehicle.TraveledDistance" to VehicleValue.Number(it * 10.0),
                    "Vehicle.Cabin.Seat.Row1.DriverSide.IsBelted" to VehicleValue.Boolean(true),
                )
            }
        val data = accumulator.evaluation(drive(DriveEvidenceState(), frames), utc)
        assertTrue(evaluator.evaluateSeatbelt(data).isSatisfied)
        assertFalse(evaluator.evaluateCleanDriveBonus(data).isSatisfied)
        assertFalse(evaluator.evaluateLaneKeeping(data).isSatisfied)
        assertFalse(evaluator.evaluateFocusedDrive(data).isSatisfied)
        assertEquals(0, data.safeDriveCount)
    }

    @Test
    fun eventsCountRisingEdgesAndTurnSignalsResetDaily() {
        val frames =
            listOf(
                drivingValues(0.0, left = true, acceleration = 4.0),
                drivingValues(10.0, left = true, acceleration = 4.0),
                drivingValues(20.0),
                drivingValues(30.0, left = true, lane = true),
            )
        val state = drive(DriveEvidenceState(), frames)
        val data = accumulator.evaluation(state, utc)
        assertEquals(2, data.turnSignalOnCount)
        assertEquals(1, data.hardAccelCount)
        assertEquals(1, data.laneDepartureCount)
        assertEquals(2, evaluator.evaluateTurnSignalManner(data).dailyCount)

        utc += 86_400_000L
        assertEquals(0, accumulator.evaluation(state, utc).turnSignalOnCount)
    }
}
