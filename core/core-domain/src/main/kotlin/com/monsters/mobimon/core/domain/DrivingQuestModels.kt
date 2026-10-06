package com.monsters.mobimon.core.domain

enum class WeatherCondition(
    val multiplier: Float,
) {
    CLEAR(1.0f),
    CLOUDY_OR_NIGHT(1.2f),
    RAIN_OR_SNOW(1.5f),
}

data class DriveEvaluationData(
    val date: String = "",
    // Identity of the drive this evidence describes; blank when no drive is known.
    val driveId: String = "",
    val distanceKm: Float = 0f,
    val safeBeltMinutes: Int = 0,
    val hardBrakeCount: Int = 0,
    val hardAccelCount: Int = 0,
    val overspeedCount: Int = 0,
    val safeDriveScore: Int = 0,
    val turnSignalOnCount: Int = 0,
    val continuousDistanceKm: Float = 0f,
    val isDistracted: Boolean = false,
    val laneDepartureCount: Int = 0,
    val isDestinationMaintenanceCenter: Boolean = false,
    val isDestinationReached: Boolean = false,
    val totalDistanceKm: Float = 0f,
    // Number of completed safe drives (each >= MIN_DRIVE_DISTANCE_KM and passing the safe-drive check).
    val safeDriveCount: Int = 0,
    val weather: WeatherCondition = WeatherCondition.CLEAR,
    val isBatteryChargedProperly: Boolean = false,
    val hasRestedDuringLongDrive: Boolean = false,
    val isWasherFluidRefilled: Boolean = false,
    val isTirePressureNormalWeekly: Boolean = false,
)

data class DrivingQuestResult(
    val questId: String,
    val isSatisfied: Boolean,
    val basePoints: Long,
    val earnedPoints: Long,
    val weatherCondition: WeatherCondition,
    val reason: String,
    // Units counted today for capped daily quests; null for quests without a daily count.
    val dailyCount: Int? = null,
)
