package com.monsters.mobimon.core.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monsters.mobimon.core.domain.ConversationGroundedReplyPolicy
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ConversationTurn
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android JSON/tokenizer/coroutine execution; no account, network or storage access. */
@RunWith(AndroidJUnit4::class)
class CopilotReplyCorrectionDeviceTest {
    @Test fun completedNonTextBodiesUseOneCheckedCorrectionOnAndroid() =
        runBlocking {
            val tools =
                ConversationTools(
                    emptyList(),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy {
                                reply,
                                _,
                            ->
                            ConversationResult.Success(reply.text)
                        },
                )
            val model =
                CopilotModel(
                    "gpt-4o",
                    CopilotChatApi.CHAT_COMPLETIONS,
                    maxPromptTokens = 64000,
                    maxContextWindowTokens = 64000,
                    tokenizer = "o200k_base",
                )
            val ordinary = """{"version":1,"status":"CONVERSATION","text":"안녕"}"""
            for (body in listOf(JSONObject.NULL, JSONObject().put("untrusted", "value"), JSONArray().put("text"))) {
                var requests = 0
                val accepted =
                    CopilotToolConversation(model, tools, { wire ->
                        requests++
                        if (requests == 1) {
                            final(ordinary).apply {
                                getJSONArray("choices").getJSONObject(0).getJSONObject("message").put("content", body)
                            }
                        } else {
                            assertFalse(wire.has("tools"))
                            final(ordinary)
                        }
                    }, {}).run(CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("안녕", true))))
                assertEquals("안녕", accepted)
                assertEquals(2, requests)
            }
        }

    @Test fun vehicleReferenceCorrectionStillRequiresAcceptanceOnAndroid() =
        runBlocking {
            var requests = 0
            var validations = 0
            val tools =
                ConversationTools(
                    emptyList(),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { reply, _ ->
                            validations++
                            if (reply.vehicleRefs.isEmpty()) {
                                ConversationResult.Failure(ConversationProblem.PROVIDER)
                            } else {
                                ConversationResult.Success(reply.text)
                            }
                        },
                )
            val model =
                CopilotModel(
                    "gpt-4o",
                    CopilotChatApi.CHAT_COMPLETIONS,
                    maxPromptTokens = 64000,
                    maxContextWindowTokens = 64000,
                    tokenizer = "o200k_base",
                )
            val request = CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("차량 상태", true)))
            val accepted =
                CopilotToolConversation(model, tools, { wire ->
                    requests++
                    if (requests == 2) {
                        assertFalse(wire.has("tools"))
                        assertFalse(wire.has("tool_choice"))
                    }
                    final(if (requests == 1) missingReferences else checkedReferences)
                }, {}).run(request)
            assertEquals("배터리는 25% 남아 있어.", accepted)
            assertEquals(2, requests)
            assertEquals(2, validations)
        }

    @Test fun malformedCorrectionStopsAfterOneAttemptOnAndroid() =
        runBlocking {
            var requests = 0
            val tools =
                ConversationTools(
                    emptyList(),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { reply, _ ->
                            ConversationResult.Success(reply.text)
                        },
                )
            val model =
                CopilotModel(
                    "gpt-4o",
                    CopilotChatApi.CHAT_COMPLETIONS,
                    maxPromptTokens = 64000,
                    maxContextWindowTokens = 64000,
                    tokenizer = "o200k_base",
                )
            try {
                CopilotToolConversation(model, tools, {
                    requests++
                    final("invalid JSON")
                }, {})
                    .run(CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("차량 상태", true))))
                error("Invalid correction escaped validation")
            } catch (error: ConversationException) {
                assertEquals(ConversationProblem.PROVIDER, error.problem)
            }
            assertEquals(2, requests)
        }

    private fun final(text: String) =
        JSONObject().put(
            "choices",
            JSONArray().put(
                JSONObject()
                    .put("finish_reason", "stop")
                    .put("message", JSONObject().put("role", "assistant").put("content", text)),
            ),
        )

    private val missingReferences = """{"version":1,"status":"VEHICLE","text":"배터리는 25% 남아 있어."}"""
    private val checkedReferences =
        """
        {"version":1,"status":"VEHICLE","text":"배터리는 25% 남아 있어.",
        "vehicleRefs":[{"evidenceId":"fixture","fieldId":"interpreted.batteryPercent"}]}
        """.trimIndent()
}
