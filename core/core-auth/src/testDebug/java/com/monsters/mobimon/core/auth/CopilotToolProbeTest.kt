package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationProblem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
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
class CopilotToolProbeTest {
    private val model =
        CopilotModel(
            "gpt-4o",
            CopilotChatApi.CHAT_COMPLETIONS,
            maxPromptTokens = 128000,
            maxContextWindowTokens = 128000,
            tokenizer = "o200k_base",
        )
    private val requests = mutableListOf<JSONObject>()
    private val events = mutableListOf<ToolProbeEvent>()
    private var executions = 0

    @Test fun repeatedAutomaticAndForcedCallsRequireTheLocallyGeneratedValue() =
        runTest {
            val report = probe().run()
            assertEquals(ToolProbeReport(ToolProbeVerdict.SUPPORTED, ToolProbeVerdict.SUPPORTED), report)
            assertEquals(7, requests.size)
            assertEquals(3, executions)
            assertFalse(requests.first().has("tools"))
            for ((initial, followup) in listOf(1 to 2, 3 to 4, 5 to 6)) {
                assertFalse(requests[initial].toString().contains("secret-value"))
                val messages = requests[followup].getJSONArray("messages")
                val assistant = messages.getJSONObject(2)
                assertTrue(assistant.isNull("content"))
                val tool = messages.getJSONObject(3)
                assertEquals("tool", tool.getString("role"))
                assertEquals(
                    assistant.getJSONArray("tool_calls").getJSONObject(0).getString("id"),
                    tool.getString("tool_call_id"),
                )
                assertTrue(tool.getString("content").contains("secret-value"))
            }
            assertEquals("auto", requests[1].getString("tool_choice"))
            assertEquals(
                "lookup_demo_value",
                requests[5].getJSONObject("tool_choice").getJSONObject("function").getString("name"),
            )
            assertEquals("auto", requests[6].getString("tool_choice"))
            assertTrue(
                events.filter { it.responseReceived }.all {
                    it.estimatedPromptTokens != null &&
                        it.estimatedPromptTokens!! > 0
                },
            )
            assertFalse(events.toString().contains("secret-value"))
            val calls = events.filter { it.responseReceived && it.finish == ToolProbeFinish.TOOL_CALLS }
            assertEquals(3, calls.size)
            assertTrue(calls.all { it.toolCallCount == 1 && it.nullAssistantContent == true })
            assertTrue(
                events.filter { it.responseReceived }.all { it.promptTokens == 10L && it.completionTokens == 5L },
            )
        }

    @Test fun malformedOrUnapprovedCallsNeverExecuteOrSendToolResults() =
        runTest {
            val variants =
                listOf(
                    call().apply { getJSONObject("function").put("name", "delete_file") },
                    call().apply { put("id", "") },
                    call().apply { put("type", "other") },
                    call().apply { getJSONObject("function").put("arguments", "{key:'probe'}") },
                    call().apply { getJSONObject("function").put("arguments", "{\"key\":\"probe\",\"extra\":true}") },
                    call().apply { getJSONObject("function").put("arguments", "{\"key\":42}") },
                    call().apply { getJSONObject("function").put("arguments", "{\"key\":\"probe\"} trailing") },
                )
            for (variant in variants) {
                requests.clear()
                val report =
                    probe { input ->
                        if (input.has("tools")) response(calls = JSONArray().put(variant)) else text("PROBE_READY")
                    }.run()
                assertEquals(ToolProbeVerdict.INCONCLUSIVE, report.automatic)
                assertEquals(2, requests.size)
                assertEquals(0, executions)
                assertEquals(ToolProbeFailure.PROTOCOL, events.last().failure)
            }
        }

    @Test fun multipleCallsAreRejectedAsAWholeIncludingDuplicateIds() =
        runTest {
            val report =
                probe { input ->
                    if (input.has(
                            "tools",
                        )
                    ) {
                        response(calls = JSONArray().put(call()).put(call()))
                    } else {
                        text("PROBE_READY")
                    }
                }.run()
            assertEquals(ToolProbeVerdict.INCONCLUSIVE, report.automatic)
            assertEquals(0, executions)
            assertEquals(2, requests.size)
        }

    @Test fun wrongFinalValueDoesNotPassCompatibilityGate() =
        runTest {
            val report =
                probe { input ->
                    if (input.getJSONArray("messages").length() > 2) text("guessed") else defaultResponse(input)
                }.run()
            assertEquals(ToolProbeVerdict.INCONCLUSIVE, report.automatic)
            assertEquals(3, requests.size)
            assertEquals(ToolProbeFailure.VALUE_MISMATCH, events.last().failure)
        }

    @Test fun secondToolRequestCannotStartAnotherLoop() =
        runTest {
            val report = probe { input -> if (input.has("tools")) response() else text("PROBE_READY") }.run()
            assertEquals(ToolProbeVerdict.INCONCLUSIVE, report.automatic)
            assertEquals(3, requests.size)
            assertEquals(1, executions)
        }

