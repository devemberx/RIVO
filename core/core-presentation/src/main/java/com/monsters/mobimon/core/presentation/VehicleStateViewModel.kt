package com.monsters.mobimon.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.ProgressionIdentity
import com.monsters.mobimon.core.domain.SignalSourceProvider
import com.monsters.mobimon.core.domain.VehicleFreshnessPolicy
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.domain.VehicleSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** One provider emission supplies display freshness and the original command evidence together. */
data class VehicleReading(
    val snapshot: VehicleSnapshot,
    val evidence: VehicleSnapshot,
    /** Decorative VSS time stays separate from command evidence. */
    val backgroundTimeOfDay: String,
)

/** Owns vehicle display freshness independently of quest storage observations. */
class VehicleStateViewModel(
    private val vehicle: VehicleRepository,
    private val identity: ProgressionIdentity,
    private val clock: Clock,
    private val freshness: VehicleFreshnessPolicy,
    private val sourceProvider: SignalSourceProvider = SignalSourceProvider { identity.source },
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(reading(vehicle.snapshots.value, clock.nowMillis()))
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val ticks =
                flow {
                    while (true) {
                        emit(Unit)
                        delay(1_000)
                    }
                }
            combine(vehicle.snapshots, ticks) { snapshot, _ ->
                reading(snapshot, clock.nowMillis())
            }.collect { mutableState.value = it }
        }
    }

    private fun reading(
        snapshot: VehicleSnapshot,
        nowMillis: Long,
    ): VehicleReading =
        VehicleReading(
            snapshot = freshness.displaySnapshot(snapshot, sourceProvider.source(), nowMillis),
            evidence = snapshot,
            backgroundTimeOfDay = snapshot.timeOfDay ?: "Night",
        )
}
