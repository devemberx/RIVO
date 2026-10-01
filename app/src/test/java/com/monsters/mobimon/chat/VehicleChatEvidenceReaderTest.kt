package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleChatTopic
import com.monsters.mobimon.core.domain.VehicleDeliveryPolicy
import com.monsters.mobimon.core.domain.VehicleEvidenceFrame
import com.monsters.mobimon.core.domain.VehicleObservation
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleSubscriptionState
import com.monsters.mobimon.core.domain.VehicleValue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleChatEvidenceReaderTest {
    @Test fun basicCaptureIsSmallAndOnChangeIsNotExpiredByAnAgeGuess() =
        runTest {
            val battery = observation(VehicleChatFieldCatalog.BATTERY, VehicleValue.Number(12.0))
            val vehicle =
                snapshot(
                    mapOf(battery.fieldId to battery),
                ).copy(batteryPercent = 12, batteryQuality = SignalQuality.VALID)
            val reader = VehicleChatEvidenceReader({ vehicle }, { 500_000 }, { false })
            val capture = reader.capture(VehicleChatTopic.BASIC)
            assertEquals(VehicleValue.Number(12.0), capture.field(battery.fieldId)!!.value)
            assertEquals(499_900L, capture.field(battery.fieldId)!!.validity.receiptAgeMillis)
            assertEquals("ON_CHANGE", capture.field(battery.fieldId)!!.validity.validityBasis)
            assertTrue(capture.fields.size < 10)
            assertEquals(VehicleValue.Text("LOW_BATTERY"), capture.field(VehicleChatFieldCatalog.CONDITION)!!.value)
            assertTrue(capture.conditionReasons.any { it.signal == battery.fieldId })
        }

    @Test fun missingProofSourceSwitchAndInvalidValuesFailClosed() =
        runTest {
            var debug = false
            val battery = observation(VehicleChatFieldCatalog.BATTERY, VehicleValue.Number(101.0))
            var vehicle = snapshot(mapOf(battery.fieldId to battery))
            val reader = VehicleChatEvidenceReader({ vehicle }, { 200 }, { debug })
            assertNull(reader.capture(VehicleChatTopic.BATTERY).field(battery.fieldId)!!.value)
            vehicle = vehicle.copy(evidenceFrame = null, batteryPercent = 75, batteryReceivedAtMillis = 100)
            assertNull(reader.capture(VehicleChatTopic.BATTERY).field(battery.fieldId)!!.value)
            debug = true
            vehicle = snapshot(mapOf(battery.fieldId to battery.copy(value = VehicleValue.Number(30.0))))
            assertNull(reader.capture(VehicleChatTopic.BATTERY).field(battery.fieldId)!!.value)
        }

    @Test fun topicKeepsMissingFieldsAndRawBatteryCanSupplyTheInterpretedAlias() =
        runTest {
            val id = "Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed"
            val value = observation(id, VehicleValue.Number(18.0))
            val reader =
                VehicleChatEvidenceReader(
                    { snapshot(mapOf(id to value)).copy(batteryPercent = 18) },
                    { 200 },
                    { false },
                )
            val capture = reader.capture(VehicleChatTopic.BATTERY)
            assertEquals(VehicleValue.Number(18.0), capture.field(VehicleChatFieldCatalog.BATTERY)!!.value)
            assertNull(capture.field("Vehicle.Powertrain.TractionBattery.StateOfHealth")!!.value)
        }

    private fun observation(
        id: String,
        value: VehicleValue,
    ) = VehicleObservation(id, value, VehicleObservationSource.VSS_ADAPTER, "session", 1, 100, 100)

    private fun snapshot(values: Map<String, VehicleObservation>) =
        VehicleSnapshot(
            "id",
            "epoch",
            1,
            100,
            SignalSource.REAL,
            DrivingState.PARKED,
            SignalQuality.VALID,
            evidenceFrame =
                VehicleEvidenceFrame(
                    1,
                    100,
                    values,
                    values.keys.associateWith {
                        VehicleDeliveryPolicy.OnChange
                    },
                    VehicleSubscriptionState("session", true, true, true),
                ),
        )
}
