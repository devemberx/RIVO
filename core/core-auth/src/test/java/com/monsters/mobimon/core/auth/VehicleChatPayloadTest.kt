package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationConditionReason
import com.monsters.mobimon.core.domain.ConversationContext
import com.monsters.mobimon.core.domain.ConversationTurn
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleChatPayloadTest {
    @Test fun exactTimeUsesOriginalVssOffsetEvenWhenInterpretationDisagrees() {
        val payload =
            data(
                ConversationContext(vssTimestamp = "2026-09-28T09:12:34+09:00", timeOfDay = "Evening"),
            )
        assertEquals("09:12:34", payload.getString("vss_clock"))
        assertEquals("+09:00", payload.getString("vss_utc_offset"))
        assertEquals("2026-09-28T09:12:34+09:00", payload.getString("vss_observed_time"))
        assertFalse(payload.has("time_of_day"))
    }

    @Test fun petConditionCarriesEvidenceInsteadOfInventedBiologicalReasons() {
        val payload =
            data(
                ConversationContext(
                    petCondition = "LOW_BATTERY",
                    conditionReasons =
                        listOf(
                            ConversationConditionReason("HUNGRY", "interpreted.batteryPercent", "12", "배터리 부족"),
                        ),
                    simulatedCondition = true,
                ),
            )
        val reason = payload.getJSONArray("condition_reasons").getJSONObject(0)
        assertEquals("LOW_BATTERY", payload.getString("pet_condition"))
        assertEquals("12", reason.getString("value"))
        assertEquals("HUNGRY", reason.getString("concern"))
        assertEquals(true, payload.getBoolean("simulated_condition"))
    }

    private fun data(context: ConversationContext): JSONObject {
        val body =
            CopilotMessageCodec.request(
                CopilotModel("gpt-4o", CopilotChatApi.CHAT_COMPLETIONS),
                "friend:mobi",
                listOf(ConversationTurn("지금 배터리 몇 퍼센트야?", true)),
                context,
            )
        val instruction = body.getJSONArray("messages").getJSONObject(0).getString("content")
        return JSONObject(instruction.substringAfter("Optional context data (JSON): "))
    }

    @Test fun missingBatteryIsExplicitlyUnavailable() {
        assertEquals("unavailable", data(ConversationContext()).getJSONObject("battery").getString("status"))
    }

    @Test fun batteryPayloadPreservesZeroAndLabelsSimulatedValues() {
        val battery = data(ConversationContext(batteryPercent = 0, simulatedBattery = true)).getJSONObject("battery")
        assertEquals("valid", battery.getString("status"))
        assertEquals(0, battery.getInt("percent"))
        assertEquals(true, battery.getBoolean("simulated"))
        assertEquals(
            "unavailable",
            data(ConversationContext(batteryPercent = 101)).getJSONObject("battery").getString("status"),
        )
    }
}
