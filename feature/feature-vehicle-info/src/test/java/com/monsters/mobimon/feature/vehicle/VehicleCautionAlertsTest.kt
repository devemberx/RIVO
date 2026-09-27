package com.monsters.mobimon.feature.vehicle

import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleCautionAlertsTest {
    private val snapshot =
        VehicleSnapshot(
            id = "test",
            epoch = "test",
            sequence = 1,
            receivedAtMillis = 1,
            source = SignalSource.SIMULATED,
            drivingState = DrivingState.PARKED,
            quality = SignalQuality.VALID,
            batteryPercent = 12,
            tirePressureStatus = "NG",
        )

    @Test
    fun `only selected caution cards appear in slot order`() {
        assertEquals(
            listOf("tire", "battery"),
            vehicleCautionAlerts(snapshot, listOf("tire", "charging", "battery", "washer", "environment", "assist"))
                .map { it.id },
        )
    }

    @Test
    fun `unselected caution cards do not appear`() {
        assertEquals(
            listOf("tire"),
            vehicleCautionAlerts(snapshot, listOf("tire", "charging", "washer", "environment", "assist", "distance"))
                .map { it.id },
        )
    }

    @Test
    fun `unavailable readings never create caution alerts`() {
        assertEquals(
            emptyList<VehicleCautionAlert>(),
            vehicleCautionAlerts(snapshot.copy(quality = SignalQuality.UNAVAILABLE), listOf("tire")),
        )
    }
}
