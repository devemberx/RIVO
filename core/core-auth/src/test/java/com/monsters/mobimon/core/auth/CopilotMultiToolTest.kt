package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationEvidence
import com.monsters.mobimon.core.domain.ConversationExecutionBudget
import com.monsters.mobimon.core.domain.ConversationGroundedReplyPolicy
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTool
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolDefinition
import com.monsters.mobimon.core.domain.ConversationToolResult
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopilotMultiToolTest {
    private val model =
        CopilotModel(
            "gpt-4o",
            CopilotChatApi.CHAT_COMPLETIONS,
            maxPromptTokens = 128000,
            maxContextWindowTokens = 128000,
            tokenizer = "o200k_base",
        )
    private val executed = mutableListOf<String>()
    private var requests = 0
    private var currentValue = 10.0
    private var payload = "fixture"
    private val manual =
        tool("manual") { ConversationToolResult.Found(payload, listOf(ConversationEvidence("ne1-0001", "page"))) }
    private val vehicle =
        tool("vehicle") {
            val id = VehicleChatFieldCatalog.BATTERY
            val observation =
                VehicleObservation(
                    id,
                    VehicleValue.Number(currentValue),
                    VehicleObservationSource.DEBUG_OVERRIDE,
                    "s",
                    executed.size.toLong(),
                    executed.size.toLong(),
                    0,
                )
            val capture =
                VehicleChatCapture(
                    "capture-${executed.size}",
                    executed.size.toLong(),
                    observation.sourceKind,
                    "s",
                    listOf(
                        VehicleChatField(
                            VehicleChatFieldCatalog.find(id)!!,
                            observation,
                            VehicleFieldValidity(SignalQuality.VALID, null, 0, "ON_CHANGE"),
                        ),
                    ),
                )
            ConversationToolResult.Found(VehicleEvidenceJson.encode(capture), vehicleCapture = capture)
        }

    @Test fun mixedSequencesAndBatchesAccumulateEvidenceOverMoreThanTwoRequests() =
        runTest {
            for (reverse in listOf(false, true)) {
                executed.clear()
                requests = 0
                val order = if (reverse) listOf("manual", "vehicle") else listOf("vehicle", "manual")
                val policy =
                    ConversationGroundedReplyPolicy { _, evidence ->
                        assertEquals(2, evidence.vehicle.size)
                        assertEquals("ne1-0001", evidence.manualSources.single().id)
                        ConversationResult.Success("checked")
                    }
                assertEquals(
                    "checked",
                    run(policy = policy) { turn ->
                        when (turn) {
                            1 -> calls(call("a", order[0], "first"), call("b", order[1], "first"))
                            2 -> calls(call("c", "vehicle", "second"))
                            else -> final()
                        }
                    },
                )
                assertEquals(3, requests)
                assertEquals(order + "vehicle", executed)
            }
        }

    @Test fun invalidOrDuplicateLaterCallRejectsEntireBatchBeforeExecution() =
        runTest {
            listOf(call("a", "manual"), call("b", "erase")).forEach { invalid ->
                requests = 0
                assertFailure(ConversationProblem.PROVIDER) { run { calls(call("a", "vehicle"), invalid) } }
                assertTrue(executed.isEmpty())
            }
        }

    @Test fun sameValueReceiptsAreNotProgressButChangedValuesCanContinue() =
        runTest {
            assertFailure(ConversationProblem.LIMIT) { run { calls(call("c$it", "vehicle")) } }
            assertEquals(2, requests)
            assertEquals(2, executed.size)
            executed.clear()
            requests = 0
            assertEquals(
                "checked",
                run { turn ->
                    currentValue += 1
                    if (turn < 4) calls(call("c$turn", "vehicle")) else final()
                },
            )
            assertEquals(4, requests)
        }

    @Test fun cumulativeTokenAndResultBudgetsBlockFurtherRequests() =
        runTest {
            assertFailure(
                ConversationProblem.LIMIT,
            ) { run(ConversationExecutionBudget(maxEstimatedTokens = 1)) { final() } }
            assertEquals(0, requests)
            payload = "x".repeat(40_000)
            assertFailure(ConversationProblem.LIMIT) { run { calls(call("c$it", "manual", "query$it")) } }
            assertEquals(2, requests)
            assertEquals(2, executed.size)
        }

    @Test fun sourceGuardLossAfterToolStopsBeforeNextRequest() =
        runTest {
            val policy =
                object : ConversationGroundedReplyPolicy {
                    override suspend fun checkCurrent(
                        evidence: com.monsters.mobimon.core.domain.ConversationEvidenceSet,
                    ) = executed.isEmpty()

                    override suspend fun accept(
                        reply: com.monsters.mobimon.core.domain.GroundedReply,
                        evidence: com.monsters.mobimon.core.domain.ConversationEvidenceSet,
                    ) = ConversationResult.Success("should not accept")
                }
            assertFailure(ConversationProblem.RESTRICTED) { run(policy = policy) { calls(call("a", "vehicle")) } }
            assertEquals(1, requests)
        }

    private suspend fun TestScope.run(
        budget: ConversationExecutionBudget = ConversationExecutionBudget(),
        policy: ConversationGroundedReplyPolicy =
            ConversationGroundedReplyPolicy {
                    _,
                    _,
                ->
                ConversationResult.Success("checked")
            },
        answer: (Int) -> JSONObject,
    ): String {
        val request = CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("fixture", true)))
        return CopilotToolConversation(
            model,
            ConversationTools(
                listOf(vehicle, manual),
                groundedReplyPolicy = policy,
            ),
            { payload ->
                val messages = payload.getJSONArray("messages")
                if (requests > 0) assertEquals("tool", messages.getJSONObject(messages.length() - 1).getString("role"))
                answer(++requests)
            },
            {},
            StandardTestDispatcher(testScheduler),
            { testScheduler.currentTime },
            budget = budget,
        ).run(request)
    }

    private fun tool(
        name: String,
        result: () -> ConversationToolResult,
    ) = object : ConversationTool {
        override val definition = ConversationToolDefinition(name, "fixture", "query", "fixture")

        override suspend fun execute(call: ConversationToolCall): ConversationToolResult {
            executed.add(name)
            return result()
        }
    }

    private fun call(
        id: String,
        name: String,
        query: String = "same",
    ) = JSONObject()
        .put("id", id)
        .put("type", "function")
        .put("function", JSONObject().put("name", name).put("arguments", JSONObject().put("query", query).toString()))

    private fun calls(vararg calls: JSONObject): JSONObject =
        response(
            JSONObject().put("role", "assistant").put("content", JSONObject.NULL).put("tool_calls", JSONArray(calls)),
            "tool_calls",
        )

    private fun final() =
        response(
            JSONObject()
                .put(
                    "role",
                    "assistant",
                ).put(
                    "content",
                    """{"version":1,"status":"CONVERSATION","text":"fixture","sourceIds":[],"vehicleRefs":[]}""",
                ),
            "stop",
        )

    private fun response(
        message: JSONObject,
        reason: String,
    ) = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", message).put("finish_reason", reason)))

    private suspend fun assertFailure(
        problem: ConversationProblem,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            error("Expected failure")
        } catch (
            error: ConversationException,
        ) {
            assertEquals(problem, error.problem)
        }
    }
}
