package com.monsters.mobimon.feature.quest

import com.monsters.mobimon.core.domain.DriveEvaluationData
import com.monsters.mobimon.core.domain.DrivingQuestEvaluator
import com.monsters.mobimon.core.domain.DrivingQuestIds
import com.monsters.mobimon.core.domain.PointQuestCatalog
import com.monsters.mobimon.core.domain.PointQuestSchedule
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.navigation.VehicleRoute
import com.monsters.mobimon.core.presentation.CompanionAppearanceState
import com.monsters.mobimon.core.presentation.PointBalanceState

internal class QuestCatalog(
    private val catalog: PointQuestCatalog,
) {
    fun present(
        state: QuestUiState,
        appearance: CompanionAppearanceState,
        pointBalance: PointBalanceState,
        parkedVerified: Boolean,
        text: (Int) -> String,
    ): QuestScreenState = present(state, appearance, pointBalance, parkedVerified, null, text)

    fun present(
        state: QuestUiState,
        appearance: CompanionAppearanceState,
        pointBalance: PointBalanceState,
        parkedVerified: Boolean,
        snapshot: VehicleSnapshot?,
        text: (Int) -> String,
    ): QuestScreenState {
        val visibleCompletedIds =
            if (state.pendingQuestId != null && state.rewardSuccess == null) {
                state.completedPointQuestIds - state.pendingQuestId
            } else {
                state.completedPointQuestIds
            }
        val quests =
            drivingContent
                .mapNotNull { content ->
                    val definition = catalog.find(content.id) ?: return@mapNotNull null
                    val status =
                        when (content.id) {
                            in visibleCompletedIds -> QuestItemStatus.COMPLETED
                            in state.satisfiedDrivingQuestIds -> QuestItemStatus.CLAIMABLE
                            else -> QuestItemStatus.IN_PROGRESS
                        }
                    QuestItemUiModel(
                        id = content.id,
                        title = text(content.title),
                        description = text(content.description),
                        detailLine1 = text(content.detailLine1),
                        detailLine2 = text(content.detailLine2),
                        scheduleText = text(definition.schedule.textResource()),
                        scheduleFullText =
                            text(
                                if (definition.schedule == PointQuestSchedule.OneTime) {
                                    R.string.quest_schedule_once_full
                                } else {
                                    definition.schedule.textResource()
                                },
                            ),
                        rewardPoints = definition.rewardPoints,
                        status = status,
                        actionType =
                            when (status) {
                                QuestItemStatus.CLAIMABLE -> QuestActionType.CLAIM_REWARD
                                QuestItemStatus.COMPLETED -> QuestActionType.ALREADY_CLAIMED
                                QuestItemStatus.IN_PROGRESS -> QuestActionType.VIEW_DETAIL
                            },
                        targetRoute = VehicleRoute.VEHICLE_INFO,
                        progressDetail = buildProgressDetail(content.id, state.driveEvaluation, snapshot),
                        completedAtUtcMillis =
                            state.completedPointQuestDates[content.id]?.takeIf {
                                content.id in
                                    visibleCompletedIds
                            },
                    )
                }.sortedBy { it.status.sortPriority }
        val hiddenQuests =
            hiddenContent
                .filter { content ->
                    content.id !in visibleCompletedIds &&
                        content.id !in state.dismissedHiddenQuestIds &&
                        content.isSatisfied(appearance)
                }.mapNotNull { content ->
                    val definition = catalog.find(content.id) ?: return@mapNotNull null
                    HiddenQuestUiModel(
                        content.id,
                        text(content.title),
                        text(content.description),
                        definition.rewardPoints,
                    )
                }
        return QuestScreenState(
            quests = quests,
            hiddenQuests = hiddenQuests,
            appearance = appearance,
            pointBalance = pointBalance,
            parkedVerified = parkedVerified,
            canClaim = parkedVerified && !state.isBusy && !state.isLoading && !state.observationFailed,
            pendingQuestId = state.pendingQuestId,
            isLoading = state.isLoading,
            observationFailed = state.observationFailed,
            errorMessage = state.message?.let { text(it.textResource()) },
            rewardSuccess = state.rewardSuccess,
        )
    }
}

private data class QuestContent(
    val id: String,
    val title: Int,
    val description: Int,
    val detailLine1: Int,
    val detailLine2: Int,
)

