package com.monsters.mobimon.chat

import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CompanionSettings
import com.monsters.mobimon.core.domain.CurrentVehicleEvidence
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.core.domain.VehicleFreshnessPolicy
import com.monsters.mobimon.core.domain.WriteResult
import com.monsters.mobimon.core.vss.VssRawVehicleSource
import com.monsters.mobimon.core.vss.VssRawVehicleState
import com.monsters.mobimon.core.vss.generated.VssSignals
import com.monsters.mobimon.debug.DebugVssProvider
import com.monsters.mobimon.debug.DebugVssState
import com.monsters.mobimon.di.AuthenticationModule
import com.monsters.mobimon.vehicle.DemoVehicleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleChatSourceTest {
    @Test fun injectedContextFollowsDebuggerAndAdapterInBothBuildVariants() =
        runTest {
            val settings =
                object : SettingsRepository {
                    override val settings = MutableStateFlow(CompanionSettings())

                    override suspend fun setReducedMotion(enabled: Boolean) = WriteResult.Success

                    override suspend fun setLauncherCharacterEnabled(enabled: Boolean) = WriteResult.Success

                    override suspend fun setDebugModeEnabled(enabled: Boolean): WriteResult {
                        settings.value = settings.value.copy(debugModeEnabled = enabled)
                        return WriteResult.Success
                    }
                }
            val debug =
                object : DebugVssProvider {
                    override val state = MutableStateFlow(DebugVssState())
                }
            val adapter =
                object : VssRawVehicleSource {
                    override val timeObservedAtMillis = 100L
                    override val batteryObservedAtMillis = 100L
                    override val state =
                        MutableStateFlow<VssRawVehicleState?>(
                            VssSignals(
                                currentLocation = VssSignals.CurrentLocation(timestamp = "2026-09-28T14:00:00+09:00"),
                                powertrain =
                                    VssSignals.Powertrain(
                                        tractionBattery =
                                            VssSignals.Powertrain.TractionBattery(
                                                stateOfCharge =
                                                    VssSignals.Powertrain.TractionBattery.StateOfCharge(
                                                        displayed = 82f,
                                                    ),
                                            ),
                                    ),
                            ),
                        )
                }
            val clock = Clock { testScheduler.currentTime + 100 }
            val repository =
                DemoVehicleRepository(clock, IdGenerator { "test" }, settings, debug, adapter, backgroundScope)
            val source =
                AuthenticationModule.conversationContext(
                    ApplicationProvider.getApplicationContext(),
                    CurrentVehicleEvidence { repository.snapshots.value },
                    clock,
                    settings,
                    VehicleFreshnessPolicy(15_000),
                )
            repository.start()
            runCurrent()
            assertEquals(82, source.current().batteryPercent)
            assertEquals("2026-09-28T14:00:00+09:00", source.current().vssTimestamp)
            assertFalse(source.current().simulatedTime)
            settings.setDebugModeEnabled(true)
            assertNull(source.current().batteryPercent)
            runCurrent()
            debug.state.value =
                debug.state.value.copy(
                    raw =
                        debug.state.value.raw.copy(
                            tractionBatterySocDisplayed = 23f,
                            currentLocationTimestamp = "2026-09-28T18:00:00+09:00",
                        ),
                )
            runCurrent()
            assertEquals(23, source.current().batteryPercent)
            assertEquals("2026-09-28T18:00:00+09:00", source.current().vssTimestamp)
            assertTrue(source.current().simulatedBattery)
            debug.state.value =
                debug.state.value.copy(
                    raw =
                        debug.state.value.raw
                            .copy(tractionBatterySocDisplayed = -1f),
                )
            runCurrent()
            assertNull(source.current().batteryPercent)
            settings.setDebugModeEnabled(false)
            assertNull(source.current().batteryPercent)
            runCurrent()
            assertEquals(82, source.current().batteryPercent)
            advanceTimeBy(16_000)
            runCurrent()
            assertNull(source.current().batteryPercent)
            adapter.state.value = null
            runCurrent()
            assertNull(source.current().vssTimestamp)
            assertNull(source.current().batteryPercent)
            repository.stop()
        }
}
