package com.monsters.mobimon.core.database

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.monsters.mobimon.core.domain.DriveEvidenceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreDriveEvidenceStoreTest {
    @get:Rule val files = TemporaryFolder()

    @Test
    fun evidenceSurvivesReopeningTheStore() =
        runBlocking {
            val file = files.newFile("evidence.preferences_pb").also { it.delete() }
            val state =
                DriveEvidenceState(
                    driveId = "drive-7",
                    driving = true,
                    startOdometerMeters = 1_000.0,
                    lastOdometerMeters = 4_500.5,
                    completedDistanceKm = 42.5f,
                    beltedMillis = 90_000,
                    lastElapsedMillis = 77,
                    hardAccelCount = 1,
                    laneDepartureCount = 2,
                    observedSignals = setOf("Vehicle.Speed"),
                    activeEdges = setOf("edge:accel"),
                    turnSignalDate = "2027-01-15",
                    turnSignalCount = 4,
                    safeDriveWeek = "2027-W2",
                    safeDriveCount = 3,
                    tireNormalSinceUtcMillis = 123L,
                    raining = true,
                )
            val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val first = DataStoreDriveEvidenceStore(PreferenceDataStoreFactory.create(scope = firstScope) { file })
            assertNull(first.read())
            first.write(state)
            firstScope.coroutineContext[Job]!!.cancelAndJoin()

            val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = DataStoreDriveEvidenceStore(PreferenceDataStoreFactory.create(scope = secondScope) { file })
            // Elapsed time is not meaningful after a restart.
            assertEquals(state.copy(lastElapsedMillis = null), reopened.read())
            secondScope.cancel()
        }
}
