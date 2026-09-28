package com.monsters.mobimon.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.ProgressionIdentity
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSourceProvider
import com.monsters.mobimon.core.domain.VehicleFreshnessPolicy
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.domain.VehicleSnapshot

/** All routes share one display ViewModel in the Activity's store, never a provider connection. */
class VehiclePresentation(
    private val vehicle: VehicleRepository,
    private val identity: ProgressionIdentity,
    private val clock: Clock,
    private val freshness: VehicleFreshnessPolicy,
    private val sourceProvider: SignalSourceProvider = SignalSourceProvider { identity.source },
) {
    @Composable
    fun snapshot(): VehicleSnapshot = reading().snapshot

    @Composable
    fun reading(): VehicleReading {
        val factory =
            remember(this) {
                viewModelFactory {
                    initializer {
                        VehicleStateViewModel(
                            vehicle,
                            identity,
                            clock,
                            freshness,
                            sourceProvider = sourceProvider,
                        )
                    }
                }
            }
        val model: VehicleStateViewModel = viewModel(factory = factory)
        val reading by model.state.collectAsStateWithLifecycle()
        return reading
    }
}

val VehicleSnapshot.parkedVerified: Boolean
    get() = quality == SignalQuality.VALID && drivingState == DrivingState.PARKED

/** Display-only badge decision; command authorization continues to use parkedVerified. */
val VehicleSnapshot.parkingBadgeConfirmed: Boolean
    get() = parkedVerified && speed == 0 && gear == "P"
