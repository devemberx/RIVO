package com.monsters.mobimon.runtime

import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.DriveEvaluationData
import com.monsters.mobimon.core.domain.DriveEvidenceAccumulator
import com.monsters.mobimon.core.domain.DriveEvidenceState
import com.monsters.mobimon.core.domain.DriveEvidenceStore
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.EquipResult
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.PointAwardResult
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.PointWallet
import com.monsters.mobimon.core.domain.PurchaseResult
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.UtcClock
import com.monsters.mobimon.core.domain.VehicleEvidenceFrame
import com.monsters.mobimon.core.domain.VehicleObservation
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleSubscriptionState
import com.monsters.mobimon.core.domain.VehicleValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveEvidenceProducerTest {
    private var ids = 0
    private val store = MemoryStore()

    @Test
    fun verifiedDrivePublishesEvidenceAndARestartKeepsTheSameDrive() =
        runTest {
            val vehicle = FakeVehicle(snapshot(1, DrivingState.MOVING, 0.0))
            val economy = RecordingEconomy()
            producer(vehicle, economy, backgroundScope).start()
            runCurrent()
            vehicle.snapshots.value = snapshot(2, DrivingState.MOVING, 6_000.0)
            runCurrent()

            val published = economy.evaluations.last()
            assertEquals(SignalSource.REAL, published.source)
            assertEquals("drive-1", published.driveId)
            assertEquals(6f, published.distanceKm, 0.01f)
            assertEquals("drive-1", store.state?.driveId)

            // A new process resumes the persisted drive instead of minting another per-drive occurrence.
            val restarted = RecordingEconomy()
            producer(FakeVehicle(snapshot(3, DrivingState.MOVING, 7_000.0)), restarted, backgroundScope).start()
            runCurrent()
            assertEquals("drive-1", restarted.evaluations.last().driveId)
            assertEquals(7f, restarted.evaluations.last().distanceKm, 0.01f)
        }

    @Test
    fun simulatedSnapshotsLeaveEvidenceUnpublished() =
        runTest {
            val economy = RecordingEconomy()
            val simulated = snapshot(1, DrivingState.MOVING, 0.0).copy(source = SignalSource.SIMULATED)
            producer(FakeVehicle(simulated), economy, backgroundScope).start()
            runCurrent()
            assertTrue(economy.evaluations.isEmpty())
            assertNull(store.state)
        }

    private fun producer(
        vehicle: VehicleRepository,
        economy: PointEconomy,
        scope: CoroutineScope,
    ) = DriveEvidenceProducer(
        vehicle,
        store,
        economy,
        DriveEvidenceAccumulator(IdGenerator { "drive-${++ids}" }),
        UtcClock { 1_800_000_000_000L },
        scope,
    )

    private fun snapshot(
        sequence: Long,
        state: DrivingState,
        odometer: Double,
    ) = VehicleSnapshot(
        id = "s$sequence",
        epoch = "e",
        sequence = sequence,
        receivedAtMillis = sequence * 2_000,
        source = SignalSource.REAL,
        drivingState = state,
        quality = SignalQuality.VALID,
        evidenceFrame =
            VehicleEvidenceFrame(
                sequence,
                sequence * 2_000,
                mapOf(
                    "Vehicle.TraveledDistance" to
                        VehicleObservation(
                            "Vehicle.TraveledDistance",
                            VehicleValue.Number(odometer),
                            VehicleObservationSource.VSS_ADAPTER,
                            "session",
                            sequence,
                            sequence * 2_000,
                            sequence * 2_000,
                        ),
                ),
                emptyMap(),
                VehicleSubscriptionState("session", true, true, true),
            ),
    )

    private class MemoryStore : DriveEvidenceStore {
        var state: DriveEvidenceState? = null

        override suspend fun read() = state

        override suspend fun write(state: DriveEvidenceState) {
            this.state = state
        }
    }

    private class FakeVehicle(
        initial: VehicleSnapshot,
    ) : VehicleRepository {
        override val snapshots = MutableStateFlow(initial)

        override fun start() = Unit

        override fun stop() = Unit
    }

    private class RecordingEconomy : PointEconomy {
        val evaluations = mutableListOf<DriveEvaluationData>()
        override val wallet = emptyFlow<PointWallet>()
        override val inventory = emptyFlow<CosmeticInventory>()
        override val catalog = emptyFlow<List<CosmeticItem>>()

        override fun updateDriveEvaluation(data: DriveEvaluationData) {
            evaluations += data
        }

        override suspend fun purchase(
            itemId: String,
            expectedPrice: Long,
        ) = PurchaseResult.StorageFailure

        override suspend fun equip(itemId: String) = EquipResult.StorageFailure

        override suspend fun awardQuest(
            questId: String,
            displayedSnapshot: VehicleSnapshot,
        ) = PointAwardResult.StorageFailure
    }
}