private val drivingContent =
    listOf(
        QuestContent(
            DrivingQuestIds.SEATBELT,
            R.string.quest_seatbelt_name,
            R.string.quest_seatbelt_short_desc,
            R.string.quest_seatbelt_detail_line1,
            R.string.quest_seatbelt_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.SAFE_DRIVE,
            R.string.quest_safe_drive_name,
            R.string.quest_safe_drive_short_desc,
            R.string.quest_safe_drive_detail_line1,
            R.string.quest_safe_drive_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.DISTANCE_100KM,
            R.string.quest_100km_name,
            R.string.quest_100km_short_desc,
            R.string.quest_100km_detail_line1,
            R.string.quest_100km_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.CLEAN_DRIVE,
            R.string.quest_clean_drive_name,
            R.string.quest_clean_drive_short_desc,
            R.string.quest_clean_drive_detail_line1,
            R.string.quest_clean_drive_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.FIRST_DRIVE,
            R.string.quest_first_drive_name,
            R.string.quest_first_drive_short_desc,
            R.string.quest_first_drive_detail_line1,
            R.string.quest_first_drive_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.FOCUS_DRIVE,
            R.string.quest_focus_drive_name,
            R.string.quest_focus_drive_short_desc,
            R.string.quest_focus_drive_detail_line1,
            R.string.quest_focus_drive_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.LANE_KEEP,
            R.string.quest_lane_keep_name,
            R.string.quest_lane_keep_short_desc,
            R.string.quest_lane_keep_detail_line1,
            R.string.quest_lane_keep_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.MAINTENANCE,
            R.string.quest_maintenance_name,
            R.string.quest_maintenance_short_desc,
            R.string.quest_maintenance_detail_line1,
            R.string.quest_maintenance_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.TURN_SIGNAL,
            R.string.quest_turn_signal_name,
            R.string.quest_turn_signal_short_desc,
            R.string.quest_turn_signal_detail_line1,
            R.string.quest_turn_signal_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.SAFE_5DAYS,
            R.string.quest_5days_safe_name,
            R.string.quest_5days_safe_short_desc,
            R.string.quest_5days_safe_detail_line1,
            R.string.quest_5days_safe_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.BATTERY_CARE,
            R.string.quest_battery_care_name,
            R.string.quest_battery_care_short_desc,
            R.string.quest_battery_care_detail_line1,
            R.string.quest_battery_care_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.LONG_TRIP_REST,
            R.string.quest_long_trip_rest_name,
            R.string.quest_long_trip_rest_short_desc,
            R.string.quest_long_trip_rest_detail_line1,
            R.string.quest_long_trip_rest_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.WASHER_FLUID,
            R.string.quest_washer_fluid_name,
            R.string.quest_washer_fluid_short_desc,
            R.string.quest_washer_fluid_detail_line1,
            R.string.quest_washer_fluid_detail_line2,
        ),
        QuestContent(
            DrivingQuestIds.TIRE_CHECK,
            R.string.quest_tire_check_name,
            R.string.quest_tire_check_short_desc,
            R.string.quest_tire_check_detail_line1,
            R.string.quest_tire_check_detail_line2,
        ),
    )

private data class HiddenQuestContent(
    val id: String,
    val title: Int,
    val description: Int,
    val isSatisfied: (CompanionAppearanceState) -> Boolean,
)

private val hiddenContent =
    listOf(
        HiddenQuestContent(
            DrivingQuestIds.HIDDEN_COSTUME,
            R.string.quest_hidden_costume_name,
            R.string.quest_hidden_costume_desc,
        ) {
            !it.accessoryId.isNullOrBlank() || !it.outfitId.isNullOrBlank()
        },
        HiddenQuestContent(
            DrivingQuestIds.HIDDEN_BACKGROUND,
            R.string.quest_hidden_background_name,
            R.string.quest_hidden_background_desc,
        ) {
            !it.backgroundId.isNullOrBlank() && it.backgroundId != "none" && it.backgroundId != "background:default"
        },
        HiddenQuestContent(
            DrivingQuestIds.HIDDEN_NEW_FRIEND,
            R.string.quest_hidden_new_friend_name,
            R.string.quest_hidden_new_friend_desc,
        ) {
            it.friendId != "friend:mobi"
        },
    )

