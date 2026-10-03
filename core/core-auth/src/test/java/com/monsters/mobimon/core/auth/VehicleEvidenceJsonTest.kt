package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.VehicleChatCapture
import com.monsters.mobimon.core.domain.VehicleChatField
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleFieldValidity
import com.monsters.mobimon.core.domain.VehicleObservation
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleValue
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleEvidenceJsonTest {
    @Test fun tireEvidenceIncludesMeaningAndWheelPositionWithoutAnswerSentences() {
        val ids = listOf("interpreted.tirePressureStatus", "Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow")
        val fields =
            ids.mapIndexed { index, id ->
                VehicleChatField(
                    VehicleChatFieldCatalog.find(id)!!,
                    VehicleObservation(
                        id,
                        if (index == 0) VehicleValue.Text("NG") else VehicleValue.Boolean(true),
                        VehicleObservationSource.DEBUG_OVERRIDE,
                        "session",
                        1,
                        100,
                        100,
                    ),
                    VehicleFieldValidity(SignalQuality.VALID, null, 0, "ON_CHANGE"),
                )
            }
        val capture = VehicleChatCapture("evidence", 100, VehicleObservationSource.DEBUG_OVERRIDE, "session", fields)
        val json = JSONObject(VehicleEvidenceJson.encode(capture)).getJSONArray("fields")
        assertEquals("NG", json.getJSONObject(0).getString("value"))
        assertTrue(json.getJSONObject(0).getString("valueMeaning").contains("공기압 부족"))
        assertEquals("앞 왼쪽 타이어 공기압 부족 경고 여부", json.getJSONObject(1).getString("label"))
        assertTrue(json.getJSONObject(1).getBoolean("value"))
        val unavailable =
            VehicleChatField(
                fields.first().spec,
                fields.first().observation,
                VehicleFieldValidity(SignalQuality.UNAVAILABLE, "EXPIRED", 100, "PERIODIC"),
            )
        val hidden =
            JSONObject(
                VehicleEvidenceJson.encode(
                    VehicleChatCapture("evidence", 100, capture.sourceKind, "session", listOf(unavailable)),
                ),
            ).getJSONArray("fields").getJSONObject(0)
        assertTrue(hidden.isNull("value"))
        assertTrue(hidden.isNull("valueMeaning"))
    }

    @Test fun wireKeepsValidityAndAgeSeparateAndHidesMonotonicClockAndSession() {
        val id = VehicleChatFieldCatalog.BATTERY
        val observation =
            VehicleObservation(
                id,
                VehicleValue.Number(0.0),
                VehicleObservationSource.VSS_ADAPTER,
                "private-session",
                3,
                100,
                100,
            )
        val capture =
            VehicleChatCapture(
                "evidence",
                500_000,
                observation.sourceKind,
                observation.sessionId,
                listOf(
                    VehicleChatField(
                        VehicleChatFieldCatalog.find(id)!!,
                        observation,
                        VehicleFieldValidity(SignalQuality.VALID, null, 499_900, "ON_CHANGE"),
                    ),
                ),
            )
        val json = JSONObject(VehicleEvidenceJson.encode(capture))
        val field = json.getJSONArray("fields").getJSONObject(0)
        assertEquals(0, field.getInt("value"))
        assertEquals(499_900, field.getInt("receiptAgeMs"))
        assertEquals("VALID", field.getString("quality"))
        assertEquals("ON_CHANGE", field.getString("deliveryMode"))
        assertFalse(json.toString().contains("private-session"))
        assertFalse(json.toString().contains("receivedAtElapsedMillis"))
        assertFalse(json.has("vss_observed_time"))
    }
}
