package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationEvidence
import com.monsters.mobimon.core.domain.ConversationGroundedReplyPolicy
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationReplyPolicy
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTool
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolDefinition
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ConversationTurn
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopilotToolRoutingTest {
    private val server = MockWebServer()
    private lateinit var api: OkHttpCopilotApi
    private lateinit var access: CopilotAccess
    private val model =
        CopilotModel(
            "gpt-4o",
            CopilotChatApi.CHAT_COMPLETIONS,
            maxPromptTokens = 128000,
            maxContextWindowTokens = 128000,
            tokenizer = "o200k_base",
        )
    private val executed = mutableListOf<String>()
    private val history =
        listOf(
            ConversationTurn("earlier question", true),
            ConversationTurn("earlier reply", false),
            ConversationTurn("current state", true),
        )
    private var selections = 0
    private var accepted = false
    private val registry =
        ConversationTools(
            listOf(tool("manual"), tool("vehicle")),
            groundedReplyPolicy =
                ConversationGroundedReplyPolicy { reply, _ ->
                    accepted = true
                    ConversationResult.Success(reply.text)
                },
            selectTools = { messages ->
                selections++
                assertEquals(history, messages)
                setOf("vehicle")
            },
        )

    @Before fun start() {
        server.start()
        api = OkHttpCopilotApi(OkHttpClient(), { 0 }, server.url("/user"))
        access = CopilotAccess("fixture", Long.MAX_VALUE, server.url("/"))
    }

    @After fun close() = server.shutdown()

    @Test fun selectionAppliesToInitialWireRequestAndSurvivesToolRoundTrip() =
        runBlocking {
            enqueueCall("vehicle")
            enqueueFinal()
            assertEquals("checked", api.completeWithTools(access, model, "friend:mobi", history, registry) {})
            assertEquals(listOf("vehicle"), executed)
            assertEquals(1, selections)
            assertTrue(accepted)
            repeat(2) {
                val request = JSONObject(server.takeRequest().body.readUtf8())
                val definitions = request.getJSONArray("tools")
                assertEquals(1, definitions.length())
                assertEquals("vehicle", definitions.getJSONObject(0).getJSONObject("function").getString("name"))
            }
        }

    @Test fun removedManualCallIsRejectedBeforeExecution() =
        runBlocking {
            enqueueCall("manual")
            enqueueFinal()
            try {
                api.completeWithTools(access, model, "friend:mobi", history, registry) {}
                error("Undeclared manual call was accepted")
            } catch (error: ConversationException) {
                assertEquals(ConversationProblem.PROVIDER, error.problem)
            }
            assertTrue(executed.isEmpty())
            assertEquals(1, server.requestCount)
        }

    @Test fun selectorCannotRegisterAnUnknownTool() {
        val invalid = ConversationTools(listOf(tool("vehicle")), selectTools = { setOf("erase") })
        try {
            invalid.forTurn(history)
            error("Unknown tool was allowed")
        } catch (_: IllegalArgumentException) {
            assertTrue(executed.isEmpty())
        }
    }

    @Test fun manualLookupMetadataSurvivesLaterResultsInLegacyAcceptance() =
        runBlocking {
            for (sources in listOf(emptyList(), listOf(ConversationEvidence("ne1-0001", "fixture page")))) {
                val manual =
                    object : ConversationTool {
                        override val definition =
                            ConversationToolDefinition("manual", "Search manual", "query", "Question")

                        override suspend fun execute(call: ConversationToolCall) =
                            ConversationToolResult.Found(
                                "fixture manual result",
                                sources,
                                manualLookupAttempted = sources.isEmpty(),
                            )
                    }
                val tools =
                    ConversationTools(
                        listOf(manual, tool("vehicle")),
                        replyPolicy =
                            ConversationReplyPolicy { _, result ->
                                assertTrue(
                                    "Merging later results must retain the manual lookup attempt",
                                    result!!.manualLookupAttempted,
                                )
                                assertEquals(sources, result.evidence)
                                ConversationResult.Success("accepted")
                            },
                    )
                enqueueCall("manual", "m1")
                enqueueCall("vehicle", "v1")
                enqueueFinal()
                assertEquals("accepted", api.completeWithTools(access, model, "friend:mobi", history, tools) {})
            }
        }

    private fun tool(name: String) =
        object : ConversationTool {
            override val definition =
                ConversationToolDefinition(name, "Read fixture evidence", "query", "Fixture query")

            override suspend fun execute(call: ConversationToolCall): ConversationToolResult {
                executed.add(name)
                return ConversationToolResult.Found("{\"status\":\"VALID\"}")
            }
        }

    private fun enqueueCall(
        name: String,
        id: String = "call1",
    ) {
        val function = JSONObject().put("name", name).put("arguments", "{\"query\":\"fixture\"}")
        val calls = JSONArray().put(JSONObject().put("id", id).put("type", "function").put("function", function))
        enqueue(
            JSONObject().put("role", "assistant").put("content", JSONObject.NULL).put("tool_calls", calls),
            "tool_calls",
        )
    }

    private fun enqueueFinal() =
        enqueue(
            JSONObject()
                .put(
                    "role",
                    "assistant",
                ).put(
                    "content",
                    """{"version":1,"status":"CONVERSATION","text":"checked","sourceIds":[],"vehicleRefs":[]}""",
                ),
            "stop",
        )

    private fun enqueue(
        message: JSONObject,
        finish: String,
    ) {
        server.enqueue(
            MockResponse().setBody(
                JSONObject()
                    .put(
                        "choices",
                        JSONArray().put(JSONObject().put("message", message).put("finish_reason", finish)),
                    ).toString(),
            ),
        )
    }
}
