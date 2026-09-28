package com.monsters.mobimon.core.auth

import android.os.SystemClock
import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.GitHubSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Only fixed classifications and numeric metadata may leave the probe. */
enum class ToolProbeVerdict { SUPPORTED, UNSUPPORTED, INCONCLUSIVE }

enum class ToolProbeFinish { STOP, LENGTH, TOOL_CALLS, OTHER }

enum class ToolProbeStage {
    AUTHENTICATION,
    AUTHORIZATION,
    MODELS,
    BASELINE,
    AUTO_CALL,
    AUTO_RESULT,
    FORCED_CALL,
    FORCED_RESULT,
}

enum class ToolProbeFailure {
    ACCOUNT,
    ACCESS,
    RESTRICTED,
    NETWORK,
    TIMEOUT,
    SERVICE,
    USAGE,
    LIMIT,
    PROVIDER,
    PROTOCOL,
    VALUE_MISMATCH,
    TOOLS_UNSUPPORTED,
}

data class ToolProbeEvent(
    val stage: ToolProbeStage,
    val attempt: Int = 0,
    val elapsedMillis: Long = 0,
    val estimatedPromptTokens: Long? = null,
    val promptTokens: Long? = null,
    val completionTokens: Long? = null,
    val failure: ToolProbeFailure? = null,
    val responseReceived: Boolean = false,
    val finish: ToolProbeFinish? = null,
    val toolCallCount: Int? = null,
    val nullAssistantContent: Boolean? = null,
)

data class ToolProbeReport(
    val automatic: ToolProbeVerdict,
    val forced: ToolProbeVerdict = ToolProbeVerdict.INCONCLUSIVE,
)

