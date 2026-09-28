package com.monsters.mobimon.core.database

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CurrentAppUse
import com.monsters.mobimon.core.domain.CurrentVehicleEvidence
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.PetAppearance
import com.monsters.mobimon.core.domain.PetProfile
import com.monsters.mobimon.core.domain.PetRepository
import com.monsters.mobimon.core.domain.ProgressionIdentity
import com.monsters.mobimon.core.domain.QuestCommandResult
import com.monsters.mobimon.core.domain.QuestCompletion
import com.monsters.mobimon.core.domain.QuestEvaluator
import com.monsters.mobimon.core.domain.QuestProgress
import com.monsters.mobimon.core.domain.QuestRejection
import com.monsters.mobimon.core.domain.QuestRepository
import com.monsters.mobimon.core.domain.QuestRun
import com.monsters.mobimon.core.domain.QuestStatus
import com.monsters.mobimon.core.domain.QuestType
import com.monsters.mobimon.core.domain.RewardRepository
import com.monsters.mobimon.core.domain.RewardResult
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.SignalSourceProvider
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.WriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.util.concurrent.CancellationException

class RoomCompanionRepository(
    private val database: AppDatabase,
    private val identity: ProgressionIdentity,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val evaluator: QuestEvaluator,
    private val currentVehicle: CurrentVehicleEvidence,
    private val currentAppUse: CurrentAppUse,
    private val sourceProvider: SignalSourceProvider = SignalSourceProvider { identity.source },
) : PetRepository,
    QuestRepository,
    RewardRepository {
    private val dao = database.companionDao()

    override val profile: Flow<PetProfile> =
        dao.observeProfile(identity.profileId).mapNotNull { it?.toDomain() }

    override val progress: Flow<QuestProgress> =
        dao.observeAggregate(identity.profileId).map { aggregate ->
            aggregate?.toProgress() ?: QuestProgress()
        }

    override suspend fun initialize() {
        database.withTransaction {
            dao.insertProfile(
                PetProfileEntity(
                    id = identity.profileId,
                    appearance = PetAppearance.GOLDEN.name,
                    totalXp = 0,
                ),
            )
            val economy = database.economyDao()
            economy.deleteEquippedObsolete()
            economy.deleteOwnedObsolete()
            economy.deleteCosmeticObsolete()
            economy.insertAccount(PointAccountEntity(identity.profileId, 0))
            economy.insertItem(CosmeticItemEntity("friend:mobi", "FRIEND", 0, null))
            economy.insertItem(CosmeticItemEntity("friend:luna", "FRIEND", 0, null))
            economy.insertItem(CosmeticItemEntity("accessory:mobi_headphones", "ACCESSORY", 300, "friend:mobi"))
            economy.insertItem(CosmeticItemEntity("accessory:mobi_goggles", "ACCESSORY", 300, "friend:mobi"))
            economy.insertItem(CosmeticItemEntity("accessory:luna_cap", "ACCESSORY", 300, "friend:luna"))
            economy.insertItem(CosmeticItemEntity("accessory:luna_sunglasses", "ACCESSORY", 300, "friend:luna"))
            economy.insertItem(CosmeticItemEntity("background:star_hanger", "BACKGROUND", 200, null))
            economy.insertItem(CosmeticItemEntity("background:star", "BACKGROUND", 200, null))
            economy.insertItem(CosmeticItemEntity("background:snow", "BACKGROUND", 200, null))
            economy.insertItem(CosmeticItemEntity("background:petal", "BACKGROUND", 200, null))
            economy.insertOwned(OwnedCosmeticEntity(identity.profileId, "friend:mobi"))
            economy.insertOwned(OwnedCosmeticEntity(identity.profileId, "friend:luna"))
            if (economy.equipped(identity.profileId, "FRIEND") == null) {
                economy.putEquipped(EquippedCosmeticEntity(identity.profileId, "FRIEND", "friend:mobi"))
            }
        }
    }

    override suspend fun setAppearance(appearance: PetAppearance): WriteResult =
        try {
            if (dao.updateAppearance(identity.profileId, appearance.name) == 1) {
                WriteResult.Success
            } else {
                WriteResult.Failure
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SQLiteException) {
            WriteResult.Failure
        }

    override suspend fun start(
        type: QuestType,
        snapshot: VehicleSnapshot,
    ): QuestCommandResult =
        try {
            database.withTransaction {
                if (type != QuestType.Q01) {
                    return@withTransaction QuestCommandResult.Rejected(QuestRejection.UNSUPPORTED_QUEST)
                }
                if (dao.profile(identity.profileId) == null) {
                    return@withTransaction QuestCommandResult.StorageFailure
                }
                if (dao.completionForType(identity.profileId, type.name) != null) {
                    return@withTransaction QuestCommandResult.AlreadyCompleted
                }
                if (dao.activeRun(identity.profileId) != null) {
                    return@withTransaction QuestCommandResult.AlreadyActive
                }

                val nowMillis = clock.nowMillis()
                validateCurrentEvidence(snapshot, nowMillis)?.let { rejection ->
                    return@withTransaction QuestCommandResult.Rejected(rejection)
                }
                val expectedSource = sourceProvider.source()
                val run =
                    QuestRun(
                        id = ids.nextId(),
                        profileId = identity.profileId,
                        type = type,
                        status = QuestStatus.ACTIVE,
                        revision = 0,
                        ruleVersion = Q01_RULE_VERSION,
                        rewardXp = Q01_REWARD_XP,
                        startEpoch = snapshot.epoch,
                        startSequence = snapshot.sequence,
                        startedAtMillis = nowMillis,
                        source = expectedSource,
                    )
                dao.insertRun(run.toEntity())
                QuestCommandResult.Started(run)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SQLiteException) {
            QuestCommandResult.StorageFailure
        }

    override suspend fun cancel(
        runId: String,
        expectedRevision: Long,
        snapshot: VehicleSnapshot,
    ): QuestCommandResult =
        try {
            database.withTransaction {
                val run = dao.run(runId)?.toDomain()
                if (run == null || run.profileId != identity.profileId) {
                    return@withTransaction QuestCommandResult.Rejected(QuestRejection.RUN_CHANGED)
                }
                if (run.status != QuestStatus.ACTIVE || run.revision != expectedRevision) {
                    return@withTransaction QuestCommandResult.Rejected(QuestRejection.RUN_CHANGED)
                }
                val expectedSource = sourceProvider.source()
                if (run.source != expectedSource) {
                    return@withTransaction QuestCommandResult.Rejected(QuestRejection.WRONG_SOURCE)
                }
                validateCurrentEvidence(snapshot, clock.nowMillis(), expectedSource)?.let { rejection ->
                    return@withTransaction QuestCommandResult.Rejected(rejection)
                }

                if (
                    dao.finishRun(
                        runId = run.id,
                        profileId = identity.profileId,
                        expectedRevision = expectedRevision,
                        newStatus = QuestStatus.CANCELLED.name,
                    ) != 1
                ) {
                    return@withTransaction QuestCommandResult.Rejected(QuestRejection.RUN_CHANGED)
                }
                QuestCommandResult.Cancelled
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SQLiteException) {
            QuestCommandResult.StorageFailure
        }

    override suspend fun complete(
        runId: String,
        expectedRevision: Long,
        snapshot: VehicleSnapshot,
    ): RewardResult =
        try {
            database.withTransaction {
                val run = dao.run(runId)?.toDomain()
                if (run == null || run.profileId != identity.profileId) {
                    return@withTransaction RewardResult.Rejected(QuestRejection.RUN_CHANGED)
                }
                if (dao.completionForRun(run.id) != null) {
                    return@withTransaction RewardResult.AlreadyAwarded
                }
                if (dao.completionForType(identity.profileId, run.type.name) != null) {
                    return@withTransaction RewardResult.AlreadyAwarded
                }

                val nowMillis = clock.nowMillis()
                val expectedSource = sourceProvider.source()
                validateCurrentEvidence(snapshot, nowMillis, expectedSource)?.let { rejection ->
                    return@withTransaction RewardResult.Rejected(rejection)
                }
                evaluator.evaluate(run, expectedRevision, snapshot, expectedSource, nowMillis)?.let { rejection ->
                    return@withTransaction RewardResult.Rejected(rejection)
                }
                val completion =
                    QuestCompletionEntity(
                        id = ids.nextId(),
                        runId = run.id,
                        profileId = identity.profileId,
                        type = run.type.name,
                        awardedXp = run.rewardXp,
                        completedAtMillis = nowMillis,
                        snapshotId = snapshot.id,
                        snapshotEpoch = snapshot.epoch,
                        snapshotSequence = snapshot.sequence,
                        snapshotSource = snapshot.source.name,
                    )
                if (dao.insertCompletion(completion) == -1L) {
                    if (
                        dao.completionForRun(run.id) != null ||
                        dao.completionForType(identity.profileId, run.type.name) != null
                    ) {
                        return@withTransaction RewardResult.AlreadyAwarded
                    }
                    throw SQLiteConstraintException("Completion identifier already exists")
                }
                if (
                    dao.finishRun(
                        runId = run.id,
                        profileId = identity.profileId,
                        expectedRevision = expectedRevision,
                        newStatus = QuestStatus.COMPLETED.name,
                    ) != 1
                ) {
                    throw RewardTransactionRejected(RewardResult.Rejected(QuestRejection.RUN_CHANGED))
                }
                if (dao.incrementXp(identity.profileId, run.rewardXp) != 1) {
                    throw SQLiteConstraintException("Profile disappeared during reward transaction")
                }
                RewardResult.Applied(completion.toDomain())
            }
        } catch (rejected: RewardTransactionRejected) {
            rejected.result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SQLiteException) {
            RewardResult.StorageFailure
        }

    private fun validateCurrentEvidence(
        supplied: VehicleSnapshot,
        nowMillis: Long,
        expectedSource: SignalSource = sourceProvider.source(),
    ): QuestRejection? {
        if (currentAppUse.state() != AppUseState.ALLOWED) return QuestRejection.APP_USE_RESTRICTED
        evaluator.validateSnapshot(supplied, expectedSource, nowMillis)?.let { return it }
        val latest = currentVehicle.snapshot()
        evaluator.validateSnapshot(latest, expectedSource, nowMillis)?.let { return it }
        if (latest.epoch != supplied.epoch) return QuestRejection.WRONG_EPOCH
        if (latest != supplied) return QuestRejection.RUN_CHANGED
        return null
    }

    private companion object {
        const val Q01_RULE_VERSION = 1
        const val Q01_REWARD_XP = 80
    }
}

private class RewardTransactionRejected(
    val result: RewardResult,
) : RuntimeException()

private fun PetProfileEntity.toDomain() =
    PetProfile(
        id = id,
        appearance = PetAppearance.valueOf(appearance),
        totalXp = totalXp,
    )

private fun QuestRunEntity.toDomain() =
    QuestRun(
        id = id,
        profileId = profileId,
        type = QuestType.valueOf(type),
        status = QuestStatus.valueOf(status),
        revision = revision,
        ruleVersion = ruleVersion,
        rewardXp = rewardXp,
        startEpoch = startEpoch,
        startSequence = startSequence,
        startedAtMillis = startedAtMillis,
        source = SignalSource.valueOf(source),
    )

private fun QuestRun.toEntity() =
    QuestRunEntity(
        id = id,
        profileId = profileId,
        type = type.name,
        status = status.name,
        revision = revision,
        ruleVersion = ruleVersion,
        rewardXp = rewardXp,
        startEpoch = startEpoch,
        startSequence = startSequence,
        startedAtMillis = startedAtMillis,
        source = source.name,
    )

private fun QuestCompletionEntity.toDomain() =
    QuestCompletion(
        id = id,
        runId = runId,
        profileId = profileId,
        type = QuestType.valueOf(type),
        awardedXp = awardedXp,
        completedAtMillis = completedAtMillis,
        snapshotId = snapshotId,
    )

private fun ProfileAggregate.toProgress(): QuestProgress {
    val activeRuns = runs.filter { it.status == QuestStatus.ACTIVE.name }
    check(activeRuns.size <= 1) { "More than one active run exists for profile ${profile.id}" }
    return QuestProgress(
        activeRun = activeRuns.singleOrNull()?.toDomain(),
        completions =
            completions
                .sortedWith(compareBy(QuestCompletionEntity::completedAtMillis, QuestCompletionEntity::id))
                .map(QuestCompletionEntity::toDomain),
    )
}