private fun PointQuestSchedule.textResource(): Int =
    when (this) {
        PointQuestSchedule.OneTime -> R.string.quest_schedule_once_short
        is PointQuestSchedule.Daily -> R.string.quest_schedule_daily_short
        is PointQuestSchedule.Weekly -> R.string.quest_schedule_weekly_short
        is PointQuestSchedule.PerDrive -> R.string.quest_schedule_per_drive_short
        is PointQuestSchedule.CappedDaily -> R.string.quest_schedule_per_count_short
    }

private fun buildProgressDetail(
    questId: String,
    data: DriveEvaluationData,
    snapshot: VehicleSnapshot?,
): QuestProgressDetail? =
    when (questId) {
        DrivingQuestIds.SEATBELT ->
            QuestProgressDetail.Seatbelt(
                currentDistanceKm = data.distanceKm,
                remainingDistanceKm = (DrivingQuestEvaluator.MIN_DRIVE_DISTANCE_KM - data.distanceKm).coerceAtLeast(0f),
            )
        DrivingQuestIds.SAFE_DRIVE ->
            QuestProgressDetail.SafeDrive(
                safeScore = data.safeDriveScore,
                currentDistanceKm = data.distanceKm,
                remainingDistanceKm = (DrivingQuestEvaluator.MIN_DRIVE_DISTANCE_KM - data.distanceKm).coerceAtLeast(0f),
            )
        DrivingQuestIds.DISTANCE_100KM ->
            QuestProgressDetail.TotalDistance(
                totalDistanceKm = data.totalDistanceKm,
            )
        DrivingQuestIds.CLEAN_DRIVE ->
            QuestProgressDetail.CleanDrive(
                hardAccelCount = data.hardAccelCount,
                hardBrakeCount = data.hardBrakeCount,
                overspeedCount = data.overspeedCount,
                currentDistanceKm = data.distanceKm,
                remainingDistanceKm = (DrivingQuestEvaluator.MIN_DRIVE_DISTANCE_KM - data.distanceKm).coerceAtLeast(0f),
            )
        DrivingQuestIds.FIRST_DRIVE ->
            QuestProgressDetail.FirstDrive
        DrivingQuestIds.FOCUS_DRIVE ->
            QuestProgressDetail.FocusDrive(
                currentDistanceKm = data.continuousDistanceKm,
                remainingDistanceKm =
                    (DrivingQuestEvaluator.CONTINUOUS_DISTANCE_KM - data.continuousDistanceKm)
                        .coerceAtLeast(
                            0f,
                        ),
                distractionLevel = snapshot?.attentionLevel?.let { (100 - it).coerceIn(0, 100) },
            )
        DrivingQuestIds.LANE_KEEP ->
            QuestProgressDetail.LaneKeep(
                currentDistanceKm = data.continuousDistanceKm,
                remainingDistanceKm =
                    (DrivingQuestEvaluator.CONTINUOUS_DISTANCE_KM - data.continuousDistanceKm)
                        .coerceAtLeast(
                            0f,
                        ),
                laneDepartureCount = data.laneDepartureCount,
            )
        DrivingQuestIds.MAINTENANCE ->
            QuestProgressDetail.Maintenance
        DrivingQuestIds.TURN_SIGNAL ->
            QuestProgressDetail.TurnSignal(
                turnSignalCount = data.turnSignalOnCount,
            )
        DrivingQuestIds.SAFE_5DAYS ->
            QuestProgressDetail.SafeDriveStreak(
                safeDriveCount = data.safeDriveCount,
            )
        DrivingQuestIds.BATTERY_CARE ->
            QuestProgressDetail.BatteryCare(
                batteryPercent = snapshot?.batteryPercent,
            )
        DrivingQuestIds.LONG_TRIP_REST ->
            QuestProgressDetail.LongTripRest(
                currentDistanceKm = data.continuousDistanceKm,
                remainingDistanceKm =
                    (DrivingQuestEvaluator.CONTINUOUS_DISTANCE_KM - data.continuousDistanceKm)
                        .coerceAtLeast(0f),
                drivingMinutes = null,
            )
        DrivingQuestIds.WASHER_FLUID ->
            QuestProgressDetail.WasherFluid(
                washerFluidLevel = snapshot?.washerFluidLevel,
            )
        DrivingQuestIds.TIRE_CHECK ->
            QuestProgressDetail.TireCheck
        else -> null
    }
