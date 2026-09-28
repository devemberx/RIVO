package com.monsters.mobimon.debug

import android.content.Context
import android.content.SharedPreferences
import com.monsters.mobimon.core.vss.interpretVssTimeOfDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class DebugRawVssState(
    val driverFatigueLevel: Float = 0f,
    val driverDistractionLevel: Float = 0f,
    val dmsIsWarning: Boolean = false,
    val laneDepartureWarning: Boolean = false,
    val obstacleDetectionWarning: Boolean = false,
    val strongCrossWindDetected: Boolean = false,
    val roadFrictionMostProbable: Float = 100f,
    val accelerationLongitudinal: Float = 0f,
    val driverEmergencyBrakingDetected: Boolean = false,
    val obstacleFrontCenterDistance: Float = 50f,
    val fuelLevelLow: Boolean = false,
    val tractionBatterySocDisplayed: Float = 72f,
    val chargingCableConnected: Boolean = false,
    val tractionBatteryChargingIsCharging: Boolean = false,
    val chargingAveragePowerKw: Float = 0f,
    val cabinAmbientAirTemperature: Float = 20f,
    val exteriorAirTemperature: Float = 20f,
    val rainIntensity: Int = 0,
    val washerFluidLow: Boolean = false,
    val washerFluidLevel: Int = 100,
    val frontWiperWear: Int = 0,
    val rearWiperWear: Int = 0,
    val row1LeftBrakePadWear: Int = 0,
    val row1RightBrakePadWear: Int = 0,
    val row2LeftBrakePadWear: Int = 0,
    val row2RightBrakePadWear: Int = 0,
    val row1LeftTirePressureLow: Boolean = false,
    val row1RightTirePressureLow: Boolean = false,
    val row2LeftTirePressureLow: Boolean = false,
    val row2RightTirePressureLow: Boolean = false,
    val diagnosticsDtcCount: Int = 0,
    val obdMilOn: Boolean = false,
    val serviceDue: Boolean = false,
    val vehicleIsMoving: Boolean = false,
    val vehicleSpeedKmh: Float = 0f,
    val selectedGear: Int = 126,
    val traveledDistanceMeters: Float = 0f,
    val driverSeatBelted: Boolean = false,
    val leftIndicatorSignaling: Boolean = false,
    val rightIndicatorSignaling: Boolean = false,
    val destinationLatitude: Double = 0.0,
    val destinationLongitude: Double = 0.0,
    val currentLocationTimestamp: String = DEFAULT_CURRENT_LOCATION_TIMESTAMP,
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val combustionEngineRunning: Boolean = false,
    val traveledDistanceSinceStartKm: Float = 0f,
    val tripDurationSeconds: Float = 0f,
    val tripMeterReadingKm: Float = 0f,
    val averageSpeedKmh: Float = 0f,
)

data class DebugInterpretationOverrides(
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
    val tirePressureStatus: String? = null,
    val isMoving: Boolean? = null,
    val speed: Int? = null,
    val gear: String? = null,
    val isNavigating: Boolean? = null,
    val distanceToDestination: Int? = null,
    val isEngineOn: Boolean? = null,
    val timeOfDay: String? = null,
)

