package com.monsters.mobimon.core.auth

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import com.monsters.mobimon.core.domain.ConversationEvidence
import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationExecutionBudget
import com.monsters.mobimon.core.domain.ConversationGroundedReplyPolicy
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
import com.monsters.mobimon.core.domain.GroundedReply
import com.monsters.mobimon.core.domain.ManualReplyRejection
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

    @Test fun manualCorrectionNamesFailureAndCurrentSourcesThenRevalidatesWithoutNewTools() =
        runTest {
            val ids = listOf("ne1-0020", "ne1-0021")
            output =
                ConversationToolResult.Found("fixture excerpts", ids.map { ConversationEvidence(it, "fixture page") })
            val corrected =
                JSONObject()
                    .put("version", 1)
                    .put("status", "ANSWERED")
                    .put("text", "안내 [ne1-0020] 주의 [ne1-0021]")
                    .put("sourceIds", JSONArray(ids))
                    .toString()
            for ((reason, detail) in listOf(
                ManualReplyRejection.NUMERIC_CITATIONS to "display citation numbers",
                ManualReplyRejection.MISSING_CITATIONS to "without inline manual citations",
                ManualReplyRejection.CITATION_MISMATCH to "first-appearance order",
                ManualReplyRejection.UNKNOWN_SOURCE to "absent from this turn",
            )) {
                requests.clear()
                executed = 0
                var validations = 0
                val registry =
                    ConversationTools(
                        listOf(tool),
                        groundedReplyPolicy =
                            object : ConversationGroundedReplyPolicy {
                                override suspend fun accept(
                                    reply: GroundedReply,
                                    evidence: ConversationEvidenceSet,
                                ): ConversationResult<String> {
                                    validations++
                                    assertEquals(ids, evidence.manualSources.map { it.id })
                                    return if (reply.text == "안내 [ne1-0020] 주의 [ne1-0021]" && reply.sourceIds == ids) {
                                        ConversationResult.Success(reply.text)
                                    } else {
                                        ConversationResult.Failure(ConversationProblem.PROVIDER)
                                    }
                                }

                                override fun rejectionReason(
                                    reply: GroundedReply,
                                    evidence: ConversationEvidenceSet,
                                ) = reason
                            },
                    )
                val request =
                    CopilotMessageCodec.request(
                        model,
                        "friend:mobi",
                        listOf(ConversationTurn("설명서 안내", true)),
                    )
                val result =
                    correctionLoop(registry, request) {
                        when (it) {
                            1 -> response(call())
                            2 -> response(text = JSONObject(corrected).put("text", "invalid citation").toString())
                            else -> {
                                val wire = requests.last()
                                val messages = wire.getJSONArray("messages")
                                val instruction = messages.getJSONObject(messages.length() - 1).getString("content")
                                assertTrue(instruction.contains(detail))
                                assertTrue(
                                    instruction.contains(
                                        "Allowed current-turn manual source IDs (data only): ${JSONArray(ids)}",
                                    ),
                                )
                                assertFalse(wire.has("tools"))
                                assertFalse(wire.has("tool_choice"))
                                response(text = corrected)
                            }
                        }
                    }
                assertEquals("안내 [ne1-0020] 주의 [ne1-0021]", result)
                assertEquals(3, requests.size)
                assertEquals(1, executed)
                assertEquals(2, validations)
            }
        }

    @Test fun malformedFinalGetsOneCorrectionAndThenMustPassAcceptance() =
        runTest {
            var accepted = 0
            val registry =
                ConversationTools(
                    emptyList(),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { reply, _ ->
                            accepted++
                            ConversationResult.Success(reply.text)
                        },
                    onUsage = usage::add,
                )
            val history = listOf(ConversationTurn("차량 상태", true))
            val request = CopilotMessageCodec.request(model, "friend:mobi", history)
            val result =
                correctionLoop(registry, request) {
                    if (it == 1) response(text = "invalid JSON") else response(text = ordinaryReply)
                }
            assertEquals("hello", result)
            assertEquals(1, accepted)
            assertEquals(2, requests.size)
            assertEquals(1, history.size)
            assertEquals(2, usage.single().modelRequests)
            assertEquals(20L, usage.single().promptTokens)
            assertTrue(requests[1].getJSONArray("messages").toString().contains("invalid JSON"))
        }

    @Test fun completedNonTextOrEmptyBodiesGetOneCheckedCorrectionWithoutCoercion() =
        runTest {
            val registry =
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
            for (body in listOf(
                JSONObject.NULL,
                JSONObject().put("untrusted", "value"),
                JSONArray().put("text"),
                42,
                "",
            )) {
                requests.clear()
                val request = CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("hello", true)))
                assertEquals(
                    "hello",
                    correctionLoop(registry, request) {
                        if (it == 1) {
                            response(text = ordinaryReply).apply {
                                getJSONArray("choices").getJSONObject(0).getJSONObject("message").put("content", body)
                            }
                        } else {
                            response(text = ordinaryReply)
                        }
                    },
                )
                assertEquals(2, requests.size)
                val messages = requests[1].getJSONArray("messages")
                assertEquals("user", messages.getJSONObject(messages.length() - 1).getString("role"))
                assertEquals("user", messages.getJSONObject(messages.length() - 2).getString("role"))
                assertFalse(requests[1].has("tools"))
            }
        }

    @Test fun repeatedMissingBodyStopsAndExplicitRefusalNeverTriggersCorrection() =
        runTest {
            val registry =
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
            for (refusal in listOf(false, true)) {
                requests.clear()
                val request = CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("hello", true)))
                try {
                    correctionLoop(registry, request) {
                        response(text = ordinaryReply).apply {
                            getJSONArray("choices").getJSONObject(0).getJSONObject("message").apply {
                                put("content", JSONObject.NULL)
                                if (refusal) put("refusal", "fixture refusal")
                            }
                        }
                    }
                    error("Invalid body escaped acceptance")
                } catch (error: ConversationException) {
                    assertEquals(ConversationProblem.PROVIDER, error.problem)
                }
                assertEquals(if (refusal) 1 else 2, requests.size)
            }
        }

    @Test fun policyRejectionCorrectsAgainstSameEvidenceWithoutExecutingToolsAgain() =
        runTest {
            output = ConversationToolResult.Found("fixture evidence", manualLookupAttempted = true)
            var validations = 0
            var original: ConversationEvidenceSet? = null
            val registry =
                ConversationTools(
                    listOf(tool),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { reply, evidence ->
                            validations++
                            if (validations == 1) original = evidence else assertTrue(original === evidence)
                            assertTrue(evidence.manualLookupAttempted)
                            if (reply.text == "bad references") {
                                ConversationResult.Failure(ConversationProblem.PROVIDER)
                            } else {
                                ConversationResult.Success(reply.text)
                            }
                        },
                    onUsage = usage::add,
                )
            val request = CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("status", true)))
            val manualReply = ordinaryReply.replace("CONVERSATION", "ANSWERED")
            assertEquals(
                "hello",
                correctionLoop(registry, request) {
                    when (it) {
                        1 -> response(call())
                        2 -> response(text = manualReply.replace("hello", "bad references"))
                        else -> response(text = manualReply)
                    }
                },
            )
            assertEquals(3, requests.size)
            assertEquals(1, executed)
            assertEquals(2, validations)
            assertFalse(requests[2].has("tools"))
            assertFalse(requests[2].has("tool_choice"))
            assertEquals(1, usage.single().toolExecutions)
        }

    @Test fun correctionCannotLoopOrExecuteRequestedTools() =
        runTest {
            val registry =
                ConversationTools(
                    listOf(tool),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { _, _ ->
                            ConversationResult.Failure(ConversationProblem.PROVIDER)
                        },
                )
            for (followup in listOf(
                response(text = "invalid JSON"),
                response(text = ordinaryReply),
                response(call()),
            )) {
                requests.clear()
                val request =
                    CopilotMessageCodec.request(
                        model,
                        "friend:mobi",
                        listOf(ConversationTurn("status", true)),
                    )
                try {
                    correctionLoop(registry, request) { if (it == 1) response(text = "invalid JSON") else followup }
                    error("Rejected correction escaped validation")
                } catch (error: ConversationException) {
                    assertEquals(ConversationProblem.PROVIDER, error.problem)
                }
                assertEquals(2, requests.size)
                assertEquals(0, executed)
            }
        }

    @Test fun rejectedEvidenceClaimCannotEscapeAsOrdinaryProse() =
        runTest {
            var validations = 0
            val registry =
                ConversationTools(
                    emptyList(),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { reply, _ ->
                            validations++
                            if (reply.status == "VEHICLE") {
                                ConversationResult.Failure(ConversationProblem.PROVIDER)
                            } else {
                                ConversationResult.Success(reply.text)
                            }
                        },
                )
            val request = CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("차량 상태", true)))
            try {
                correctionLoop(registry, request) {
                    response(text = if (it == 1) ordinaryReply.replace("CONVERSATION", "VEHICLE") else ordinaryReply)
                }
                error("Rejected vehicle claim escaped as ordinary prose")
            } catch (error: ConversationException) {
                assertEquals(ConversationProblem.PROVIDER, error.problem)
            }
            assertEquals(2, requests.size)
            assertEquals(1, validations)
        }

    @Test fun correctionPreservesRestrictionsEvidenceAndWholeTurnBudgets() =
        runTest {
            for (problem in listOf(
                ConversationProblem.RESTRICTED,
                ConversationProblem.NO_EVIDENCE,
                ConversationProblem.ACCOUNT,
            )) {
                requests.clear()
                val registry =
                    ConversationTools(
                        emptyList(),
                        groundedReplyPolicy =
                            ConversationGroundedReplyPolicy { _, _ ->
                                ConversationResult.Failure(problem)
                            },
                    )
                val request =
                    CopilotMessageCodec.request(
                        model,
                        "friend:mobi",
                        listOf(ConversationTurn("status", true)),
                    )
                try {
                    correctionLoop(registry, request) { response(text = ordinaryReply) }
                    error("Rejected response escaped validation")
                } catch (error: ConversationException) {
                    assertEquals(problem, error.problem)
                }
                assertEquals(1, requests.size)
            }
            val registry =
                ConversationTools(
                    emptyList(),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { reply, _ ->
                            ConversationResult.Success(reply.text)
                        },
                )
            for (problem in listOf(
                ConversationProblem.ACCOUNT,
                ConversationProblem.LIMIT,
                ConversationProblem.TIMEOUT,
            )) {
                requests.clear()
                val request =
                    CopilotMessageCodec.request(
                        model,
                        "friend:mobi",
                        listOf(ConversationTurn("status", true)),
                    )
                val configured = JSONObject(request.toString()).also { CopilotToolCodec.configure(it, registry) }
                val firstReservation = CopilotTokenBudget.promptTokens(model, configured) + model.maxOutputTokens
                try {
                    CopilotToolConversation(
                        model,
                        registry,
                        { wire ->
                            requests.add(JSONObject(wire.toString()))
                            if (problem == ConversationProblem.TIMEOUT) delay(20_000)
                            response(text = "invalid JSON")
                        },
                        {
                            if (problem == ConversationProblem.ACCOUNT &&
                                requests.isNotEmpty()
                            ) {
                                throw ConversationException(problem)
                            }
                        },
                        StandardTestDispatcher(testScheduler),
                        { testScheduler.currentTime },
                        budget =
                            ConversationExecutionBudget(
                                maxEstimatedTokens =
                                    if (problem ==
                                        ConversationProblem.LIMIT
                                    ) {
                                        firstReservation
                                    } else {
                                        64000
                                    },
                            ),
                    ).run(request)
                    error("Correction escaped restriction or budget")
                } catch (error: ConversationException) {
                    assertEquals(problem, error.problem)
                }
                assertEquals(if (problem == ConversationProblem.TIMEOUT) 2 else 1, requests.size)
            }
        }

    @Test fun correctionAfterToolExecutionCountsRetainedProtocolBeforeSending() =
        runTest {
            val registry =
                ConversationTools(
                    listOf(tool),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { reply, _ ->
                            ConversationResult.Success(reply.text)
                        },
                )
            val corrected = """{"version":1,"status":"ANSWERED","text":"corrected"}"""

            fun request() = CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("lookup", true)))

            fun reply(number: Int) =
                when (number) {
                    1 -> response(call())
                    2 -> response(text = "invalid JSON")
                    else -> response(text = corrected)
                }

            assertEquals("corrected", correctionLoop(registry, request(), ::reply))
            val wire = requests.map { JSONObject(it.toString()) }
            assertEquals(3, wire.size)
            assertFalse(wire.last().has("tools"))
            assertTrue(
                wire
                    .last()
                    .getJSONArray("messages")
                    .getJSONObject(2)
                    .has("tool_calls"),
            )
            val encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.O200K_BASE)
            // Budget one token below the complete correction transcript, including call metadata.
            val reservations =
                wire.sumOf {
                    encoding.countTokensOrdinary(it.toString()).toLong() + 128L +
                        model.maxOutputTokens
                }
            val budget = ConversationExecutionBudget(maxEstimatedTokens = reservations - 1)
            requests.clear()
            executed = 0
            try {
                CopilotToolConversation(
                    model,
                    registry,
                    { wireRequest ->
                        requests.add(JSONObject(wireRequest.toString()))
                        reply(requests.size)
                    },
                    {},
                    StandardTestDispatcher(testScheduler),
                    { testScheduler.currentTime },
                    budget = budget,
                ).run(request())
                error("Correction exceeded the whole-turn token budget")
            } catch (error: ConversationException) {
                assertEquals(ConversationProblem.LIMIT, error.problem)
            }
            assertEquals(2, requests.size)
            assertEquals(1, executed)
        }

    @Test fun httpFailureIsNeverReplayedAsCorrection() =
        runTest {
            val registry =
                ConversationTools(
                    emptyList(),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { reply, _ ->
                            ConversationResult.Success(reply.text)
                        },
                )
            val request = CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("status", true)))
            try {
                correctionLoop(registry, request) { throw ConversationException(ConversationProblem.PROVIDER) }
                error("HTTP failure was accepted")
            } catch (error: ConversationException) {
                assertEquals(ConversationProblem.PROVIDER, error.problem)
            }
            assertEquals(1, requests.size)
        }

    private val ordinaryReply = """{"version":1,"status":"CONVERSATION","text":"hello"}"""

    private suspend fun TestScope.correctionLoop(
        registry: ConversationTools,
        request: JSONObject,
        exchange: suspend (Int) -> JSONObject,
    ): String =
        CopilotToolConversation(
            model,
            registry,
            { wire ->
                requests.add(JSONObject(wire.toString()))
                exchange(requests.size)
            },
            {},
            StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
        ).run(request)

    @Test fun manualRoundTripWithOmittedVehicleArrayStillPassesCurrentEvidenceToAcceptance() =
        runTest {
            output =
                ConversationToolResult.Found(
                    "fixture manual result",
                    listOf(ConversationEvidence("ne1-0001", "fixture page")),
                )
            var exchanges = 0
            val registry =
                ConversationTools(
                    listOf(tool),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { reply, evidence ->
                            assertEquals("ANSWERED", reply.status)
                            assertEquals(listOf("ne1-0001"), reply.sourceIds)
                            assertTrue(reply.vehicleRefs.isEmpty())
                            assertTrue(evidence.manualLookupAttempted)
                            assertEquals(listOf("ne1-0001"), evidence.manualSources.map { it.id })
                            ConversationResult.Success(reply.text)
                        },
                )
            val request =
                CopilotMessageCodec.request(
                    model,
                    "friend:mobi",
                    listOf(ConversationTurn("와이퍼 교체방법", true)),
                    toolsEnabled = true,
                )
            val accepted =
                CopilotToolConversation(model, registry, {
                    exchanges++
                    if (exchanges ==
                        1
                    ) {
                        response(call())
                    } else {
                        response(
                            text =
                                """
                                {"version":1,"status":"ANSWERED","text":"와이퍼 교체 안내 [ne1-0001]",
                                "sourceIds":["ne1-0001"]}
                                """.trimIndent(),
                        )
                    }
                }, {}, StandardTestDispatcher(testScheduler)).run(request)
            assertEquals("와이퍼 교체 안내 [ne1-0001]", accepted)
            assertEquals(2, exchanges)
            assertEquals(1, executed)
        }

    @Test fun ordinaryChatWithOmittedUnusedReferencesStillRequiresAcceptanceWithoutReplay() =
        runTest {
            for (accept in listOf(true, false)) {
                var validations = 0
                var exchanges = 0
                val registry =
                    ConversationTools(
                        listOf(tool),
                        groundedReplyPolicy =
                            ConversationGroundedReplyPolicy { reply, _ ->
                                validations++
                                assertEquals("CONVERSATION", reply.status)
                                assertTrue(reply.vehicleRefs.isEmpty())
                                assertTrue(reply.sourceIds.isEmpty())
                                if (accept) {
                                    ConversationResult.Success(
                                        reply.text,
                                    )
                                } else {
                                    ConversationResult.Failure(ConversationProblem.NO_EVIDENCE)
                                }
                            },
                    )
                val request =
                    CopilotMessageCodec.request(
                        model,
                        "friend:mobi",
                        listOf(ConversationTurn("오늘 하루 이야기할래", true)),
                        toolsEnabled = true,
                    )
                val loop =
                    CopilotToolConversation(model, registry, {
                        exchanges++
                        response(
                            text = """{"version":1,"status":"CONVERSATION","text":"응, 오늘 어떤 일이 있었어?"}""",
                        )
                    }, {}, StandardTestDispatcher(testScheduler))
                if (accept) {
                    assertEquals("응, 오늘 어떤 일이 있었어?", loop.run(request))
                } else {
                    try {
                        loop.run(request)
                        error("Rejected ordinary reply escaped acceptance")
                    } catch (error: ConversationException) {
                        assertEquals(ConversationProblem.NO_EVIDENCE, error.problem)
                    }
                }
                assertEquals(1, exchanges)
                assertEquals(1, validations)
                assertEquals(0, executed)
            }
        }

    @Test fun noToolsStillParsesAndValidatesGroundedReplies() =
        runTest {
            var validated = false
            val registry =
                ConversationTools(
                    emptyList(),
                    groundedReplyPolicy =
                        ConversationGroundedReplyPolicy { reply, _ ->
                            validated = true
                            assertEquals("CONVERSATION", reply.status)
                            ConversationResult.Failure(ConversationProblem.NO_EVIDENCE)
                        },
                )
            val request = CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("hello", true)))
            try {
                CopilotToolConversation(model, registry, {
                    assertFalse(it.has("tools"))
                    response(
                        text =
                            """
                            {"version":1,"status":"CONVERSATION","text":"hello","sourceIds":[],"vehicleRefs":[]}
                            """.trimIndent(),
                    )
                }, {}, StandardTestDispatcher(testScheduler)).run(request)
                error("Rejected reply escaped policy")
            } catch (error: ConversationException) {
                assertEquals(ConversationProblem.NO_EVIDENCE, error.problem)
            }
            assertTrue(validated)
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

    @Test fun malformedUnknownAndDuplicateCallsNeverExecute() =
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
            for (secondId in listOf("call_1")) {
                requests.clear()
                answer = {
                    response(call()).apply {
                        getJSONArray(
                            "choices",
                        ).getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls").put(call(id = secondId))
                    }
                }
                assertEquals(ConversationResult.Failure(ConversationProblem.PROVIDER), runTurn())
            }
            assertEquals(0, executed)
        }

    @Test fun repeatedCallStopsWithoutSecondExecution() =
        runTest {
            answer = { response(call()) }
            assertEquals(ConversationResult.Failure(ConversationProblem.PROVIDER), runTurn())
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
