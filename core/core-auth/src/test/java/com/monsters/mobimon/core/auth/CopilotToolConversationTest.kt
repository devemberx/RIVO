package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationReplyPolicy
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTool
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolDefinition
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.ConversationToolUsage
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ConversationTurn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopilotToolConversationTest {
    private val model =
        CopilotModel(
            "gpt-4o",
            CopilotChatApi.CHAT_COMPLETIONS,
            maxPromptTokens = 128000,
            maxContextWindowTokens = 128000,
            tokenizer = "o200k_base",
        )
    private val requests = mutableListOf<JSONObject>()
    private val usage = mutableListOf<ConversationToolUsage>()
    private var executed = 0
    private var allowed = true
    private var current = true
    private var output: ConversationToolResult = ConversationToolResult.Found("local secret value")
    private var action: suspend () -> Unit = {}
    private var answer: suspend (Int) -> JSONObject = { if (it == 1) response(call()) else response(text = "grounded") }
    private var policy = ConversationReplyPolicy { text, _ -> ConversationResult.Success(text) }
    private val tool =
        object : ConversationTool {
            override val definition =
                ConversationToolDefinition("lookup", "Look up a local value.", "query", "The lookup key.", 100)

            override suspend fun execute(call: ConversationToolCall): ConversationToolResult {
                executed++
                assertEquals("probe", call.argument)
                action()
                return output
            }
        }

    @Test fun boundedRoundTripMatchesIdsAndSumsUsageWithoutPersistingProtocolMessages() =
        runTest {
            val history = listOf(ConversationTurn("question", true))
            assertEquals(ConversationResult.Success("grounded"), runTurn(history = history))
            assertEquals(1, executed)
            assertEquals(2, requests.size)
            assertEquals(1, history.size)
            assertFalse(requests[0].toString().contains("local secret"))
            val messages = requests[1].getJSONArray("messages")
            assertTrue(messages.getJSONObject(2).isNull("content"))
            assertEquals("call_1", messages.getJSONObject(3).getString("tool_call_id"))
            assertEquals("local secret value", messages.getJSONObject(3).getString("content"))
            assertEquals("gpt-4o", requests[1].getString("model"))
            assertEquals("auto", requests[0].getString("tool_choice"))
            assertEquals(20L, usage.single().promptTokens)
            assertEquals(10L, usage.single().completionTokens)
            assertEquals(1, usage.single().toolExecutions)
            assertTrue(usage.single().estimatedPromptTokens > 20L)
        }

    @Test fun directReplyStillRequiresAcceptancePolicy() =
        runTest {
            answer = { response(text = "ungrounded") }
            policy =
                ConversationReplyPolicy { _, evidence ->
                    assertEquals(null, evidence)
                    ConversationResult.Failure(ConversationProblem.NO_EVIDENCE)
                }
            assertEquals(ConversationResult.Failure(ConversationProblem.NO_EVIDENCE), runTurn())
            assertEquals(0, executed)
            assertEquals(1, requests.size)
        }

    @Test fun acceptedDirectConversationUsesOneRequestWithoutExecutingATool() =
        runTest {
            answer = { response(text = "ordinary conversation") }
            policy =
                ConversationReplyPolicy { text, evidence ->
                    assertEquals(null, evidence)
                    ConversationResult.Success(text)
                }
            assertEquals(ConversationResult.Success("ordinary conversation"), runTurn())
            assertEquals(0, executed)
            assertEquals(1, requests.size)
            assertEquals(0, usage.single().toolExecutions)
            assertEquals("gpt-4o", requests.single().getString("model"))
        }

    @Test fun toolEnabledDirectConversationRequestsJsonObjectOutput() =
        runTest {
            val reply = "{\"status\":\"CONVERSATION\",\"text\":\"좋은 이야기\",\"sourceIds\":[]}"
            answer = { response(text = reply) }
            assertEquals(
                ConversationResult.Success(reply),
                runTurn(history = listOf(ConversationTurn("기분 좋아지는 이야기 해줘.", true))),
            )
            assertEquals("json_object", requests.single().getJSONObject("response_format").getString("type"))
            assertEquals(0, executed)
        }

    @Test fun malformedUnknownAndMultipleCallsNeverExecute() =
        runTest {
            val invalid =
                listOf(
                    call(name = "erase"),
                    call(id = ""),
                    call(id = "bad id"),
                    call(arguments = "{query:'probe'}"),
                    call(arguments = "{\"query\":2}"),
                    call(arguments = "{\"query\":\"probe\",\"query\":\"probe\"}"),
                    call(arguments = "{\"query\":\"probe\",\"extra\":true}"),
                    call(arguments = "{\"query\":\"probe\"} {}"),
                    call(arguments = "{\"query\":[]}"),
                    call(arguments = "{\"query\":\"${"x".repeat(101)}\"}"),
                    call().apply { put("type", "script") },
                )
            for (bad in invalid) {
                requests.clear()
                answer = { response(bad) }
                assertEquals(ConversationResult.Failure(ConversationProblem.PROVIDER), runTurn())
                assertEquals(1, requests.size)
            }
            for (secondId in listOf("call_1", "call_2")) {
                requests.clear()
                answer = {
                    response(call()).apply {
                        getJSONArray(
                            "choices",
                        ).getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls").put(call(id = secondId))
                    }
                }
                assertEquals(ConversationResult.Failure(ConversationProblem.LIMIT), runTurn())
            }
            assertEquals(0, executed)
        }

    @Test fun repeatedCallStopsWithoutSecondExecution() =
        runTest {
            answer = { response(call()) }
            assertEquals(ConversationResult.Failure(ConversationProblem.LIMIT), runTurn())
            assertEquals(2, requests.size)
            assertEquals(1, executed)
        }

    @Test fun failedToolSkipsFollowupGeneration() =
        runTest {
            for ((result, problem) in listOf(
                ConversationToolResult.NoEvidence to ConversationProblem.NO_EVIDENCE,
                ConversationToolResult.Unavailable to ConversationProblem.TOOL_UNAVAILABLE,
                ConversationToolResult.Limit to ConversationProblem.LIMIT,
            )) {
                requests.clear()
                output = result
                assertEquals(ConversationResult.Failure(problem), runTurn())
                assertEquals(1, requests.size)
            }
            action = { error("private failure") }
            requests.clear()
            assertEquals(ConversationResult.Failure(ConversationProblem.PROVIDER), runTurn())
            assertEquals(1, requests.size)
        }

    @Test fun budgetsIncludeSchemaCallAndLargeToolResultAndNeverTrimHistory() =
        runTest {
            assertEquals(ConversationResult.Failure(ConversationProblem.LIMIT), runTurn(limit = 1))
            assertEquals(0, requests.size)
            output = ConversationToolResult.Found("uncommon12345 ".repeat(3000))
            assertEquals(ConversationResult.Failure(ConversationProblem.LIMIT), runTurn(limit = 3000))
            assertEquals(1, requests.size)
            assertEquals(1, executed)
        }

    @Test fun accountAndParkingChangesAtToolBoundaryRejectFollowupAndAcceptance() =
        runTest {
            action = { allowed = false }
            assertEquals(ConversationResult.Failure(ConversationProblem.RESTRICTED), runTurn())
            assertEquals(1, requests.size)
            allowed = true
            requests.clear()
            action = { current = false }
            assertEquals(ConversationResult.Failure(ConversationProblem.ACCOUNT), runTurn())
            assertEquals(1, requests.size)
            current = true
            action = {}
            requests.clear()
            answer = {
                if (it == 1) {
                    response(call())
                } else {
                    current = false
                    response(text = "late")
                }
            }
            assertEquals(ConversationResult.Failure(ConversationProblem.ACCOUNT), runTurn())
        }

    @Test fun parkingMonitorCancelsSuspendedLocalWork() =
        runTest {
            var cancelled = false
            action = {
                allowed = false
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            assertEquals(ConversationResult.Failure(ConversationProblem.RESTRICTED), runTurn())
            assertTrue(cancelled)
            assertEquals(1, requests.size)
        }

    @Test fun callerCancellationStopsLocalWorkAndNeverSendsResult() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            var cancelled = false
            action = {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            val pending = async { runTurn() }
            entered.await()
            pending.cancelAndJoin()
            assertTrue(cancelled)
            assertEquals(1, requests.size)
        }

    @Test fun oneTimeoutCoversBothRequestsAndLocalWork() =
        runTest {
            action = { delay(20_000) }
            answer = {
                delay(6000)
                if (it == 1) response(call()) else response(text = "too late")
            }
            assertEquals(ConversationResult.Failure(ConversationProblem.TIMEOUT), runTurn())
            assertEquals(30_000L, testScheduler.currentTime)
            assertEquals(2, requests.size)
        }

    @Test fun truncatedOrInconsistentFinalResponseIsNeverAccepted() =
        runTest {
            answer =
                {
                    response(
                        text = "partial",
                    ).apply { getJSONArray("choices").getJSONObject(0).put("finish_reason", "length") }
                }
            assertEquals(ConversationResult.Failure(ConversationProblem.PROVIDER), runTurn())
            answer =
                { response(call()).apply { getJSONArray("choices").getJSONObject(0).put("finish_reason", "stop") } }
            assertEquals(ConversationResult.Failure(ConversationProblem.PROVIDER), runTurn())
            assertEquals(0, executed)
        }

    @Test fun emptyRegistryKeepsExistingCompletionPath() =
        runTest {
            assertEquals(ConversationResult.Success("legacy"), runTurn(enabled = false))
            assertEquals(0, requests.size)
            assertEquals(0, executed)
            assertEquals(0, usage.size)
        }

    private suspend fun TestScope.runTurn(
        limit: Int = 128000,
        enabled: Boolean = true,
        history: List<ConversationTurn> = listOf(ConversationTurn("question", true)),
    ): ConversationResult<String> {
        val selected = model.copy(maxPromptTokens = limit)
        val api =
            object : CopilotApi {
                override suspend fun authorize(githubToken: String) =
                    CopilotAccess("private", Long.MAX_VALUE, "https://api.githubcopilot.com".toHttpUrl())

                override suspend fun models(access: CopilotAccess) = listOf(selected)

                override suspend fun complete(
                    access: CopilotAccess,
                    model: CopilotModel,
                    friendId: String,
                    messages: List<ConversationTurn>,
                ) = "legacy"

                override suspend fun completeWithTools(
                    access: CopilotAccess,
                    model: CopilotModel,
                    friendId: String,
                    messages: List<ConversationTurn>,
                    tools: ConversationTools,
                    guard: suspend () -> Unit,
                ): String {
                    if (tools.tools.isEmpty()) {
                        return super.completeWithTools(
                            access,
                            model,
                            friendId,
                            messages,
                            tools,
                            guard,
                        )
                    }
                    return CopilotToolConversation(model, tools, { request ->
                        requests.add(JSONObject(request.toString()))
                        answer(requests.size)
                    }, guard, StandardTestDispatcher(testScheduler), { testScheduler.currentTime })
                        .run(CopilotMessageCodec.request(model, friendId, messages, toolsEnabled = true))
                }
            }
        return CopilotConversationProvider(
            { ConversationCredential(it, 1, "private") },
            { current },
            { allowed },
            api,
            {
                0
            },
            {
            },
            if (enabled) {
                ConversationTools(
                    listOf(tool),
                    replyPolicy = policy,
                    onUsage = usage::add,
                )
            } else {
                ConversationTools.None
            },
        ).reply(1, "thread", "friend:mobi", history)
    }

    private fun call(
        name: String = "lookup",
        id: String = "call_1",
        arguments: String = "{\"query\":\"probe\"}",
    ) = JSONObject()
        .put(
            "id",
            id,
        ).put("type", "function")
        .put("function", JSONObject().put("name", name).put("arguments", arguments))

    private fun response(
        call: JSONObject? = null,
        text: String = "",
    ): JSONObject {
        val message = JSONObject().put("role", "assistant").put("content", if (call == null) text else JSONObject.NULL)
        if (call != null) message.put("tool_calls", JSONArray().put(call))
        return JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("message", message)
                        .put("finish_reason", if (call == null) "stop" else "tool_calls"),
                ),
            ).put("usage", JSONObject().put("prompt_tokens", 10).put("completion_tokens", 5))
    }
}
