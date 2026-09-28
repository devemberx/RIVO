package com.monsters.mobimon.core.vss

import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.SignalUnavailableReason
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.vss.generated.VssSignals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

typealias VssRawVehicleState = VssSignals

data class VssInterpretationOverrides(
    val isDistracted: Boolean? = null,
    val isDrowsy: Boolean? = null,
    val attentionLevel: Int? = null,
    val isEmergencyBraking: Boolean? = null,
    val distanceToFrontVehicle: Int? = null,
    val isCharging: Boolean? = null,
    val batteryPercent: Int? = null,
    val outsideTemperature: Int? = null,
    val isRaining: Boolean? = null,
    val washerFluidLevel: Int? = null,
    val isEngineWarning: Boolean? = null,
    val isFuelLevelLow: Boolean? = null,
    val tirePressureStatus: String? = null,
    val isMoving: Boolean? = null,
    val speed: Int? = null,
    val gear: String? = null,
    val isNavigating: Boolean? = null,
    val distanceToDestination: Int? = null,
    val isEngineOn: Boolean? = null,
    val timeOfDay: String? = null,
)

interface VssRawVehicleSource {
    val state: StateFlow<VssRawVehicleState?>

    fun start() = Unit

    fun stop() = Unit
}

class DefaultParkedVssRawVehicleSource : VssRawVehicleSource {
    private val mutableState = MutableStateFlow<VssRawVehicleState?>(parkedVssSignals())
    override val state: StateFlow<VssRawVehicleState?> = mutableState.asStateFlow()
}

object VssVehicleInterpreter {
    fun snapshot(
        raw: VssRawVehicleState?,
        id: String,
        epoch: String,
        sequence: Long,
        observedAtMillis: Long,
        source: SignalSource,
        overrides: VssInterpretationOverrides = VssInterpretationOverrides(),
    ): VehicleSnapshot {
        if (raw == null) {
            return VehicleSnapshot(
                id = id,
                epoch = epoch,
                sequence = sequence,
                receivedAtMillis = observedAtMillis,
                source = source,
                drivingState = DrivingState.UNKNOWN,
                quality = SignalQuality.UNAVAILABLE,
                batteryQuality = SignalQuality.UNAVAILABLE,
                parkingUnavailableReason = SignalUnavailableReason.NOT_REPORTED,
                batteryUnavailableReason = SignalUnavailableReason.NOT_REPORTED,
            )
        }

        val speed = overrides.speed ?: raw.speed.roundToInt().coerceAtLeast(0)
        val gear = overrides.gear ?: raw.selectedGear.toGearLabel()
        val isMoving = overrides.isMoving ?: (raw.isMoving || raw.speed > 0f)
        val distanceToDestination =
            overrides.distanceToDestination
                ?: distanceMeters(
                    raw.currentLatitude,
                    raw.currentLongitude,
                    raw.destinationLatitude,
                    raw.destinationLongitude,
                ).roundToInt()

        return VehicleSnapshot(
            id = id,
            epoch = epoch,
            sequence = sequence,
            receivedAtMillis = observedAtMillis,
            source = source,
            drivingState = drivingState(isMoving, speed, gear),
            quality = SignalQuality.VALID,
            batteryPercent =
                overrides.batteryPercent
                    ?: raw.tractionBatterySocDisplayed.roundToInt().coerceIn(0, 100),
            batteryReceivedAtMillis = observedAtMillis,
            batteryQuality = SignalQuality.VALID,
            isDistracted = overrides.isDistracted ?: (raw.driverDistractionLevel >= DISTRACTION_THRESHOLD_PERCENT),
            isDrowsy = overrides.isDrowsy ?: (raw.driverFatigueLevel >= FATIGUE_THRESHOLD_PERCENT || raw.dmsIsWarning),
            attentionLevel =
                overrides.attentionLevel
                    ?: (100f - raw.driverDistractionLevel).roundToInt().coerceIn(0, 100),
            isEmergencyBraking = overrides.isEmergencyBraking ?: raw.driverEmergencyBrakingDetected,
            distanceToFrontVehicle =
                overrides.distanceToFrontVehicle ?: raw.obstacleFrontCenterDistance.roundToInt().coerceAtLeast(0),
            isCharging =
                overrides.isCharging
                    ?: (
                        raw.tractionBatteryChargingIsCharging ||
                            (raw.chargingCableConnected && raw.chargingAveragePowerKw > 0f)
                    ),
            outsideTemperature = overrides.outsideTemperature ?: raw.exteriorAirTemperature.roundToInt(),
            isRaining = overrides.isRaining ?: (raw.rainIntensity > 0),
            washerFluidLevel = overrides.washerFluidLevel ?: raw.washerFluidLevel.coerceIn(0, 100),
            isEngineWarning = overrides.isEngineWarning ?: (raw.diagnosticsDtcCount > 0),
            isFuelLevelLow = overrides.isFuelLevelLow ?: raw.fuelLevelLow,
            tirePressureStatus =
                overrides.tirePressureStatus
                    ?: if (
                        raw.row1LeftTirePressureLow ||
                        raw.row1RightTirePressureLow ||
                        raw.row2LeftTirePressureLow ||
                        raw.row2RightTirePressureLow
                    ) {
                        "NG"
                    } else {
                        "OK"
                    },
            speed = speed,
            gear = gear,
            isNavigating = overrides.isNavigating ?: (distanceToDestination > ARRIVAL_THRESHOLD_METERS),
            distanceToDestination = distanceToDestination,
            isEngineOn = overrides.isEngineOn ?: raw.combustionEngineRunning,
            timeOfDay = overrides.timeOfDay?.toTimeOfDay() ?: raw.currentLocationTimestamp.toTimeOfDay(),
        )
    }
}

