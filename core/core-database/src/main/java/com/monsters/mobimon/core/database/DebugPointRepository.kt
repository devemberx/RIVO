package com.monsters.mobimon.core.database

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.domain.CurrentAppUse
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.UtcClock
import kotlinx.coroutines.CancellationException

enum class DebugPointResult { UPDATED, NO_CHANGE, INTERACTION_RESTRICTED, STORAGE_FAILURE }

class DebugPointRepository(
    private val database: AppDatabase,
    private val profileId: String,
    private val utcClock: UtcClock,
    private val ids: IdGenerator,
    private val appUse: CurrentAppUse,
) {
    suspend fun add(amount: Long): DebugPointResult {
        if (amount <= 0) return DebugPointResult.NO_CHANGE
        return transact {
            val account = account() ?: return@transact DebugPointResult.STORAGE_FAILURE
            if (account.balance > Long.MAX_VALUE - amount) return@transact DebugPointResult.NO_CHANGE
            if (credit(profileId, amount, Long.MAX_VALUE - amount) != 1) {
                throw SQLiteException("Account changed during debug credit")
            }
            insertDebugLedger(amount)
            DebugPointResult.UPDATED
        }
    }

    suspend fun subtract(amount: Long): DebugPointResult {
        if (amount <= 0) return DebugPointResult.NO_CHANGE
        return transact {
            val account = account() ?: return@transact DebugPointResult.STORAGE_FAILURE
            if (account.balance < amount) return@transact DebugPointResult.NO_CHANGE
            if (debit(profileId, amount) != 1) {
                throw SQLiteException("Account changed during debug debit")
            }
            insertDebugLedger(-amount)
            DebugPointResult.UPDATED
        }
    }

    suspend fun reset(): DebugPointResult =
        transact {
            val account = account() ?: return@transact DebugPointResult.STORAGE_FAILURE
            if (account.balance == 0L) return@transact DebugPointResult.NO_CHANGE
            if (account.balance < 0L || debit(profileId, account.balance) != 1) {
                throw SQLiteException("Account changed during debug reset")
            }
            insertDebugLedger(-account.balance)
            DebugPointResult.UPDATED
        }

    suspend fun resetQuestCompletions(): DebugPointResult =
        transact {
            clearQuestCompletions(profileId)
            // Clear the matching award ledger rows too; otherwise the unique (profileId, referenceKey)
            // index survives the reset and makes every subsequent re-claim fail with StorageFailure.
            clearQuestLedger(profileId)
            DebugPointResult.UPDATED
        }

    suspend fun resetStoreInventory(): DebugPointResult =
        transact {
            clearOwnedNonDefault(profileId)
            clearAllEquipped(profileId)
            clearPurchaseLedger(profileId)
            insertOwned(OwnedCosmeticEntity(profileId, "friend:mobi"))
            insertOwned(OwnedCosmeticEntity(profileId, "friend:luna"))
            putEquipped(EquippedCosmeticEntity(profileId, "FRIEND", "friend:mobi"))
            DebugPointResult.UPDATED
        }

    private suspend fun transact(block: suspend PointEconomyDao.() -> DebugPointResult): DebugPointResult =
        try {
            database.withTransaction {
                if (appUse.state() != AppUseState.ALLOWED) {
                    return@withTransaction DebugPointResult.INTERACTION_RESTRICTED
                }
                database.economyDao().block()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SQLiteException) {
            DebugPointResult.STORAGE_FAILURE
        }

    private suspend fun PointEconomyDao.account(): PointAccountEntity? = account(profileId)

    private suspend fun PointEconomyDao.insertDebugLedger(amount: Long) {
        val id = ids.nextId()
        insertLedger(
            PointLedgerEntity(
                id = id,
                profileId = profileId,
                referenceKey = "debug:$id",
                amount = amount,
                occurredAtUtcMillis = utcClock.nowEpochMillis(),
            ),
        )
    }
}
