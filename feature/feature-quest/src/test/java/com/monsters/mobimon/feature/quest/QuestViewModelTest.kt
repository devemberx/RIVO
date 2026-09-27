package com.monsters.mobimon.feature.quest

import androidx.lifecycle.ViewModelStore
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.DriveEvaluationData
import com.monsters.mobimon.core.domain.DrivingQuestIds
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.EquipResult
import com.monsters.mobimon.core.domain.PointAwardResult
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.PointWallet
import com.monsters.mobimon.core.domain.PurchaseResult
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val economy = TestEconomy()
    private val displayedSnapshot =
        VehicleSnapshot(
            "displayed",
            "epoch",
            2,
            100,
            SignalSource.SIMULATED,
            DrivingState.PARKED,
            SignalQuality.VALID,
            67,
        )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun runModelTest(block: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                block()
            } finally {
                store.clear()
                runCurrent()
            }
        }

    private fun subject() = QuestViewModel(economy).also { store.put("quest", it) }

    @Test
    fun suspendedClaimPreventsDuplicatesWithoutInventingCompletion() =
        runModelTest {
            economy.gate = CompletableDeferred()
            val vm = subject()
            runCurrent()
            vm.claimPointQuest(DrivingQuestIds.SEATBELT, displayedSnapshot)
            vm.claimPointQuest(DrivingQuestIds.SAFE_DRIVE, displayedSnapshot)
            runCurrent()

            assertEquals(1, economy.awardCalls)
            assertEquals(displayedSnapshot, economy.lastSnapshot)
            assertEquals(DrivingQuestIds.SEATBELT, vm.state.value.pendingQuestId)
            assertTrue(
                vm.state.value.completedPointQuestIds
                    .isEmpty(),
            )
            assertNull(vm.state.value.rewardSuccess)
            assertTrue(
                vm.state.value.dismissedHiddenQuestIds
                    .isEmpty(),
            )
        }

    @Test
    fun committedResultPublishesActualAmountAndSurvivesDelayedObservation() =
        runModelTest {
            economy.result = PointAwardResult.Awarded(17, 117, "verified-occurrence")
            val vm = subject()
            runCurrent()
            vm.claimPointQuest(DrivingQuestIds.SEATBELT, displayedSnapshot)
            runCurrent()

            assertEquals(QuestRewardSuccess(DrivingQuestIds.SEATBELT, 17), vm.state.value.rewardSuccess)
            assertTrue(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)
            assertFalse(vm.state.value.isBusy)
            economy.evaluation.value = DriveEvaluationData(distanceKm = 7f)
            runCurrent()
            assertTrue(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)
            vm.dismissRewardSuccess()
            assertNull(vm.state.value.rewardSuccess)
            assertEquals(1, economy.awardCalls)
        }

    @Test
    fun completionResetClearsClaimAfterRepositoryAcknowledgment() =
        runModelTest {
            val vm = subject()
            runCurrent()
            vm.claimPointQuest(DrivingQuestIds.SEATBELT, displayedSnapshot)
            runCurrent()
            economy.completions.value = setOf(DrivingQuestIds.SEATBELT)
            runCurrent()
            assertTrue(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)

            economy.completions.value = emptySet()
            runCurrent()

            assertFalse(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)
        }

    @Test
    fun observedCompletionDateDisappearsWhenCompletionResets() =
        runModelTest {
            val vm = subject()
            runCurrent()
            economy.completions.value = setOf(DrivingQuestIds.SEATBELT)
            economy.completionDates.value = mapOf(DrivingQuestIds.SEATBELT to 1_800_000_000_000L)
            runCurrent()
            assertEquals(1_800_000_000_000L, vm.state.value.completedPointQuestDates[DrivingQuestIds.SEATBELT])

            economy.completions.value = emptySet()
            runCurrent()
            assertTrue(
                vm.state.value.completedPointQuestDates
                    .isEmpty(),
            )
        }

    @Test
    fun completionResetClearsClaimObservedBeforeItsResultReturns() =
        runModelTest {
            val result = CompletableDeferred<PointAwardResult>()
            economy.gate = result
            val vm = subject()
            runCurrent()
            vm.claimPointQuest(DrivingQuestIds.SEATBELT, displayedSnapshot)
            runCurrent()
            economy.completions.value = setOf(DrivingQuestIds.SEATBELT)
            runCurrent()
            result.complete(PointAwardResult.Awarded(5, 105, "once"))
            runCurrent()
            assertTrue(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)

            economy.completions.value = emptySet()
            runCurrent()

            assertFalse(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)
        }

    @Test
    fun completionResetDuringPendingClaimSurvivesItsLateResult() =
        runModelTest {
            val result = CompletableDeferred<PointAwardResult>()
            economy.gate = result
            val vm = subject()
            runCurrent()
            vm.claimPointQuest(DrivingQuestIds.SEATBELT, displayedSnapshot)
            runCurrent()
            economy.completions.value = setOf(DrivingQuestIds.SEATBELT)
            runCurrent()
            economy.completions.value = emptySet()
            runCurrent()
            assertFalse(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)

            result.complete(PointAwardResult.Awarded(5, 105, "once"))
            runCurrent()
            economy.evaluation.value = DriveEvaluationData(distanceKm = 7f)
            runCurrent()

            assertFalse(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)
        }

    @Test
    fun completionResetClearsAlreadyAwardedReconciliation() =
        runModelTest {
            economy.result = PointAwardResult.AlreadyAwarded
            economy.completions.value = setOf(DrivingQuestIds.SEATBELT)
            val vm = subject()
            runCurrent()
            vm.claimPointQuest(DrivingQuestIds.SEATBELT, displayedSnapshot)
            runCurrent()
            assertTrue(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)

            economy.completions.value = emptySet()
            runCurrent()

            assertFalse(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)
            assertNull(vm.state.value.rewardSuccess)
        }

    @Test
    fun alreadyAwardedReconcilesWithoutCelebratingAnotherCredit() =
        runModelTest {
            economy.result = PointAwardResult.AlreadyAwarded
            val vm = subject()
            runCurrent()
            vm.claimPointQuest(DrivingQuestIds.HIDDEN_NEW_FRIEND, displayedSnapshot)
            runCurrent()

            assertTrue(DrivingQuestIds.HIDDEN_NEW_FRIEND in vm.state.value.completedPointQuestIds)
            assertNull(vm.state.value.rewardSuccess)
            assertNull(vm.state.value.message)
        }

    @Test
    fun failedPointClaimKeepsHiddenQuestAvailableForRetry() =
        runModelTest {
            economy.result = PointAwardResult.StorageFailure
            val vm = subject()
            runCurrent()
            vm.claimPointQuest(DrivingQuestIds.HIDDEN_NEW_FRIEND, displayedSnapshot)
            runCurrent()

            assertEquals(QuestMessage.STORAGE_FAILURE, vm.state.value.message)
            assertFalse(DrivingQuestIds.HIDDEN_NEW_FRIEND in vm.state.value.dismissedHiddenQuestIds)
            assertFalse(DrivingQuestIds.HIDDEN_NEW_FRIEND in vm.state.value.completedPointQuestIds)
            assertNull(vm.state.value.rewardSuccess)
            assertFalse(vm.state.value.isBusy)
            economy.result = PointAwardResult.Awarded(30, 130, "once")
            vm.claimPointQuest(DrivingQuestIds.HIDDEN_NEW_FRIEND, displayedSnapshot)
            runCurrent()
            assertEquals(2, economy.awardCalls)
            assertEquals(
                30,
                vm.state.value.rewardSuccess
                    ?.points
                    ?.toInt(),
            )
        }

    @Test
    fun rejectedEvidenceCannotCreateSuccessOrDismissAHiddenQuest() =
        runModelTest {
            val vm = subject()
            runCurrent()
            for ((result, message) in listOf(
                PointAwardResult.EvidenceChanged to QuestMessage.REFRESH_REQUIRED,
                PointAwardResult.ConditionNotMet to QuestMessage.CONDITION_NOT_MET,
                PointAwardResult.InteractionRestricted to QuestMessage.INTERACTION_RESTRICTED,
                PointAwardResult.QuestUnavailable to QuestMessage.UNSUPPORTED,
            )) {
                economy.result = result
                vm.claimPointQuest(DrivingQuestIds.HIDDEN_NEW_FRIEND, displayedSnapshot)
                runCurrent()
                assertEquals(message, vm.state.value.message)
                assertTrue(
                    vm.state.value.completedPointQuestIds
                        .isEmpty(),
                )
                assertTrue(
                    vm.state.value.dismissedHiddenQuestIds
                        .isEmpty(),
                )
                assertNull(vm.state.value.rewardSuccess)
            }
        }

    @Test
    fun observationFailureRetainsCommittedDataAndRetryHasOneCollector() =
        runModelTest {
            economy.completions.value = setOf(DrivingQuestIds.SEATBELT)
            val vm = subject()
            runCurrent()
            assertTrue(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)
            economy.failObservation.value = true
            runCurrent()
            assertTrue(vm.state.value.observationFailed)
            assertTrue(DrivingQuestIds.SEATBELT in vm.state.value.completedPointQuestIds)
            vm.claimPointQuest(DrivingQuestIds.SAFE_DRIVE, displayedSnapshot)
            assertEquals(0, economy.awardCalls)
            economy.failObservation.value = false
            vm.retry()
            vm.retry()
            runCurrent()
            assertFalse(vm.state.value.observationFailed)
            assertFalse(vm.state.value.isLoading)
            assertEquals(2, economy.observationStarts)
            assertEquals(1, economy.activeObservations)
        }

    @Test
    fun cancellingPendingClaimDoesNotConvertCancellationIntoStorageFailure() =
        runModelTest {
            economy.gate = CompletableDeferred()
            val vm = subject()
            runCurrent()
            vm.claimPointQuest(DrivingQuestIds.SEATBELT, displayedSnapshot)
            runCurrent()
            store.clear()
            runCurrent()
            assertTrue(economy.cancelled)
            assertNull(vm.state.value.rewardSuccess)
            assertNull(vm.state.value.message)
            assertFalse(vm.state.value.isBusy)
        }

    @Test
    fun parkingInterruptionClearsPendingClaimAndCancelsItsWork() =
        runModelTest {
            economy.gate = CompletableDeferred()
            val vm = subject()
            runCurrent()
            vm.claimPointQuest(DrivingQuestIds.SEATBELT, displayedSnapshot)
            runCurrent()
            assertTrue(vm.state.value.isBusy)

            vm.onParkingInterrupted()
            runCurrent()

            assertTrue(economy.cancelled)
            assertFalse(vm.state.value.isBusy)
            assertNull(vm.state.value.rewardSuccess)
            assertNull(vm.state.value.message)
        }

    @Test
    fun drivingEvaluationUpdatesEligibilityWithoutVehicleObservation() =
        runModelTest {
            val vm = subject()
            runCurrent()
            economy.evaluation.value = DriveEvaluationData(distanceKm = 10f, safeBeltMinutes = 15, safeDriveScore = 90)
            runCurrent()
            assertTrue(DrivingQuestIds.SEATBELT in vm.state.value.satisfiedDrivingQuestIds)
            assertTrue(DrivingQuestIds.SAFE_DRIVE in vm.state.value.satisfiedDrivingQuestIds)
            assertEquals(10f, vm.state.value.driveEvaluation.distanceKm)
        }

    @Test
    fun drivingEvaluationUpdatesDriveEvaluationInState() =
        runModelTest {
            val vm = subject()
            runCurrent()
            assertEquals(0f, vm.state.value.driveEvaluation.distanceKm)
            economy.evaluation.value = DriveEvaluationData(distanceKm = 12.5f, safeDriveScore = 95)
            runCurrent()
            assertEquals(12.5f, vm.state.value.driveEvaluation.distanceKm)
            assertEquals(95, vm.state.value.driveEvaluation.safeDriveScore)
        }

    private class TestEconomy : PointEconomy {
        var awardCalls = 0
        var lastSnapshot: VehicleSnapshot? = null
        var result: PointAwardResult = PointAwardResult.Awarded(5, 105, "once")
        var gate: CompletableDeferred<PointAwardResult>? = null
        var cancelled = false
        var observationStarts = 0
        var activeObservations = 0
        val completions = MutableStateFlow<Set<String>>(emptySet())
        val completionDates = MutableStateFlow<Map<String, Long>>(emptyMap())
        val evaluation = MutableStateFlow(DriveEvaluationData())
        val failObservation = MutableStateFlow(false)
        override val wallet = emptyFlow<PointWallet>()
        override val inventory = emptyFlow<CosmeticInventory>()
        override val catalog = emptyFlow<List<CosmeticItem>>()
        override val completedQuestIds =
            flow {
                observationStarts++
                activeObservations++
                try {
                    emitAll(
                        kotlinx.coroutines.flow.combine(completions, failObservation) { ids, fail ->
                            check(!fail) { "Observation unavailable" }
                            ids
                        },
                    )
                } finally {
                    activeObservations--
                }
            }
        override val completedQuestDates = completionDates
        override val driveEvaluation = evaluation

        override suspend fun purchase(
            itemId: String,
            expectedPrice: Long,
        ): PurchaseResult = PurchaseResult.AlreadyOwned

        override suspend fun equip(itemId: String): EquipResult = EquipResult.Applied

        override suspend fun awardQuest(
            questId: String,
            displayedSnapshot: VehicleSnapshot,
        ): PointAwardResult {
            awardCalls++
            lastSnapshot = displayedSnapshot
            return try {
                gate?.await() ?: result
            } catch (cancelled: CancellationException) {
                this.cancelled = true
                throw cancelled
            }
        }
    }
}