data class DebugVssState(
    val raw: DebugRawVssState = DebugRawVssState(),
    val overrides: DebugInterpretationOverrides = DebugInterpretationOverrides(),
    val cardExtraSignals: Map<String, String> = DebugCardVssSignals.defaults,
) {
    val isDistracted: Boolean
        get() = overrides.isDistracted ?: (raw.driverDistractionLevel >= DISTRACTION_THRESHOLD_PERCENT)

    val isDrowsy: Boolean
        get() = overrides.isDrowsy ?: (raw.driverFatigueLevel >= FATIGUE_THRESHOLD_PERCENT || raw.dmsIsWarning)

    val attentionLevel: Int
        get() =
            overrides.attentionLevel
                ?: (100f - raw.driverDistractionLevel).roundToInt().coerceIn(0, 100)

    val isEmergencyBraking: Boolean
        get() = overrides.isEmergencyBraking ?: raw.driverEmergencyBrakingDetected

    val distanceToFrontVehicle: Int
        get() = overrides.distanceToFrontVehicle ?: raw.obstacleFrontCenterDistance.roundToInt().coerceAtLeast(0)

    val isCharging: Boolean
        get() =
            overrides.isCharging
                ?: (
                    raw.tractionBatteryChargingIsCharging ||
                        (raw.chargingCableConnected && raw.chargingAveragePowerKw > 0f)
                )

    val batteryPercent: Int
        get() = overrides.batteryPercent ?: raw.tractionBatterySocDisplayed.roundToInt().coerceIn(0, 100)

    val outsideTemperature: Int
        get() = overrides.outsideTemperature ?: raw.exteriorAirTemperature.roundToInt()

    val isRaining: Boolean
        get() = overrides.isRaining ?: (raw.rainIntensity > 0)

    val washerFluidLevel: Int
        get() = overrides.washerFluidLevel ?: raw.washerFluidLevel.coerceIn(0, 100)

    val isEngineWarning: Boolean
        get() = overrides.isEngineWarning ?: (raw.obdMilOn || raw.diagnosticsDtcCount > 0)

    val tirePressureStatus: String
        get() =
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
                }

    val isMoving: Boolean
        get() = overrides.isMoving ?: (raw.vehicleIsMoving || raw.vehicleSpeedKmh > 0f)

    val speed: Int
        get() = overrides.speed ?: raw.vehicleSpeedKmh.roundToInt().coerceAtLeast(0)

    val gear: String
        get() = overrides.gear ?: raw.selectedGear.toGearLabel()

    val isNavigating: Boolean
        get() = overrides.isNavigating ?: (distanceToDestination > ARRIVAL_THRESHOLD_METERS)

    val distanceToDestination: Int
        get() =
            overrides.distanceToDestination
                ?: distanceMeters(
                    raw.currentLatitude,
                    raw.currentLongitude,
                    raw.destinationLatitude,
                    raw.destinationLongitude,
                ).roundToInt()

    val isEngineOn: Boolean
        get() = overrides.isEngineOn ?: raw.combustionEngineRunning

    val timeOfDay: String
        get() = overrides.timeOfDay?.let(::interpretVssTimeOfDay) ?: interpretVssTimeOfDay(raw.currentLocationTimestamp)
}

interface DebugVssProvider {
    val state: StateFlow<DebugVssState>
}

@Singleton
class DebugStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DebugVssProvider {
        private val prefs: SharedPreferences = context.getSharedPreferences("debug_vss_prefs", Context.MODE_PRIVATE)

        private val _state = MutableStateFlow(loadState())
        override val state: StateFlow<DebugVssState> = _state.asStateFlow()

        // Count of completed safe drives. VSS is a snapshot and cannot express this history, so the
        // debug overlay accumulates it: each qualifying VSS-derived drive records one.
        private val _safeDriveCount = MutableStateFlow(prefs.getInt(SAFE_DRIVE_COUNT_KEY, 0))
        val safeDriveCount: StateFlow<Int> = _safeDriveCount.asStateFlow()

        fun recordSafeDrive() {
            _safeDriveCount.value += 1
            prefs.edit().putInt(SAFE_DRIVE_COUNT_KEY, _safeDriveCount.value).apply()
        }

        fun resetSafeDriveCount() {
            _safeDriveCount.value = 0
            prefs.edit().putInt(SAFE_DRIVE_COUNT_KEY, 0).apply()
        }