    @Test fun onlyExplicitToolRejectionIsUnsupported() =
        runTest {
            for (rejection in listOf(CopilotRejection.UNKNOWN, CopilotRejection.TOOLS_UNSUPPORTED)) {
                requests.clear()
                val report =
                    probe { input ->
                        if (input.has("tools")) throw ConversationException(ConversationProblem.PROVIDER, rejection)
                        text("PROBE_READY")
                    }.run()
                assertEquals(
                    if (rejection ==
                        CopilotRejection.UNKNOWN
                    ) {
                        ToolProbeVerdict.INCONCLUSIVE
                    } else {
                        ToolProbeVerdict.UNSUPPORTED
                    },
                    report.automatic,
                )
                assertEquals(2, requests.size)
            }
        }

    @Test fun forcedSelectionFailureDoesNotEraseReproducibleAutomaticSupport() =
        runTest {
            val report =
                probe { input ->
                    if (input.opt(
                            "tool_choice",
                        ) is JSONObject
                    ) {
                        throw ConversationException(
                            ConversationProblem.PROVIDER,
                            CopilotRejection.TOOLS_UNSUPPORTED,
                        )
                    }
                    defaultResponse(input)
                }.run()
            assertEquals(ToolProbeReport(ToolProbeVerdict.SUPPORTED, ToolProbeVerdict.UNSUPPORTED), report)
            assertEquals(6, requests.size)
        }

    @Test fun permissionLossAfterModelResponsePreventsLocalExecution() =
        runTest {
            var allowed = true
            val probe =
                CopilotToolProbe(
                    model,
                    exchange = { input ->
                        requests.add(JSONObject(input.toString()))
                        if (input.has("tools")) allowed = false
                        defaultResponse(input)
                    },
                    guard = { if (!allowed) throw ConversationException(ConversationProblem.RESTRICTED) },
                    onEvent = events::add,
                    computationDispatcher = Dispatchers.Unconfined,
                    newValue = {
                        executions++
                        "secret-value"
                    },
                )
            try {
                probe.run()
                error("Expected restriction")
            } catch (expected: ConversationException) {
                assertEquals(ConversationProblem.RESTRICTED, expected.problem)
            }
            assertEquals(0, executions)
            assertEquals(2, requests.size)
        }

    @Test fun failedBaselineAndPromptBudgetNeverReachTools() =
        runTest {
            assertEquals(
                ToolProbeVerdict.INCONCLUSIVE,
                probe {
                    throw ConversationException(ConversationProblem.NETWORK)
                }.run().automatic,
            )
            assertEquals(1, requests.size)
            requests.clear()
            val tiny =
                CopilotToolProbe(
                    model.copy(maxPromptTokens = 1),
                    exchange = {
                        requests.add(it)
                        text("PROBE_READY")
                    },
                    guard = {},
                    onEvent = events::add,
                    computationDispatcher = Dispatchers.Unconfined,
                )
            assertEquals(ToolProbeVerdict.INCONCLUSIVE, tiny.run().automatic)
            assertTrue(requests.isEmpty())
            assertEquals(ToolProbeFailure.LIMIT, events.last().failure)
        }

    @Test fun wholeRoundTripHasThirtySecondDeadline() =
        runTest {
            val probe =
                probe { input ->
                    if (input.has("tools")) delay(16_000)
                    defaultResponse(input)
                }
            assertEquals(ToolProbeVerdict.INCONCLUSIVE, probe.run().automatic)
            assertEquals(ToolProbeFailure.TIMEOUT, events.last().failure)
            assertEquals(3, requests.size)
        }

    @Test fun externalCancellationPropagatesWithoutFailureOrRetry() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val pending =
                async {
                    probe {
                        entered.complete(Unit)
                        delay(Long.MAX_VALUE)
                        text("PROBE_READY")
                    }.run()
                }
            entered.await()
            pending.cancelAndJoin()
            assertTrue(pending.isCancelled)
            assertEquals(1, events.size)
            assertFalse(events.single().responseReceived)
            assertEquals(null, events.single().failure)
            assertEquals(1, requests.size)
        }

    private fun probe(reply: suspend (JSONObject) -> JSONObject = ::defaultResponse) =
        CopilotToolProbe(
            model,
            exchange = { input ->
                requests.add(JSONObject(input.toString()))
                reply(input)
            },
            guard = {},
            onEvent = events::add,
            computationDispatcher = Dispatchers.Unconfined,
            newValue = {
                executions++
                "secret-value-$executions"
            },
        )

    private fun defaultResponse(input: JSONObject): JSONObject {
        val messages = input.getJSONArray("messages")
        return when {
            !input.has("tools") -> text("PROBE_READY")
            messages.length() == 2 -> response()
            else -> text(JSONObject(messages.getJSONObject(3).getString("content")).getString("value"))
        }
    }

    private fun call() =
        JSONObject().put("id", "call_1").put("type", "function").put(
            "function",
            JSONObject()
                .put("name", "lookup_demo_value")
                .put("arguments", "{\"key\":\"probe\"}"),
        )

    private fun response(calls: JSONArray = JSONArray().put(call())) =
        JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("finish_reason", "tool_calls")
                        .put(
                            "message",
                            JSONObject()
                                .put("role", "assistant")
                                .put("content", JSONObject.NULL)
                                .put("tool_calls", calls),
                        ),
                ),
            ).put("usage", JSONObject().put("prompt_tokens", 10).put("completion_tokens", 5))

    private fun text(value: String) =
        JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("finish_reason", "stop")
                        .put("message", JSONObject().put("role", "assistant").put("content", value)),
                ),
            ).put("usage", JSONObject().put("prompt_tokens", 10).put("completion_tokens", 5))
}
