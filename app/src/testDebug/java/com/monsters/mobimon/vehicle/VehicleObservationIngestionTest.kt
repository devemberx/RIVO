package com.monsters.mobimon.vehicle

import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CompanionSettings
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleValue
import com.monsters.mobimon.core.vss.DefaultParkedVssRawVehicleSource
import com.monsters.mobimon.core.vss.ObservedVssRawVehicleSource
import com.monsters.mobimon.core.vss.VehicleObservationRecorder
import com.monsters.mobimon.core.vss.VssObservationFrame
import com.monsters.mobimon.core.vss.VssRawVehicleState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleObservationIngestionTest {
    @Test fun debuggerReceiptsDoNotRefreshOnTicksAndFallbackIsNotEvidence() =
        runTest {
            val settings = FakeSettingsRepository()
            val debug = FakeDebugStore()
            val repository =
                DemoVehicleRepository(
                    Clock {
                        testScheduler.currentTime
                    },
                    IdGenerator { "epoch" },
                    settings,
                    debug,
                    DefaultParkedVssRawVehicleSource(),
                    backgroundScope,
                )
            repository.start()
            runCurrent()
            assertNull(repository.snapshots.value.evidenceFrame)
            settings.mutableSettings.value = CompanionSettings(debugModeEnabled = true)
            runCurrent()
            val first = repository.snapshots.value.evidenceFrame!!
            advanceTimeBy(6_000)
            runCurrent()
            assertEquals(
                first.observations,
                repository.snapshots.value.evidenceFrame!!
                    .observations,
            )
            debug.mutableState.value =
                debug.mutableState.value.copy(
                    raw =
                        debug.mutableState.value.raw
                            .copy(tractionBatterySocDisplayed = 23f),
                )
            runCurrent()
            val battery =
                repository.snapshots.value.evidenceFrame!!
                    .observations
                    .getValue(VehicleChatFieldCatalog.BATTERY)
            assertEquals(6_000L, battery.receivedAtElapsedMillis)
            assertEquals(VehicleObservationSource.DEBUG_OVERRIDE, battery.sourceKind)
        }

    @Test fun atomicFramesPropagateEqualValueReceiptsAndDisconnect() =
        runTest {
            val source =
                object : ObservedVssRawVehicleSource {
                    override val state = MutableStateFlow<VssRawVehicleState?>(null)
                    override val observationFrames = MutableStateFlow<VssObservationFrame?>(null)
                }
            val recorder = VehicleObservationRecorder(VehicleObservationSource.VSS_ADAPTER, "session")
            val raw = VssRawVehicleState()
            source.observationFrames.value =
                VssObservationFrame(raw, recorder.receive(mapOf("battery" to VehicleValue.Number(30.0)), 0))
            val repository =
                DemoVehicleRepository(
                    Clock {
                        testScheduler.currentTime
                    },
                    IdGenerator { "epoch" },
                    FakeSettingsRepository(),
                    FakeDebugStore(),
                    source,
                    backgroundScope,
                )
            repository.start()
            runCurrent()
            assertNotNull(repository.snapshots.value.evidenceFrame)
            advanceTimeBy(50)
            source.observationFrames.value =
                VssObservationFrame(raw, recorder.receive(mapOf("battery" to VehicleValue.Number(30.0)), 50))
            runCurrent()
            assertEquals(
                50L,
                repository.snapshots.value.evidenceFrame!!
                    .observations
                    .getValue("battery")
                    .receivedAtElapsedMillis,
            )
            repository.stop()
            assertEquals(
                false,
                repository.snapshots.value.evidenceFrame!!
                    .subscription.connected,
            )
        }
}