        private fun loadState(): DebugVssState =
            DebugVssState(
                cardExtraSignals =
                    DebugCardVssSignals.additional.associate { definition ->
                        definition.path to
                            (prefs.getString("card.${definition.path}", null) ?: definition.defaultValue)
                    },
                raw =
                    DebugRawVssState(
                        driverFatigueLevel = prefs.float("Vehicle.Driver.FatigueLevel", 0f),
                        driverDistractionLevel = prefs.float("Vehicle.Driver.DistractionLevel", 0f),
                        dmsIsWarning = prefs.bool("Vehicle.ADAS.DMS.IsWarning", false),
                        laneDepartureWarning = prefs.bool("Vehicle.ADAS.LaneDepartureDetection.IsWarning", false),
                        obstacleDetectionWarning = prefs.bool("Vehicle.ADAS.ObstacleDetection.IsWarning", false),
                        strongCrossWindDetected = prefs.bool("Vehicle.ADAS.ESC.IsStrongCrossWindDetected", false),
                        roadFrictionMostProbable = prefs.float("Vehicle.ADAS.ESC.RoadFriction.MostProbable", 100f),
                        accelerationLongitudinal = prefs.float("Vehicle.Acceleration.Longitudinal", 0f),
                        driverEmergencyBrakingDetected =
                            prefs.bool("Vehicle.Chassis.Brake.IsDriverEmergencyBrakingDetected", false),
                        obstacleFrontCenterDistance =
                            prefs.float("Vehicle.ADAS.ObstacleDetection.Front.Center.Distance", 50f),
                        fuelLevelLow = prefs.bool("Vehicle.Powertrain.FuelSystem.IsFuelLevelLow", false),
                        tractionBatterySocDisplayed =
                            prefs.float("Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed", 72f),
                        chargingCableConnected =
                            prefs.bool(
                                "Vehicle.Powertrain.TractionBattery.Charging.ChargingPort.AnyPosition.IsChargingCableConnected",
                                false,
                            ),
                        tractionBatteryChargingIsCharging =
                            prefs.bool("Vehicle.Powertrain.TractionBattery.Charging.IsCharging", false),
                        chargingAveragePowerKw =
                            prefs.float("Vehicle.Powertrain.TractionBattery.Charging.AveragePower", 0f),
                        cabinAmbientAirTemperature = prefs.float("Vehicle.Cabin.HVAC.AmbientAirTemperature", 20f),
                        exteriorAirTemperature = prefs.float("Vehicle.Exterior.AirTemperature", 20f),
                        rainIntensity = prefs.int("Vehicle.Body.Raindetection.Intensity", 0),
                        washerFluidLow = prefs.bool("Vehicle.Body.Windshield.Front.WasherFluid.IsLevelLow", false),
                        washerFluidLevel = prefs.int("Vehicle.Body.Windshield.Front.WasherFluid.Level", 100),
                        frontWiperWear = prefs.int("Vehicle.Body.Windshield.Front.Wiping.WiperWear", 0),
                        rearWiperWear = prefs.int("Vehicle.Body.Windshield.Rear.Wiping.WiperWear", 0),
                        row1LeftBrakePadWear =
                            prefs.int("Vehicle.Chassis.Axle.Row1.Wheel.Left.Brake.PadWear", 0),
                        row1RightBrakePadWear =
                            prefs.int("Vehicle.Chassis.Axle.Row1.Wheel.Right.Brake.PadWear", 0),
                        row2LeftBrakePadWear =
                            prefs.int("Vehicle.Chassis.Axle.Row2.Wheel.Left.Brake.PadWear", 0),
                        row2RightBrakePadWear =
                            prefs.int("Vehicle.Chassis.Axle.Row2.Wheel.Right.Brake.PadWear", 0),
                        row1LeftTirePressureLow =
                            prefs.bool("Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow", false),
                        row1RightTirePressureLow =
                            prefs.bool("Vehicle.Chassis.Axle.Row1.Wheel.Right.Tire.IsPressureLow", false),
                        row2LeftTirePressureLow =
                            prefs.bool("Vehicle.Chassis.Axle.Row2.Wheel.Left.Tire.IsPressureLow", false),
                        row2RightTirePressureLow =
                            prefs.bool("Vehicle.Chassis.Axle.Row2.Wheel.Right.Tire.IsPressureLow", false),
                        diagnosticsDtcCount = prefs.int("Vehicle.Diagnostics.DTCCount", 0),
                        obdMilOn = prefs.bool("Vehicle.OBD.Status.IsMILOn", false),
                        serviceDue = prefs.bool("Vehicle.Service.IsServiceDue", false),
                        vehicleIsMoving = prefs.bool("Vehicle.IsMoving", false),
                        vehicleSpeedKmh = prefs.float("Vehicle.Speed", 0f),
                        selectedGear = prefs.int("Vehicle.Powertrain.Transmission.SelectedGear", 126),
                        traveledDistanceMeters =
                            prefs.float(
                                "Vehicle.TraveledDistance.meters",
                                prefs.float("Vehicle.TraveledDistance", 0f) * 1_000f,
                            ),
                        driverSeatBelted = prefs.bool("Vehicle.Cabin.Seat.Row1.DriverSide.IsBelted", false),
                        leftIndicatorSignaling =
                            prefs.bool("Vehicle.Body.Lights.DirectionIndicator.Left.IsSignaling", false),
                        rightIndicatorSignaling =
                            prefs.bool("Vehicle.Body.Lights.DirectionIndicator.Right.IsSignaling", false),
                        destinationLatitude =
                            prefs.double("Vehicle.Cabin.Infotainment.Navigation.DestinationSet.Latitude", 0.0),
                        destinationLongitude =
                            prefs.double("Vehicle.Cabin.Infotainment.Navigation.DestinationSet.Longitude", 0.0),
                        currentLocationTimestamp =
                            prefs.getString(
                                "Vehicle.CurrentLocation.Timestamp",
                                DEFAULT_CURRENT_LOCATION_TIMESTAMP,
                            ) ?: DEFAULT_CURRENT_LOCATION_TIMESTAMP,
                        currentLatitude = prefs.double("Vehicle.CurrentLocation.Latitude", 0.0),
                        currentLongitude = prefs.double("Vehicle.CurrentLocation.Longitude", 0.0),
                        combustionEngineRunning =
                            prefs.bool("Vehicle.Powertrain.CombustionEngine.IsRunning", false),
                        traveledDistanceSinceStartKm = prefs.float("Vehicle.TraveledDistanceSinceStart", 0f),
                        tripDurationSeconds = prefs.float("Vehicle.TripDuration", 0f),
                        tripMeterReadingKm = prefs.float("Vehicle.TripMeterReading", 0f),
                        averageSpeedKmh = prefs.float("Vehicle.AverageSpeed", 0f),
                    ),
                overrides =
                    DebugInterpretationOverrides(
                        isDistracted = prefs.booleanOverride("override.isDistracted"),
                        isDrowsy = prefs.booleanOverride("override.isDrowsy"),
                        attentionLevel = prefs.intOverride("override.attentionLevel"),
                        isEmergencyBraking = prefs.booleanOverride("override.isEmergencyBraking"),
                        distanceToFrontVehicle = prefs.intOverride("override.distanceToFrontVehicle"),
                        isCharging = prefs.booleanOverride("override.isCharging"),
                        batteryPercent = prefs.intOverride("override.batteryPercent"),
                        outsideTemperature = prefs.intOverride("override.outsideTemperature"),
                        isRaining = prefs.booleanOverride("override.isRaining"),
                        washerFluidLevel = prefs.intOverride("override.washerFluidLevel"),
                        isEngineWarning = prefs.booleanOverride("override.isEngineWarning"),
                        tirePressureStatus = prefs.stringOverride("override.tirePressureStatus"),
                        isMoving = prefs.booleanOverride("override.isMoving"),
                        speed = prefs.intOverride("override.speed"),
                        gear = prefs.stringOverride("override.gear"),
                        isNavigating = prefs.booleanOverride("override.isNavigating"),
                        distanceToDestination = prefs.intOverride("override.distanceToDestination"),
                        isEngineOn = prefs.booleanOverride("override.isEngineOn"),
                        timeOfDay = prefs.stringOverride("override.timeOfDay"),
                    ),
            )

