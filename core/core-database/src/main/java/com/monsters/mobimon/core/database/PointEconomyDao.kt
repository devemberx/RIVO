package com.monsters.mobimon.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PointEconomyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccount(account: PointAccountEntity): Long

    @Query("SELECT * FROM point_accounts WHERE profileId = :profileId")
    suspend fun account(profileId: String): PointAccountEntity?

    @Query("SELECT * FROM point_accounts WHERE profileId = :profileId")
    fun observeAccount(profileId: String): Flow<PointAccountEntity?>

    @Query("UPDATE point_accounts SET balance = balance - :amount WHERE profileId = :profileId AND balance >= :amount")
    suspend fun debit(
        profileId: String,
        amount: Long,
    ): Int

    @Query(
        "UPDATE point_accounts SET balance = balance + :amount WHERE profileId = :profileId AND balance <= :maxBalance",
    )
    suspend fun credit(
        profileId: String,
        amount: Long,
        maxBalance: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestCompletion(completion: PointQuestCompletionEntity): Long

    @Query(
        "SELECT * FROM point_quest_completions WHERE profileId = :profileId AND questId = :questId AND occurrenceKey = :occurrenceKey",
    )
    suspend fun questCompletion(
        profileId: String,
        questId: String,
        occurrenceKey: String,
    ): PointQuestCompletionEntity?

    @Query("SELECT * FROM point_quest_completions WHERE profileId = :profileId ORDER BY completedAtUtcMillis")
    suspend fun questCompletions(profileId: String): List<PointQuestCompletionEntity>

    @Query("SELECT * FROM point_quest_completions WHERE profileId = :profileId ORDER BY completedAtUtcMillis")
    fun observeQuestCompletions(profileId: String): Flow<List<PointQuestCompletionEntity>>

    @Query("DELETE FROM point_quest_completions WHERE profileId = :profileId")
    suspend fun clearQuestCompletions(profileId: String): Int

    // Quest award ledger keys are "quest:<id>:<occurrence>"; clearing them alongside completions
    // keeps the reset consistent so the unique (profileId, referenceKey) index cannot block re-claims.
    @Query("DELETE FROM point_ledger WHERE profileId = :profileId AND referenceKey LIKE 'quest:%'")
    suspend fun clearQuestLedger(profileId: String): Int

    // Purchase ledger keys are "purchase:<itemId>"; clearing them alongside owned/equipped cosmetics
    // keeps the reset consistent so the unique (profileId, referenceKey) index cannot block re-purchases.
    @Query("DELETE FROM point_ledger WHERE profileId = :profileId AND referenceKey LIKE 'purchase:%'")
    suspend fun clearPurchaseLedger(profileId: String): Int

    @Insert
    suspend fun insertLedger(entry: PointLedgerEntity)

    @Query("SELECT * FROM point_ledger WHERE profileId = :profileId ORDER BY occurredAtUtcMillis, id")
    suspend fun ledger(profileId: String): List<PointLedgerEntity>

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM point_ledger WHERE profileId = :profileId " +
            "AND amount > 0 AND occurredAtUtcMillis >= :dayStart AND occurredAtUtcMillis < :nextDayStart",
    )
    suspend fun earnedBetween(
        profileId: String,
        dayStart: Long,
        nextDayStart: Long,
    ): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: CosmeticItemEntity): Long

    @Query("SELECT * FROM cosmetic_items")
    fun observeAllItems(): Flow<List<CosmeticItemEntity>>

    @Query("SELECT * FROM cosmetic_items WHERE id = :itemId")
    suspend fun item(itemId: String): CosmeticItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOwned(item: OwnedCosmeticEntity): Long

    @Query("SELECT * FROM owned_cosmetics WHERE profileId = :profileId AND itemId = :itemId")
    suspend fun owned(
        profileId: String,
        itemId: String,
    ): OwnedCosmeticEntity?

    @Query("SELECT * FROM owned_cosmetics WHERE profileId = :profileId")
    fun observeOwned(profileId: String): Flow<List<OwnedCosmeticEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEquipped(item: EquippedCosmeticEntity)

    @Query("SELECT * FROM equipped_cosmetics WHERE profileId = :profileId AND slot = :slot")
    suspend fun equipped(
        profileId: String,
        slot: String,
    ): EquippedCosmeticEntity?

    @Query("SELECT * FROM equipped_cosmetics WHERE profileId = :profileId")
    fun observeEquipped(profileId: String): Flow<List<EquippedCosmeticEntity>>

    @Query("DELETE FROM equipped_cosmetics WHERE profileId = :profileId AND slot = :slot")
    suspend fun deleteEquipped(
        profileId: String,
        slot: String,
    ): Int

    @Query("DELETE FROM equipped_cosmetics WHERE itemId IN ('accessory:necklace', 'accessory:mint_scarf')")
    suspend fun deleteEquippedObsolete(): Int

    @Query("DELETE FROM owned_cosmetics WHERE itemId IN ('accessory:necklace', 'accessory:mint_scarf')")
    suspend fun deleteOwnedObsolete(): Int

    @Query("DELETE FROM cosmetic_items WHERE id IN ('accessory:necklace', 'accessory:mint_scarf')")
    suspend fun deleteCosmeticObsolete(): Int

    @Query(
        "DELETE FROM equipped_cosmetics WHERE profileId = :profileId AND slot != 'FRIEND' AND slot NOT LIKE '%:%' " +
            "AND itemId IN (SELECT id FROM cosmetic_items WHERE compatibleFriendId IS NOT NULL " +
            "AND compatibleFriendId != :friendId)",
    )
    suspend fun unequipIncompatible(
        profileId: String,
        friendId: String,
    )

    @Query("DELETE FROM owned_cosmetics WHERE profileId = :profileId AND itemId NOT IN ('friend:mobi', 'friend:luna')")
    fun clearOwnedNonDefault(profileId: String): Int

    @Query("DELETE FROM equipped_cosmetics WHERE profileId = :profileId")
    fun clearAllEquipped(profileId: String): Int
}
