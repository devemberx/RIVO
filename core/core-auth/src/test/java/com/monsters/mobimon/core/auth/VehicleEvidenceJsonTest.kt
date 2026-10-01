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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleEvidenceJsonTest {
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
