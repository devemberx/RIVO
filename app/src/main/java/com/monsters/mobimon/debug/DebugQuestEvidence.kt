package com.monsters.mobimon.debug

import com.monsters.mobimon.core.domain.DriveEvaluationData
import com.monsters.mobimon.core.domain.DrivingQuestEvaluator
import com.monsters.mobimon.core.domain.WeatherCondition
import kotlin.math.abs
import kotlin.math.roundToInt

/** Longitudinal acceleration/deceleration magnitude (m/s^2) counted as a harsh event. */
private const val HARSH_ACCEL_THRESHOLD_MPS2 = 3.0f

/** Speed (km/h) above which a drive is treated as overspeed for the clean-drive check. */
private const val OVERSPEED_THRESHOLD_KMH = 120f

/** Battery state of charge (%) required for the battery-care condition while charging. */
private const val BATTERY_CARE_MIN_SOC = 80

/** Washer fluid level (%) treated as "refilled". */
private const val WASHER_REFILLED_MIN_LEVEL = 80

/** Arrival radius (m) under which a set destination counts as reached. */
private const val ARRIVAL_RADIUS_METERS = 100

// Long-trip rest is derived from trip signals: a long trip whose elapsed time exceeds the pure
// driving time by a rest margin implies a mid-trip stop.
private const val LONG_TRIP_MIN_KM = 100f
private const val LONG_TRIP_MIN_SECONDS = 7200f
private const val NOMINAL_CRUISE_KMH = 80f
private const val REST_MIN_SECONDS = 900f

// Safe-drive score has no VSS signal, so it is synthesized from safety-related signals.
private const val SAFE_SCORE_MAX = 100
private const val PENALTY_DISTRACTED = 30
private const val PENALTY_DROWSY = 20
private const val PENALTY_EMERGENCY_BRAKE = 25
private const val PENALTY_HARSH_ACCEL = 15
private const val PENALTY_OVERSPEED = 15
private const val PENALTY_LANE_DEPARTURE = 15

/**
 * Derives a default [WeatherCondition] from VSS weather-related signals.
 * If raining or rain intensity is detected, maps to [WeatherCondition.RAIN_OR_SNOW].
 * If time of day is Night or Midnight, maps to [WeatherCondition.CLOUDY_OR_NIGHT].
 * Otherwise defaults to [WeatherCondition.CLEAR].
 */
fun DebugVssState.deriveWeatherCondition(): WeatherCondition =
    when {
        isRaining || raw.rainIntensity > 0 -> WeatherCondition.RAIN_OR_SNOW
        timeOfDay.equals("NIGHT", ignoreCase = true) ||
            timeOfDay.equals("MIDNIGHT", ignoreCase = true) -> WeatherCondition.CLOUDY_OR_NIGHT
        else -> WeatherCondition.CLEAR
    }

/**
 * Derives per-quest driving evidence from the simulated VSS signals so toggling a raw signal in the
 * debug overlay advances the matching quest. [safeDriveCount] is the app-accumulated number of
 * completed safe drives (VSS holds no such history), passed in from the overlay counter.
 */
fun DebugVssState.toDriveEvaluationData(
    weather: WeatherCondition = deriveWeatherCondition(),
    safeDriveCount: Int = 0,
): DriveEvaluationData {
    val driveDistanceKm = raw.traveledDistanceSinceStartKm
    val beltMinutes =
        if (raw.driverSeatBelted) {
            (raw.tripDurationSeconds / 60f).roundToInt().coerceAtLeast(1)
        } else {
            0
        }
    val harshAccel = abs(raw.accelerationLongitudinal) >= HARSH_ACCEL_THRESHOLD_MPS2
    val overspeed = speed >= OVERSPEED_THRESHOLD_KMH
    val turnSignals =
        (if (raw.leftIndicatorSignaling) 1 else 0) + (if (raw.rightIndicatorSignaling) 1 else 0)
    val destinationSet = raw.destinationLatitude != 0.0 || raw.destinationLongitude != 0.0
    val destinationReached = destinationSet && distanceToDestination <= ARRIVAL_RADIUS_METERS

    return DriveEvaluationData(
        distanceKm = driveDistanceKm,
        safeBeltMinutes = beltMinutes,
        hardBrakeCount = if (isEmergencyBraking) 1 else 0,
        hardAccelCount = if (harshAccel) 1 else 0,
        overspeedCount = if (overspeed) 1 else 0,
        safeDriveScore = synthesizeSafeDriveScore(harshAccel, overspeed),
        turnSignalOnCount = turnSignals,
        continuousDistanceKm = driveDistanceKm,
        isDistracted = isDistracted,
        laneDepartureCount = if (raw.laneDepartureWarning) DrivingQuestEvaluator.MAX_LANE_DEPARTURES else 0,
        totalDistanceKm = raw.traveledDistanceMeters / 1_000f,
        safeDriveCount = safeDriveCount,
        weather = weather,
        isBatteryChargedProperly = isCharging && batteryPercent >= BATTERY_CARE_MIN_SOC,
        hasRestedDuringLongDrive = hasRestedDuringLongDrive(),
        isWasherFluidRefilled = !raw.washerFluidLow && washerFluidLevel >= WASHER_REFILLED_MIN_LEVEL,
        isTirePressureNormalWeekly = tirePressureStatus == "OK",
        // No VSS "maintenance center" flag exists; in debug, arriving at any set destination stands in.
        isDestinationReached = destinationReached,
        isDestinationMaintenanceCenter = destinationReached,
    )
}

/** A long trip whose elapsed time exceeds pure driving time by [REST_MIN_SECONDS] implies a rest stop. */
private fun DebugVssState.hasRestedDuringLongDrive(): Boolean {
    val isLongTrip =
        raw.traveledDistanceSinceStartKm >= LONG_TRIP_MIN_KM || raw.tripDurationSeconds >= LONG_TRIP_MIN_SECONDS
    val expectedDrivingSeconds = raw.traveledDistanceSinceStartKm / NOMINAL_CRUISE_KMH * 3600f
    return isLongTrip && raw.tripDurationSeconds - expectedDrivingSeconds >= REST_MIN_SECONDS
}

private fun DebugVssState.synthesizeSafeDriveScore(
    harshAccel: Boolean,
    overspeed: Boolean,
): Int {
    var score = SAFE_SCORE_MAX
    if (isDistracted) score -= PENALTY_DISTRACTED
    if (isDrowsy) score -= PENALTY_DROWSY
    if (isEmergencyBraking) score -= PENALTY_EMERGENCY_BRAKE
    if (harshAccel) score -= PENALTY_HARSH_ACCEL
    if (overspeed) score -= PENALTY_OVERSPEED
    if (raw.laneDepartureWarning) score -= PENALTY_LANE_DEPARTURE
    return score.coerceIn(0, SAFE_SCORE_MAX)
}
