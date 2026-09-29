package com.monsters.mobimon.feature.quest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsters.mobimon.core.domain.DriveEvaluationData
import com.monsters.mobimon.core.domain.DrivingQuestEvaluator
import com.monsters.mobimon.core.domain.PointAwardResult
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.VehicleSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuestMessage {
    INTERACTION_RESTRICTED,
    REFRESH_REQUIRED,
    CONDITION_NOT_MET,
    UNSUPPORTED,
    STORAGE_FAILURE,
}

data class QuestRewardSuccess(
    val questId: String,
    val points: Long,
    val basePoints: Long = points,
    val weatherMultiplier: Float = 1.0f,
) {
    val bonusPoints: Long get() = (points - basePoints).coerceAtLeast(0)
}

data class QuestUiState(
    val completedPointQuestIds: Set<String> = emptySet(),
    val completedPointQuestDates: Map<String, Long> = emptyMap(),
    val satisfiedDrivingQuestIds: Set<String> = emptySet(),
    val dismissedHiddenQuestIds: Set<String> = emptySet(),
    val driveEvaluation: DriveEvaluationData = DriveEvaluationData(),
    val pendingQuestId: String? = null,
    val isLoading: Boolean = true,
    val observationFailed: Boolean = false,
    val message: QuestMessage? = null,
    val rewardSuccess: QuestRewardSuccess? = null,
    val requestedQuestId: String? = null,
) {
    val isBusy: Boolean get() = pendingQuestId != null
}

class QuestViewModel(
    private val economy: PointEconomy,
    private val drivingEvaluator: DrivingQuestEvaluator = DrivingQuestEvaluator(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(QuestUiState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private var claimJob: Job? = null
    private var claimGeneration = 0
    private var observedQuestIds = emptySet<String>()
    private val unobservedConfirmedQuestIds = mutableSetOf<String>()
    private var pendingClaimObserved = false

    init {
        retry()
    }

    fun retry() {
        if (observation?.isActive == true) return
        mutableState.update { it.copy(isLoading = true, observationFailed = false) }
        observation =
            viewModelScope.launch {
                try {
                    combine(
                        economy.completedQuestIds,
                        economy.completedQuestDates,
                        economy.driveEvaluation,
                    ) { completedIds, completionDates, evaluation ->
                        QuestObservation(
                            completedIds = completedIds,
                            completionDates = completionDates,
                            satisfiedIds =
                                drivingEvaluator
                                    .evaluateAll(evaluation)
                                    .filter { it.isSatisfied }
                                    .map { it.questId }
                                    .toSet(),
                            evaluation = evaluation,
                        )
                    }.collect { (completedIds, completionDates, satisfiedIds, evaluation) ->
                        observedQuestIds = completedIds
                        unobservedConfirmedQuestIds.removeAll(completedIds)
                        val pendingQuestId = state.value.pendingQuestId
                        if (pendingQuestId != null && pendingQuestId in completedIds) pendingClaimObserved = true
                        mutableState.update {
                            it.copy(
                                completedPointQuestIds = completedIds + unobservedConfirmedQuestIds,
                                completedPointQuestDates =
                                    completionDates.filterKeys { questId ->
                                        questId in
                                            completedIds
                                    },
                                satisfiedDrivingQuestIds = satisfiedIds,
                                driveEvaluation = evaluation,
                                isLoading = false,
                                observationFailed = false,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.update { it.copy(isLoading = false, observationFailed = true) }
                }
            }
    }

    fun dismissHiddenQuest(questId: String) {
        if (state.value.pendingQuestId == questId) return
        mutableState.update { it.copy(dismissedHiddenQuestIds = it.dismissedHiddenQuestIds + questId) }
    }

    fun dismissRewardSuccess() {
        mutableState.update { it.copy(rewardSuccess = null) }
    }

    fun onParkingInterrupted() {
        claimGeneration++
        claimJob?.cancel()
        pendingClaimObserved = false
        mutableState.update { it.copy(pendingQuestId = null, message = null, rewardSuccess = null) }
    }

    fun claimPointQuest(
        questId: String,
        displayedSnapshot: VehicleSnapshot,
    ) {
        if (state.value.isBusy || state.value.isLoading || state.value.observationFailed) return
        pendingClaimObserved = questId in observedQuestIds
        mutableState.update { it.copy(pendingQuestId = questId, message = null, rewardSuccess = null) }
        val generation = claimGeneration
        claimJob =
            viewModelScope.launch {
                try {
                    val result = economy.awardQuest(questId, displayedSnapshot)
                    if (generation != claimGeneration) return@launch
                    when (result) {
                        is PointAwardResult.Awarded ->
                            confirm(
                                questId,
                                QuestRewardSuccess(
                                    questId = questId,
                                    points = result.points,
                                    basePoints = result.basePoints,
                                    weatherMultiplier = result.weatherMultiplier,
                                ),
                            )
                        PointAwardResult.AlreadyAwarded -> confirm(questId, null)
                        PointAwardResult.EvidenceChanged -> show(QuestMessage.REFRESH_REQUIRED)
                        PointAwardResult.ConditionNotMet -> show(QuestMessage.CONDITION_NOT_MET)
                        PointAwardResult.InteractionRestricted -> show(QuestMessage.INTERACTION_RESTRICTED)
                        PointAwardResult.QuestUnavailable -> show(QuestMessage.UNSUPPORTED)
                        PointAwardResult.StorageFailure -> show(QuestMessage.STORAGE_FAILURE)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == claimGeneration) show(QuestMessage.STORAGE_FAILURE)
                } finally {
                    if (generation == claimGeneration) mutableState.update { it.copy(pendingQuestId = null) }
                }
            }
    }

    private fun confirm(
        questId: String,
        rewardSuccess: QuestRewardSuccess?,
    ) {
        // Bridge delayed observation without pinning completions after the repository acknowledges them.
        // Keep that acknowledgment even if a reset arrives before this result returns.
        if (!pendingClaimObserved) unobservedConfirmedQuestIds += questId
        mutableState.update {
            it.copy(
                completedPointQuestIds = observedQuestIds + unobservedConfirmedQuestIds,
                rewardSuccess = rewardSuccess,
                message = null,
            )
        }
    }

    private fun show(message: QuestMessage) {
        mutableState.update { it.copy(message = message) }
    }

    fun requestOpenQuest(questId: String) {
        mutableState.update { it.copy(requestedQuestId = questId) }
    }

    fun clearRequestedQuest() {
        mutableState.update { it.copy(requestedQuestId = null) }
    }
}

private data class QuestObservation(
    val completedIds: Set<String>,
    val completionDates: Map<String, Long>,
    val satisfiedIds: Set<String>,
    val evaluation: DriveEvaluationData,
)