        fun updateState(reducer: (DebugVssState) -> DebugVssState) {
            val newState = reducer(_state.value)
            _state.value = newState
            prefs.edit().write(newState).apply()
        }

        private fun SharedPreferences.Editor.write(state: DebugVssState): SharedPreferences.Editor {
            val raw = state.raw
            putFloat("Vehicle.Driver.FatigueLevel", raw.driverFatigueLevel)
            putFloat("Vehicle.Driver.DistractionLevel", raw.driverDistractionLevel)
            putBoolean("Vehicle.ADAS.DMS.IsWarning", raw.dmsIsWarning)
            putBoolean("Vehicle.ADAS.LaneDepartureDetection.IsWarning", raw.laneDepartureWarning)
            putBoolean("Vehicle.ADAS.ObstacleDetection.IsWarning", raw.obstacleDetectionWarning)
            putBoolean("Vehicle.ADAS.ESC.IsStrongCrossWindDetected", raw.strongCrossWindDetected)
            putFloat("Vehicle.ADAS.ESC.RoadFriction.MostProbable", raw.roadFrictionMostProbable)
            putFloat("Vehicle.Acceleration.Longitudinal", raw.accelerationLongitudinal)
            putBoolean("Vehicle.Chassis.Brake.IsDriverEmergencyBrakingDetected", raw.driverEmergencyBrakingDetected)
            putFloat("Vehicle.ADAS.ObstacleDetection.Front.Center.Distance", raw.obstacleFrontCenterDistance)
            putBoolean("Vehicle.Powertrain.FuelSystem.IsFuelLevelLow", raw.fuelLevelLow)
            putFloat("Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed", raw.tractionBatterySocDisplayed)
            putBoolean(
                "Vehicle.Powertrain.TractionBattery.Charging.ChargingPort.AnyPosition.IsChargingCableConnected",
                raw.chargingCableConnected,
            )
            putBoolean("Vehicle.Powertrain.TractionBattery.Charging.IsCharging", raw.tractionBatteryChargingIsCharging)
            putFloat("Vehicle.Powertrain.TractionBattery.Charging.AveragePower", raw.chargingAveragePowerKw)
            putFloat("Vehicle.Cabin.HVAC.AmbientAirTemperature", raw.cabinAmbientAirTemperature)
            putFloat("Vehicle.Exterior.AirTemperature", raw.exteriorAirTemperature)
            putInt("Vehicle.Body.Raindetection.Intensity", raw.rainIntensity)
            putBoolean("Vehicle.Body.Windshield.Front.WasherFluid.IsLevelLow", raw.washerFluidLow)
            putInt("Vehicle.Body.Windshield.Front.WasherFluid.Level", raw.washerFluidLevel)
            putInt("Vehicle.Body.Windshield.Front.Wiping.WiperWear", raw.frontWiperWear)
            putInt("Vehicle.Body.Windshield.Rear.Wiping.WiperWear", raw.rearWiperWear)
            putInt("Vehicle.Chassis.Axle.Row1.Wheel.Left.Brake.PadWear", raw.row1LeftBrakePadWear)
            putInt("Vehicle.Chassis.Axle.Row1.Wheel.Right.Brake.PadWear", raw.row1RightBrakePadWear)
            putInt("Vehicle.Chassis.Axle.Row2.Wheel.Left.Brake.PadWear", raw.row2LeftBrakePadWear)
            putInt("Vehicle.Chassis.Axle.Row2.Wheel.Right.Brake.PadWear", raw.row2RightBrakePadWear)
            putBoolean("Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow", raw.row1LeftTirePressureLow)
            putBoolean("Vehicle.Chassis.Axle.Row1.Wheel.Right.Tire.IsPressureLow", raw.row1RightTirePressureLow)
            putBoolean("Vehicle.Chassis.Axle.Row2.Wheel.Left.Tire.IsPressureLow", raw.row2LeftTirePressureLow)
            putBoolean("Vehicle.Chassis.Axle.Row2.Wheel.Right.Tire.IsPressureLow", raw.row2RightTirePressureLow)
            putInt("Vehicle.Diagnostics.DTCCount", raw.diagnosticsDtcCount)
            putBoolean("Vehicle.OBD.Status.IsMILOn", raw.obdMilOn)
            putBoolean("Vehicle.Service.IsServiceDue", raw.serviceDue)
            putBoolean("Vehicle.IsMoving", raw.vehicleIsMoving)
            putFloat("Vehicle.Speed", raw.vehicleSpeedKmh)
            putInt("Vehicle.Powertrain.Transmission.SelectedGear", raw.selectedGear)
            putFloat("Vehicle.TraveledDistance.meters", raw.traveledDistanceMeters)
            putBoolean("Vehicle.Cabin.Seat.Row1.DriverSide.IsBelted", raw.driverSeatBelted)
            putBoolean("Vehicle.Body.Lights.DirectionIndicator.Left.IsSignaling", raw.leftIndicatorSignaling)
            putBoolean("Vehicle.Body.Lights.DirectionIndicator.Right.IsSignaling", raw.rightIndicatorSignaling)
            putDouble("Vehicle.Cabin.Infotainment.Navigation.DestinationSet.Latitude", raw.destinationLatitude)
            putDouble("Vehicle.Cabin.Infotainment.Navigation.DestinationSet.Longitude", raw.destinationLongitude)
            putString("Vehicle.CurrentLocation.Timestamp", raw.currentLocationTimestamp)
            putDouble("Vehicle.CurrentLocation.Latitude", raw.currentLatitude)
            putDouble("Vehicle.CurrentLocation.Longitude", raw.currentLongitude)
            putBoolean("Vehicle.Powertrain.CombustionEngine.IsRunning", raw.combustionEngineRunning)
            putFloat("Vehicle.TraveledDistanceSinceStart", raw.traveledDistanceSinceStartKm)
            putFloat("Vehicle.TripDuration", raw.tripDurationSeconds)
            putFloat("Vehicle.TripMeterReading", raw.tripMeterReadingKm)
            putFloat("Vehicle.AverageSpeed", raw.averageSpeedKmh)
            state.cardExtraSignals.forEach { (path, value) -> putString("card.$path", value) }
            writeOverrides(state.overrides)
            return this
        }