private fun parkedVssSignals(): VssSignals {
    val washer =
        VssSignals.Body.Windshield.Front
            .WasherFluid(level = 100)
    return VssSignals(
        body =
            VssSignals.Body(
                windshield =
                    VssSignals.Body.Windshield(
                        front =
                            VssSignals.Body.Windshield.Front(
                                washerFluid = washer,
                            ),
                    ),
            ),
        currentLocation = VssSignals.CurrentLocation(timestamp = DEFAULT_CURRENT_LOCATION_TIMESTAMP),
        exterior = VssSignals.Exterior(airTemperature = 20f),
        powertrain =
            VssSignals.Powertrain(
                tractionBattery =
                    VssSignals.Powertrain.TractionBattery(
                        stateOfCharge = VssSignals.Powertrain.TractionBattery.StateOfCharge(displayed = 72f),
                    ),
                transmission = VssSignals.Powertrain.Transmission(selectedGear = 126),
            ),
    )
}

private val VssSignals.selectedGear: Int
    get() = powertrain.transmission.selectedGear

private val VssSignals.tractionBatterySocDisplayed: Float
    get() = powertrain.tractionBattery.stateOfCharge.displayed

private val VssSignals.chargingCableConnected: Boolean
    get() = powertrain.tractionBattery.charging.chargingPort.anyPosition.isChargingCableConnected

private val VssSignals.tractionBatteryChargingIsCharging: Boolean
    get() = powertrain.tractionBattery.charging.isCharging

private val VssSignals.chargingAveragePowerKw: Float
    get() = powertrain.tractionBattery.charging.averagePower

private val VssSignals.driverDistractionLevel: Float
    get() = driver.distractionLevel

private val VssSignals.driverFatigueLevel: Float
    get() = driver.fatigueLevel

private val VssSignals.dmsIsWarning: Boolean
    get() = adas.dms.isWarning

private val VssSignals.driverEmergencyBrakingDetected: Boolean
    get() = chassis.brake.isDriverEmergencyBrakingDetected

private val VssSignals.obstacleFrontCenterDistance: Float
    get() = adas.obstacleDetection.front.center.distance

private val VssSignals.exteriorAirTemperature: Float
    get() = exterior.airTemperature

private val VssSignals.rainIntensity: Int
    get() = body.raindetection.intensity

private val VssSignals.washerFluidLevel: Int
    get() = body.windshield.front.washerFluid.level

private val VssSignals.row1LeftTirePressureLow: Boolean
    get() = chassis.axle.row1.wheel.left.tire.isPressureLow

private val VssSignals.row1RightTirePressureLow: Boolean
    get() = chassis.axle.row1.wheel.right.tire.isPressureLow

private val VssSignals.row2LeftTirePressureLow: Boolean
    get() = chassis.axle.row2.wheel.left.tire.isPressureLow

private val VssSignals.row2RightTirePressureLow: Boolean
    get() = chassis.axle.row2.wheel.right.tire.isPressureLow

private val VssSignals.diagnosticsDtcCount: Int
    get() = diagnostics.dTCCount

