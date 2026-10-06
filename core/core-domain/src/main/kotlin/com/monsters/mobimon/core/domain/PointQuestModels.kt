package com.monsters.mobimon.core.domain

sealed interface PointQuestSchedule {
    data object OneTime : PointQuestSchedule

    data class Daily(
        val resetZoneId: String,
    ) : PointQuestSchedule

    data class Weekly(
        val resetZoneId: String,
    ) : PointQuestSchedule

    /** One occurrence per [DriveEvaluationData.driveId]; claims without a drive identity are not met. */
    data object PerDrive : PointQuestSchedule

    /** Claims add newly counted units each day until [maxPerDay] units have been awarded. */
    data class CappedDaily(
        val resetZoneId: String,
        val maxPerDay: Int,
    ) : PointQuestSchedule
}

data class PointQuestDefinition(
    val id: String,
    val rewardPoints: Long,
    val schedule: PointQuestSchedule,
)

fun interface PointQuestCatalog {
    fun find(questId: String): PointQuestDefinition?
}

object DrivingQuestIds {
    const val SEATBELT = "quest_seatbelt"
    const val SAFE_DRIVE = "quest_safe_drive"
    const val DISTANCE_100KM = "quest_100km"
    const val CLEAN_DRIVE = "quest_clean_drive"
    const val FIRST_DRIVE = "quest_first_drive"
    const val FOCUS_DRIVE = "quest_focus_drive"
    const val LANE_KEEP = "quest_lane_keep"
    const val MAINTENANCE = "quest_maintenance"
    const val TURN_SIGNAL = "quest_turn_signal"
    const val SAFE_5DAYS = "quest_5days_safe"

    const val BATTERY_CARE = "quest_battery_care"
    const val LONG_TRIP_REST = "quest_long_trip_rest"
    const val WASHER_FLUID = "quest_washer_fluid"
    const val TIRE_CHECK = "quest_tire_check"

    const val HIDDEN_COSTUME = "quest_hidden_costume"
    const val HIDDEN_BACKGROUND = "quest_hidden_background"
    const val HIDDEN_NEW_FRIEND = "quest_hidden_new_friend"
}

class DefaultPointQuestCatalog(
    private val resetZoneId: String = "Asia/Seoul",
) : PointQuestCatalog {
    private val definitions =
        mapOf(
            DrivingQuestIds.SEATBELT to
                PointQuestDefinition(
                    id = DrivingQuestIds.SEATBELT,
                    rewardPoints = 5L,
                    schedule = PointQuestSchedule.PerDrive,
                ),
            DrivingQuestIds.SAFE_DRIVE to
                PointQuestDefinition(
                    id = DrivingQuestIds.SAFE_DRIVE,
                    rewardPoints = 20L,
                    schedule = PointQuestSchedule.PerDrive,
                ),
            DrivingQuestIds.DISTANCE_100KM to
                PointQuestDefinition(
                    id = DrivingQuestIds.DISTANCE_100KM,
                    rewardPoints = 25L,
                    schedule = PointQuestSchedule.OneTime,
                ),
            DrivingQuestIds.CLEAN_DRIVE to
                PointQuestDefinition(
                    id = DrivingQuestIds.CLEAN_DRIVE,
                    rewardPoints = 15L,
                    schedule = PointQuestSchedule.PerDrive,
                ),
            DrivingQuestIds.FIRST_DRIVE to
                PointQuestDefinition(
                    id = DrivingQuestIds.FIRST_DRIVE,
                    rewardPoints = 10L,
                    schedule = PointQuestSchedule.Daily(resetZoneId),
                ),
            DrivingQuestIds.FOCUS_DRIVE to
                PointQuestDefinition(
                    id = DrivingQuestIds.FOCUS_DRIVE,
                    rewardPoints = 10L,
                    schedule = PointQuestSchedule.PerDrive,
                ),
            DrivingQuestIds.LANE_KEEP to
                PointQuestDefinition(
                    id = DrivingQuestIds.LANE_KEEP,
                    rewardPoints = 10L,
                    schedule = PointQuestSchedule.PerDrive,
                ),
            DrivingQuestIds.MAINTENANCE to
                PointQuestDefinition(
                    id = DrivingQuestIds.MAINTENANCE,
                    rewardPoints = 10L,
                    schedule = PointQuestSchedule.OneTime,
                ),
            DrivingQuestIds.TURN_SIGNAL to
                PointQuestDefinition(
                    id = DrivingQuestIds.TURN_SIGNAL,
                    rewardPoints = 1L,
                    schedule = PointQuestSchedule.CappedDaily(resetZoneId, maxPerDay = 10),
                ),
            DrivingQuestIds.SAFE_5DAYS to
                PointQuestDefinition(
                    id = DrivingQuestIds.SAFE_5DAYS,
                    rewardPoints = 50L,
                    schedule = PointQuestSchedule.Weekly(resetZoneId),
                ),
            DrivingQuestIds.BATTERY_CARE to
                PointQuestDefinition(
                    id = DrivingQuestIds.BATTERY_CARE,
                    rewardPoints = 20L,
                    schedule = PointQuestSchedule.Weekly(resetZoneId),
                ),
            DrivingQuestIds.LONG_TRIP_REST to
                PointQuestDefinition(
                    id = DrivingQuestIds.LONG_TRIP_REST,
                    rewardPoints = 25L,
                    schedule = PointQuestSchedule.PerDrive,
                ),
            DrivingQuestIds.WASHER_FLUID to
                PointQuestDefinition(
                    id = DrivingQuestIds.WASHER_FLUID,
                    rewardPoints = 15L,
                    schedule = PointQuestSchedule.OneTime,
                ),
            DrivingQuestIds.TIRE_CHECK to
                PointQuestDefinition(
                    id = DrivingQuestIds.TIRE_CHECK,
                    rewardPoints = 15L,
                    schedule = PointQuestSchedule.Weekly(resetZoneId),
                ),
            DrivingQuestIds.HIDDEN_COSTUME to
                PointQuestDefinition(
                    id = DrivingQuestIds.HIDDEN_COSTUME,
                    rewardPoints = 30L,
                    schedule = PointQuestSchedule.OneTime,
                ),
            DrivingQuestIds.HIDDEN_BACKGROUND to
                PointQuestDefinition(
                    id = DrivingQuestIds.HIDDEN_BACKGROUND,
                    rewardPoints = 30L,
                    schedule = PointQuestSchedule.OneTime,
                ),
            DrivingQuestIds.HIDDEN_NEW_FRIEND to
                PointQuestDefinition(
                    id = DrivingQuestIds.HIDDEN_NEW_FRIEND,
                    rewardPoints = 30L,
                    schedule = PointQuestSchedule.OneTime,
                ),
        )

    override fun find(questId: String): PointQuestDefinition? = definitions[questId]
}

sealed interface PointAwardResult {
    data class Awarded(
        val points: Long,
        val resultingBalance: Long,
        val occurrenceKey: String,
        val basePoints: Long = points,
        val weatherMultiplier: Float = 1.0f,
    ) : PointAwardResult

    data object AlreadyAwarded : PointAwardResult

    data object QuestUnavailable : PointAwardResult

    data object ConditionNotMet : PointAwardResult

    data object InteractionRestricted : PointAwardResult

    data object EvidenceChanged : PointAwardResult

    data object StorageFailure : PointAwardResult
}
