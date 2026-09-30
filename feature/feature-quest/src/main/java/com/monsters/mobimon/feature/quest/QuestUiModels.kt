package com.monsters.mobimon.feature.quest

import com.monsters.mobimon.core.domain.DrivingQuestIds
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.presentation.CompanionAppearanceState
import com.monsters.mobimon.core.presentation.PointBalanceState

enum class QuestFilterTab { ALL, IN_PROGRESS, COMPLETED }

enum class QuestItemStatus(
    val sortPriority: Int,
) {
    CLAIMABLE(0),
    IN_PROGRESS(1),
    COMPLETED(2),
}

enum class QuestActionType { CLAIM_REWARD, VIEW_DETAIL, ALREADY_CLAIMED }

sealed interface QuestProgressDetail {
    data class Seatbelt(
        val currentDistanceKm: Float,
        val remainingDistanceKm: Float,
    ) : QuestProgressDetail

    data class SafeDrive(
        val safeScore: Int,
        val currentDistanceKm: Float,
        val remainingDistanceKm: Float,
    ) : QuestProgressDetail

    data class TotalDistance(
        val totalDistanceKm: Float,
    ) : QuestProgressDetail

    data class CleanDrive(
        val hardAccelCount: Int,
        val hardBrakeCount: Int,
        val overspeedCount: Int,
        val currentDistanceKm: Float,
        val remainingDistanceKm: Float,
    ) : QuestProgressDetail

    data object FirstDrive : QuestProgressDetail

    data class FocusDrive(
        val currentDistanceKm: Float,
        val remainingDistanceKm: Float,
        val distractionLevel: Int?,
    ) : QuestProgressDetail

    data class LaneKeep(
        val currentDistanceKm: Float,
        val remainingDistanceKm: Float,
        val laneDepartureCount: Int,
    ) : QuestProgressDetail

    data object Maintenance : QuestProgressDetail

    data class TurnSignal(
        val turnSignalCount: Int,
    ) : QuestProgressDetail

    data class SafeDriveStreak(
        val safeDriveCount: Int,
    ) : QuestProgressDetail

    data class BatteryCare(
        val batteryPercent: Int?,
    ) : QuestProgressDetail

    data class LongTripRest(
        val currentDistanceKm: Float,
        val remainingDistanceKm: Float,
        val drivingMinutes: Int? = null,
    ) : QuestProgressDetail

    data class WasherFluid(
        val washerFluidLevel: Int?,
    ) : QuestProgressDetail

    data object TireCheck : QuestProgressDetail
}

data class QuestItemUiModel(
    val id: String,
    val title: String,
    val description: String,
    val detailLine1: String,
    val detailLine2: String,
    val scheduleText: String,
    val scheduleFullText: String,
    val rewardPoints: Long,
    val status: QuestItemStatus,
    val actionType: QuestActionType,
    val targetRoute: AppRoute,
    val progressDetail: QuestProgressDetail? = null,
    val completedAtUtcMillis: Long? = null,
) {
    val showVehicleStep: Boolean get() = id == DrivingQuestIds.BATTERY_CARE || id == DrivingQuestIds.TIRE_CHECK
    val showExecuteButton: Boolean get() = id == DrivingQuestIds.BATTERY_CARE || id == DrivingQuestIds.TIRE_CHECK
}

data class HiddenQuestUiModel(
    val id: String,
    val title: String,
    val description: String,
    val rewardPoints: Long,
)

data class QuestScreenState(
    val quests: List<QuestItemUiModel> = emptyList(),
    val hiddenQuests: List<HiddenQuestUiModel> = emptyList(),
    val appearance: CompanionAppearanceState = CompanionAppearanceState(),
    val pointBalance: PointBalanceState = PointBalanceState.Loading,
    val parkedVerified: Boolean = false,
    val canClaim: Boolean = false,
    val pendingQuestId: String? = null,
    val isLoading: Boolean = false,
    val observationFailed: Boolean = false,
    val errorMessage: String? = null,
    val rewardSuccess: QuestRewardSuccess? = null,
    val requestedQuestId: String? = null,
    val vehicleWarning: Boolean = false,
    val vehicleHungry: Boolean = false,
)
