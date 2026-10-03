package com.monsters.mobimon.core.auth

import android.os.SystemClock
import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationExecutionBudget
import com.monsters.mobimon.core.domain.ConversationLimits
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.ConversationToolUsage
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ManualReplyRejection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Validated batches run sequentially within a shared turn budget. Protocol stays turn-local. */
internal class CopilotToolConversation(
    private val model: CopilotModel,
    private val tools: ConversationTools,
    private val exchange: suspend (JSONObject) -> JSONObject,
    private val guard: suspend () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val turnEvidence: ConversationEvidenceSet = ConversationEvidenceSet(),
    private val budget: ConversationExecutionBudget = ConversationExecutionBudget(),
) {
    suspend fun run(request: JSONObject): String =
        withTimeoutOrNull(budget.maxElapsedMillis) { execute(request) }
            ?: throw ConversationException(ConversationProblem.TIMEOUT)

    private suspend fun execute(request: JSONObject): String {
        var responses = 0
        var requests = 0
        var executions = 0
        var estimated = 0L
        var reserved = 0L
        var characters = 0L
        var prompt: Long? = 0L
        var completion: Long? = 0L
        val started = nowMillis()
        val callIds = mutableSetOf<String>()
        val seenResults = mutableSetOf<Pair<String, String>>()
        var legacyEvidence: ConversationToolResult.Found? = null
        var correctingReply = false
        var evidenceClaimRejected = false

        suspend fun check() {
            guard()
            if (nowMillis() - started >=
                budget.maxElapsedMillis
            ) {
                throw ConversationException(ConversationProblem.TIMEOUT)
            }
            if (tools.groundedReplyPolicy?.checkCurrent(turnEvidence) ==
                false
            ) {
                throw ConversationException(ConversationProblem.RESTRICTED)
            }
        }

        suspend fun send(): CopilotToolReply {
            check()
            val tokens =
                withContext(dispatcher) {
                    CopilotTokenBudget.requireFits(model, request)
                    CopilotTokenBudget.promptTokens(model, request)
                }
            check()
            val nextReservation = tokens + model.maxOutputTokens
            if (reserved + nextReservation >
                budget.maxEstimatedTokens
            ) {
                throw ConversationException(ConversationProblem.LIMIT)
            }
            reserved += nextReservation
            estimated += tokens
            requests++
            val response = exchange(request)
            responses++
            val usage = response.optJSONObject("usage")

            fun count(name: String): Long? = (usage?.opt(name) as? Number)?.toLong()?.takeIf { it >= 0 }
            prompt = prompt?.let { total -> count("prompt_tokens")?.let { total + it } }
            completion = completion?.let { total -> count("completion_tokens")?.let { total + it } }
            check()
            return withContext(dispatcher) { CopilotToolCodec.reply(response, tools) }
        }
        try {
            withContext(dispatcher) { CopilotToolCodec.configure(request, tools) }
            while (true) {
                when (val reply = send()) {
                    is CopilotToolReply.Calls -> {
                        // Correction uses existing evidence only; never execute another batch.
                        if (correctingReply) throw ConversationException(ConversationProblem.PROVIDER)
                        // Entire batch is validated, including IDs from previous batches, before any execution.
                        if (reply.values.any { it.id in callIds }) {
                            throw ConversationException(
                                ConversationProblem.PROVIDER,
                            )
                        }
                        callIds.addAll(reply.values.map { it.id })
                        request.getJSONArray("messages").put(reply.assistant)
                        for (call in reply.values) {
                            check()
                            executions++
                            val executor = tools.tools.single { it.definition.name == call.name }
                            val result =
                                when (val value = executor.execute(call)) {
                                    is ConversationToolResult.Found -> value
                                    ConversationToolResult.NoEvidence -> throw ConversationException(
                                        ConversationProblem.NO_EVIDENCE,
                                    )
                                    ConversationToolResult.Unavailable -> throw ConversationException(
                                        ConversationProblem.TOOL_UNAVAILABLE,
                                    )
                                    ConversationToolResult.Limit -> throw ConversationException(
                                        ConversationProblem.LIMIT,
                                    )
                                }
                            check()
                            if (result.content.isBlank()) {
                                throw ConversationException(
                                    ConversationProblem.TOOL_UNAVAILABLE,
                                )
                            }
                            characters += result.content.length
                            if (result.content.length > budget.maxResultCharacters ||
                                characters > budget.maxCombinedResultCharacters
                            ) {
                                throw ConversationException(ConversationProblem.LIMIT)
                            }
                            val key = call.name + ":" + call.argument.trim().replace(Regex("\\s+"), " ")
                            val fingerprint = withContext(dispatcher) { VehicleEvidenceFingerprint.of(result) }
                            if (!seenResults.add(
                                    key to fingerprint,
                                )
                            ) {
                                throw ConversationException(ConversationProblem.LIMIT)
                            }
                            turnEvidence.add(result)
                            legacyEvidence =
                                if (legacyEvidence ==
                                    null
                                ) {
                                    result
                                } else {
                                    ConversationToolResult.Found(
                                        result.content,
                                        turnEvidence.manualSources,
                                        manualLookupAttempted = turnEvidence.manualLookupAttempted,
                                    )
                                }
                            check()
                            request.getJSONArray("messages").put(
                                JSONObject()
                                    .put("role", "tool")
                                    .put("tool_call_id", call.id)
                                    .put("content", result.content),
                            )
                        }
                    }
                    is CopilotToolReply.Final -> {
                        check()
                        var manualRejection: ManualReplyRejection? = null
                        val result =
                            try {
                                val grounded = tools.groundedReplyPolicy
                                if (grounded == null) {
                                    tools.replyPolicy.accept(reply.text, legacyEvidence)
                                } else {
                                    val parsed = GroundedReplyCodec.parse(reply.text)
                                    if (correctingReply && evidenceClaimRejected && parsed.status == "CONVERSATION") {
                                        ConversationResult.Failure(ConversationProblem.PROVIDER)
                                    } else {
                                        evidenceClaimRejected =
                                            evidenceClaimRejected ||
                                            parsed.status in setOf("VEHICLE", "ANSWERED")
                                        grounded.accept(parsed, turnEvidence).also { accepted ->
                                            if (accepted is ConversationResult.Failure &&
                                                accepted.problem == ConversationProblem.PROVIDER
                                            ) {
                                                manualRejection = grounded.rejectionReason(parsed, turnEvidence)
                                            }
                                        }
                                    }
                                }
                            } catch (error: ConversationException) {
                                if (error.problem != ConversationProblem.PROVIDER) throw error
                                ConversationResult.Failure(error.problem)
                            }
                        if (result is ConversationResult.Failure &&
                            result.problem == ConversationProblem.PROVIDER &&
                            tools.groundedReplyPolicy != null &&
                            !correctingReply
                        ) {
                            // A new, bounded correction request, not a replay of a failed HTTP request.
                            // Nothing from the rejected answer is stored or shown to the user.
                            check()
                            correctingReply = true
                            evidenceClaimRejected = evidenceClaimRejected || executions > 0
                            request.remove("tools")
                            request.remove("tool_choice")
                            val messages = request.getJSONArray("messages")
                            if (reply.text.isNotBlank()) {
                                messages.put(JSONObject().put("role", "assistant").put("content", reply.text))
                            }
                            messages.put(
                                JSONObject().put("role", "user").put(
                                    "content",
                                    GroundedReplyCodec.correctionInstruction(manualRejection, turnEvidence),
                                ),
                            )
                            CopilotDiagnostics.replyCorrection(manualRejection)
                            continue
                        }
                        val accepted =
                            when (result) {
                                is ConversationResult.Success -> result.value
                                is ConversationResult.Failure -> throw ConversationException(result.problem)
                            }
                        if (accepted.isBlank() ||
                            accepted.length > ConversationLimits.REPLY_CHARACTERS
                        ) {
                            throw ConversationException(ConversationProblem.PROVIDER)
                        }
                        check()
                        return accepted
                    }
                }
            }
        } finally {
            tools.onUsage(
                ConversationToolUsage(
                    requests,
                    executions,
                    estimated,
                    prompt.takeIf { responses == requests },
                    completion.takeIf { responses == requests },
                    nowMillis() - started,
                ),
            )
        }
    }
}
