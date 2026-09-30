package com.monsters.mobimon.core.auth

import android.os.SystemClock
import com.monsters.mobimon.core.domain.ConversationLimits
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.ConversationToolUsage
import com.monsters.mobimon.core.domain.ConversationTools
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One request, optionally one local execution and one final request. Protocol messages are turn-local. */
internal class CopilotToolConversation(
    private val model: CopilotModel,
    private val tools: ConversationTools,
    private val exchange: suspend (JSONObject) -> JSONObject,
    private val guard: suspend () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    suspend fun run(request: JSONObject): String {
        var responses = 0
        var requests = 0
        var executions = 0
        var estimated = 0L
        var prompt: Long? = 0L
        var completion: Long? = 0L
        val started = nowMillis()

        suspend fun send(): CopilotToolReply {
            guard()
            val tokens =
                withContext(dispatcher) {
                    CopilotTokenBudget.requireFits(model, request)
                    CopilotTokenBudget.promptTokens(model, request)
                }
            guard()
            estimated += tokens
            requests++
            val response = exchange(request)
            responses++
            val usage = response.optJSONObject("usage")

            fun count(name: String): Long? = (usage?.opt(name) as? Number)?.toLong()?.takeIf { it >= 0 }
            prompt = prompt?.let { total -> count("prompt_tokens")?.let { total + it } }
            completion = completion?.let { total -> count("completion_tokens")?.let { total + it } }
            guard()
            return withContext(dispatcher) { CopilotToolCodec.reply(response, tools) }
        }
        try {
            withContext(dispatcher) { CopilotToolCodec.configure(request, tools) }
            var reply = send()
            var evidence: ConversationToolResult.Found? = null
            if (reply is CopilotToolReply.Call) {
                val call = reply
                guard()
                executions++
                val executor = tools.tools.single { it.definition.name == call.value.name }
                val result = executor.execute(call.value)
                guard()
                evidence =
                    when (result) {
                        is ConversationToolResult.Found -> result
                        ConversationToolResult.NoEvidence -> throw ConversationException(
                            ConversationProblem.NO_EVIDENCE,
                        )
                        ConversationToolResult.Unavailable -> throw ConversationException(
                            ConversationProblem.TOOL_UNAVAILABLE,
                        )
                        ConversationToolResult.Limit -> throw ConversationException(ConversationProblem.LIMIT)
                    }
                if (evidence.content.isBlank()) throw ConversationException(ConversationProblem.TOOL_UNAVAILABLE)
                if (evidence.content.length > 64_000) throw ConversationException(ConversationProblem.LIMIT)
                request.getJSONArray("messages").put(call.assistant).put(
                    JSONObject()
                        .put(
                            "role",
                            "tool",
                        ).put("tool_call_id", call.value.id)
                        .put("content", evidence.content),
                )
                reply = send()
            }
            if (reply !is CopilotToolReply.Final) throw ConversationException(ConversationProblem.LIMIT)
            guard()
            val accepted =
                when (val result = tools.replyPolicy.accept(reply.text, evidence)) {
                    is ConversationResult.Success -> result.value
                    is ConversationResult.Failure -> throw ConversationException(result.problem)
                }
            if (accepted.isBlank() || accepted.length > ConversationLimits.REPLY_CHARACTERS) {
                throw ConversationException(ConversationProblem.PROVIDER)
            }
            guard()
            return accepted
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
