package com.monsters.mobimon.core.domain

enum class SignalSource { REAL, SIMULATED }

enum class DrivingState { UNKNOWN, PARKED, MOVING }

enum class SignalQuality { UNAVAILABLE, VALID, STALE }

enum class SignalUnavailableReason { UNSUPPORTED, PERMISSION_DENIED, DISCONNECTED, NOT_REPORTED, INVALID }

enum class WarningSeverity { NOTICE, CAUTION, CRITICAL }

data class VehicleWarning(
    val item: String,
    val location: String? = null,
    val severity: WarningSeverity,
    val description: String,
    val nextAction: String,
    val observedAtMillis: Long,
    val quality: SignalQuality = SignalQuality.VALID,
)

data class VehicleSnapshot(
    val id: String,
    val epoch: String,
    val sequence: Long,
    val receivedAtMillis: Long,
    val source: SignalSource,
    val drivingState: DrivingState,
    val quality: SignalQuality,
    val batteryPercent: Int? = null,
    val batteryReceivedAtMillis: Long? = null,
    val batteryQuality: SignalQuality? = null,
    val parkingUnavailableReason: SignalUnavailableReason? = null,
    val batteryUnavailableReason: SignalUnavailableReason? = null,
    val warnings: List<VehicleWarning> = emptyList(),
    val isDistracted: Boolean? = null,
    val isDrowsy: Boolean? = null,
    val attentionLevel: Int? = null,
    val isEmergencyBraking: Boolean? = null,
    val distanceToFrontVehicle: Int? = null,
    val isCharging: Boolean? = null,
    val outsideTemperature: Int? = null,
    val isRaining: Boolean? = null,
    val washerFluidLevel: Int? = null,
    val isEngineWarning: Boolean? = null,
    val isFuelLevelLow: Boolean? = null,
    val tirePressureStatus: String? = null,
    val speed: Int? = null,
    val gear: String? = null,
    val isNavigating: Boolean? = null,
    val distanceToDestination: Int? = null,
    val isEngineOn: Boolean? = null,
    val timeOfDay: String? = null,
    /** Original offset-bearing VSS time; its own monotonic observation time, not a publication ticker. */
    val vssTimestamp: String? = null,
    val timeObservedAtMillis: Long? = null,
    /** Derived UI metadata; never used as quest evidence. */
    val parkingAgeMillis: Long? = null,
    val batteryAgeMillis: Long? = null,
    /** Display-only VSS readings from the isolated simulation; absent paths are unavailable. */
    val vssCardSignals: Map<String, String> = emptyMap(),
    /** Explicit debugger input, distinct from a simulated fallback when no adapter exists. */
    val isDebuggerOverride: Boolean = false,
)
