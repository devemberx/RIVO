package com.monsters.mobimon.core.auth

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopilotTokenBudgetTest {
    private val model =
        CopilotModel(
            "gpt-4o",
            CopilotChatApi.CHAT_COMPLETIONS,
            maxPromptTokens = 128000,
            maxContextWindowTokens = 128000,
            tokenizer = "o200k_base",
        )

    @Test fun retainedToolArgumentsCountWithoutToolDefinitions() {
        val function = JSONObject().put("name", "lookup").put("arguments", "{\"query\":\"short\"}")
        val request =
            request(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", JSONObject.NULL)
                    .put(
                        "tool_calls",
                        JSONArray().put(
                            JSONObject().put("id", "call1").put("type", "function").put("function", function),
                        ),
                    ),
            )
        val short = CopilotTokenBudget.promptTokens(model, request)
        function.put("arguments", JSONObject().put("query", "tire maintenance warning ".repeat(20)).toString())

        assertTrue(CopilotTokenBudget.promptTokens(model, request) > short)
    }

    @Test fun retainedToolResultIdsCountWithoutToolDefinitions() {
        val result = JSONObject().put("role", "tool").put("content", "evidence").put("tool_call_id", "short")
        val request = request(result)
        val short = CopilotTokenBudget.promptTokens(model, request)
        result.put("tool_call_id", "long_call_id_".repeat(8))

        assertTrue(CopilotTokenBudget.promptTokens(model, request) > short)
    }

    private fun request(message: JSONObject): JSONObject =
        JSONObject().put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", "Return JSON"))
                .put(message),
        )
}
