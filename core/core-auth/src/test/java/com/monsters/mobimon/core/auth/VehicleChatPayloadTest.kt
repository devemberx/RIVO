package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationConditionReason
import com.monsters.mobimon.core.domain.ConversationContext
import com.monsters.mobimon.core.domain.ConversationGroundedReplyPolicy
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ConversationTurn
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
class VehicleChatPayloadTest {
    @Test fun enablingLocalToolsPreservesVehicleEvidenceAndLimitsToolAuthority() {
        val body =
            CopilotMessageCodec.request(
                CopilotModel("gpt-4o", CopilotChatApi.CHAT_COMPLETIONS),
                "friend:mobi",
                listOf(ConversationTurn("배터리 상태가 어때?", true)),
                ConversationContext(batteryPercent = 42, petCondition = "NORMAL"),
                toolsEnabled = true,
            )
        val instruction = body.getJSONArray("messages").getJSONObject(0).getString("content")
        val payload = JSONObject(instruction.substringAfter("Optional context data (JSON): "))
        assertTrue(instruction.contains("Use only the declared read-only local tools."))
        assertTrue(
            instruction.contains(
                "Use only this turn's context or declared tool results for current vehicle facts",
            ),
        )
        assertFalse(instruction.contains("For time and battery questions"))
        assertTrue(instruction.contains("You have no authority to control vehicles"))
        assertEquals(42, payload.getJSONObject("battery").getInt("percent"))
        assertEquals("NORMAL", payload.getString("pet_condition"))
    }

    @Test fun checkedPromptKeepsRichContextLastAndLegacyDataOutOfTheVehicleContract() {
        val fieldId = VehicleChatFieldCatalog.CONDITION
        val observation =
            VehicleObservation(
                fieldId,
                VehicleValue.Text("LOW_BATTERY"),
                VehicleObservationSource.DEBUG_OVERRIDE,
                "session",
                1,
                100,
                100,
            )
        val capture =
            VehicleChatCapture(
                "this-turn",
                100,
                observation.sourceKind,
                observation.sessionId,
                listOf(
                    VehicleChatField(
                        VehicleChatFieldCatalog.find(fieldId)!!,
                        observation,
                        VehicleFieldValidity(SignalQuality.VALID, null, 0, "ON_CHANGE"),
                    ),
                ),
            )
        for (friend in listOf("friend:mobi", "friend:luna")) {
            val history = listOf(ConversationTurn("너 왜 배고파?", true))
            val request =
                CopilotMessageCodec.request(
                    CopilotModel("gpt-4o", CopilotChatApi.CHAT_COMPLETIONS),
                    friend,
                    history,
                    ConversationContext(petCondition = "WARNING", vehicleCapture = capture),
                )
            CopilotToolCodec.configure(
                request,
                ConversationTools(
                    emptyList(),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy {
                                reply,
                                _,
                            ->
                            ConversationResult.Success(reply.text)
                        },
                ),
            )
            val messages = request.getJSONArray("messages")
            val system = messages.getJSONObject(0).getString("content")
            val instructions = system.substringBefore(CopilotMessageCodec.CONTEXT_PREFIX)
            val data = JSONObject(system.substringAfter(CopilotMessageCodec.CONTEXT_PREFIX))
            assertEquals("this-turn", data.getString("evidenceId"))
            assertEquals(fieldId, data.getJSONArray("fields").getJSONObject(0).getString("id"))
            assertEquals("LOW_BATTERY", data.getJSONArray("fields").getJSONObject(0).getString("value"))
            assertFalse(data.has("pet_condition"))
            assertFalse(instructions.contains("pet_condition"))
            assertTrue(
                instructions.indexOf("# Vehicle data format") < instructions.indexOf("# Final response contract"),
            )
            assertTrue(instructions.contains("join signal to fields[].id"))
            assertFalse(request.has("tools"))
            assertEquals("json_object", request.getJSONObject("response_format").getString("type"))
            assertEquals(2, messages.length())
            assertEquals(history.single().text, messages.getJSONObject(1).getString("content"))
        }
    }

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
