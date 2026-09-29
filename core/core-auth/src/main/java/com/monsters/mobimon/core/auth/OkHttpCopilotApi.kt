package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationContext
import com.monsters.mobimon.core.domain.ConversationContextSource
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class OkHttpCopilotApi(
    client: OkHttpClient,
    private val nowMillis: () -> Long,
    private val userUrl: HttpUrl = "https://api.github.com/copilot_internal/user".toHttpUrl(),
    private val context: ConversationContextSource = ConversationContextSource { ConversationContext() },
) : CopilotApi {
    // Never follow a provider redirect with a credential or automatically replay a paid request.
    private val client =
        client
            .newBuilder()
            .followRedirects(
                false,
            ).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

    override suspend fun authorize(githubToken: String): CopilotAccess {
        val json =
            request(
                CopilotRequestStage.AUTHORIZATION,
                Request
                    .Builder()
                    .url(userUrl)
                    .header("Authorization", "Bearer $githubToken")
                    .header("X-GitHub-Api-Version", "2022-11-28"),
            )
        if (json.opt("chat_enabled") != true) fail(ConversationProblem.ACCESS)
        val endpoint =
            json.optJSONObject("endpoints")?.opt("api")?.let {
                (it as? String)?.toHttpUrlOrNull() ?: fail(ConversationProblem.PROVIDER)
            } ?: "https://api.githubcopilot.com/".toHttpUrl()
        if (endpoint.scheme != "https" ||
            endpoint.host !in
            setOf(
                "api.githubcopilot.com",
                "api.individual.githubcopilot.com",
                "api.business.githubcopilot.com",
                "api.enterprise.githubcopilot.com",
            ) ||
            endpoint.port != 443 ||
            endpoint.username.isNotEmpty() ||
            endpoint.password.isNotEmpty() ||
            endpoint.encodedPath != "/" ||
            endpoint.query != null ||
            endpoint.fragment != null
        ) {
            fail(ConversationProblem.PROVIDER)
        }
        // Cache the access check briefly, not the OAuth token's actual lifetime. The owner
        // refreshes/validates its credential on every operation before this cache is used.
        return CopilotAccess(githubToken, nowMillis() + 300_000, endpoint)
    }

    override suspend fun models(access: CopilotAccess): List<CopilotModel> {
        val data =
            request(CopilotRequestStage.MODELS, builder(access, "models")).optJSONArray("data")
                ?: fail(ConversationProblem.PROVIDER)
        val models = (0 until data.length()).mapNotNull { data.optJSONObject(it)?.let { item -> parseModel(item) } }
        CopilotDiagnostics.models(data.length(), models.count { it.enabled && it.api != null })
        return models
    }

    override suspend fun complete(
        access: CopilotAccess,
        model: CopilotModel,
        friendId: String,
        messages: List<ConversationTurn>,
    ): String {
        val endpoint = model.api ?: fail(ConversationProblem.PROVIDER)
        val payload =
            withContext(Dispatchers.Default) {
                CopilotMessageCodec.request(model, friendId, messages, context.current()).also {
                    CopilotTokenBudget.requireFits(model, it)
                }
            }
        return CopilotMessageCodec.reply(endpoint, exchange(access, endpoint, payload))
    }

    // Internal wire seam also used by the opt-in Debug protocol probe. Callers enforce their budget.
    internal suspend fun exchange(
        access: CopilotAccess,
        endpoint: CopilotChatApi,
        payload: JSONObject,
    ): JSONObject {
        val body = withContext(Dispatchers.Default) { payload.toString().toRequestBody(JSON_MEDIA_TYPE) }
        // OkHttp can follow a 503 Retry-After: 0 even with connection retries disabled.
        val singleUseBody =
            object : RequestBody() {
                override fun contentType() = body.contentType()

                override fun contentLength() = body.contentLength()

                override fun isOneShot() = true

                override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
            }
        return request(
            CopilotRequestStage.COMPLETION,
            builder(access, endpoint.path)
                .header("X-Initiator", "user")
                .post(singleUseBody),
        )
    }

    private fun validModelId(id: String) = id.matches(Regex("[A-Za-z0-9._:/-]{1,128}"))

    private fun parseModel(item: JSONObject): CopilotModel? {
        val id = item.opt("id") as? String ?: return null
        if (!validModelId(id)) return null
        val capabilities = item.optJSONObject("capabilities") ?: return null
        if (capabilities.optString("type") != "chat") return null
        val endpoints = item.optJSONArray("supported_endpoints")
        val paths = endpoints?.let { (0 until it.length()).map(it::optString) }
        val endpoint =
            when {
                paths == null || "/chat/completions" in paths -> CopilotChatApi.CHAT_COMPLETIONS
                "/responses" in paths -> CopilotChatApi.RESPONSES
                else -> null
            }
        val limits = capabilities.optJSONObject("limits")

        fun positive(key: String): Int? =
            (limits?.opt(key) as? Number)
                ?.toDouble()
                ?.takeIf { it.isFinite() && it >= 1 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }
                ?.toInt()
        val output = positive("max_output_tokens") ?: return null
        val prompt = positive("max_prompt_tokens") ?: return null
        val window = positive("max_context_window_tokens")
        if (limits?.has("max_context_window_tokens") == true && window == null) return null
        val tokenizer =
            capabilities.optString("tokenizer").takeIf { it in setOf("o200k_base", "cl100k_base") }
                ?: return null
        return CopilotModel(
            id,
            endpoint,
            (item.optJSONObject("policy")?.optString("state") ?: "enabled") == "enabled",
            minOf(output, 2048),
            prompt,
            window,
            tokenizer,
        )
    }

    private fun builder(
        access: CopilotAccess,
        path: String,
    ) = Request
        .Builder()
        .url(access.endpoint.resolve(path)!!)
        .header("Authorization", "Bearer ${access.token}")
        .header("X-GitHub-Api-Version", "2026-08-01")
        .header("Copilot-Integration-Id", "mobimon")

    private suspend fun request(
        stage: CopilotRequestStage,
        builder: Request.Builder,
    ): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val call =
                client.newCall(
                    builder
                        .header("Accept", "application/json")
                        .header("User-Agent", "MobiMon/0.1")
                        .header("Editor-Version", "MobiMon/0.1")
                        .header("Editor-Plugin-Version", "mobimon/0.1")
                        .header("X-Request-Id", UUID.randomUUID().toString())
                        .build(),
                )
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                ConversationException(
                                    if (e is InterruptedIOException) {
                                        ConversationProblem.TIMEOUT
                                    } else {
                                        ConversationProblem.NETWORK
                                    },
                                ),
                            )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        try {
                            val json =
                                response.use {
                                    CopilotDiagnostics.http(stage, it.code)
                                    val rejection =
                                        if (!it.isSuccessful) {
                                            val source = it.body?.source()
                                            val error =
                                                if (source != null && !source.request(16_385)) {
                                                    val body = source.readUtf8()
                                                    runCatching { JSONObject(body) }
                                                        .getOrElse { JSONObject().put("message", body) }
                                                } else {
                                                    null
                                                }
                                            CopilotRejection.from(error).also(CopilotDiagnostics::rejection)
                                        } else {
                                            CopilotRejection.UNKNOWN
                                        }
                                    if (!it.isSuccessful && rejection == CopilotRejection.CONTEXT_LIMIT) {
                                        fail(ConversationProblem.LIMIT)
                                    }
                                    when (it.code) {
                                        401 -> fail(ConversationProblem.ACCOUNT)
                                        403 -> {
                                            throw ConversationException(
                                                if (rejection ==
                                                    CopilotRejection.RATE_LIMIT
                                                ) {
                                                    ConversationProblem.USAGE
                                                } else {
                                                    ConversationProblem.ACCESS
                                                },
                                                rejection,
                                            )
                                        }
                                        402, 429 -> fail(ConversationProblem.USAGE)
                                        408, 504 -> fail(ConversationProblem.TIMEOUT)
                                        in 500..599 -> fail(ConversationProblem.SERVICE)
                                    }
                                    if (!it.isSuccessful) {
                                        throw ConversationException(ConversationProblem.PROVIDER, rejection)
                                    }
                                    val source = it.body?.source() ?: fail(ConversationProblem.PROVIDER)
                                    if (source.request(1_048_577)) fail(ConversationProblem.PROVIDER)
                                    JSONObject(source.readUtf8())
                                }
                            if (continuation.isActive) continuation.resume(json)
                        } catch (error: Exception) {
                            val problem =
                                when (error) {
                                    is ConversationException -> error.problem
                                    is InterruptedIOException -> ConversationProblem.TIMEOUT
                                    is IOException -> ConversationProblem.NETWORK
                                    else -> ConversationProblem.PROVIDER
                                }
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    if (error is ConversationException) error else ConversationException(problem),
                                )
                            }
                        }
                    }
                },
            )
        }

    private fun fail(problem: ConversationProblem): Nothing = throw ConversationException(problem)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
