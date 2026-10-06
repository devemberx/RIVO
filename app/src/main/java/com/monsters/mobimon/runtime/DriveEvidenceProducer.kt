package com.monsters.mobimon.runtime

import com.monsters.mobimon.core.domain.DriveEvidenceAccumulator
import com.monsters.mobimon.core.domain.DriveEvidenceState
import com.monsters.mobimon.core.domain.DriveEvidenceStore
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.UtcClock
import com.monsters.mobimon.core.domain.VehicleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Publishes driving quest evidence from verified vehicle observations outside Debugger mode.
 * Snapshots without verified adapter evidence leave the published evaluation untouched.
 */
class DriveEvidenceProducer(
    private val vehicle: VehicleRepository,
    private val store: DriveEvidenceStore,
    private val economy: PointEconomy,
    private val accumulator: DriveEvidenceAccumulator,
    private val utcClock: UtcClock,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                var state = readOrFresh()
                var persisted = state
                var persistedAt = Long.MIN_VALUE
                vehicle.snapshots.collect { snapshot ->
                    val now = utcClock.nowEpochMillis()
                    state = accumulator.accept(state, snapshot, now) ?: return@collect
                    economy.updateDriveEvaluation(accumulator.evaluation(state, now))
                    // Drive boundaries and counters persist at once; distance and belt time at most every 10 s.
                    if (state.withoutProgress() != persisted.withoutProgress() || now - persistedAt >= PERSIST_MILLIS) {
                        if (write(state)) {
                            persisted = state
                            persistedAt = now
                        }
                    }
                }
            }
    }

    private suspend fun readOrFresh(): DriveEvidenceState =
        try {
            store.read() ?: DriveEvidenceState()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DriveEvidenceState()
        }

    private suspend fun write(state: DriveEvidenceState): Boolean =
        try {
            store.write(state)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

    private fun DriveEvidenceState.withoutProgress() =
        copy(lastOdometerMeters = null, beltedMillis = 0, lastElapsedMillis = null, activeEdges = emptySet())

    private companion object {
        const val PERSIST_MILLIS = 10_000L
    }
}
