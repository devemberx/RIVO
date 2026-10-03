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

    @Test fun batteryAliasUsesAppRoundingAndCannotHideOutOfRangeRawValues() =
        runTest {
            val id = "Vehicle.Powertrain.TractionBattery.StateOfCharge.Displayed"
            var raw = observation(id, VehicleValue.Number(18.5))
            val reader =
                VehicleChatEvidenceReader({ snapshot(mapOf(id to raw)).copy(batteryPercent = 19) }, { 200 }, { false })
            assertEquals(
                VehicleValue.Number(19.0),
                reader.capture(VehicleChatTopic.BATTERY).field(VehicleChatFieldCatalog.BATTERY)!!.value,
            )
            raw = raw.copy(value = VehicleValue.Number(100.4))
            assertNull(reader.capture(VehicleChatTopic.BATTERY).field(VehicleChatFieldCatalog.BATTERY)!!.value)
        }

    @Test fun lowWasherFluidDoesNotClaimBatteryIsLow() =
        runTest {
            val id = "interpreted.washerFluidLevel"
            val observed = observation(id, VehicleValue.Number(10.0))
            val reader =
                VehicleChatEvidenceReader({
                    snapshot(mapOf(id to observed)).copy(washerFluidLevel = 10, batteryPercent = 72)
                }, { 200 }, { false })
            assertEquals(
                VehicleValue.Text("NEEDS_REPLENISHMENT"),
                reader.capture(VehicleChatTopic.BASIC).field(VehicleChatFieldCatalog.CONDITION)!!.value,
            )
        }

    @Test fun conditionUsesTheSameAtomicEvidenceAsBatteryWhenLegacyScalarsAreMissing() =
        runTest {
            val id = VehicleChatFieldCatalog.BATTERY
            val observed = observation(id, VehicleValue.Number(12.0))
            val reader = VehicleChatEvidenceReader({ snapshot(mapOf(id to observed)) }, { 200 }, { false })
            val capture = reader.capture(VehicleChatTopic.BASIC)
            assertEquals(VehicleValue.Text("LOW_BATTERY"), capture.field(VehicleChatFieldCatalog.CONDITION)!!.value)
            assertTrue(capture.conditionReasons.any { it.signal == id })
        }

    @Test fun confirmedBatteryErrorDoesNotRequireAvailableBatteryPercentage() =
        runTest {
            val id = "Vehicle.Powertrain.TractionBattery.ErrorCodes"
            val observed = observation(id, VehicleValue.Text("BMS123"))
            val reader = VehicleChatEvidenceReader({ snapshot(mapOf(id to observed)) }, { 200 }, { false })
            val capture = reader.capture(VehicleChatTopic.BASIC)
            assertEquals(VehicleValue.Text("WARNING"), capture.field(VehicleChatFieldCatalog.CONDITION)!!.value)
            assertTrue(capture.conditionReasons.any { it.signal == id })
        }

    @Test fun longBatteryErrorsKeepWarningsAndFullEvidenceWithBoundedReasonText() =
        runTest {
            val id = "Vehicle.Powertrain.TractionBattery.ErrorCodes"
            for (length in listOf(200, 201, 2000)) {
                val codes = "BMS123 ".repeat(286).take(length)
                val observed = observation(id, VehicleValue.Text(codes))
                val reader = VehicleChatEvidenceReader({ snapshot(mapOf(id to observed)) }, { 200 }, { false })
                val capture = reader.capture(VehicleChatTopic.BASIC)

                assertEquals(
                    "length=$length",
                    VehicleValue.Text("WARNING"),
                    capture.field(VehicleChatFieldCatalog.CONDITION)!!.value,
                )
                assertEquals(VehicleValue.Text(codes), capture.field(id)!!.value)
                val reason = capture.conditionReasons.single { it.signal == id }
                assertEquals("SICK", reason.concern)
                assertEquals(codes.take(200), reason.value)
            }
        }

    @Test fun invalidOrStaleBatteryErrorsCannotCreateWarnings() =
        runTest {
            val id = "Vehicle.Powertrain.TractionBattery.ErrorCodes"
            val observed = observation(id, VehicleValue.Text("BMS123 ".repeat(30)))
            for (invalid in listOf(
                observed.copy(value = VehicleValue.Text("E".repeat(2001))),
                observed.copy(sourceQuality = SignalQuality.STALE),
            )) {
                val reader = VehicleChatEvidenceReader({ snapshot(mapOf(id to invalid)) }, { 200 }, { false })
                val capture = reader.capture(VehicleChatTopic.BATTERY)

                assertNull(capture.field(id)!!.value)
                assertTrue(capture.conditionReasons.isEmpty())
                assertNull(reader.capture(VehicleChatTopic.BASIC).field(VehicleChatFieldCatalog.CONDITION)!!.value)
            }
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
