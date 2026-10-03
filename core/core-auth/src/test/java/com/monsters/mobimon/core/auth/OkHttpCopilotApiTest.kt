package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OkHttpCopilotApiTest {
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

    @Test fun genericModelFailuresMentioningToolsAreNotProofOfToolIncompatibility() {
        val error = JSONObject().put("message", "Unsupported model for this request containing tools")
        assertEquals(CopilotRejection.UNKNOWN, CopilotRejection.from(error))
        assertEquals(
            CopilotRejection.TOOLS_UNSUPPORTED,
            CopilotRejection.from(JSONObject().put("message", "Unknown parameter: 'tool_choice'")),
        )
    }

    @Test fun explicitToolRejectionRetainsOnlyItsClassification() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(
                        400,
                    ).setBody("""{"error":{"message":"tools are not supported secret-provider-detail"}}"""),
            )
            try {
                api.exchange(access, CopilotChatApi.CHAT_COMPLETIONS, JSONObject().put("tools", org.json.JSONArray()))
                error("Expected rejection")
            } catch (error: ConversationException) {
                assertEquals(ConversationProblem.PROVIDER, error.problem)
                assertEquals(CopilotRejection.TOOLS_UNSUPPORTED, error.rejection)
                assertFalse(error.toString().contains("secret-provider-detail"))
            }
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("mobimon", request.getHeader("Copilot-Integration-Id"))
            assertEquals("MobiMon/0.1", request.getHeader("Editor-Version"))
            assertEquals("user", request.getHeader("X-Initiator"))
        }

    @Test fun forbiddenToolRejectionRetainsItsClassification() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setBody("""{"error":{"message":"tools are not supported"}}"""),
            )
            try {
                api.exchange(access, CopilotChatApi.CHAT_COMPLETIONS, JSONObject().put("tools", org.json.JSONArray()))
                error("Expected rejection")
            } catch (error: ConversationException) {
                assertEquals(ConversationProblem.ACCESS, error.problem)
                assertEquals(CopilotRejection.TOOLS_UNSUPPORTED, error.rejection)
            }
        }

    @Test fun personasGiveEachPetIdentityWithoutHabitualAnimalSuffixes() {
        val mobi =
            CopilotMessageCodec
                .request(model, "friend:mobi", listOf(ConversationTurn("hi", true)))
                .getJSONArray("messages")
                .getJSONObject(0)
                .getString("content")
        val luna =
            CopilotMessageCodec
                .request(model, "friend:luna", listOf(ConversationTurn("hi", true)))
                .getJSONArray("messages")
                .getJSONObject(0)
                .getString("content")
        val las =
            CopilotMessageCodec
                .request(model, "friend:las", listOf(ConversationTurn("hi", true)))
                .getJSONArray("messages")
                .getJSONObject(0)
                .getString("content")
        assertTrue(mobi.contains("rabbit"))
        assertTrue(luna.contains("cat"))
        assertTrue(las.contains("terminator"))
        assertTrue(las.contains("yellow"))
        assertTrue(mobi.contains("emoji"))
        assertTrue(luna.contains("banmal"))
    }

    @Test fun budgetIncludesKoreanPersonaContextAndReplyReservationAtExactBoundary() =
        runBlocking {
            val request =
                CopilotMessageCodec.request(
                    model,
                    "friend:mobi",
                    listOf(ConversationTurn("같이 가자", true)),
                    com.monsters.mobimon.core.domain
                        .ConversationContext(userName = "하늘"),
                )
            val tokens = CopilotTokenBudget.promptTokens(model, request).toInt()
            assertTrue(tokens > 100)
            CopilotTokenBudget.requireFits(
                model.copy(maxPromptTokens = tokens, maxContextWindowTokens = tokens + 2048),
                request,
            )
            assertProblem(ConversationProblem.LIMIT) {
                CopilotTokenBudget.requireFits(model.copy(maxPromptTokens = tokens - 1), request)
            }
            assertProblem(ConversationProblem.LIMIT) {
                CopilotTokenBudget.requireFits(model.copy(maxContextWindowTokens = tokens + 2047), request)
            }
            assertProblem(ConversationProblem.PROVIDER) {
                CopilotTokenBudget.requireFits(model.copy(tokenizer = "unknown"), request)
            }
            assertProblem(ConversationProblem.PROVIDER) {
                CopilotTokenBudget.requireFits(model.copy(maxPromptTokens = null), request)
            }
        }

    @Test fun serverContextOverflowIsRecoverableLimitWithoutReplay() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(400).setBody("""{"error":{"code":"context_length_exceeded"}}"""),
            )
            assertProblem(ConversationProblem.LIMIT) {
                api.complete(access, model, "friend:mobi", listOf(ConversationTurn("hi", true)))
            }
            assertEquals(1, server.requestCount)
        }

    @Before fun setup() {
        server.start()
        api = OkHttpCopilotApi(OkHttpClient(), { 1_000_000 }, server.url("/copilot_internal/user"))
        access = CopilotAccess("copilot-secret", 4_600_000, server.url("/"))
    }

    @After fun close() = server.shutdown()

    @Test fun authorizationRequiresChatAccessAndAcceptsOnlyTrustedCopilotHosts() =
        runBlocking {
            enqueue(
                """{"chat_enabled":true,
                   "endpoints":{"api":"https://api.individual.githubcopilot.com"}}""",
            )
            val token = api.authorize("github-secret")
            assertEquals("api.individual.githubcopilot.com", token.endpoint.host)
            assertEquals(1_300_000, token.expiresAtMillis)
            assertEquals("Bearer github-secret", server.takeRequest().getHeader("Authorization"))
            for (endpoint in listOf(
                "https://attacker.example/",
                "https://api.githubcopilot.com.attacker.example/",
                "http://api.githubcopilot.com/",
                "https://api.githubcopilot.com/private",
                "https://user@api.githubcopilot.com/",
            )) {
                enqueue(
                    JSONObject()
                        .put("chat_enabled", true)
                        .put("endpoints", JSONObject().put("api", endpoint))
                        .toString(),
                )
                assertProblem(ConversationProblem.PROVIDER) { api.authorize("github-secret") }
            }
            assertFalse(token.toString().contains("github-secret"))
            for (body in listOf("{}", "{\"chat_enabled\":false}")) {
                enqueue(body)
                assertProblem(ConversationProblem.ACCESS) { api.authorize("github-secret") }
            }
        }

    @Test fun missingUnknownAndMalformedModelLimitsFailClosed() =
        runBlocking {
            for (capabilities in listOf(
                """{"type":"chat"}""",
                """{"type":"chat","tokenizer":"unknown","limits":{"max_prompt_tokens":1000,"max_output_tokens":100}}""",
                """{"type":"chat","tokenizer":"o200k_base","limits":{"max_prompt_tokens":1000,"max_output_tokens":100,"max_context_window_tokens":-1}}""",
                """{"type":"chat","tokenizer":"o200k_base","limits":{"max_prompt_tokens":1000.5,"max_output_tokens":100}}""",
            )) {
                enqueue("""{"data":[{"id":"gpt-4o","capabilities":$capabilities}]}""")
                assertTrue(api.models(access).isEmpty())
            }
        }

    @Test fun modelDiscoveryReadsChatMetadataAndPolicy() =
        runBlocking {
            enqueue(
                """{"data":[
          {"id":"blocked","is_chat_default":true,"policy":{"state":"disabled"},"capabilities":{"type":"chat","tokenizer":"o200k_base","limits":{"max_prompt_tokens":64000,"max_context_window_tokens":128000,"max_output_tokens":4096}}},
          {"id":"responses-only","capabilities":{"type":"chat","tokenizer":"o200k_base","limits":{"max_prompt_tokens":64000,"max_context_window_tokens":128000,"max_output_tokens":4096}},"supported_endpoints":["/responses"]},
          {"id":"gpt-4o","model_picker_enabled":false,"capabilities":{"type":"chat","tokenizer":"o200k_base","limits":{"max_prompt_tokens":64000,"max_context_window_tokens":128000,"max_output_tokens":4096}}}
        ]}""",
            )
            val models = api.models(access)
            assertFalse(models.first { it.id == "blocked" }.enabled)
            assertEquals(CopilotChatApi.RESPONSES, models.first { it.id == "responses-only" }.api)
            assertTrue(models.first { it.id == "gpt-4o" }.enabled)
            assertEquals(CopilotChatApi.CHAT_COMPLETIONS, models.first { it.id == "gpt-4o" }.api)
            val request = server.takeRequest()
            assertEquals("/models", request.path)
            assertEquals("Bearer copilot-secret", request.getHeader("Authorization"))
            enqueue("""{"data":[]}""")
            assertTrue(api.models(access).isEmpty())
        }

    @Test fun chatSendsBoundedDialogueAndCompanionOnlyWithNoToolsOrOAuthToken() =
        runBlocking {
            enqueue("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"안녕하세요"}}]}""")
            assertEquals(
                "안녕하세요",
                api.complete(access, model, "friend:luna", listOf(ConversationTurn("안녕", true))),
            )
            val request = server.takeRequest()
            assertEquals("/chat/completions", request.path)
            assertEquals("user", request.getHeader("X-Initiator"))
            assertEquals(null, request.getHeader("Copilot-Session-Token"))
            val body = JSONObject(request.body.readUtf8())
            assertEquals("gpt-4o", body.getString("model"))
            assertFalse(body.getBoolean("stream"))
            assertFalse(body.has("tools"))
            assertEquals(2, body.getJSONArray("messages").length())
            assertTrue(
                body
                    .getJSONArray("messages")
                    .getJSONObject(0)
                    .getString("content")
                    .contains("Luna"),
            )
            assertEquals("안녕", body.getJSONArray("messages").getJSONObject(1).getString("content"))
            assertFalse(body.toString().contains("github-secret"))
        }

    @Test fun fixedGpt4oCompletionDoesNotSendASessionToken() =
        runBlocking {
            enqueue("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Hello"}}]}""")
            val fixedModel =
                CopilotModel(
                    "gpt-4o",
                    CopilotChatApi.CHAT_COMPLETIONS,
                    maxPromptTokens = 128000,
                    maxContextWindowTokens = 128000,
                    tokenizer = "o200k_base",
                )
            assertEquals(
                "Hello",
                api.complete(
                    access,
                    fixedModel,
                    "friend:mobi",
                    listOf(ConversationTurn("ping", true)),
                ),
            )
            val request = server.takeRequest()
            assertEquals("/chat/completions", request.path)
            assertEquals(null, request.getHeader("Copilot-Session-Token"))
            assertEquals("gpt-4o", JSONObject(request.body.readUtf8()).getString("model"))
            assertEquals(1, server.requestCount)
        }

    @Test fun statusErrorsAreRedactedAndNeverAutomaticallyRetried() =
        runBlocking {
            for ((status, problem) in listOf(
                401 to ConversationProblem.ACCOUNT,
                403 to ConversationProblem.ACCESS,
                402 to ConversationProblem.USAGE,
                429 to ConversationProblem.USAGE,
                504 to ConversationProblem.TIMEOUT,
                503 to ConversationProblem.SERVICE,
                400 to ConversationProblem.PROVIDER,
            )) {
                val before = server.requestCount
                server.enqueue(MockResponse().setResponseCode(status).setBody("private-provider-error"))
                assertProblem(
                    problem,
                ) { api.complete(access, model, "friend:mobi", listOf(ConversationTurn("hello", true))) }
                assertEquals(before + 1, server.requestCount)
            }
        }

    @Test fun immediateServiceRetryDoesNotReplayCompletion() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            enqueue("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"replayed"}}]}""")
            assertProblem(ConversationProblem.SERVICE) {
                api.complete(access, model, "friend:mobi", listOf(ConversationTurn("hello", true)))
            }
            assertEquals(1, server.requestCount)
        }

    @Test fun providerRejectionsAreClassifiedWithoutExposingProviderText() {
        for (body in listOf(
            """{"error":"no_eligible_models"}""",
            """{"error":{"code":"no_available_models","message":"private-provider-error"}}""",
            """{"message":"No models available for this request"}""",
        )) {
            assertEquals(CopilotRejection.NO_ELIGIBLE_MODELS, CopilotRejection.from(JSONObject(body)))
        }
        assertEquals(
            CopilotRejection.UNSUPPORTED_INTEGRATION,
            CopilotRejection.from(JSONObject("""{"error":{"code":"unsupported_integration"}}""")),
        )
        assertEquals(
            CopilotRejection.UNKNOWN,
            CopilotRejection.from(JSONObject("""{"error":{"message":"private-provider-error"}}""")),
        )
        assertEquals(
            CopilotRejection.NO_HEALTHY_UPSTREAM,
            CopilotRejection.from(JSONObject("""{"message":"no healthy upstream"}""")),
        )
    }

    @Test fun serviceRejectionsRemainBoundedRedactedAndNeverReplayed() =
        runBlocking {
            for (body in listOf(
                """{"error":{"code":"no_available_models","message":"private-provider-error"}}""",
                "No eligible models",
                "no healthy upstream",
                "x".repeat(16_385),
                "not json",
            )) {
                val before = server.requestCount
                server.enqueue(MockResponse().setResponseCode(503).setBody(body))
                assertProblem(ConversationProblem.SERVICE) {
                    api.complete(access, model, "friend:mobi", listOf(ConversationTurn("hello", true)))
                }
                assertEquals(before + 1, server.requestCount)
            }
        }

    @Test fun redirectsMalformedOversizedAndToolResponsesFailClosed() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/leak")))
            assertProblem(ConversationProblem.PROVIDER) { api.authorize("github-secret") }
            assertEquals(1, server.requestCount)
            for (body in listOf(
                "not json",
                "x".repeat(1_048_577),
                """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","tool_calls":[]}}]}""",
                """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":""}}]}""",
            )) {
                enqueue(body)
                assertProblem(ConversationProblem.PROVIDER) {
                    api.complete(access, model, "friend:mobi", listOf(ConversationTurn("hello", true)))
                }
            }
        }

    @Test fun connectedTransportFailuresNeverBecomeAccountOrAccessErrorsOrReplayCompletion() =
        runBlocking {
            for ((policy, expected) in listOf(
                SocketPolicy.NO_RESPONSE to ConversationProblem.TIMEOUT,
                SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY to ConversationProblem.NETWORK,
            )) {
                val boundedApi =
                    OkHttpCopilotApi(
                        OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build(),
                        { 1_000_000 },
                        server.url("/copilot_internal/user"),
                    )
                val before = server.requestCount
                server.enqueue(MockResponse().setBody("x".repeat(1024)).setSocketPolicy(policy))
                val pending =
                    async(Dispatchers.Default) {
                        assertProblem(expected) {
                            boundedApi.complete(access, model, "friend:mobi", listOf(ConversationTurn("hello", true)))
                        }
                    }
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                pending.await()
                assertEquals(before + 1, server.requestCount)
            }
        }

    @Test fun cancellationStopsWaitingForHttpResponse() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val pending =
                async(Dispatchers.Default) {
                    api.complete(access, model, "friend:mobi", listOf(ConversationTurn("hello", true)))
                }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            pending.cancelAndJoin()
            assertTrue(pending.isCancelled)
        }

    @Test fun responsesRejectToolsFailuresAndEmptyOrOversizedText() =
        runBlocking {
            val routed =
                CopilotModel(
                    "responses",
                    CopilotChatApi.RESPONSES,
                    maxPromptTokens = 128000,
                    maxContextWindowTokens = 128000,
                    tokenizer = "o200k_base",
                )
            val message =
                JSONObject(
                    """{"type":"message","role":"assistant","content":[{"type":"output_text","text":"answer"}]}""",
                )
            for (body in listOf(
                """{"status":"failed","output":[]}""",
                """{"status":"completed","output":[{"type":"function_call"}]}""",
                """{"status":"completed","output":[]}""",
                JSONObject()
                    .put(
                        "status",
                        "incomplete",
                    ).put(
                        "incomplete_details",
                        JSONObject().put("reason", "content_filter"),
                    ).put("output", org.json.JSONArray().put(message))
                    .toString(),
                JSONObject()
                    .put(
                        "status",
                        "completed",
                    ).put(
                        "output",
                        org.json.JSONArray().put(
                            JSONObject(
                                message.toString(),
                            ).put(
                                "content",
                                org.json.JSONArray().put(
                                    JSONObject().put("type", "output_text").put("text", "x".repeat(12001)),
                                ),
                            ),
                        ),
                    ).toString(),
            )) {
                enqueue(body)
                assertProblem(ConversationProblem.PROVIDER) {
                    api.complete(access, routed, "friend:mobi", listOf(ConversationTurn("hello", true)))
                }
            }
            enqueue(
                JSONObject()
                    .put(
                        "status",
                        "incomplete",
                    ).put(
                        "incomplete_details",
                        JSONObject().put("reason", "max_output_tokens"),
                    ).put("output", org.json.JSONArray().put(message))
                    .toString(),
            )
            assertEquals("answer", api.complete(access, routed, "friend:mobi", listOf(ConversationTurn("hello", true))))
        }

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setBody(body))
    }

    private suspend fun assertProblem(
        expected: ConversationProblem,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            throw AssertionError("Expected $expected")
        } catch (error: ConversationException) {
            assertEquals(expected, error.problem)
            assertEquals(expected.name, error.message)
        }
    }
}
