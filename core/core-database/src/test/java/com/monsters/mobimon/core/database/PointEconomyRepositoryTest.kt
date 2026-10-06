package com.monsters.mobimon.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.CurrentAppUse
import com.monsters.mobimon.core.domain.CurrentVehicleEvidence
import com.monsters.mobimon.core.domain.DefaultPointQuestCatalog
import com.monsters.mobimon.core.domain.DriveEvaluationData
import com.monsters.mobimon.core.domain.DrivingQuestIds
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.EquipResult
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.PointAwardResult
import com.monsters.mobimon.core.domain.PointQuestCatalog
import com.monsters.mobimon.core.domain.PointQuestDefinition
import com.monsters.mobimon.core.domain.PointQuestSchedule
import com.monsters.mobimon.core.domain.PurchaseResult
import com.monsters.mobimon.core.domain.QuestEvaluator
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.UtcClock
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.WeatherCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PointEconomyRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: PointEconomyRepository
    private val ids = AtomicInteger()
    private var appUse = AppUseState.ALLOWED
    private var vehicle =
        VehicleSnapshot("current", "epoch", 1, 10_000, SignalSource.REAL, DrivingState.PARKED, SignalQuality.VALID)
    private var utcNow = 1_800_000_000_000L
    private var catalog: PointQuestDefinition? = null

    @Before
    fun setUp() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            database.companionDao().insertProfile(PetProfileEntity("profile", "GOLDEN", 80))
            database.economyDao().insertAccount(PointAccountEntity("profile", 100))
            repository =
                PointEconomyRepository(
                    database,
                    "profile",
                    UtcClock { utcNow },
                    IdGenerator { "entry-${ids.incrementAndGet()}" },
                    SignalSource.REAL,
                    CurrentVehicleEvidence { vehicle },
                    CurrentAppUse { appUse },
                    Clock { 10_000 },
                    QuestEvaluator(15_000),
                    PointQuestCatalog { id -> catalog?.takeIf { it.id == id } },
                )
        }

    @After
    fun tearDown() {
        database.close()
    }

    private fun defaultCatalogRepository() =
        PointEconomyRepository(
            database,
            "profile",
            UtcClock { utcNow },
            IdGenerator { "entry-${ids.incrementAndGet()}" },
            SignalSource.REAL,
            CurrentVehicleEvidence { vehicle },
            CurrentAppUse { appUse },
            Clock { 10_000 },
            QuestEvaluator(15_000),
            DefaultPointQuestCatalog(),
        )

    @Test
    fun starHangerSeedsPurchasesAppliesAndRemovesForBothFriends() =
        runBlocking {
            val companion =
                RoomCompanionRepository(
                    database,
                    com.monsters.mobimon.core.domain
                        .ProgressionIdentity("profile", SignalSource.REAL),
                    Clock { 10_000 },
                    IdGenerator { "hanger-${ids.incrementAndGet()}" },
                    QuestEvaluator(15_000),
                    CurrentVehicleEvidence { vehicle },
                    CurrentAppUse { appUse },
                )
            companion.initialize()
            val item = repository.catalog.first().single { it.id == "background:star_hanger" }
            assertEquals(CosmeticSlot.BACKGROUND, item.slot)
            assertEquals(200L, item.price)
            assertNull(item.compatibleFriendId)
            database.economyDao().credit("profile", 300, Long.MAX_VALUE - 300)
            assertEquals(PurchaseResult.Purchased(200), repository.purchase(item.id, 200))
            assertNull(repository.inventory.first().equippedItemIds[CosmeticSlot.BACKGROUND])
            assertEquals(PurchaseResult.AlreadyOwned, repository.purchase(item.id, 200))
            assertEquals(EquipResult.Applied, repository.equip(item.id))
            assertEquals(EquipResult.Applied, repository.equip("friend:luna"))
            companion.initialize()
            assertEquals(item.id, repository.inventory.first().equippedItemIds[CosmeticSlot.BACKGROUND])
            assertEquals(200L, repository.wallet.first().balance)
            assertEquals(EquipResult.Applied, repository.equip("none:background_prop"))
            assertNull(repository.inventory.first().equippedItemIds[CosmeticSlot.BACKGROUND])
            assertTrue(item.id in repository.inventory.first().ownedItemIds)
        }

    @Test
    fun yarnBasketSeedsPurchasesAppliesAndRemovesForBothFriends() =
        runBlocking {
            val companion =
                RoomCompanionRepository(
                    database,
                    com.monsters.mobimon.core.domain
                        .ProgressionIdentity("profile", SignalSource.REAL),
                    Clock { 10_000 },
                    IdGenerator { "basket-${ids.incrementAndGet()}" },
                    QuestEvaluator(15_000),
                    CurrentVehicleEvidence { vehicle },
                    CurrentAppUse { appUse },
                )
            companion.initialize()
            val item = repository.catalog.first().single { it.id == "background:starlight_yarn_basket" }
            assertEquals(CosmeticSlot.BACKGROUND, item.slot)
            assertEquals(200L, item.price)
            assertNull(item.compatibleFriendId)
            database.economyDao().credit("profile", 300, Long.MAX_VALUE - 300)
            assertEquals(PurchaseResult.Purchased(200), repository.purchase(item.id, 200))
            assertNull(repository.inventory.first().equippedItemIds[CosmeticSlot.BACKGROUND])
            assertEquals(PurchaseResult.AlreadyOwned, repository.purchase(item.id, 200))
            assertEquals(EquipResult.Applied, repository.equip(item.id))
            assertEquals(EquipResult.Applied, repository.equip("friend:luna"))
            companion.initialize()
            assertEquals(item.id, repository.inventory.first().equippedItemIds[CosmeticSlot.BACKGROUND])
            assertEquals(200L, repository.wallet.first().balance)
            assertEquals(EquipResult.Applied, repository.equip("none:background_prop"))
            assertNull(repository.inventory.first().equippedItemIds[CosmeticSlot.BACKGROUND])
            assertTrue(item.id in repository.inventory.first().ownedItemIds)
        }

    @Test
    fun cyberpunkThemeAndPropOverlayEquipTogetherAndRetainInInventory() =
        runBlocking {
            val companion =
                RoomCompanionRepository(
                    database,
                    com.monsters.mobimon.core.domain
                        .ProgressionIdentity("profile", SignalSource.REAL),
                    Clock { 10_000 },
                    IdGenerator { "bg-${ids.incrementAndGet()}" },
                    QuestEvaluator(15_000),
                    CurrentVehicleEvidence { vehicle },
                    CurrentAppUse { appUse },
                )
            companion.initialize()
            database.economyDao().credit("profile", 1000, Long.MAX_VALUE - 1000)

            val cyberpunk = repository.catalog.first().single { it.id == "background:cyberpunk_city" }
            val hanger = repository.catalog.first().single { it.id == "background:star_hanger" }

            assertEquals(PurchaseResult.Purchased(700), repository.purchase(cyberpunk.id, 400))
            assertEquals(PurchaseResult.Purchased(500), repository.purchase(hanger.id, 200))

            assertEquals(EquipResult.Applied, repository.equip(cyberpunk.id))
            var inv = repository.inventory.first()
            assertEquals("background:cyberpunk_city", inv.equippedItemIds[CosmeticSlot.BACKGROUND])
            assertNull(inv.backgroundPropId)

            assertEquals(EquipResult.Applied, repository.equip(hanger.id))
            inv = repository.inventory.first()
            assertEquals("background:cyberpunk_city", inv.equippedItemIds[CosmeticSlot.BACKGROUND])
            assertEquals("background:star_hanger", inv.backgroundPropId)

            assertEquals(EquipResult.Applied, repository.equip("none:background_prop"))
            inv = repository.inventory.first()
            assertEquals("background:cyberpunk_city", inv.equippedItemIds[CosmeticSlot.BACKGROUND])
            assertNull(inv.backgroundPropId)
        }

    @Test
    fun applyingDefaultBackgroundPreservesEquippedPropsAndEffects() =
        runBlocking {
            val companion =
                RoomCompanionRepository(
                    database,
                    com.monsters.mobimon.core.domain
                        .ProgressionIdentity("profile", SignalSource.REAL),
                    Clock { 10_000 },
                    IdGenerator { "bg-${ids.incrementAndGet()}" },
                    QuestEvaluator(15_000),
                    CurrentVehicleEvidence { vehicle },
                    CurrentAppUse { appUse },
                )
            companion.initialize()
            database.economyDao().credit("profile", 1000, Long.MAX_VALUE - 1000)

            val cyberpunk = repository.catalog.first().single { it.id == "background:cyberpunk_city" }
            val hanger = repository.catalog.first().single { it.id == "background:star_hanger" }
            val star = repository.catalog.first().single { it.id == "background:star" }

            assertEquals(PurchaseResult.Purchased(700), repository.purchase(cyberpunk.id, 400))
            assertEquals(PurchaseResult.Purchased(500), repository.purchase(hanger.id, 200))
            assertEquals(PurchaseResult.Purchased(300), repository.purchase(star.id, 200))

            assertEquals(EquipResult.Applied, repository.equip(cyberpunk.id))
            assertEquals(EquipResult.Applied, repository.equip(hanger.id))
            assertEquals(EquipResult.Applied, repository.equip(star.id))

            var inv = repository.inventory.first()
            assertEquals("background:star_hanger", inv.backgroundPropId)
            assertEquals("background:star", inv.backgroundEffectId)

            assertEquals(EquipResult.Applied, repository.equip("none:background"))
            inv = repository.inventory.first()
            assertEquals("background:star_hanger", inv.backgroundPropId)
            assertEquals("background:star", inv.backgroundEffectId)
        }

    @Test
    fun purchaseChargesOnceAndEquipRequiresASeparateOwnedItemCommand() =
        runBlocking {
            database.economyDao().insertItem(CosmeticItemEntity("hat", CosmeticSlot.ACCESSORY.name, 30, null))

            assertEquals(PurchaseResult.PriceChanged(30), repository.purchase("hat", expectedPrice = 25))
            assertEquals(PurchaseResult.Purchased(70), repository.purchase("hat", expectedPrice = 30))
            assertEquals(PurchaseResult.AlreadyOwned, repository.purchase("hat", expectedPrice = 25))
            assertEquals(70, repository.wallet.first().balance)
            assertEquals(1, database.economyDao().ledger("profile").size)
            assertTrue(database.economyDao().owned("profile", "hat") != null)
            assertNull(database.economyDao().equipped("profile", CosmeticSlot.ACCESSORY.name))

            assertEquals(EquipResult.Applied, repository.equip("hat"))
            assertEquals("hat", database.economyDao().equipped("profile", CosmeticSlot.ACCESSORY.name)?.itemId)
            assertEquals(70, repository.wallet.first().balance)
        }

    @Test
    fun purchaseForOwnedInactiveFriendPreservesEquipmentAndChargesOnce() =
        runBlocking {
            val dao = database.economyDao()
            dao.insertItem(CosmeticItemEntity("friend:mobi", "FRIEND", 0, null))
            dao.insertItem(CosmeticItemEntity("friend:luna", "FRIEND", 0, null))
            dao.insertItem(CosmeticItemEntity("accessory:luna_sunglasses", "ACCESSORY", 30, "friend:luna"))
            dao.insertOwned(OwnedCosmeticEntity("profile", "friend:mobi"))
            dao.insertOwned(OwnedCosmeticEntity("profile", "friend:luna"))
            dao.putEquipped(EquippedCosmeticEntity("profile", "FRIEND", "friend:mobi"))

            assertEquals(PurchaseResult.Purchased(70), repository.purchase("accessory:luna_sunglasses", 30))
            val inventory = repository.inventory.first()
            assertTrue("accessory:luna_sunglasses" in inventory.ownedItemIds)
            assertEquals(mapOf(CosmeticSlot.FRIEND to "friend:mobi"), inventory.equippedItemIds)
            assertTrue(inventory.equippedByFriend.isEmpty())
            assertEquals(PurchaseResult.AlreadyOwned, repository.purchase("accessory:luna_sunglasses", 30))
            assertEquals(70L, repository.wallet.first().balance)
            assertEquals(1, dao.ledger("profile").size)

            assertEquals(EquipResult.Incompatible, repository.equip("accessory:luna_sunglasses"))
            assertEquals(EquipResult.Applied, repository.equip("friend:luna"))
            assertEquals(EquipResult.Applied, repository.equip("accessory:luna_sunglasses"))
            assertEquals(
                "accessory:luna_sunglasses",
                repository.inventory.first().equippedItemIds[CosmeticSlot.ACCESSORY],
            )
            assertEquals(70L, repository.wallet.first().balance)
        }

    @Test
    fun purchaseRejectsAccessoryForFriendOwnedOnlyByAnotherProfile() =
        runBlocking {
            val dao = database.economyDao()
            database.companionDao().insertProfile(PetProfileEntity("other", "GOLDEN", 80))
            dao.insertItem(CosmeticItemEntity("friend:luna", "FRIEND", 0, null))
            dao.insertItem(CosmeticItemEntity("accessory:luna_sunglasses", "ACCESSORY", 30, "friend:luna"))
            dao.insertOwned(OwnedCosmeticEntity("other", "friend:luna"))

            assertEquals(PurchaseResult.Incompatible, repository.purchase("accessory:luna_sunglasses", 30))
            assertEquals(100L, repository.wallet.first().balance)
            assertNull(dao.owned("profile", "accessory:luna_sunglasses"))
            assertTrue(dao.ledger("profile").isEmpty())
        }

    @Test
    fun purchaseRejectsRestrictedOrUnknownInteractionWithoutDebit() =
        runBlocking {
            database.economyDao().insertItem(CosmeticItemEntity("hat", CosmeticSlot.ACCESSORY.name, 30, null))
            appUse = AppUseState.RESTRICTED
            assertEquals(PurchaseResult.InteractionRestricted, repository.purchase("hat", 30))
            appUse = AppUseState.ALLOWED
            vehicle = vehicle.copy(drivingState = DrivingState.UNKNOWN)
            assertEquals(PurchaseResult.InteractionRestricted, repository.purchase("hat", 30))
            assertEquals(100L, repository.wallet.first().balance)
            assertTrue(database.economyDao().ledger("profile").isEmpty())
        }

    @Test
    fun oneTimeAwardCreditsOnceWithEvidenceAndLedger() =
        runBlocking {
            catalog = PointQuestDefinition("welcome", 25, PointQuestSchedule.OneTime)
            assertEquals(PointAwardResult.Awarded(25, 125, "once"), repository.awardQuest("welcome", vehicle))
            assertEquals(PointAwardResult.AlreadyAwarded, repository.awardQuest("welcome", vehicle))
            assertEquals(125L, repository.wallet.first().balance)
            assertEquals(1, database.economyDao().questCompletions("profile").size)
            assertEquals(mapOf("welcome" to utcNow), repository.completedQuestDates.first())
            assertEquals(1, database.economyDao().ledger("profile").size)
        }

    @Test
    fun dailyAwardUsesDefinedZoneAndOccurrenceWhileRejectingChangedEvidence() =
        runBlocking {
            catalog = PointQuestDefinition("daily-check", 10, PointQuestSchedule.Daily("Asia/Seoul"))
            val displayed = vehicle
            vehicle = vehicle.copy(id = "new-card", epoch = "new-session", sequence = 2)
            assertEquals(PointAwardResult.EvidenceChanged, repository.awardQuest("daily-check", displayed))
            val first = repository.awardQuest("daily-check", vehicle)
            assertTrue(first is PointAwardResult.Awarded)
            assertEquals(PointAwardResult.AlreadyAwarded, repository.awardQuest("daily-check", vehicle))
            utcNow += 86_400_000L
            val second = repository.awardQuest("daily-check", vehicle)
            assertTrue(second is PointAwardResult.Awarded)
            assertEquals(120L, repository.wallet.first().balance)
            assertEquals(2, database.economyDao().questCompletions("profile").size)
            assertEquals(mapOf("daily-check" to utcNow), repository.completedQuestDates.first())
        }

    @Test
    fun refreshedParkedEvidenceAwardsOnceAndRecordsCurrentObservation() =
        runBlocking {
            catalog = PointQuestDefinition("welcome", 25, PointQuestSchedule.OneTime)
            val displayed = vehicle.copy(receivedAtMillis = 9_000)
            vehicle = vehicle.copy(id = "refresh", sequence = 2, batteryPercent = 75)
            assertTrue(repository.awardQuest("welcome", displayed) is PointAwardResult.Awarded)
            assertEquals(PointAwardResult.AlreadyAwarded, repository.awardQuest("welcome", displayed))
            val completion = database.economyDao().questCompletions("profile").single()
            assertEquals(vehicle.id, completion.snapshotId)
            assertEquals(vehicle.sequence, completion.snapshotSequence)
            assertEquals(125L, repository.wallet.first().balance)
            assertEquals(1, database.economyDao().ledger("profile").size)
        }

    @Test
    fun refreshedEvidenceStillRejectsInvalidDisplayAndRestrictedCurrentState() =
        runBlocking {
            catalog = PointQuestDefinition("welcome", 25, PointQuestSchedule.OneTime)
            val displayed = vehicle
            listOf(
                displayed.copy(source = SignalSource.SIMULATED),
                displayed.copy(quality = SignalQuality.STALE),
                displayed.copy(drivingState = DrivingState.UNKNOWN),
                displayed.copy(sequence = 2),
                displayed.copy(receivedAtMillis = 10_001),
            ).forEach {
                assertEquals(PointAwardResult.EvidenceChanged, repository.awardQuest("welcome", it))
            }
            listOf(
                displayed.copy(drivingState = DrivingState.MOVING),
                displayed.copy(quality = SignalQuality.UNAVAILABLE),
                displayed.copy(source = SignalSource.SIMULATED),
            ).forEach {
                vehicle = it
                assertEquals(PointAwardResult.InteractionRestricted, repository.awardQuest("welcome", displayed))
            }
            vehicle = displayed.copy(id = "refresh", sequence = 2)
            appUse = AppUseState.RESTRICTED
            assertEquals(PointAwardResult.InteractionRestricted, repository.awardQuest("welcome", displayed))
            assertEquals(100L, repository.wallet.first().balance)
            assertTrue(database.economyDao().questCompletions("profile").isEmpty())
            assertTrue(database.economyDao().ledger("profile").isEmpty())
        }

    @Test
    fun weeklyAndPerDriveAndCappedDailyAwardsGenerateCorrectOccurrenceKeys() =
        runBlocking {
            catalog = PointQuestDefinition("weekly-bonus", 50, PointQuestSchedule.Weekly("Asia/Seoul"))
            val weeklyResult = repository.awardQuest("weekly-bonus", vehicle)
            assertTrue(weeklyResult is PointAwardResult.Awarded)
            assertTrue((weeklyResult as PointAwardResult.Awarded).occurrenceKey.startsWith("weekly:"))
            assertEquals(PointAwardResult.AlreadyAwarded, repository.awardQuest("weekly-bonus", vehicle))

            catalog = PointQuestDefinition("drive-seatbelt", 5, PointQuestSchedule.PerDrive)
            assertEquals(PointAwardResult.ConditionNotMet, repository.awardQuest("drive-seatbelt", vehicle))
            repository.updateDriveEvaluation(DriveEvaluationData(driveId = "drive-101"))
            val driveResult = repository.awardQuest("drive-seatbelt", vehicle)
            assertEquals(PointAwardResult.Awarded(5, 155, "drive:drive-101"), driveResult)
            assertEquals(PointAwardResult.AlreadyAwarded, repository.awardQuest("drive-seatbelt", vehicle))

            // Without a counted source, each claim adds one unit until the daily cap.
            catalog = PointQuestDefinition("turn-signal", 1, PointQuestSchedule.CappedDaily("Asia/Seoul", 2))
            val signal1 = repository.awardQuest("turn-signal", vehicle)
            assertTrue((signal1 as PointAwardResult.Awarded).occurrenceKey.endsWith(":count:1"))
            val signal2 = repository.awardQuest("turn-signal", vehicle)
            assertTrue((signal2 as PointAwardResult.Awarded).occurrenceKey.endsWith(":count:2"))
            assertEquals(PointAwardResult.AlreadyAwarded, repository.awardQuest("turn-signal", vehicle))
            assertEquals(157L, repository.wallet.first().balance)
        }

    @Test
    fun completedQuestIdsFollowTheCurrentDailyOccurrence() =
        runBlocking {
            catalog = PointQuestDefinition("daily-check", 10, PointQuestSchedule.Daily("Asia/Seoul"))
            assertTrue(repository.awardQuest("daily-check", vehicle) is PointAwardResult.Awarded)
            assertEquals(setOf("daily-check"), repository.completedQuestIds.first())
            utcNow += 86_400_000L
            assertTrue(repository.completedQuestIds.first().isEmpty())
            assertTrue(repository.awardQuest("daily-check", vehicle) is PointAwardResult.Awarded)
            assertEquals(setOf("daily-check"), repository.completedQuestIds.first())
        }

    @Test
    fun perDriveQuestReopensForEachNewDrive() =
        runBlocking {
            val pointRepo = defaultCatalogRepository()
            pointRepo.updateDriveEvaluation(DriveEvaluationData(distanceKm = 10f, safeBeltMinutes = 15))
            assertEquals(PointAwardResult.ConditionNotMet, pointRepo.awardQuest(DrivingQuestIds.SEATBELT, vehicle))

            pointRepo.updateDriveEvaluation(DriveEvaluationData(driveId = "a", distanceKm = 10f, safeBeltMinutes = 15))
            assertEquals(
                PointAwardResult.Awarded(5, 105, "drive:a"),
                pointRepo.awardQuest(DrivingQuestIds.SEATBELT, vehicle),
            )
            assertEquals(PointAwardResult.AlreadyAwarded, pointRepo.awardQuest(DrivingQuestIds.SEATBELT, vehicle))
            assertEquals(setOf(DrivingQuestIds.SEATBELT), pointRepo.completedQuestIds.first())

            pointRepo.updateDriveEvaluation(DriveEvaluationData(driveId = "b", distanceKm = 10f, safeBeltMinutes = 15))
            assertTrue(pointRepo.completedQuestIds.first().isEmpty())
            assertEquals(
                PointAwardResult.Awarded(5, 110, "drive:b"),
                pointRepo.awardQuest(DrivingQuestIds.SEATBELT, vehicle),
            )
            assertEquals(2, database.economyDao().ledger("profile").size)
        }

    @Test
    fun turnSignalAwardsNewlyCountedSignalsUpToTheDailyCap() =
        runBlocking {
            val pointRepo = defaultCatalogRepository()

            suspend fun claim(signals: Int): PointAwardResult {
                pointRepo.updateDriveEvaluation(DriveEvaluationData(turnSignalOnCount = signals))
                return pointRepo.awardQuest(DrivingQuestIds.TURN_SIGNAL, vehicle)
            }

            val date = "2027-01-15"
            assertEquals(PointAwardResult.Awarded(3, 103, "daily:$date:count:3"), claim(3))
            assertEquals(PointAwardResult.AlreadyAwarded, claim(3))
            assertEquals(setOf(DrivingQuestIds.TURN_SIGNAL), pointRepo.completedQuestIds.first())
            pointRepo.updateDriveEvaluation(DriveEvaluationData(turnSignalOnCount = 5))
            assertTrue(pointRepo.completedQuestIds.first().isEmpty())
            assertEquals(PointAwardResult.Awarded(2, 105, "daily:$date:count:5"), claim(5))
            assertEquals(PointAwardResult.Awarded(5, 110, "daily:$date:count:10"), claim(12))
            assertEquals(PointAwardResult.AlreadyAwarded, claim(12))
            assertEquals(110L, pointRepo.wallet.first().balance)
            assertEquals(3, database.economyDao().ledger("profile").size)

            utcNow += 86_400_000L
            assertTrue(claim(2) is PointAwardResult.Awarded)
            assertEquals(112L, pointRepo.wallet.first().balance)
        }

    @Test
    fun ledgerFailureRollsBackPurchaseOwnershipAndDebit() =
        runBlocking {
            database.economyDao().insertItem(CosmeticItemEntity("hat", CosmeticSlot.ACCESSORY.name, 30, null))
            database.economyDao().insertLedger(PointLedgerEntity("entry-1", "profile", "collision", 0, utcNow))

            assertEquals(PurchaseResult.StorageFailure, repository.purchase("hat", 30))
            assertEquals(100L, repository.wallet.first().balance)
            assertNull(database.economyDao().owned("profile", "hat"))
        }

    @Test
    fun earnedTodayCountsCreditsEvenAfterPurchase() =
        runBlocking {
            catalog = PointQuestDefinition("welcome", 25, PointQuestSchedule.OneTime)
            assertTrue(repository.awardQuest("welcome", vehicle) is PointAwardResult.Awarded)
            database.economyDao().insertItem(CosmeticItemEntity("hat", CosmeticSlot.ACCESSORY.name, 20, null))
            assertEquals(PurchaseResult.Purchased(105), repository.purchase("hat", 20))
            assertEquals(25L, repository.earnedToday(ZoneId.of("Asia/Seoul")))
            assertEquals(105L, repository.wallet.first().balance)
        }

    @Test
    fun changingFriendRetainsEachFriendsEquipmentWithoutRefund() =
        runBlocking {
            val dao = database.economyDao()
            dao.insertItem(CosmeticItemEntity("friend:mobi", "FRIEND", 0, null))
            dao.insertItem(CosmeticItemEntity("friend:luna", "FRIEND", 0, null))
            dao.insertItem(CosmeticItemEntity("mobi-hat", "ACCESSORY", 20, "friend:mobi"))
            dao.insertItem(CosmeticItemEntity("luna-glasses", "ACCESSORY", 20, "friend:luna"))
            dao.insertOwned(OwnedCosmeticEntity("profile", "friend:mobi"))
            dao.insertOwned(OwnedCosmeticEntity("profile", "friend:luna"))
            dao.insertOwned(OwnedCosmeticEntity("profile", "mobi-hat"))
            dao.insertOwned(OwnedCosmeticEntity("profile", "luna-glasses"))
            assertEquals(EquipResult.Applied, repository.equip("friend:mobi"))
            assertEquals(EquipResult.Applied, repository.equip("mobi-hat"))
            assertEquals(EquipResult.Applied, repository.equip("friend:luna"))
            assertEquals(EquipResult.Applied, repository.equip("luna-glasses"))
            assertNull(dao.equipped("profile", "ACCESSORY"))
            assertEquals("mobi-hat", dao.equipped("profile", "ACCESSORY:friend:mobi")?.itemId)
            assertEquals("luna-glasses", dao.equipped("profile", "ACCESSORY:friend:luna")?.itemId)
            assertEquals("luna-glasses", repository.inventory.first().equippedItemIds[CosmeticSlot.ACCESSORY])
            assertEquals(EquipResult.Applied, repository.equip("friend:mobi"))
            assertEquals("mobi-hat", repository.inventory.first().equippedItemIds[CosmeticSlot.ACCESSORY])
            assertTrue(dao.owned("profile", "mobi-hat") != null)
            assertEquals(100L, repository.wallet.first().balance)
        }

    @Test
    fun defaultCatalogQuestsAwardPointsAndPersistCompletionsWhenParkedAndRejectWhenMoving() =
        runBlocking {
            val defaultCatalog = DefaultPointQuestCatalog()
            val pointRepo =
                PointEconomyRepository(
                    database,
                    "profile",
                    UtcClock { utcNow },
                    IdGenerator { "entry-${ids.incrementAndGet()}" },
                    SignalSource.REAL,
                    CurrentVehicleEvidence { vehicle },
                    CurrentAppUse { appUse },
                    Clock { 10_000 },
                    QuestEvaluator(15_000),
                    defaultCatalog,
                )

            vehicle = vehicle.copy(drivingState = DrivingState.MOVING)
            val movingResult = pointRepo.awardQuest(DrivingQuestIds.SEATBELT, vehicle)
            assertEquals(PointAwardResult.InteractionRestricted, movingResult)
            assertEquals(100L, pointRepo.wallet.first().balance)

            vehicle = vehicle.copy(drivingState = DrivingState.PARKED)
            pointRepo.updateDriveEvaluation(
                DriveEvaluationData(driveId = "drive-1", distanceKm = 10f, safeBeltMinutes = 15),
            )
            val awardResult = pointRepo.awardQuest(DrivingQuestIds.SEATBELT, vehicle)
            assertTrue(awardResult is PointAwardResult.Awarded)
            assertEquals(5L, (awardResult as PointAwardResult.Awarded).points)
            assertEquals(105L, awardResult.resultingBalance)
            assertEquals(105L, pointRepo.wallet.first().balance)

            val account = database.economyDao().account("profile")
            assertEquals(105L, account?.balance)
            val completions = database.economyDao().questCompletions("profile")
            assertTrue(completions.any { it.questId == DrivingQuestIds.SEATBELT })
            val ledger = database.economyDao().ledger("profile")
            assertTrue(ledger.any { it.referenceKey.startsWith("quest:${DrivingQuestIds.SEATBELT}:") })

            val repeatResult = pointRepo.awardQuest(DrivingQuestIds.SEATBELT, vehicle)
            assertEquals(PointAwardResult.AlreadyAwarded, repeatResult)
            assertEquals(105L, pointRepo.wallet.first().balance)
        }

    @Test
    fun simulatedVehicleSnapshotInDebugModeAwardsAndPersistsPointsWhenParked() =
        runBlocking {
            val simVehicle =
                VehicleSnapshot(
                    id = "sim-card-1",
                    epoch = "sim-epoch",
                    sequence = 1,
                    receivedAtMillis = 10_000,
                    source = SignalSource.SIMULATED,
                    drivingState = DrivingState.PARKED,
                    quality = SignalQuality.VALID,
                )
            val defaultCatalog = DefaultPointQuestCatalog()
            val debugPointRepo =
                PointEconomyRepository(
                    database,
                    "profile",
                    UtcClock { utcNow },
                    IdGenerator { "entry-${ids.incrementAndGet()}" },
                    SignalSource.SIMULATED,
                    CurrentVehicleEvidence { simVehicle },
                    CurrentAppUse { appUse },
                    Clock { 10_000 },
                    QuestEvaluator(15_000),
                    defaultCatalog,
                )

            debugPointRepo.updateDriveEvaluation(
                DriveEvaluationData(driveId = "drive-1", distanceKm = 10f, safeDriveScore = 90),
            )
            val awardResult = debugPointRepo.awardQuest(DrivingQuestIds.SAFE_DRIVE, simVehicle)
            assertTrue(awardResult is PointAwardResult.Awarded)
            assertEquals(20L, (awardResult as PointAwardResult.Awarded).points)
            assertEquals(120L, debugPointRepo.wallet.first().balance)
            assertEquals(120L, database.economyDao().account("profile")?.balance)
        }

    @Test
    fun drivingQuestAwardIsGatedOnEvidenceAndDoesNotCreditOrPersistWhenUnsatisfied() =
        runBlocking {
            val defaultCatalog = DefaultPointQuestCatalog()
            val pointRepo =
                PointEconomyRepository(
                    database,
                    "profile",
                    UtcClock { utcNow },
                    IdGenerator { "entry-${ids.incrementAndGet()}" },
                    SignalSource.REAL,
                    CurrentVehicleEvidence { vehicle },
                    CurrentAppUse { appUse },
                    Clock { 10_000 },
                    QuestEvaluator(15_000),
                    defaultCatalog,
                )

            // No drive evidence set: seatbelt condition is unsatisfied, so the award is refused.
            val displayed = vehicle
            vehicle = vehicle.copy(id = "refresh", sequence = 2)
            val refused = pointRepo.awardQuest(DrivingQuestIds.SEATBELT, displayed)
            assertEquals(PointAwardResult.ConditionNotMet, refused)
            assertEquals(100L, pointRepo.wallet.first().balance)
            assertTrue(database.economyDao().questCompletions("profile").isEmpty())

            // Satisfying only the seatbelt evidence unlocks that quest, leaving unrelated ones gated.
            pointRepo.updateDriveEvaluation(
                DriveEvaluationData(driveId = "drive-1", distanceKm = 10f, safeBeltMinutes = 15),
            )
            assertEquals(PointAwardResult.ConditionNotMet, pointRepo.awardQuest(DrivingQuestIds.SAFE_DRIVE, vehicle))
            val awarded = pointRepo.awardQuest(DrivingQuestIds.SEATBELT, vehicle)
            assertTrue(awarded is PointAwardResult.Awarded)
            assertEquals(105L, pointRepo.wallet.first().balance)
            // Replay stays idempotent even though the evidence still satisfies the condition.
            assertEquals(PointAwardResult.AlreadyAwarded, pointRepo.awardQuest(DrivingQuestIds.SEATBELT, vehicle))
            assertEquals(105L, pointRepo.wallet.first().balance)
        }

    @Test
    fun `awardQuest applies weather multiplier to awarded points and ledger`() =
        runBlocking {
            val pointRepo =
                PointEconomyRepository(
                    database,
                    "profile",
                    UtcClock { utcNow },
                    IdGenerator { "entry-${ids.incrementAndGet()}" },
                    SignalSource.REAL,
                    CurrentVehicleEvidence { vehicle },
                    CurrentAppUse { appUse },
                    Clock { 10_000 },
                    QuestEvaluator(15_000),
                    DefaultPointQuestCatalog(),
                )
            val parkedVehicle = vehicle.copy(drivingState = DrivingState.PARKED)
            // Seatbelt base is 5L. In RAIN_OR_SNOW (1.5x), 5 * 1.5 = 7.5 -> 8L.
            pointRepo.updateDriveEvaluation(
                DriveEvaluationData(
                    driveId = "drive-1",
                    distanceKm = 10f,
                    safeBeltMinutes = 15,
                    weather = WeatherCondition.RAIN_OR_SNOW,
                ),
            )
            val awardResult = pointRepo.awardQuest(DrivingQuestIds.SEATBELT, parkedVehicle)
            assertTrue(awardResult is PointAwardResult.Awarded)
            val awarded = awardResult as PointAwardResult.Awarded
            assertEquals(8L, awarded.points)
            assertEquals(5L, awarded.basePoints)
            assertEquals(1.5f, awarded.weatherMultiplier)
            assertEquals(108L, awarded.resultingBalance)
            assertEquals(108L, pointRepo.wallet.first().balance)

            val ledger = database.economyDao().ledger("profile")
            val entry = ledger.first { it.referenceKey.startsWith("quest:${DrivingQuestIds.SEATBELT}:") }
            assertEquals(8L, entry.amount)
        }
}
