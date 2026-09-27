package com.monsters.mobimon.core.presentation

import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleWarning
import com.monsters.mobimon.core.domain.WarningSeverity
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleConditionTest {
    private val normal =
        VehicleSnapshot(
            id = "vehicle",
            epoch = "epoch",
            sequence = 1,
            receivedAtMillis = 100,
            source = SignalSource.SIMULATED,
            drivingState = DrivingState.PARKED,
            quality = SignalQuality.VALID,
            batteryPercent = 80,
            tirePressureStatus = "OK",
            isEmergencyBraking = false,
            isDrowsy = false,
            isDistracted = false,
        )

    @Test
    fun sharedConditionPreservesExistingWarningAndFreshnessRules() {
        val warning =
            VehicleWarning(
                "tire",
                severity = WarningSeverity.CAUTION,
                description = "Low pressure",
                nextAction = "Check",
                observedAtMillis = 100,
            )
        assertEquals(VehicleCondition.CHECKED, normal.vehicleCondition())
        assertEquals(VehicleCondition.WARNING, normal.copy(warnings = listOf(warning)).vehicleCondition())
        assertEquals(
            VehicleCondition.CHECKED,
            normal.copy(warnings = listOf(warning.copy(quality = SignalQuality.STALE))).vehicleCondition(),
        )
        assertEquals(
            VehicleCondition.CHECKED,
            normal.copy(warnings = listOf(warning.copy(severity = WarningSeverity.NOTICE))).vehicleCondition(),
        )
        assertEquals(VehicleCondition.WARNING, normal.copy(isDrowsy = true).vehicleCondition())
        assertEquals(
            VehicleCondition.STALE,
            normal.copy(quality = SignalQuality.STALE, isDrowsy = true).vehicleCondition(),
        )
        assertEquals(VehicleCondition.UNAVAILABLE, normal.copy(quality = SignalQuality.UNAVAILABLE).vehicleCondition())
        assertEquals(VehicleCondition.LOW_BATTERY, normal.copy(batteryPercent = 10).vehicleCondition())
        assertEquals(VehicleCondition.PARTIAL, normal.copy(tirePressureStatus = null).vehicleCondition())
    }

    @Test
    fun currentAggregatedTireLowSignalMakesTheCharacterSick() {
        assertEquals(VehicleCondition.WARNING, normal.copy(tirePressureStatus = "NG").vehicleCondition())
        assertEquals(
            VehicleCondition.WARNING,
            normal.copy(tirePressureStatus = "NG", batteryPercent = 10).vehicleCondition(),
        )
        assertEquals(
            VehicleCondition.STALE,
            normal.copy(tirePressureStatus = "NG", quality = SignalQuality.STALE).vehicleCondition(),
        )
    }

    @Test
    fun lowFuelOrChargingLevelMakesTheCharacterHungryAndEngineWarningMakesSick() {
        assertEquals(VehicleCondition.LOW_BATTERY, normal.copy(isFuelLevelLow = true).vehicleCondition())
        assertEquals(
            VehicleCondition.LOW_BATTERY,
            normal
                .copy(
                    vssCardSignals = mapOf("Vehicle.Powertrain.FuelSystem.IsFuelLevelLow" to "true"),
                ).vehicleCondition(),
        )
        assertEquals(VehicleCondition.WARNING, normal.copy(isEngineWarning = true).vehicleCondition())
        // Warning takes precedence over hungry
        assertEquals(
            VehicleCondition.WARNING,
            normal.copy(isEngineWarning = true, isFuelLevelLow = true).vehicleCondition(),
        )
    }

    @Test
    fun cardCautionsChangeTheSharedConditionWithoutDependingOnVisibleSlots() {
        val washerLow = "Vehicle.Body.Windshield.Front.WasherFluid.IsLevelLow"
        val serviceDue = "Vehicle.Service.IsServiceDue"

        assertEquals(VehicleCondition.LOW_BATTERY, normal.copy(washerFluidLevel = 10).vehicleCondition())
        assertEquals(
            VehicleCondition.LOW_BATTERY,
            normal.copy(vssCardSignals = mapOf(washerLow to "true")).vehicleCondition(),
        )
        assertEquals(
            VehicleCondition.WARNING,
            normal.copy(vssCardSignals = mapOf(serviceDue to "true")).vehicleCondition(),
        )
        assertEquals(
            VehicleCondition.WARNING,
            normal.copy(vssCardSignals = mapOf(washerLow to "true", serviceDue to "true")).vehicleCondition(),
        )
        assertEquals(
            VehicleCondition.CHECKED,
            normal.copy(vssCardSignals = mapOf(serviceDue to "false")).vehicleCondition(),
        )
        assertEquals(
            VehicleCondition.STALE,
            normal.copy(quality = SignalQuality.STALE, vssCardSignals = mapOf(serviceDue to "true")).vehicleCondition(),
        )
    }
}