        private fun SharedPreferences.Editor.writeOverrides(overrides: DebugInterpretationOverrides) {
            putOverride("override.isDistracted", overrides.isDistracted)
            putOverride("override.isDrowsy", overrides.isDrowsy)
            putOverride("override.attentionLevel", overrides.attentionLevel)
            putOverride("override.isEmergencyBraking", overrides.isEmergencyBraking)
            putOverride("override.distanceToFrontVehicle", overrides.distanceToFrontVehicle)
            putOverride("override.isCharging", overrides.isCharging)
            putOverride("override.batteryPercent", overrides.batteryPercent)
            putOverride("override.outsideTemperature", overrides.outsideTemperature)
            putOverride("override.isRaining", overrides.isRaining)
            putOverride("override.washerFluidLevel", overrides.washerFluidLevel)
            putOverride("override.isEngineWarning", overrides.isEngineWarning)
            putOverride("override.tirePressureStatus", overrides.tirePressureStatus)
            putOverride("override.isMoving", overrides.isMoving)
            putOverride("override.speed", overrides.speed)
            putOverride("override.gear", overrides.gear)
            putOverride("override.isNavigating", overrides.isNavigating)
            putOverride("override.distanceToDestination", overrides.distanceToDestination)
            putOverride("override.isEngineOn", overrides.isEngineOn)
            putOverride("override.timeOfDay", overrides.timeOfDay)
        }
    }

