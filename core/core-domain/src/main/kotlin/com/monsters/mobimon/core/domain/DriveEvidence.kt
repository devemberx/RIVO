package com.monsters.mobimon.core.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields

/** Persisted drive evidence. Only verified adapter observations advance it; it survives restarts. */
data class DriveEvidenceState(
    val driveId: String = "",
    val driving: Boolean = false,
    val startOdometerMeters: Double? = null,
    val lastOdometerMeters: Double? = null,
    val completedDistanceKm: Float = 0f,
    val beltedMillis: Long = 0,
    val lastElapsedMillis: Long? = null,
    val hardAccelCount: Int = 0,
    val hardBrakeCount: Int = 0,
    val overspeedCount: Int = 0,
    val laneDepartureCount: Int = 0,
    val distracted: Boolean = false,
    // Negative-event signals observed during this drive; unobserved ones cannot prove a clean drive.
    val observedSignals: Set<String> = emptySet(),
    // Conditions currently on, so only rising edges are counted.
    val activeEdges: Set<String> = emptySet(),
    val turnSignalDate: String = "",
    val turnSignalCount: Int = 0,
    val safeDriveWeek: String = "",
    val safeDriveCount: Int = 0,
    val tireNormalSinceUtcMillis: Long? = null,
    val raining: Boolean = false,
    val batteryChargedProperly: Boolean = false,
    val washerRefilled: Boolean = false,
)

fun interface DriveEvidenceStore {
    suspend fun read(): DriveEvidenceState?

    suspend fun write(state: DriveEvidenceState) {}
}

/**
 * Derives driving quest evidence from verified VSS adapter observations on REAL snapshots.
 * Simulated, Debug-overridden or fallback observations never advance evidence.
 */
