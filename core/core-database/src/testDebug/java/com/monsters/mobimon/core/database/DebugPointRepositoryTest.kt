package com.monsters.mobimon.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.domain.CurrentAppUse
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.UtcClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DebugPointRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val ids = AtomicInteger()
    private var appUse = AppUseState.ALLOWED

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = openDatabase()
            database.companionDao().insertProfile(PetProfileEntity("profile", "GOLDEN", 0))
            database.economyDao().insertAccount(PointAccountEntity("profile", 0))
            database.economyDao().insertItem(CosmeticItemEntity("friend:mobi", "FRIEND", 0, null))
            database.economyDao().insertItem(CosmeticItemEntity("friend:luna", "FRIEND", 0, null))
        }
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun `debug adjustments and ledger remain consistent across repository operations`() =
        runBlocking {
            val repository = subject()

            assertEquals(DebugPointResult.UPDATED, repository.add(100))
            assertEquals(DebugPointResult.UPDATED, repository.subtract(25))

            assertEquals(75L, database.economyDao().account("profile")?.balance)
            assertEquals(75L, database.economyDao().ledger("profile").sumOf { it.amount })

            assertEquals(DebugPointResult.UPDATED, subject().reset())

            assertEquals(0L, database.economyDao().account("profile")?.balance)
            assertEquals(0L, database.economyDao().ledger("profile").sumOf { it.amount })
        }

    @Test
    fun `resetStoreInventory clears non default owned cosmetics and purchase ledger`() =
        runBlocking {
            val repository = subject()
            val dao = database.economyDao()
            dao.insertItem(CosmeticItemEntity("accessory:hat", "ACCESSORY", 100, null))
            dao.insertOwned(OwnedCosmeticEntity("profile", "accessory:hat"))
            dao.putEquipped(EquippedCosmeticEntity("profile", "ACCESSORY", "accessory:hat"))
            dao.insertLedger(PointLedgerEntity("led-purchase", "profile", "purchase:accessory:hat", -100, 1L))

            assertEquals(DebugPointResult.UPDATED, repository.resetStoreInventory())

            assertNotNull(dao.owned("profile", "friend:mobi"))
            assertNotNull(dao.owned("profile", "friend:luna"))
            assertNull(dao.owned("profile", "accessory:hat"))
            assertEquals("friend:mobi", dao.equipped("profile", "FRIEND")?.itemId)
            assertNull(dao.equipped("profile", "ACCESSORY"))
            assertEquals(
                emptyList<PointLedgerEntity>(),
                dao.ledger("profile").filter { it.referenceKey.startsWith("purchase:") },
            )
        }

    @Test
    fun `resetQuestCompletions clears award ledger so a re-claim cannot hit the unique reference index`() =
        runBlocking {
            val dao = database.economyDao()
            dao.insertQuestCompletion(
                PointQuestCompletionEntity(
                    id = "completion-1",
                    profileId = "profile",
                    questId = "quest_seatbelt",
                    occurrenceKey = "once",
                    rewardPoints = 5,
                    completedAtUtcMillis = 1_800_000_000_000L,
                    snapshotId = "snap",
                    snapshotEpoch = "epoch",
                    snapshotSequence = 1,
                    snapshotSource = "SIMULATED",
                ),
            )
            dao.insertLedger(PointLedgerEntity("led-quest", "profile", "quest:quest_seatbelt:once", 5, 1L))
            dao.insertLedger(PointLedgerEntity("led-debug", "profile", "debug:led-debug", 100, 2L))

            assertEquals(DebugPointResult.UPDATED, subject().resetQuestCompletions())

            assertEquals(emptyList<PointQuestCompletionEntity>(), dao.questCompletions("profile"))
            // Only the quest ledger row is removed; unrelated debug ledger entries are preserved.
            assertEquals(listOf("debug:led-debug"), dao.ledger("profile").map { it.referenceKey })
            // The freed reference key can be reused, so a subsequent re-award no longer conflicts.
            dao.insertLedger(PointLedgerEntity("led-quest-2", "profile", "quest:quest_seatbelt:once", 5, 3L))
            assertEquals(2, dao.ledger("profile").size)
        }

    @Test
    fun `restricted app use rejects debug adjustment inside transaction`() =
        runBlocking {
            appUse = AppUseState.RESTRICTED

            assertEquals(DebugPointResult.INTERACTION_RESTRICTED, subject().add(100))
            assertEquals(0L, database.economyDao().account("profile")?.balance)
            assertEquals(emptyList<PointLedgerEntity>(), database.economyDao().ledger("profile"))
        }

    @Test
    fun `ledger failure rolls back debug balance change`() =
        runBlocking {
            val duplicateIds = IdGenerator { "duplicate" }
            val repository = subject(duplicateIds)

            assertEquals(DebugPointResult.UPDATED, repository.add(10))
            assertEquals(DebugPointResult.STORAGE_FAILURE, repository.add(20))

            assertEquals(10L, database.economyDao().account("profile")?.balance)
            assertEquals(listOf(10L), database.economyDao().ledger("profile").map { it.amount })
        }

    private fun subject(idGenerator: IdGenerator = IdGenerator { "debug-${ids.incrementAndGet()}" }) =
        DebugPointRepository(
            database = database,
            profileId = "profile",
            utcClock = UtcClock { 1_800_000_000_000L },
            ids = idGenerator,
            appUse = CurrentAppUse { appUse },
        )

    private fun openDatabase(): AppDatabase =
        Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
}