private val VssSignals.destinationLatitude: Double
    get() =
        cabin.infotainment.navigation.destinationSet.latitude
            .toDouble()

private val VssSignals.destinationLongitude: Double
    get() =
        cabin.infotainment.navigation.destinationSet.longitude
            .toDouble()

private val VssSignals.currentLatitude: Double
    get() = currentLocation.latitude.toDouble()

private val VssSignals.currentLongitude: Double
    get() = currentLocation.longitude.toDouble()

private val VssSignals.currentLocationTimestamp: String
    get() = currentLocation.timestamp

private val VssSignals.combustionEngineRunning: Boolean
    get() = powertrain.combustionEngine.isRunning

private val VssSignals.fuelLevelLow: Boolean
    get() = powertrain.fuelSystem.isFuelLevelLow

private const val DISTRACTION_THRESHOLD_PERCENT = 70f
private const val FATIGUE_THRESHOLD_PERCENT = 70f
private const val ARRIVAL_THRESHOLD_METERS = 100
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DEFAULT_CURRENT_LOCATION_TIMESTAMP = "2026-10-08T10:00:00Z"

private fun drivingState(
    isMoving: Boolean,
    speed: Int,
    gear: String,
): DrivingState =
    when {
        isMoving || speed > 0 -> DrivingState.MOVING
        gear == "P" -> DrivingState.PARKED
        else -> DrivingState.UNKNOWN
    }

private fun Int.toGearLabel(): String =
    when {
        this == 126 -> "P"
        this == 127 -> "D"
        this == 0 -> "N"
        this < 0 -> "R"
        else -> "D"
    }

private fun distanceMeters(
    fromLat: Double,
    fromLon: Double,
    toLat: Double,
    toLon: Double,
): Double {
    if (fromLat == 0.0 && fromLon == 0.0 && toLat == 0.0 && toLon == 0.0) return 0.0
    val fromLatRad = Math.toRadians(fromLat)
    val toLatRad = Math.toRadians(toLat)
    val latDelta = Math.toRadians(toLat - fromLat)
    val lonDelta = Math.toRadians(toLon - fromLon)
    val a =
        sin(latDelta / 2) * sin(latDelta / 2) +
            cos(fromLatRad) * cos(toLatRad) * sin(lonDelta / 2) * sin(lonDelta / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}

fun interpretVssTimeOfDay(input: String): String {
    val trimmed = input.trim()
    when (trimmed.lowercase()) {
        "sunrise", "일출", "새벽" -> return "Sunrise"
        "morning", "아침" -> return "Morning"
        "day", "낮" -> return "Day"
        "afternoon", "오후", "늦은 오후" -> return "Afternoon"
        "sunset", "노을", "저녁" -> return "Sunset"
        "night", "밤" -> return "Night"
        "midnight", "한밤", "한밤중", "자정" -> return "Midnight"
    }

    val hour = extractHour(trimmed) ?: return "Day"
    return when (hour) {
        in 0..4 -> "Midnight"
        in 5..6 -> "Sunrise"
        in 7..11 -> "Morning"
        in 12..15 -> "Day"
        in 16..17 -> "Afternoon"
        in 18..19 -> "Sunset"
        else -> "Night"
    }
}

internal fun String.toTimeOfDay(): String = interpretVssTimeOfDay(this)

private fun extractHour(input: String): Int? {
    val koreanHourMatch = Regex("""^(\d{1,2})\s*시""").find(input)
    if (koreanHourMatch != null) {
        val h = koreanHourMatch.groupValues[1].toIntOrNull()
        if (h != null && h in 0..24) return if (h == 24) 0 else h
    }

    input.toIntOrNull()?.let {
        if (it in 0..24) {
            return if (it == 24) 0 else it
        }
    }

    val timeMatch = Regex("""^(\d{1,2}):\d{2}(?::\d{2})?""").find(input)
    if (timeMatch != null) {
        val h = timeMatch.groupValues[1].toIntOrNull()
        if (h != null && h in 0..23) return h
    }

    try {
        return OffsetDateTime.parse(input).hour
    } catch (_: DateTimeParseException) {
    }

    try {
        return LocalDateTime.parse(input).hour
    } catch (_: DateTimeParseException) {
    }

    val generalTimeMatch = Regex("""[T ](\d{1,2}):\d{2}""").find(input)
    if (generalTimeMatch != null) {
        val h = generalTimeMatch.groupValues[1].toIntOrNull()
        if (h != null && h in 0..23) return h
    }

    return null
}
