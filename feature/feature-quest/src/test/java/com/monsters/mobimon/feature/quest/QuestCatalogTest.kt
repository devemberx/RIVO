package com.monsters.mobimon.feature.quest

import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.DefaultPointQuestCatalog
import com.monsters.mobimon.core.domain.DriveEvaluationData
import com.monsters.mobimon.core.domain.DrivingQuestIds
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.PointQuestCatalog
import com.monsters.mobimon.core.domain.PointQuestSchedule
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.presentation.CompanionAppearanceState
import com.monsters.mobimon.core.presentation.PointBalanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestCatalogTest {
    private val definitions = DefaultPointQuestCatalog()
    private val catalog = QuestCatalog(definitions)

    @Test
    fun rewardAndScheduleComeFromInjectedCatalog() {
        val overridden =
            QuestCatalog(
                PointQuestCatalog { id ->
                    definitions.find(id)?.let {
                        if (id ==
                            DrivingQuestIds.SEATBELT
                        ) {
                            it.copy(rewardPoints = 91, schedule = PointQuestSchedule.Weekly("UTC"))
                        } else {
                            it
                        }
                    }
                },
            )
        val item =
            overridden
                .present(
                    ready(),
                    CompanionAppearanceState(),
                    PointBalanceState.Ready(0),
                    true,
                    Int::toString,
                ).quests
                .first()
        assertEquals(DrivingQuestIds.SEATBELT, item.id)
        assertEquals(91L, item.rewardPoints)
        assertEquals(R.string.quest_schedule_weekly_short.toString(), item.scheduleText)
    }

    @Test
    fun drivingAndHiddenQuestsUseSeparatePresentationFromCommittedAppearance() {
        val appearance =
            CompanionAppearanceState(
                CosmeticInventory(
                    emptySet(),
                    mapOf(
                        CosmeticSlot.FRIEND to "friend:luna",
                        CosmeticSlot.ACCESSORY to "accessory:luna-cap",
                        CosmeticSlot.BACKGROUND to "background:star",
                    ),
                ),
            )
        val state = catalog.present(ready(), appearance, PointBalanceState.Ready(0), true, Int::toString)
        assertEquals(14, state.quests.size)
        assertEquals(
            setOf(
                DrivingQuestIds.HIDDEN_COSTUME,
                DrivingQuestIds.HIDDEN_BACKGROUND,
                DrivingQuestIds.HIDDEN_NEW_FRIEND,
            ),
            state.hiddenQuests
                .map {
                    it.id
                }.toSet(),
        )
        assertFalse(state.quests.any { it.id == "q01" })
        val completed =
            catalog.present(
                ready().copy(completedPointQuestIds = setOf(DrivingQuestIds.HIDDEN_NEW_FRIEND)),
                appearance,
                PointBalanceState.Ready(30),
                true,
                Int::toString,
            )
        assertFalse(completed.hiddenQuests.any { it.id == DrivingQuestIds.HIDDEN_NEW_FRIEND })
    }

    @Test
    fun hiddenBackgroundQuestFollowsEquippedPropOrEffect() {
        fun hidden(inventory: CosmeticInventory) =
            catalog
                .present(ready(), CompanionAppearanceState(inventory), PointBalanceState.Ready(0), true, Int::toString)
                .hiddenQuests
                .any { it.id == DrivingQuestIds.HIDDEN_BACKGROUND }

        val none = CosmeticInventory(emptySet(), mapOf(CosmeticSlot.FRIEND to "friend:mobi"))
        assertFalse(hidden(none))
        assertTrue(hidden(none.copy(backgroundPropId = "background:star_hanger")))
        assertTrue(hidden(none.copy(backgroundEffectId = "background:snow")))
        assertTrue(
            hidden(
                none.copy(
                    equippedItemIds =
                        none.equippedItemIds + (CosmeticSlot.BACKGROUND to "background:cyberpunk_city"),
                ),
            ),
        )
        assertFalse(
            hidden(
                none.copy(
                    equippedItemIds =
                        none.equippedItemIds + (CosmeticSlot.BACKGROUND to "background:default"),
                ),
            ),
        )
    }

    @Test
    fun eligibilityDoesNotAuthorizeClaimsWhenParkingOrObservationIsUnavailable() {
        val eligible = ready().copy(satisfiedDrivingQuestIds = setOf(DrivingQuestIds.SEATBELT))
        for (state in listOf(
            eligible.copy(isLoading = true),
            eligible.copy(observationFailed = true),
            eligible.copy(pendingQuestId = DrivingQuestIds.SAFE_DRIVE),
        )) {
            val screen =
                catalog.present(
                    state,
                    CompanionAppearanceState(),
                    PointBalanceState.Ready(0),
                    true,
                    Int::toString,
                )
            assertFalse(screen.canClaim)
        }
        val unparked =
            catalog.present(
                eligible,
                CompanionAppearanceState(),
                PointBalanceState.Ready(0),
                false,
                Int::toString,
            )
        assertFalse(unparked.canClaim)
        assertEquals(QuestItemStatus.CLAIMABLE, unparked.quests.first().status)
        val parked =
            catalog.present(
                eligible,
                CompanionAppearanceState(),
                PointBalanceState.Ready(0),
                true,
                Int::toString,
            )
        assertTrue(parked.canClaim)
    }

    @Test
    fun committedObservationWaitsForClaimResultBeforeChangingTheVisibleQuest() {
        val state =
            ready().copy(
                satisfiedDrivingQuestIds = setOf(DrivingQuestIds.SEATBELT),
                completedPointQuestIds = setOf(DrivingQuestIds.SEATBELT, DrivingQuestIds.HIDDEN_NEW_FRIEND),
                pendingQuestId = DrivingQuestIds.SEATBELT,
            )
        val screen = catalog.present(state, CompanionAppearanceState(), PointBalanceState.Ready(0), true, Int::toString)
        assertEquals(QuestItemStatus.CLAIMABLE, screen.quests.first { it.id == DrivingQuestIds.SEATBELT }.status)
        assertFalse(screen.canClaim)
        val confirmed =
            catalog.present(
                state.copy(pendingQuestId = null, rewardSuccess = QuestRewardSuccess(DrivingQuestIds.SEATBELT, 5)),
                CompanionAppearanceState(),
                PointBalanceState.Ready(5),
                true,
                Int::toString,
            )
        assertEquals(QuestItemStatus.COMPLETED, confirmed.quests.first { it.id == DrivingQuestIds.SEATBELT }.status)
    }

    @Test
    fun questsAreOrderedByClaimableThenInProgressThenCompleted() {
        val state =
            ready().copy(
                satisfiedDrivingQuestIds = setOf(DrivingQuestIds.TIRE_CHECK, DrivingQuestIds.MAINTENANCE),
                completedPointQuestIds = setOf(DrivingQuestIds.SEATBELT),
            )
        val screen =
            catalog.present(
                state,
                CompanionAppearanceState(),
                PointBalanceState.Ready(0),
                true,
                Int::toString,
            )
        val quests = screen.quests
        assertEquals(14, quests.size)

        // Claimable quests come first in their catalog order
        assertEquals(DrivingQuestIds.MAINTENANCE, quests[0].id)
        assertEquals(QuestItemStatus.CLAIMABLE, quests[0].status)
        assertEquals(DrivingQuestIds.TIRE_CHECK, quests[1].id)
        assertEquals(QuestItemStatus.CLAIMABLE, quests[1].status)

        // In-progress quests come next
        val inProgressQuests = quests.subList(2, 13)
        assertTrue(inProgressQuests.all { it.status == QuestItemStatus.IN_PROGRESS })
        assertEquals(DrivingQuestIds.SAFE_DRIVE, inProgressQuests.first().id)

        // Completed quests come last
        assertEquals(DrivingQuestIds.SEATBELT, quests.last().id)
        assertEquals(QuestItemStatus.COMPLETED, quests.last().status)
    }

    @Test
    fun allFourteenQuestsBuildExpectedProgressDetailAndButtonVisibility() {
        val evaluation =
            DriveEvaluationData(
                distanceKm = 3.5f,
                continuousDistanceKm = 15.0f,
                totalDistanceKm = 80.0f,
                safeDriveScore = 88,
                hardAccelCount = 1,
                hardBrakeCount = 2,
                overspeedCount = 0,
                turnSignalOnCount = 4,
                laneDepartureCount = 2,
                safeDriveCount = 3,
            )
        val snapshot =
            VehicleSnapshot(
                id = "snap-1",
                epoch = "epoch-1",
                sequence = 1,
                receivedAtMillis = 1000L,
                source = SignalSource.SIMULATED,
                drivingState = DrivingState.PARKED,
                quality = SignalQuality.VALID,
                batteryPercent = 75,
                washerFluidLevel = 50,
                attentionLevel = 80,
            )
        val state =
            catalog.present(
                state = ready().copy(driveEvaluation = evaluation),
                appearance = CompanionAppearanceState(),
                pointBalance = PointBalanceState.Ready(0),
                parkedVerified = true,
                snapshot = snapshot,
                text = Int::toString,
            )
        val byId = state.quests.associateBy { it.id }

        // 1. SEATBELT
        val seatbelt = byId.getValue(DrivingQuestIds.SEATBELT)
        assertFalse(seatbelt.showVehicleStep)
        assertFalse(seatbelt.showExecuteButton)
        assertEquals(QuestProgressDetail.Seatbelt(3.5f, 1.5f), seatbelt.progressDetail)

        // 2. SAFE_DRIVE
        val safeDrive = byId.getValue(DrivingQuestIds.SAFE_DRIVE)
        assertFalse(safeDrive.showVehicleStep)
        assertFalse(safeDrive.showExecuteButton)
        assertEquals(QuestProgressDetail.SafeDrive(88, 3.5f, 1.5f), safeDrive.progressDetail)

        // 3. DISTANCE_100KM
        val dist100 = byId.getValue(DrivingQuestIds.DISTANCE_100KM)
        assertFalse(dist100.showVehicleStep)
        assertFalse(dist100.showExecuteButton)
        assertEquals(QuestProgressDetail.TotalDistance(80.0f), dist100.progressDetail)

        // 4. CLEAN_DRIVE
        val cleanDrive = byId.getValue(DrivingQuestIds.CLEAN_DRIVE)
        assertFalse(cleanDrive.showVehicleStep)
        assertFalse(cleanDrive.showExecuteButton)
        assertEquals(QuestProgressDetail.CleanDrive(1, 2, 0, 3.5f, 1.5f), cleanDrive.progressDetail)

        // 5. FIRST_DRIVE
        val firstDrive = byId.getValue(DrivingQuestIds.FIRST_DRIVE)
        assertFalse(firstDrive.showVehicleStep)
        assertFalse(firstDrive.showExecuteButton)
        assertEquals(QuestProgressDetail.FirstDrive, firstDrive.progressDetail)

        // 6. FOCUS_DRIVE
        val focusDrive = byId.getValue(DrivingQuestIds.FOCUS_DRIVE)
        assertFalse(focusDrive.showVehicleStep)
        assertFalse(focusDrive.showExecuteButton)
        assertEquals(QuestProgressDetail.FocusDrive(15.0f, 15.0f, 20), focusDrive.progressDetail)

        // 7. LANE_KEEP
        val laneKeep = byId.getValue(DrivingQuestIds.LANE_KEEP)
        assertFalse(laneKeep.showVehicleStep)
        assertFalse(laneKeep.showExecuteButton)
        assertEquals(QuestProgressDetail.LaneKeep(15.0f, 15.0f, 2), laneKeep.progressDetail)

        // 8. MAINTENANCE
        val maintenance = byId.getValue(DrivingQuestIds.MAINTENANCE)
        assertFalse(maintenance.showVehicleStep)
        assertFalse(maintenance.showExecuteButton)
        assertEquals(QuestProgressDetail.Maintenance, maintenance.progressDetail)

        // 9. TURN_SIGNAL
        val turnSignal = byId.getValue(DrivingQuestIds.TURN_SIGNAL)
        assertFalse(turnSignal.showVehicleStep)
        assertFalse(turnSignal.showExecuteButton)
        assertEquals(QuestProgressDetail.TurnSignal(4), turnSignal.progressDetail)

        // 10. SAFE_5DAYS
        val safe5 = byId.getValue(DrivingQuestIds.SAFE_5DAYS)
        assertFalse(safe5.showVehicleStep)
        assertFalse(safe5.showExecuteButton)
        assertEquals(QuestProgressDetail.SafeDriveStreak(3), safe5.progressDetail)

        // 11. BATTERY_CARE
        val batteryCare = byId.getValue(DrivingQuestIds.BATTERY_CARE)
        assertTrue(batteryCare.showVehicleStep)
        assertTrue(batteryCare.showExecuteButton)
        assertEquals(QuestProgressDetail.BatteryCare(75), batteryCare.progressDetail)

        // 12. LONG_TRIP_REST
        val longTrip = byId.getValue(DrivingQuestIds.LONG_TRIP_REST)
        assertFalse(longTrip.showVehicleStep)
        assertFalse(longTrip.showExecuteButton)
        assertEquals(QuestProgressDetail.LongTripRest(15.0f, 15.0f, null), longTrip.progressDetail)

        // 13. WASHER_FLUID
        val washer = byId.getValue(DrivingQuestIds.WASHER_FLUID)
        assertFalse(washer.showVehicleStep)
        assertFalse(washer.showExecuteButton)
        assertEquals(QuestProgressDetail.WasherFluid(50), washer.progressDetail)

        // 14. TIRE_CHECK
        val tireCheck = byId.getValue(DrivingQuestIds.TIRE_CHECK)
        assertTrue(tireCheck.showVehicleStep)
        assertTrue(tireCheck.showExecuteButton)
        assertEquals(QuestProgressDetail.TireCheck, tireCheck.progressDetail)
    }

    @Test
    fun snapshotConditionSetsVehicleWarningAndVehicleHungry() {
        val hungrySnapshot =
            VehicleSnapshot(
                id = "snap-1",
                epoch = "epoch-1",
                sequence = 1,
                receivedAtMillis = 1000L,
                source = SignalSource.SIMULATED,
                drivingState = DrivingState.PARKED,
                quality = SignalQuality.VALID,
                batteryPercent = 10,
            )
        val hungryState =
            catalog.present(
                state = ready(),
                appearance = CompanionAppearanceState(),
                pointBalance = PointBalanceState.Ready(0),
                parkedVerified = true,
                snapshot = hungrySnapshot,
                text = Int::toString,
            )
        assertFalse(hungryState.vehicleWarning)
        assertTrue(hungryState.vehicleHungry)

        val warningSnapshot =
            VehicleSnapshot(
                id = "snap-2",
                epoch = "epoch-2",
                sequence = 2,
                receivedAtMillis = 2000L,
                source = SignalSource.SIMULATED,
                drivingState = DrivingState.PARKED,
                quality = SignalQuality.VALID,
                warnings =
                    listOf(
                        com.monsters.mobimon.core.domain.VehicleWarning(
                            "tire",
                            "front-left",
                            com.monsters.mobimon.core.domain.WarningSeverity.CAUTION,
                            "check",
                            "check",
                            1000L,
                        ),
                    ),
            )
        val warningState =
            catalog.present(
                state = ready(),
                appearance = CompanionAppearanceState(),
                pointBalance = PointBalanceState.Ready(0),
                parkedVerified = true,
                snapshot = warningSnapshot,
                text = Int::toString,
            )
        assertTrue(warningState.vehicleWarning)
        assertFalse(warningState.vehicleHungry)
    }

    private fun ready() = QuestUiState(isLoading = false)
}
