package com.monsters.mobimon.core.vss

import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class VssSourceVehicleRepository(
    private val clock: Clock,
    private val ids: IdGenerator,
    private val rawSource: VssRawVehicleSource,
    private val scope: CoroutineScope,
) : VehicleRepository {
    private val mutableSnapshots =
        MutableStateFlow(
            VssVehicleInterpreter.snapshot(
                raw = rawSource.state.value,
                id = INITIAL_SNAPSHOT_ID,
                epoch = INITIAL_SNAPSHOT_EPOCH,
                sequence = 0,
                observedAtMillis = clock.nowMillis(),
                source = SignalSource.REAL,
                timeObservedAtMillis = rawSource.timeObservedAtMillis,
                batteryObservedAtMillis = rawSource.batteryObservedAtMillis,
            ),
        )
    override val snapshots = mutableSnapshots.asStateFlow()
    private var observation: Job? = null
    private var generation = 0L

    @Synchronized
    override fun start() {
        if (observation?.isActive == true) return
        val currentGeneration = ++generation
        val epoch = ids.nextId()
        rawSource.start()
        observation =
            scope.launch {
                var sequence = 0L
                publicationRequests().collect {
                    val nextSequence = ++sequence
                    val snapshot =
                        VssVehicleInterpreter.snapshot(
                            raw = rawSource.state.value,
                            id = "$epoch-$nextSequence",
                            epoch = epoch,
                            sequence = nextSequence,
                            observedAtMillis = clock.nowMillis(),
                            source = SignalSource.REAL,
                            timeObservedAtMillis = rawSource.timeObservedAtMillis,
                            batteryObservedAtMillis = rawSource.batteryObservedAtMillis,
                        )
                    synchronized(this@VssSourceVehicleRepository) {
                        if (generation == currentGeneration) {
                            mutableSnapshots.value = snapshot
                        }
                    }
                }
            }
    }

    private fun publicationRequests() =
        merge(
            flow {
                emit(Unit)
                while (true) {
                    delay(2_000)
                    emit(Unit)
                }
            },
            rawSource.state.drop(1).map { Unit },
        )

    @Synchronized
    override fun stop() {
        generation++
        observation?.cancel()
        observation = null
        rawSource.stop()
        mutableSnapshots.value =
            mutableSnapshots.value.copy(
                quality = SignalQuality.UNAVAILABLE,
                drivingState = DrivingState.UNKNOWN,
                batteryQuality = SignalQuality.UNAVAILABLE,
            )
    }
}

private const val INITIAL_SNAPSHOT_ID = "initial"
private const val INITIAL_SNAPSHOT_EPOCH = "initial"