/** Debug only, opt-in, synthetic traffic. Uses the app's credential store without exporting credentials. */
suspend fun PersistentGitHubAuthentication.probeToolCalling(onEvent: (ToolProbeEvent) -> Unit): ToolProbeReport {
    var stage = ToolProbeStage.AUTHENTICATION
    val client = CopilotConversationProvider.httpClient()
    var lease: ConversationCredential? = null
    try {
        val account =
            (session.value as? GitHubSession.Authenticated)?.account
                ?: throw ConversationException(ConversationProblem.ACCOUNT)
        lease = conversationCredential(account.id)
        val credential = lease

        suspend fun guard() {
            currentCoroutineContext().ensureActive()
            requireInteraction()
            if (!isCurrent(credential)) throw ConversationException(ConversationProblem.ACCOUNT)
        }
        return coroutineScope {
            // Match UI cancellation during a pending request, not only at the next stage boundary.
            val monitor =
                launch {
                    while (true) {
                        delay(100)
                        guard()
                    }
                }
            try {
                val api = OkHttpCopilotApi(client, System::currentTimeMillis)
                val prepared =
                    withTimeoutOrNull(30_000) {
                        guard()
                        stage = ToolProbeStage.AUTHORIZATION
                        val access = api.authorize(credential.token)
                        guard()
                        onEvent(ToolProbeEvent(stage))
                        stage = ToolProbeStage.MODELS
                        val model =
                            api.models(access).firstOrNull {
                                it.id == "gpt-4o" && it.enabled && it.api == CopilotChatApi.CHAT_COMPLETIONS
                            } ?: throw ConversationException(ConversationProblem.ACCESS)
                        guard()
                        onEvent(ToolProbeEvent(stage))
                        access to model
                    } ?: throw ConversationException(ConversationProblem.TIMEOUT)
                val (access, model) = prepared
                CopilotToolProbe(
                    model = model,
                    exchange = { api.exchange(access, CopilotChatApi.CHAT_COMPLETIONS, it) },
                    guard = ::guard,
                    onEvent = {
                        stage = it.stage
                        onEvent(it)
                    },
                ).run()
            } finally {
                monitor.cancel()
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (error is ConversationException && error.problem == ConversationProblem.ACCOUNT) {
            lease?.let { rejectConversationCredential(it) }
        }
        onEvent(ToolProbeEvent(stage, failure = probeFailure(error)))
        return ToolProbeReport(ToolProbeVerdict.INCONCLUSIVE)
    } finally {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

/** A compatibility experiment, deliberately not a general tool registry or conversation provider. */
internal class CopilotToolProbe(
    private val model: CopilotModel,
    private val exchange: suspend (JSONObject) -> JSONObject,
    private val guard: suspend () -> Unit,
    private val onEvent: (ToolProbeEvent) -> Unit,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val newValue: () -> String = { UUID.randomUUID().toString() },
) {
    private var stage = ToolProbeStage.BASELINE
    private var attempt = 0
    private var started = 0L

    suspend fun run(): ToolProbeReport {
        val baseline =
            attempt {
                stage = ToolProbeStage.BASELINE
                val response = request(payload("Return exactly PROBE_READY.", tools = false))
                if (finalText(response) != "PROBE_READY") throw ProbeProtocolFailure(ToolProbeFailure.VALUE_MISMATCH)
            }
        if (baseline != ToolProbeVerdict.SUPPORTED) return ToolProbeReport(ToolProbeVerdict.INCONCLUSIVE)
        for (index in 1..2) {
            attempt = index
            val automatic = attempt { roundTrip(forced = false) }
            if (automatic != ToolProbeVerdict.SUPPORTED) return ToolProbeReport(automatic)
        }
        attempt = 1
        val forced = attempt { roundTrip(forced = true) }
        return ToolProbeReport(ToolProbeVerdict.SUPPORTED, forced)
    }

    private suspend fun attempt(block: suspend () -> Unit): ToolProbeVerdict {
        started = nowMillis()
        return try {
            withTimeoutOrNull(30_000) {
                block()
                true
            }
                ?: throw ConversationException(ConversationProblem.TIMEOUT)
            ToolProbeVerdict.SUPPORTED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = probeFailure(error)
            onEvent(ToolProbeEvent(stage, attempt, nowMillis() - started, failure = failure))
            if (failure == ToolProbeFailure.ACCOUNT || failure == ToolProbeFailure.RESTRICTED) throw error
            if (failure ==
                ToolProbeFailure.TOOLS_UNSUPPORTED
            ) {
                ToolProbeVerdict.UNSUPPORTED
            } else {
                ToolProbeVerdict.INCONCLUSIVE
            }
        }
    }

    private suspend fun roundTrip(forced: Boolean) {
        stage = if (forced) ToolProbeStage.FORCED_CALL else ToolProbeStage.AUTO_CALL
        val input = payload("Look up key probe using lookup_demo_value. Return only its value after the tool replies.")
        if (forced) {
            input.put(
                "tool_choice",
                JSONObject().put("type", "function").put("function", JSONObject().put("name", TOOL)),
            )
        }
        val response = request(input)
        val call = validatedCall(response)
        guard()
        // Created only after a validated call: the model cannot learn this value from the first request.
        val value = newValue()
        guard()
        input
            .getJSONArray("messages")
            .put(
                JSONObject()
                    .put(
                        "role",
                        "assistant",
                    ).put("content", JSONObject.NULL)
                    .put("tool_calls", JSONArray().put(call)),
            ).put(
                JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", call.getString("id"))
                    .put("content", JSONObject().put("value", value).toString()),
            )
        input.put("tool_choice", "auto")
        stage = if (forced) ToolProbeStage.FORCED_RESULT else ToolProbeStage.AUTO_RESULT
        if (finalText(request(input)) != value) throw ProbeProtocolFailure(ToolProbeFailure.VALUE_MISMATCH)
    }

    private suspend fun request(payload: JSONObject): JSONObject {
        guard()
        onEvent(ToolProbeEvent(stage, attempt, nowMillis() - started))
        val estimated = withContext(computationDispatcher) { requireBudget(payload) }
        guard()
        val response = exchange(payload)
        guard()
        val usage = response.optJSONObject("usage")
        val choice = response.optJSONArray("choices")?.optJSONObject(0)
        val message = choice?.optJSONObject("message")
        onEvent(
            ToolProbeEvent(
                stage,
                attempt,
                nowMillis() - started,
                estimated,
                usage?.positiveInt("prompt_tokens"),
                usage?.positiveInt("completion_tokens"),
                responseReceived = true,
                finish =
                    when (choice?.optString("finish_reason")) {
                        "stop" -> ToolProbeFinish.STOP
                        "length" -> ToolProbeFinish.LENGTH
                        "tool_calls" -> ToolProbeFinish.TOOL_CALLS
                        else -> ToolProbeFinish.OTHER
                    },
                toolCallCount = message?.optJSONArray("tool_calls")?.length(),
                nullAssistantContent = message?.isNull("content"),
            ),
        )
        return response
    }

    private fun payload(
        question: String,
        tools: Boolean = true,
    ): JSONObject {
        val body =
            JSONObject()
                .put("model", "gpt-4o")
                .put("stream", false)
                .put("max_tokens", minOf(128, model.maxOutputTokens))
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put(
                                    "role",
                                    "system",
                                ).put(
                                    "content",
                                    "Synthetic protocol check. Follow the user exactly. " +
                                        "Use the tool only once when asked. Never guess a lookup value.",
                                ),
                        ).put(JSONObject().put("role", "user").put("content", question)),
                )
        if (tools) {
            body
                .put(
                    "tools",
                    JSONArray().put(
                        JSONObject().put("type", "function").put(
                            "function",
                            JSONObject()
                                .put("name", TOOL)
                                .put("description", "Read the current synthetic value for key probe.")
                                .put(
                                    "parameters",
                                    JSONObject()
                                        .put("type", "object")
                                        .put(
                                            "properties",
                                            JSONObject().put(
                                                "key",
                                                JSONObject()
                                                    .put(
                                                        "type",
                                                        "string",
                                                    ).put("enum", JSONArray().put("probe")),
                                            ),
                                        ).put("required", JSONArray().put("key"))
                                        .put("additionalProperties", false),
                                ),
                        ),
                    ),
                ).put("tool_choice", "auto")
        }
        return body
    }

    private fun requireBudget(payload: JSONObject): Long {
        val serialized = payload.toString()
        if (serialized.length > 16_384) throw ConversationException(ConversationProblem.LIMIT)
        // Count the entire synthetic wire request (including schemas/IDs/results) with framing slack.
        // This is a conservative local estimate, never reported as provider usage.
        val wrapper =
            JSONObject().put(
                "messages",
                JSONArray().put(JSONObject().put("role", "system").put("content", serialized)),
            )
        val estimated = CopilotTokenBudget.promptTokens(model, wrapper) + 128L
        val promptLimit = model.maxPromptTokens ?: throw ConversationException(ConversationProblem.PROVIDER)
        if (estimated > promptLimit ||
            model.maxContextWindowTokens?.let { estimated + payload.getInt("max_tokens") > it } == true
        ) {
            throw ConversationException(ConversationProblem.LIMIT)
        }
        return estimated
    }

    private fun validatedCall(response: JSONObject): JSONObject {
        val (reason, message) = choice(response)
        if (reason != "tool_calls" || !message.isNull("function_call")) invalid()
        if (!message.isNull("content") &&
            (message.opt("content") !is String || message.getString("content").length > 4096)
        ) {
            invalid()
        }
        val calls = message.optJSONArray("tool_calls") ?: invalid()
        if (calls.length() != 1) invalid()
        val call = calls.optJSONObject(0) ?: invalid()
        val id = call.opt("id") as? String ?: invalid()
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,128}")) || call.opt("type") != "function") invalid()
        val function = call.optJSONObject("function") ?: invalid()
        if (function.opt("name") != TOOL) invalid()
        val arguments = function.opt("arguments") as? String ?: invalid()
        if (arguments.length > 256) invalid()
        // Deliberately accept only this tiny JSON shape, not Android's permissive JSON extensions.
        if (!arguments.matches(Regex("""\{[ \t\r\n]*"key"[ \t\r\n]*:[ \t\r\n]*"probe"[ \t\r\n]*\}"""))) invalid()
        return JSONObject().put("id", id).put("type", "function").put(
            "function",
            JSONObject()
                .put("name", TOOL)
                .put("arguments", JSONObject().put("key", "probe").toString()),
        )
    }

    private fun finalText(response: JSONObject): String {
        val (reason, message) = choice(response)
        if (reason != "stop" || !message.isNull("tool_calls") || !message.isNull("function_call")) invalid()
        return (message.opt("content") as? String)?.takeIf { it.length <= 4096 }?.trim() ?: invalid()
    }

    private fun choice(response: JSONObject): Pair<String, JSONObject> {
        val choices = response.optJSONArray("choices") ?: invalid()
        if (choices.length() != 1) invalid()
        val choice = choices.optJSONObject(0) ?: invalid()
        val message = choice.optJSONObject("message") ?: invalid()
        if (message.opt("role") != "assistant") invalid()
        return (choice.opt("finish_reason") as? String ?: invalid()) to message
    }

    private fun invalid(): Nothing = throw ProbeProtocolFailure(ToolProbeFailure.PROTOCOL)

    private companion object {
        const val TOOL = "lookup_demo_value"
    }
}

private class ProbeProtocolFailure(
    val failure: ToolProbeFailure,
) : Exception(failure.name)

private fun probeFailure(error: Exception): ToolProbeFailure =
    when (error) {
        is ProbeProtocolFailure -> error.failure
        is ConversationException ->
            if (error.rejection ==
                CopilotRejection.TOOLS_UNSUPPORTED
            ) {
                ToolProbeFailure.TOOLS_UNSUPPORTED
            } else {
                when (error.problem) {
                    ConversationProblem.ACCOUNT -> ToolProbeFailure.ACCOUNT
                    ConversationProblem.ACCESS -> ToolProbeFailure.ACCESS
                    ConversationProblem.RESTRICTED -> ToolProbeFailure.RESTRICTED
                    ConversationProblem.NETWORK -> ToolProbeFailure.NETWORK
                    ConversationProblem.TIMEOUT -> ToolProbeFailure.TIMEOUT
                    ConversationProblem.SERVICE -> ToolProbeFailure.SERVICE
                    ConversationProblem.USAGE -> ToolProbeFailure.USAGE
                    ConversationProblem.LIMIT -> ToolProbeFailure.LIMIT
                    else -> ToolProbeFailure.PROVIDER
                }
            }
        is AuthenticationException ->
            when (error.problem) {
                AuthenticationProblem.RESTRICTED -> ToolProbeFailure.RESTRICTED
                AuthenticationProblem.NETWORK -> ToolProbeFailure.NETWORK
                AuthenticationProblem.REAUTHENTICATION, AuthenticationProblem.STORAGE -> ToolProbeFailure.ACCOUNT
                else -> ToolProbeFailure.PROVIDER
            }
        else -> ToolProbeFailure.PROVIDER
    }

private fun JSONObject.positiveInt(key: String): Long? =
    (opt(key) as? Number)
        ?.toDouble()
        ?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }
        ?.toLong()
