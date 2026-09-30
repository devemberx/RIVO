package com.monsters.mobimon.runtime

import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.domain.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionRuntimeTest {
    @Test
    fun repeatedForegroundNotificationsDoNotDuplicateConnection() {
        val vehicle = RecordingVehicleRepository()
        val appUse = RecordingAppUse()
        val runtime = CompanionRuntime(vehicle, appUse)
        repeat(5) { runtime.start() }
        assertEquals(1, vehicle.starts)
        assertEquals(1, appUse.starts)
        repeat(5) { runtime.stop() }
        assertEquals(1, vehicle.stops)
        assertEquals(1, appUse.stops)
        runtime.start()
        assertEquals(2, vehicle.starts)
        runtime.stop()
    }

    @Test
    fun overlayKeepsVehicleObservationAliveWhileAppIsBackgrounded() {
        val vehicle = RecordingVehicleRepository()
        val appUse = RecordingAppUse()
        val runtime = CompanionRuntime(vehicle, appUse)

        runtime.start()
        runtime.startOverlay()
        runtime.stop()

        assertEquals(1, vehicle.starts)
        assertEquals(0, vehicle.stops)
        assertEquals(1, appUse.stops)

        runtime.stopOverlay()
        assertEquals(1, vehicle.stops)
    }

    private class RecordingAppUse : AppUseLifecycle {
        var starts = 0
        var stops = 0

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }
    }

    private class RecordingVehicleRepository : VehicleRepository {
        override val snapshots =
            MutableStateFlow(
                VehicleSnapshot(
                    "missing",
                    "none",
                    0,
                    0,
                    SignalSource.REAL,
                    DrivingState.UNKNOWN,
                    SignalQuality.UNAVAILABLE,
                ),
            )
        var starts = 0
        var stops = 0

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }
    }
}