class DriveEvidenceAccumulator(
    private val ids: IdGenerator,
    private val resetZone: ZoneId = ZoneId.of("Asia/Seoul"),
) {
    /** Returns the advanced state, or null when the snapshot carries no verified adapter evidence. */
    fun accept(
        state: DriveEvidenceState,
        snapshot: VehicleSnapshot,
        utcMillis: Long,
    ): DriveEvidenceState? {
        val frame = snapshot.evidenceFrame
        if (snapshot.source != SignalSource.REAL ||
            snapshot.isDebuggerOverride ||
            frame == null ||
            !frame.subscription.connected ||
            !frame.subscription.subscriptionValid
        ) {
            return null
        }
        val values =
            frame.observations.values
                .filter {
                    it.sourceKind == VehicleObservationSource.VSS_ADAPTER &&
                        it.sourceQuality == SignalQuality.VALID &&
                        it.sessionId == frame.subscription.sessionId
                }.mapNotNull { observation -> observation.value?.let { observation.fieldId to it } }
                .toMap()
        if (values.isEmpty()) return null

        fun number(path: String) = (values[path] as? VehicleValue.Number)?.value

        fun flag(path: String) = (values[path] as? VehicleValue.Boolean)?.value
        val odometer = number(ODOMETER)
        var next =
            when {
                snapshot.drivingState == DrivingState.MOVING && !state.driving ->
                    state.copy(
                        driveId = ids.nextId(),
                        driving = true,
                        startOdometerMeters = odometer,
                        lastOdometerMeters = odometer,
                        beltedMillis = 0,
                        lastElapsedMillis = null,
                        hardAccelCount = 0,
                        hardBrakeCount = 0,
                        overspeedCount = 0,
                        laneDepartureCount = 0,
                        distracted = false,
                        observedSignals = emptySet(),
                        activeEdges = emptySet(),
                    )
                snapshot.drivingState == DrivingState.PARKED && state.driving -> finishDrive(state, utcMillis)
                else -> state
            }
        val today = date(utcMillis)
        if (next.turnSignalDate != today) next = next.copy(turnSignalDate = today, turnSignalCount = 0)
        if (next.driving) {
            val elapsed = snapshot.receivedAtMillis
            val delta = next.lastElapsedMillis?.let { elapsed - it }?.takeIf { it in 1..MAX_BELT_STEP_MILLIS } ?: 0
            val edges = mutableSetOf<String>()
            val acceleration = number(ACCELERATION)
            val speed = number(SPEED)
            if (acceleration != null && acceleration >= HARSH_MPS2) edges += EDGE_ACCEL
            if (acceleration != null && acceleration <= -HARSH_MPS2) edges += EDGE_BRAKE
            if (speed != null && speed >= OVERSPEED_KMH) edges += EDGE_OVERSPEED
            if (flag(LANE_DEPARTURE) == true) edges += LANE_DEPARTURE
            if (flag(LEFT_SIGNAL) == true) edges += LEFT_SIGNAL
            if (flag(RIGHT_SIGNAL) == true) edges += RIGHT_SIGNAL
            val rising = edges - next.activeEdges
            next =
                next.copy(
                    startOdometerMeters = next.startOdometerMeters ?: odometer,
                    lastOdometerMeters = odometer ?: next.lastOdometerMeters,
                    beltedMillis = next.beltedMillis + if (flag(DRIVER_BELT) == true) delta else 0,
                    lastElapsedMillis = elapsed,
                    hardAccelCount = next.hardAccelCount + if (EDGE_ACCEL in rising) 1 else 0,
                    hardBrakeCount = next.hardBrakeCount + if (EDGE_BRAKE in rising) 1 else 0,
                    overspeedCount = next.overspeedCount + if (EDGE_OVERSPEED in rising) 1 else 0,
                    laneDepartureCount = next.laneDepartureCount + if (LANE_DEPARTURE in rising) 1 else 0,
                    turnSignalCount = next.turnSignalCount + rising.count { it == LEFT_SIGNAL || it == RIGHT_SIGNAL },
                    distracted = next.distracted || (number(DISTRACTION)?.let { it >= DISTRACTION_PERCENT } == true),
                    observedSignals = next.observedSignals + NEGATIVE_SIGNALS.filter { it in values },
                    activeEdges = edges,
                )
        }
        val tires = TIRES.map { flag(it) }
        val tireSince =
            when {
                tires.any { it == true } -> null
                tires.all { it == false } -> next.tireNormalSinceUtcMillis ?: utcMillis
                else -> next.tireNormalSinceUtcMillis
            }
        return next.copy(
            tireNormalSinceUtcMillis = tireSince,
            raining = number(RAIN)?.let { it > 0 } ?: next.raining,
            batteryChargedProperly =
                flag(CHARGING) == true && (number(SOC)?.let { it >= BATTERY_CARE_SOC } == true),
            washerRefilled = number(WASHER)?.let { it >= WASHER_REFILLED_LEVEL } ?: next.washerRefilled,
        )
    }

    fun evaluation(
        state: DriveEvidenceState,
        utcMillis: Long,
    ): DriveEvaluationData {
        val driveKm = driveDistanceKm(state)
        // An unobserved negative-event signal cannot prove its absence, so it counts as not met.
        val accelObserved = ACCELERATION in state.observedSignals
        val speedObserved = SPEED in state.observedSignals
        val laneObserved = LANE_DEPARTURE in state.observedSignals
        val distracted = state.distracted || DISTRACTION !in state.observedSignals
        val hardAccel = if (accelObserved) state.hardAccelCount else 1
        val hardBrake = if (accelObserved) state.hardBrakeCount else 1
        val overspeed = if (speedObserved) state.overspeedCount else 1
        val lanes = if (laneObserved) state.laneDepartureCount else DrivingQuestEvaluator.MAX_LANE_DEPARTURES
        return DriveEvaluationData(
            date = date(utcMillis),
            driveId = state.driveId,
            source = SignalSource.REAL,
            distanceKm = driveKm,
            safeBeltMinutes = (state.beltedMillis / 60_000).toInt(),
            hardBrakeCount = hardBrake,
            hardAccelCount = hardAccel,
            overspeedCount = overspeed,
            safeDriveScore = safeScore(distracted, hardAccel, hardBrake, overspeed, lanes),
            turnSignalOnCount = if (state.turnSignalDate == date(utcMillis)) state.turnSignalCount else 0,
            continuousDistanceKm = driveKm,
            isDistracted = distracted,
            laneDepartureCount = lanes,
            totalDistanceKm = state.completedDistanceKm + if (state.driving) driveKm else 0f,
            safeDriveCount = if (state.safeDriveWeek == week(utcMillis)) state.safeDriveCount else 0,
            weather = if (state.raining) WeatherCondition.RAIN_OR_SNOW else WeatherCondition.CLEAR,
            isBatteryChargedProperly = state.batteryChargedProperly,
            isWasherFluidRefilled = state.washerRefilled,
            isTirePressureNormalWeekly =
                state.tireNormalSinceUtcMillis?.let { utcMillis - it >= TIRE_NORMAL_MILLIS } == true,
        )
    }

    private fun finishDrive(
        state: DriveEvidenceState,
        utcMillis: Long,
    ): DriveEvidenceState {
        val ended = evaluation(state.copy(driving = false), utcMillis)
        val safe = DrivingQuestEvaluator().evaluateSafeDriveCompletion(ended).isSatisfied
        val week = week(utcMillis)
        val count = if (state.safeDriveWeek == week) state.safeDriveCount else 0
        return state.copy(
            driving = false,
            completedDistanceKm = state.completedDistanceKm + driveDistanceKm(state),
            activeEdges = emptySet(),
            lastElapsedMillis = null,
            safeDriveWeek = week,
            safeDriveCount = count + if (safe) 1 else 0,
        )
    }

    private fun driveDistanceKm(state: DriveEvidenceState): Float {
        val start = state.startOdometerMeters ?: return 0f
        val last = state.lastOdometerMeters ?: return 0f
        return ((last - start) / 1_000).coerceAtLeast(0.0).toFloat()
    }

    private fun safeScore(
        distracted: Boolean,
        hardAccel: Int,
        hardBrake: Int,
        overspeed: Int,
        lanes: Int,
    ): Int {
        var score = 100
        if (distracted) score -= 30
        if (hardBrake > 0) score -= 25
        if (hardAccel > 0) score -= 15
        if (overspeed > 0) score -= 15
        if (lanes > 0) score -= 15
        return score.coerceIn(0, 100)
    }

    private fun date(utcMillis: Long) =
        Instant
            .ofEpochMilli(utcMillis)
            .atZone(resetZone)
            .toLocalDate()
            .toString()

    private fun week(utcMillis: Long): String {
        val zoned = Instant.ofEpochMilli(utcMillis).atZone(resetZone)
        return "${zoned.get(IsoFields.WEEK_BASED_YEAR)}-W${zoned.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
    }

    private companion object {
        const val ODOMETER = "Vehicle.TraveledDistance"
        const val SPEED = "Vehicle.Speed"
        const val ACCELERATION = "Vehicle.Acceleration.Longitudinal"
        const val DRIVER_BELT = "Vehicle.Cabin.Seat.Row1.DriverSide.IsBelted"
        const val LANE_DEPARTURE = "Vehicle.ADAS.LaneDepartureDetection.IsWarning"
        const val DISTRACTION = "Vehicle.Driver.DistractionLevel"
        const val LEFT_SIGNAL = "Vehicle.Body.Lights.DirectionIndicator.Left.IsSignaling"
        const val RIGHT_SIGNAL = "Vehicle.Body.Lights.DirectionIndicator.Right.IsSignaling"
        const val RAIN = "Vehicle.Body.Raindetection.Intensity"
        const val CHARGING = "Vehicle.Powertrain.TractionBattery.Charging.IsCharging"
        const val SOC = "Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed"
        const val WASHER = "Vehicle.Body.Windshield.Front.WasherFluid.Level"
        val TIRES =
            listOf("Row1.Wheel.Left", "Row1.Wheel.Right", "Row2.Wheel.Left", "Row2.Wheel.Right")
                .map { "Vehicle.Chassis.Axle.$it.Tire.IsPressureLow" }
        val NEGATIVE_SIGNALS = listOf(ACCELERATION, SPEED, LANE_DEPARTURE, DISTRACTION)
        const val EDGE_ACCEL = "edge:accel"
        const val EDGE_BRAKE = "edge:brake"
        const val EDGE_OVERSPEED = "edge:overspeed"
        const val HARSH_MPS2 = 3.0
        const val OVERSPEED_KMH = 120.0
        const val DISTRACTION_PERCENT = 70.0
        const val BATTERY_CARE_SOC = 80.0
        const val WASHER_REFILLED_LEVEL = 80.0
        const val MAX_BELT_STEP_MILLIS = 10_000L
        const val TIRE_NORMAL_MILLIS = 7 * 24 * 60 * 60 * 1_000L
    }
}
