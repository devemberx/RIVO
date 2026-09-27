package com.monsters.mobimon.feature.vehicle

import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleCardVssDefaults
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.presentation.VehicleCondition
import com.monsters.mobimon.core.presentation.vehicleCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCardCatalogTest {
    @Test
    fun initialSlotsMatchTheSixFigmaCards() {
        assertEquals(
            listOf("battery", "charging", "tire", "washer", "environment", "assist"),
            VehicleCardCatalog.defaultSlots.map { it.id },
        )
    }

    @Test
    fun catalogOffersThirtyDistinctVssMappedCards() {
        val cards = VehicleCardCatalog.cards
        assertEquals(30, cards.size)
        assertEquals(30, cards.map { it.id }.distinct().size)
        cards.forEach { card ->
            assertEquals(true, card.vssPaths.isNotEmpty())
            assertEquals(true, card.vssPaths.all { it.startsWith("Vehicle.") })
        }
        assertEquals(4, cards.first { it.id == "brake-fluid" }.vssPaths.size)
        assertEquals(4, cards.first { it.id == "pad-wear" }.vssPaths.size)
        assertEquals(4, cards.first { it.id == "pad-warning" }.vssPaths.size)
        assertEquals(4, cards.first { it.id == "tire-low" }.vssPaths.size)
    }

    @Test
    fun missingOrStaleSignalsNeverBecomeHealthyExamples() {
        val unavailable = snapshot(SignalQuality.UNAVAILABLE)
        val stale = snapshot(SignalQuality.STALE).copy(batteryPercent = 82, washerFluidLevel = 68)
        assertNull(VehicleCardCatalog.reading("battery", unavailable)?.value)
        assertNull(VehicleCardCatalog.reading("washer", stale)?.value)
        assertNull(VehicleCardCatalog.reading("battery-health", snapshot())?.value)
        assertNull(VehicleCardCatalog.reading("brake-fluid", snapshot())?.value)
    }

    @Test
    fun supportedSignalsUseCurrentSnapshotValues() {
        val current =
            snapshot().copy(
                batteryPercent = 18,
                isCharging = true,
                tirePressureStatus = "NG",
                outsideTemperature = 18,
                washerFluidLevel = 68,
            )
        assertEquals("18%", VehicleCardCatalog.reading("battery", current)?.value)
        assertEquals("충전 중", VehicleCardCatalog.reading("charging", current)?.value)
        assertEquals("NG", VehicleCardCatalog.reading("tire", current)?.value)
        assertEquals("18°", VehicleCardCatalog.reading("environment", current)?.value)
        assertEquals("68%", VehicleCardCatalog.reading("washer", current)?.value)
    }

    @Test
    fun simulatedDefaultsCoverEveryCardPathAndRenderAllThirtyCards() {
        val definitions = VehicleCardVssDefaults.definitions.associateBy { it.path }
        val paths = (VehicleCardCatalog.cards + VehicleCardCatalog.defaultSlots).flatMap { it.vssPaths }
        assertTrue(paths.all { it in definitions })

        val current = snapshot().copy(vssCardSignals = VehicleCardVssDefaults.values)
        VehicleCardCatalog.cards.forEach { card ->
            assertNotNull(card.id, VehicleCardCatalog.reading(card.id, current)?.value)
        }
    }

    @Test
    fun fourWheelWarningCombinesAllFourSignals() {
        val signals = VehicleCardVssDefaults.values.toMutableMap()
        signals["Vehicle.Chassis.Axle.Row2.Wheel.Right.Brake.IsFluidLevelLow"] = "true"

        assertEquals("부족", VehicleCardCatalog.reading("brake-fluid", snapshot().copy(vssCardSignals = signals))?.value)
        signals["Vehicle.Chassis.Axle.Row1.Wheel.Left.Brake.PadWear"] = "65"
        signals["Vehicle.Chassis.Axle.Row2.Wheel.Right.Brake.IsBrakesWorn"] = "true"
        signals["Vehicle.Chassis.Axle.Row2.Wheel.Left.Tire.IsPressureLow"] = "true"
        val current = snapshot().copy(vssCardSignals = signals)
        assertEquals("65%", VehicleCardCatalog.reading("pad-wear", current)?.value)
        assertEquals("경고 있음", VehicleCardCatalog.reading("pad-warning", current)?.value)
        assertEquals("경고 있음", VehicleCardCatalog.reading("tire-low", current)?.value)
        signals.remove("Vehicle.Chassis.Axle.Row1.Wheel.Right.Brake.PadWear")
        assertNull(VehicleCardCatalog.reading("pad-wear", snapshot().copy(vssCardSignals = signals))?.value)
    }

    @Test
    fun oneConfirmedWheelWarningRemainsVisibleWhenAnotherWheelIsMissing() {
        val signals = VehicleCardVssDefaults.values.toMutableMap()
        signals["Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow"] = "true"
        signals.remove("Vehicle.Chassis.Axle.Row2.Wheel.Right.Tire.IsPressureLow")
        val current = snapshot().copy(vssCardSignals = signals)

        assertEquals("경고 있음", VehicleCardCatalog.reading("tire-low", current)?.value)
        assertEquals(VehicleCardStatus.CAUTION, VehicleCardCatalog.status("tire-low", current))
        assertEquals(
            VehicleCardStatus.CAUTION,
            VehicleCardCatalog.status("tire", current.copy(tirePressureStatus = "OK")),
        )
        assertEquals(VehicleCondition.WARNING, current.vehicleCondition())
    }

    @Test
    fun chargingTimeRequiresChargingState() {
        val signals = VehicleCardVssDefaults.values.toMutableMap()
        assertEquals(
            "충전 안 함",
            VehicleCardCatalog.reading("charging-time", snapshot().copy(vssCardSignals = signals))?.value,
        )
        signals["Vehicle.Powertrain.TractionBattery.Charging.IsCharging"] = "true"
        signals["Vehicle.Powertrain.TractionBattery.Charging.TimeToComplete"] = "3600"
        assertEquals(
            "1시간",
            VehicleCardCatalog.reading("charging-time", snapshot().copy(vssCardSignals = signals))?.value,
        )
    }

    @Test
    fun everyCardHasAnExplicitInformationOrConditionClassification() {
        val infoIds =
            setOf(
                "battery-health",
                "battery-range",
                "battery-time",
                "driver-door",
                "charging-time",
                "service-distance",
                "service-time",
                "parking-brake",
                "driver-belt",
                "pad-wear",
                "hood",
                "trunk",
                "air-temperature",
                "rain-intensity",
                "cabin-temperature",
                "distance",
                "dtc-count",
                "fatigue",
                "distraction",
                "charging",
                "environment",
            )
        val allIds = (VehicleCardCatalog.cards + VehicleCardCatalog.defaultSlots).map { it.id }.toSet()
        val normal =
            snapshot().copy(
                batteryPercent = 72,
                tirePressureStatus = "OK",
                washerFluidLevel = 100,
                isCharging = false,
                outsideTemperature = 20,
                isEmergencyBraking = false,
                isDrowsy = false,
                isDistracted = false,
                vssCardSignals = VehicleCardVssDefaults.values,
            )

        assertEquals(35, allIds.size)
        infoIds.forEach { assertEquals(it, VehicleCardStatus.INFO, VehicleCardCatalog.status(it, normal)) }
        (allIds - infoIds).forEach { assertEquals(it, VehicleCardStatus.NORMAL, VehicleCardCatalog.status(it, normal)) }
    }

    @Test
    fun cautionSignalsUseTheirOwnCardAndSharedCharacterCondition() {
        val baseline =
            snapshot().copy(
                batteryPercent = 72,
                tirePressureStatus = "OK",
                washerFluidLevel = 100,
                isEmergencyBraking = false,
                isDrowsy = false,
                isDistracted = false,
                vssCardSignals = VehicleCardVssDefaults.values,
            )
        val cases =
            listOf(
                Triple("battery-error", "Vehicle.Powertrain.TractionBattery.ErrorCodes", "P001"),
                Triple("service-due", "Vehicle.Service.IsServiceDue", "true"),
                Triple("brake-fluid", "Vehicle.Chassis.Axle.Row1.Wheel.Left.Brake.IsFluidLevelLow", "true"),
                Triple("low-beam", "Vehicle.Body.Lights.Beam.Low.IsDefect", "true"),
                Triple("brake-light", "Vehicle.Body.Lights.Brake.IsDefect", "true"),
                Triple("tire-low", "Vehicle.Chassis.Axle.Row2.Wheel.Right.Tire.IsPressureLow", "true"),
                Triple("pad-warning", "Vehicle.Chassis.Axle.Row1.Wheel.Right.Brake.IsBrakesWorn", "true"),
                Triple("abs", "Vehicle.ADAS.ABS.IsError", "true"),
                Triple("breakdown", "Vehicle.IsBrokenDown", "true"),
            )
        cases.forEach { (id, path, value) ->
            val snapshot = baseline.copy(vssCardSignals = baseline.vssCardSignals + (path to value))
            assertEquals(id, VehicleCardStatus.CAUTION, VehicleCardCatalog.status(id, snapshot))
            assertEquals(id, VehicleCondition.WARNING, snapshot.vehicleCondition())
        }
        val hungry =
            baseline.copy(
                vssCardSignals =
                    baseline.vssCardSignals + ("Vehicle.Body.Windshield.Front.WasherFluid.IsLevelLow" to "true"),
            )
        assertEquals(VehicleCardStatus.CAUTION, VehicleCardCatalog.status("washer-low", hungry))
        assertEquals(VehicleCondition.LOW_BATTERY, hungry.vehicleCondition())
    }

    @Test
    fun missingOrStaleConditionEvidenceNeverReceivesNormalBadge() {
        val missing = snapshot().copy(vssCardSignals = mapOf("Vehicle.Service.IsServiceDue" to "invalid"))
        assertEquals(VehicleCardStatus.UNAVAILABLE, VehicleCardCatalog.status("service-due", missing))
        assertEquals(VehicleCardStatus.UNAVAILABLE, VehicleCardCatalog.status("battery-error", missing))
        assertEquals(VehicleCardStatus.UNAVAILABLE, VehicleCardCatalog.status("battery-health", missing))
        val stale = missing.copy(quality = SignalQuality.STALE, vssCardSignals = VehicleCardVssDefaults.values)
        assertEquals(VehicleCardStatus.UNAVAILABLE, VehicleCardCatalog.status("service-due", stale))
        assertEquals(VehicleCardStatus.UNAVAILABLE, VehicleCardCatalog.status("battery", stale))
    }

    private fun snapshot(quality: SignalQuality = SignalQuality.VALID) =
        VehicleSnapshot(
            id = "catalog",
            epoch = "test",
            sequence = 1,
            receivedAtMillis = 1,
            source = SignalSource.SIMULATED,
            drivingState = DrivingState.PARKED,
            quality = quality,
        )
}
