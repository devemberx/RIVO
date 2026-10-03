package com.monsters.mobimon.vehicle

import com.monsters.mobimon.chat.VehicleChatEvidenceReader
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CompanionSettings
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleChatTopic
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleObservationValidity
import com.monsters.mobimon.core.domain.VehicleValue
import com.monsters.mobimon.core.vss.DefaultParkedVssRawVehicleSource
import com.monsters.mobimon.core.vss.ObservedVssRawVehicleSource
import com.monsters.mobimon.core.vss.VehicleObservationRecorder
import com.monsters.mobimon.core.vss.VssObservationFrame
import com.monsters.mobimon.core.vss.VssRawVehicleState
import com.monsters.mobimon.debug.DebugRawVssState
import com.monsters.mobimon.debug.DebugVssState
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

    @Test fun debugSessionRestartAndDerivedTimePreserveProvenance() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.mutableSettings.value = CompanionSettings(debugModeEnabled = true)
            val repository =
                DemoVehicleRepository(
                    Clock {
                        testScheduler.currentTime
                    },
                    IdGenerator { "epoch" },
                    settings,
                    FakeDebugStore(),
                    DefaultParkedVssRawVehicleSource(),
                    backgroundScope,
                )
            repository.start()
            runCurrent()
            val first = repository.snapshots.value.evidenceFrame!!
            assertNotNull(first.observations["Vehicle.Powertrain.TractionBattery.Charging.AveragePower"])
            assertEquals(
                SignalQuality.STALE,
                VehicleObservationValidity.evaluate(first, "interpreted.timeOfDay", 60_001).quality,
            )
            repository.stop()
            repository.start()
            runCurrent()
            org.junit.Assert.assertNotEquals(
                first.subscription.sessionId,
                repository.snapshots.value.evidenceFrame!!
                    .subscription.sessionId,
            )
        }

    @Test fun conflatedDebuggerMutationsKeepPerFieldReceiptTimesAndRawCoverage() {
        val initial = DebugVssState().withObservationReceipts(null, 0)
        val clock =
            initial
                .copy(
                    raw = initial.raw.copy(currentLocationTimestamp = "2026-10-02T10:00:00+09:00"),
                ).withObservationReceipts(initial, 100)
        val battery =
            clock
                .copy(
                    raw = clock.raw.copy(tractionBatterySocDisplayed = 31f),
                ).withObservationReceipts(clock, 200)
        assertEquals(100L, battery.observationReceipts.getValue(VehicleChatFieldCatalog.TIME).receivedAt)
        assertEquals(
            200L,
            battery.observationReceipts
                .getValue(
                    "Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed",
                ).receivedAt,
        )
        val rawFields =
            DebugRawVssState::class.java.declaredFields.filterNot {
                java.lang.reflect.Modifier
                    .isStatic(it.modifiers)
            }
        assertEquals(rawFields.size, battery.raw.chatRawValues().size)
        battery.raw
            .chatRawValues()
            .keys
            .forEach { assertNotNull(VehicleChatFieldCatalog.find(it)) }
    }

    @Test fun framesPreserveConflatedRawAndDerivedTimesAndInvalidateRemovedCards() =
        runTest {
            val initial = DebugVssState().withObservationReceipts(null, 0)
            val clock =
                initial
                    .copy(
                        raw = initial.raw.copy(currentLocationTimestamp = "2026-10-02T15:00:00+09:00"),
                    ).withObservationReceipts(initial, 100)
            val battery =
                clock
                    .copy(
                        raw = clock.raw.copy(tractionBatterySocDisplayed = 31f),
                    ).withObservationReceipts(clock, 200)
            val settings = FakeSettingsRepository()
            settings.mutableSettings.value = CompanionSettings(debugModeEnabled = true)
            val debug = FakeDebugStore()
            debug.mutableState.value = battery
            advanceTimeBy(300)
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
            val first = repository.snapshots.value.evidenceFrame!!
            assertEquals(100L, first.observations.getValue(VehicleChatFieldCatalog.TIME).receivedAtElapsedMillis)
            assertEquals(100L, first.observations.getValue("interpreted.timeOfDay").receivedAtElapsedMillis)
            val health = "Vehicle.Powertrain.TractionBattery.StateOfHealth"
            assertNotNull(first.observations.getValue(health).value)
            debug.mutableState.value =
                battery.copy(cardExtraSignals = battery.cardExtraSignals - health).withObservationReceipts(battery, 300)
            runCurrent()
            assertNull(
                repository.snapshots.value.evidenceFrame!!
                    .observations
                    .getValue(health)
                    .value,
            )
        }

    @Test fun lowFuelReasonSurvivesProjectionAndIsNotAnAllClear() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.mutableSettings.value = CompanionSettings(debugModeEnabled = true)
            val debug = FakeDebugStore()
            debug.mutableState.value = DebugVssState(raw = DebugRawVssState(fuelLevelLow = true))
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
            val capture =
                VehicleChatEvidenceReader({
                    repository.snapshots.value
                }, { testScheduler.currentTime }, { true }).capture(VehicleChatTopic.BASIC)
            assertEquals(
                VehicleValue.Text("NEEDS_REPLENISHMENT"),
                capture.field(VehicleChatFieldCatalog.CONDITION)!!.value,
            )
            org.junit.Assert.assertTrue(
                capture.conditionReasons.any {
                    it.signal ==
                        "Vehicle.Powertrain.FuelSystem.IsFuelLevelLow"
                },
            )
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
