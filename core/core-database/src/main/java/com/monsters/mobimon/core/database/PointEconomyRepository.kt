package com.monsters.mobimon.core.database

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.CurrentAppUse
import com.monsters.mobimon.core.domain.CurrentVehicleEvidence
import com.monsters.mobimon.core.domain.DriveEvaluationData
import com.monsters.mobimon.core.domain.DrivingQuestEvaluator
import com.monsters.mobimon.core.domain.EquipResult
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.PointAwardResult
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.PointQuestCatalog
import com.monsters.mobimon.core.domain.PointQuestDefinition
import com.monsters.mobimon.core.domain.PointQuestSchedule
import com.monsters.mobimon.core.domain.PointWallet
import com.monsters.mobimon.core.domain.PurchaseResult
import com.monsters.mobimon.core.domain.QuestEvaluator
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.SignalSourceProvider
import com.monsters.mobimon.core.domain.UtcClock
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.hasCustomBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

class PointEconomyRepository(
    private val database: AppDatabase,
    private val profileId: String,
    private val utcClock: UtcClock,
    private val ids: IdGenerator,
    private val source: SignalSource,
    private val vehicle: CurrentVehicleEvidence,
    private val appUse: CurrentAppUse,
    private val clock: Clock,
    private val evaluator: QuestEvaluator,
    private val quests: PointQuestCatalog,
    private val drivingEvaluator: DrivingQuestEvaluator = DrivingQuestEvaluator(),
    private val sourceProvider: SignalSourceProvider = SignalSourceProvider { source },
) : PointEconomy {
    private val dao = database.economyDao()

    override val wallet =
        dao
            .observeAccount(
                profileId,
            ).mapNotNull { it?.let { account -> PointWallet(account.balance) } }

    override val inventory =
        combine(dao.observeAccount(profileId), dao.observeOwned(profileId), dao.observeEquipped(profileId)) {
                account,
                owned,
                equipped,
            ->
            if (account == null) {
                null
            } else {
                val global =
                    equipped
                        .filter {
                            ':' !in it.slot &&
                                !it.slot.contains("OVERLAY") &&
                                !it.slot.contains("PROP") &&
                                !it.slot.contains("EFFECT")
                        }.associate { CosmeticSlot.valueOf(it.slot) to it.itemId }
                val perFriend =
                    equipped
                        .filter { ':' in it.slot }
                        .groupBy { it.slot.substringAfter(':') }
                        .mapValues { (_, rows) ->
                            rows.associate { CosmeticSlot.valueOf(it.slot.substringBefore(':')) to it.itemId }
                        }
                val activeFriend = global[CosmeticSlot.FRIEND] ?: "friend:mobi"
                val legacyAccessory =
                    if (activeFriend == "friend:mobi") {
                        global[CosmeticSlot.ACCESSORY]?.let { mapOf(CosmeticSlot.ACCESSORY to it) } ?: emptyMap()
                    } else {
                        emptyMap()
                    }
                val bgTheme = equipped.firstOrNull { it.slot == "BACKGROUND" }?.itemId
                val bgProp =
                    equipped
                        .firstOrNull {
                            it.slot == "BACKGROUND_PROP" ||
                                (
                                    it.slot == "BACKGROUND_OVERLAY" &&
                                        it.itemId in setOf("background:star_hanger", "background:starlight_yarn_basket")
                                )
                        }?.itemId
                val bgEffect =
                    equipped
                        .firstOrNull {
                            it.slot == "BACKGROUND_EFFECT" ||
                                (
                                    it.slot == "BACKGROUND_OVERLAY" &&
                                        it.itemId in setOf("background:star", "background:snow", "background:petal")
                                )
                        }?.itemId
                val bgOverlay = bgProp ?: bgEffect
                val effectiveBg = bgTheme ?: bgProp ?: bgEffect
                val finalGlobal =
                    if (effectiveBg != null) {
                        global - CosmeticSlot.ACCESSORY + legacyAccessory + (CosmeticSlot.BACKGROUND to effectiveBg) +
                            (perFriend[activeFriend] ?: emptyMap())
                    } else {
                        global - CosmeticSlot.ACCESSORY - CosmeticSlot.BACKGROUND + legacyAccessory +
                            (perFriend[activeFriend] ?: emptyMap())
                    }
                CosmeticInventory(
                    ownedItemIds = owned.mapTo(mutableSetOf()) { it.itemId },
                    equippedItemIds = finalGlobal,
                    equippedByFriend = perFriend,
                    backgroundOverlayId = bgOverlay,
                    backgroundPropId = bgProp,
                    backgroundEffectId = bgEffect,
                )
            }
        }.mapNotNull { it }

    override val catalog =
        dao.observeAllItems().mapNotNull { items ->
            items.map { it.toDomain() }
        }

    override val completedQuestDates: Flow<Map<String, Long>> =
        dao.observeQuestCompletions(profileId).mapNotNull { items ->
            items.associate { it.questId to it.completedAtUtcMillis }
        }

    private val _driveEvaluation = MutableStateFlow(DriveEvaluationData())
    override val driveEvaluation: Flow<DriveEvaluationData> = _driveEvaluation.asStateFlow()

    // A quest is completed when the occurrence a claim would use now is committed; ticks move day/week resets.
    override val completedQuestIds: Flow<Set<String>> =
        combine(dao.observeQuestCompletions(profileId), _driveEvaluation, occurrenceTicks()) { items, evaluation, _ ->
            val now = utcClock.nowEpochMillis()
            items
                .groupBy({ it.questId }, { it.occurrenceKey })
                .filter { (questId, keys) ->
                    val definition = quests.find(questId) ?: return@filter true
                    val count = drivingEvaluator.evaluateById(questId, evaluation)?.dailyCount
                    definition.currentOccurrence(now, evaluation.driveId, count, keys)?.key in keys
                }.keys
        }.distinctUntilChanged()

    override fun updateDriveEvaluation(data: DriveEvaluationData) {
        _driveEvaluation.value = data
    }

    override suspend fun purchase(
        itemId: String,
        expectedPrice: Long,
    ): PurchaseResult =
        try {
            database.withTransaction {
                if (appUse.state() != AppUseState.ALLOWED ||
                    evaluator.validateSnapshot(vehicle.snapshot(), sourceProvider.source(), clock.nowMillis()) != null
                ) {
                    return@withTransaction PurchaseResult.InteractionRestricted
                }
                val item = dao.item(itemId) ?: return@withTransaction PurchaseResult.ItemUnavailable
                if (dao.owned(profileId, itemId) != null) return@withTransaction PurchaseResult.AlreadyOwned
                if (item.price < 0) return@withTransaction PurchaseResult.ItemUnavailable
                if (item.price != expectedPrice) return@withTransaction PurchaseResult.PriceChanged(item.price)
                if (item.compatibleFriendId != null && dao.owned(profileId, item.compatibleFriendId) == null) {
                    return@withTransaction PurchaseResult.Incompatible
                }
                val account = dao.account(profileId) ?: return@withTransaction PurchaseResult.StorageFailure
                if (account.balance < item.price) {
                    return@withTransaction PurchaseResult.InsufficientPoints(item.price - account.balance)
                }
                if (dao.insertOwned(OwnedCosmeticEntity(profileId, itemId)) == -1L) {
                    return@withTransaction PurchaseResult.AlreadyOwned
                }
                if (dao.debit(profileId, item.price) != 1) {
                    throw SQLiteException("Account balance changed during purchase")
                }
                val purchaseId = ids.nextId()
                dao.insertLedger(
                    PointLedgerEntity(
                        id = purchaseId,
                        profileId = profileId,
                        referenceKey = "purchase:$itemId",
                        amount = -item.price,
                        occurredAtUtcMillis = utcClock.nowEpochMillis(),
                    ),
                )
                PurchaseResult.Purchased(account.balance - item.price)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SQLiteException) {
            PurchaseResult.StorageFailure
        }

    override suspend fun equip(itemId: String): EquipResult =
        try {
            database.withTransaction {
                if (appUse.state() != AppUseState.ALLOWED ||
                    evaluator.validateSnapshot(vehicle.snapshot(), sourceProvider.source(), clock.nowMillis()) != null
                ) {
                    return@withTransaction EquipResult.InteractionRestricted
                }
                if (itemId.startsWith("none")) {
                    val rawSlot = itemId.substringAfter("none:").uppercase().ifEmpty { "ACCESSORY" }
                    val activeFriend = dao.equipped(profileId, "FRIEND")?.itemId ?: "friend:mobi"
                    val storageSlot =
                        if (rawSlot != "FRIEND" &&
                            rawSlot != "BACKGROUND" &&
                            rawSlot != "BACKGROUND_OVERLAY" &&
                            rawSlot != "BACKGROUND_PROP" &&
                            rawSlot != "BACKGROUND_EFFECT"
                        ) {
                            "$rawSlot:$activeFriend"
                        } else {
                            rawSlot
                        }
                    dao.deleteEquipped(profileId, storageSlot)
                    dao.deleteEquipped(profileId, rawSlot)
                    dao.deleteEquipped(profileId, "ACCESSORY")
                    if (rawSlot == "BACKGROUND") {
                        dao.deleteEquipped(profileId, "BACKGROUND_OVERLAY")
                        dao.deleteEquipped(profileId, "BACKGROUND_PROP")
                        dao.deleteEquipped(profileId, "BACKGROUND_EFFECT")
                    }
                    return@withTransaction EquipResult.Applied
                }
                val item = dao.item(itemId) ?: return@withTransaction EquipResult.ItemUnavailable
                if (dao.owned(profileId, itemId) == null) return@withTransaction EquipResult.NotOwned
                if (!item.isCompatible()) return@withTransaction EquipResult.Incompatible
                val slot = CosmeticSlot.valueOf(item.slot)
                val isProp = item.id in setOf("background:star_hanger", "background:starlight_yarn_basket")
                val isEffect = item.id in setOf("background:star", "background:snow", "background:petal")
                val baseSlotName =
                    if (isProp) {
                        "BACKGROUND_PROP"
                    } else if (isEffect) {
                        "BACKGROUND_EFFECT"
                    } else {
                        slot.name
                    }
                val storageSlot =
                    if (item.compatibleFriendId != null && slot != CosmeticSlot.FRIEND) {
                        "$baseSlotName:${item.compatibleFriendId}"
                    } else {
                        baseSlotName
                    }
                if (dao.equipped(profileId, storageSlot)?.itemId == itemId) {
                    return@withTransaction EquipResult.AlreadyApplied
                }
                dao.putEquipped(EquippedCosmeticEntity(profileId, storageSlot, itemId))
                if (slot == CosmeticSlot.FRIEND) dao.unequipIncompatible(profileId, itemId)
                EquipResult.Applied
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SQLiteException) {
            EquipResult.StorageFailure
        }

    override suspend fun unequip(
        slot: CosmeticSlot,
        friendId: String?,
    ): EquipResult = equip("none:${slot.name.lowercase()}")

    override suspend fun awardQuest(
        questId: String,
        displayedSnapshot: VehicleSnapshot,
    ): PointAwardResult =
        try {
            database.withTransaction {
                val definition =
                    quests.find(questId)
                        ?: return@withTransaction PointAwardResult.QuestUnavailable
                if (definition.id != questId || questId.isBlank() || definition.rewardPoints <= 0) {
                    return@withTransaction PointAwardResult.QuestUnavailable
                }
                val current = vehicle.snapshot()
                val expectedSource = sourceProvider.source()
                if (appUse.state() != AppUseState.ALLOWED ||
                    evaluator.validateSnapshot(current, expectedSource, clock.nowMillis()) != null
                ) {
                    return@withTransaction PointAwardResult.InteractionRestricted
                }

                // Gate driving quests on their per-quest evidence; hidden quests rely on appearance.
                val isHiddenQuest = questId.startsWith("quest_hidden_")
                val evaluation = _driveEvaluation.value
                val drivingResult = drivingEvaluator.evaluateById(questId, evaluation)
                if (!isHiddenQuest) {
                    // Publications can advance while the claim waits for the transaction.
                    // Revalidate both observations, retaining source/session and ordering guards.
                    if (evaluator.validateSnapshot(displayedSnapshot, expectedSource, clock.nowMillis()) != null ||
                        current.epoch != displayedSnapshot.epoch ||
                        current.sequence < displayedSnapshot.sequence ||
                        current.receivedAtMillis < displayedSnapshot.receivedAtMillis
                    ) {
                        return@withTransaction PointAwardResult.EvidenceChanged
                    }
                    // Driving evidence must come from the profile's own source, never a Debug simulation in Release.
                    if (drivingResult != null &&
                        (!drivingResult.isSatisfied || evaluation.source != source)
                    ) {
                        return@withTransaction PointAwardResult.ConditionNotMet
                    }
                } else {
                    val activeFriend = dao.equipped(profileId, "FRIEND")?.itemId ?: "friend:mobi"
                    val valid =
                        when (questId) {
                            com.monsters.mobimon.core.domain.DrivingQuestIds.HIDDEN_COSTUME -> {
                                dao.equipped(profileId, "ACCESSORY:$activeFriend") != null ||
                                    dao.equipped(profileId, "ACCESSORY") != null ||
                                    dao.equipped(profileId, "OUTFIT:$activeFriend") != null ||
                                    dao.equipped(profileId, "OUTFIT") != null
                            }
                            com.monsters.mobimon.core.domain.DrivingQuestIds.HIDDEN_BACKGROUND -> {
                                hasCustomBackground(
                                    *BACKGROUND_SLOTS.map { dao.equipped(profileId, it)?.itemId }.toTypedArray(),
                                )
                            }
                            com.monsters.mobimon.core.domain.DrivingQuestIds.HIDDEN_NEW_FRIEND -> {
                                activeFriend != "friend:mobi"
                            }
                            else -> true
                        }
                    if (!valid) return@withTransaction PointAwardResult.ConditionNotMet
                }
                if (definition.schedule == PointQuestSchedule.PerDrive && evaluation.driveId.isBlank()) {
                    return@withTransaction PointAwardResult.ConditionNotMet
                }
                val completedAt = utcClock.nowEpochMillis()
                val claim =
                    definition.currentOccurrence(
                        completedAt,
                        evaluation.driveId,
                        drivingResult?.dailyCount,
                        dao.questOccurrenceKeys(profileId, questId),
                    ) ?: return@withTransaction PointAwardResult.QuestUnavailable
                val occurrence = claim.key
                if (claim.units <= 0 || dao.questCompletion(profileId, questId, occurrence) != null) {
                    return@withTransaction PointAwardResult.AlreadyAwarded
                }
                val capped = definition.schedule is PointQuestSchedule.CappedDaily
                val basePoints =
                    if (capped) {
                        claim.units * definition.rewardPoints
                    } else {
                        drivingResult?.basePoints ?: definition.rewardPoints
                    }
                val awardedPoints =
                    when {
                        drivingResult == null -> basePoints
                        capped -> drivingEvaluator.calculatePoints(basePoints, drivingResult.weatherCondition)
                        else -> drivingResult.earnedPoints
                    }
                val weatherMultiplier = drivingResult?.weatherCondition?.multiplier ?: 1.0f
                val account = dao.account(profileId) ?: return@withTransaction PointAwardResult.StorageFailure
                if (account.balance > Long.MAX_VALUE - awardedPoints) {
                    return@withTransaction PointAwardResult.StorageFailure
                }
                val completionId = ids.nextId()
                val completion =
                    PointQuestCompletionEntity(
                        id = completionId,
                        profileId = profileId,
                        questId = questId,
                        occurrenceKey = occurrence,
                        rewardPoints = awardedPoints,
                        completedAtUtcMillis = completedAt,
                        snapshotId = current.id,
                        snapshotEpoch = current.epoch,
                        snapshotSequence = current.sequence,
                        snapshotSource = current.source.name,
                    )
                if (dao.insertQuestCompletion(completion) == -1L) {
                    return@withTransaction if (dao.questCompletion(profileId, questId, occurrence) != null) {
                        PointAwardResult.AlreadyAwarded
                    } else {
                        PointAwardResult.StorageFailure
                    }
                }
                if (dao.credit(profileId, awardedPoints, Long.MAX_VALUE - awardedPoints) != 1) {
                    throw SQLiteException("Account changed during point award")
                }
                dao.insertLedger(
                    PointLedgerEntity(
                        id = ids.nextId(),
                        profileId = profileId,
                        referenceKey = "quest:$questId:$occurrence",
                        amount = awardedPoints,
                        occurredAtUtcMillis = completedAt,
                    ),
                )
                PointAwardResult.Awarded(
                    points = awardedPoints,
                    resultingBalance = account.balance + awardedPoints,
                    occurrenceKey = occurrence,
                    basePoints = basePoints,
                    weatherMultiplier = weatherMultiplier,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SQLiteException) {
            PointAwardResult.StorageFailure
        }

    suspend fun earnedToday(zoneId: ZoneId): Long {
        val date = Instant.ofEpochMilli(utcClock.nowEpochMillis()).atZone(zoneId).toLocalDate()
        return dao.earnedBetween(
            profileId,
            date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            date
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli(),
        )
    }

    private suspend fun CosmeticItemEntity.isCompatible(): Boolean =
        compatibleFriendId == null ||
            dao.equipped(profileId, CosmeticSlot.FRIEND.name)?.itemId == compatibleFriendId
}

private fun occurrenceTicks() =
    flow {
        while (true) {
            emit(Unit)
            delay(OCCURRENCE_REFRESH_MILLIS)
        }
    }

private const val OCCURRENCE_REFRESH_MILLIS = 60_000L

/** The occurrence a claim would use now; [units] is how many new units it would award. */
private class QuestOccurrence(
    val key: String,
    val units: Int,
)

/**
 * Capped daily keys record the cumulative units awarded that day, so a claim awards only units counted
 * after the last committed key. Returns null when the schedule cannot produce a key.
 */
private fun PointQuestDefinition.currentOccurrence(
    utcMillis: Long,
    driveId: String,
    dailyCount: Int?,
    committedKeys: Collection<String>,
): QuestOccurrence? =
    try {
        when (val schedule = schedule) {
            PointQuestSchedule.OneTime -> QuestOccurrence("once", 1)
            is PointQuestSchedule.Daily -> QuestOccurrence("daily:${localDate(utcMillis, schedule.resetZoneId)}", 1)
            is PointQuestSchedule.Weekly -> {
                val zdt = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of(schedule.resetZoneId))
                val week = zdt.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                val year = zdt.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR)
                QuestOccurrence("weekly:$year-W$week", 1)
            }
            PointQuestSchedule.PerDrive -> driveId.takeIf { it.isNotBlank() }?.let { QuestOccurrence("drive:$it", 1) }
            is PointQuestSchedule.CappedDaily -> {
                val prefix = "daily:${localDate(utcMillis, schedule.resetZoneId)}:count:"
                val awarded =
                    committedKeys
                        .filter { it.startsWith(prefix) }
                        .maxOfOrNull { it.removePrefix(prefix).toIntOrNull() ?: 0 } ?: 0
                val target = (dailyCount ?: (awarded + 1)).coerceAtMost(schedule.maxPerDay)
                QuestOccurrence("$prefix${maxOf(target, awarded)}", target - awarded)
            }
        }
    } catch (_: DateTimeException) {
        null
    }

private fun localDate(
    utcMillis: Long,
    zoneId: String,
) = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of(zoneId)).toLocalDate()

// Theme, prop and effect slots, plus the legacy overlay slot that older builds used for props/effects.
private val BACKGROUND_SLOTS = listOf("BACKGROUND", "BACKGROUND_PROP", "BACKGROUND_EFFECT", "BACKGROUND_OVERLAY")

private fun CosmeticItemEntity.toDomain() =
    com.monsters.mobimon.core.domain.CosmeticItem(
        id = id,
        slot =
            com.monsters.mobimon.core.domain.CosmeticSlot
                .valueOf(slot),
        price = price,
        compatibleFriendId = compatibleFriendId,
    )