private const val SAFE_DRIVE_COUNT_KEY = "debug.safeDriveCount"
private const val DISTRACTION_THRESHOLD_PERCENT = 70f
private const val FATIGUE_THRESHOLD_PERCENT = 70f
private const val ARRIVAL_THRESHOLD_METERS = 100
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DEFAULT_CURRENT_LOCATION_TIMESTAMP = "2026-10-08T10:00:00Z"

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

private fun SharedPreferences.bool(
    key: String,
    default: Boolean,
) = getBoolean(key, default)

private fun SharedPreferences.int(
    key: String,
    default: Int,
) = getInt(key, default)

private fun SharedPreferences.float(
    key: String,
    default: Float,
) = getFloat(key, default)

private fun SharedPreferences.double(
    key: String,
    default: Double,
) = getString(key, null)?.toDoubleOrNull() ?: default

private fun SharedPreferences.booleanOverride(key: String): Boolean? =
    if (contains(key)) {
        getBoolean(key, false)
    } else {
        null
    }

private fun SharedPreferences.intOverride(key: String): Int? = if (contains(key)) getInt(key, 0) else null

private fun SharedPreferences.stringOverride(key: String): String? = if (contains(key)) getString(key, null) else null

private fun SharedPreferences.Editor.putDouble(
    key: String,
    value: Double,
) {
    putString(key, value.toString())
}

private fun SharedPreferences.Editor.putOverride(
    key: String,
    value: Boolean?,
) {
    if (value == null) remove(key) else putBoolean(key, value)
}

private fun SharedPreferences.Editor.putOverride(
    key: String,
    value: Int?,
) {
    if (value == null) remove(key) else putInt(key, value)
}

private fun SharedPreferences.Editor.putOverride(
    key: String,
    value: String?,
) {
    if (value == null) remove(key) else putString(key, value)
}
