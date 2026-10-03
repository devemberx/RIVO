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
    fun `selected caution cards appear in slot order`() {
        assertEquals(
            listOf("tire", "battery"),
            vehicleCautionAlerts(snapshot, listOf("tire", "charging", "battery", "washer", "environment", "assist"))
                .map { it.id },
        )
    }

    @Test
    fun `unselected caution cards also appear`() {
        assertEquals(
            listOf("tire", "battery"),
            vehicleCautionAlerts(snapshot, listOf("tire", "charging", "washer", "environment", "assist", "distance"))
                .map { it.id },
        )
    }

    @Test
    fun `non-tire cautions outside selected slots appear without duplicate condition alerts`() {
        val signals =
            mapOf(
                "Vehicle.Body.Lights.Beam.Low.IsDefect" to "true",
                "Vehicle.Body.Windshield.Front.WasherFluid.IsLevelLow" to "true",
                "Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow" to "true",
            )
        val current = snapshot.copy(washerFluidLevel = 12, vssCardSignals = signals)

        assertEquals(
            listOf("battery", "tire", "washer", "low-beam"),
            vehicleCautionAlerts(current, VehicleCardSelectionStore.defaults()).map { it.id },
        )
    }

    @Test
    fun `cleared and stale cautions do not appear`() {
        val current =
            snapshot.copy(
                batteryPercent = 72,
                tirePressureStatus = "OK",
                vssCardSignals = mapOf("Vehicle.Body.Lights.Beam.Low.IsDefect" to "false"),
            )
        assertEquals(
            emptyList<VehicleCautionAlert>(),
            vehicleCautionAlerts(current, VehicleCardSelectionStore.defaults()),
        )
        assertEquals(
            emptyList<VehicleCautionAlert>(),
            vehicleCautionAlerts(current.copy(quality = SignalQuality.STALE), VehicleCardSelectionStore.defaults()),
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
