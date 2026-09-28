package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.domain.ConversationContextSource
import com.monsters.mobimon.core.domain.ConversationLimits
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ConversationTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal class ConversationCredential(
    val accountId: Long,
    val revision: Long,
    val token: String,
) {
    override fun toString() = "ConversationCredential(REDACTED)"
}

internal class CopilotAccess(
    val token: String,
    val expiresAtMillis: Long,
    val endpoint: HttpUrl,
) {
    override fun toString() = "CopilotAccess(REDACTED)"
}

internal class ConversationException(
    val problem: ConversationProblem,
    val rejection: CopilotRejection = CopilotRejection.UNKNOWN,
) : Exception(problem.name)

internal enum class CopilotChatApi(
    val path: String,
) {
    CHAT_COMPLETIONS("chat/completions"),
    RESPONSES("responses"),
}

internal data class CopilotModel(
    val id: String,
    val api: CopilotChatApi?,
    val enabled: Boolean = true,
    val maxOutputTokens: Int = 2048,
    val maxPromptTokens: Int? = null,
    val maxContextWindowTokens: Int? = null,
    val tokenizer: String? = null,
)

internal interface CopilotApi {
    suspend fun authorize(githubToken: String): CopilotAccess

    suspend fun models(access: CopilotAccess): List<CopilotModel>

    suspend fun complete(
        access: CopilotAccess,
        model: CopilotModel,
        friendId: String,
        messages: List<ConversationTurn>,
    ): String

    suspend fun completeWithTools(
        access: CopilotAccess,
        model: CopilotModel,
        friendId: String,
        messages: List<ConversationTurn>,
        tools: ConversationTools,
        guard: suspend () -> Unit,
    ): String {
        if (tools.tools.isNotEmpty()) throw ConversationException(ConversationProblem.PROVIDER)
        guard()
        return complete(access, model, friendId, messages).also { guard() }
    }
}

/** Experimental native adapter. Successful GitHub authentication alone never establishes readiness. */
internal class CopilotConversationProvider(
    private val credential: suspend (Long) -> ConversationCredential,
    private val isCurrent: (ConversationCredential) -> Boolean,
    private val interactionAllowed: () -> Boolean,
    private val api: CopilotApi,
    private val nowMillis: () -> Long,
    private val rejectCredential: suspend (ConversationCredential) -> Unit,
    private val tools: ConversationTools = ConversationTools.None,
) : ConversationProvider {
    private val mutex = Mutex()
    private var cachedCredential: ConversationCredential? = null
    private var access: CopilotAccess? = null
    private var selectedModel: CopilotModel? = null

    override suspend fun connect(accountId: Long): ConversationResult<String> =
        operation(accountId) { lease ->
            prepare(lease, forceRefresh = true)
            selectedModel!!.id
        }

    override suspend fun reply(
        accountId: Long,
        conversationId: String,
        friendId: String,
        messages: List<ConversationTurn>,
    ): ConversationResult<String> =
        withTimeoutOrNull(30_000) {
            operation(accountId) { lease ->
                if (conversationId.isBlank() ||
                    conversationId.length > 128 ||
                    messages.isEmpty() ||
                    messages.size % 2 != 1 ||
                    messages.withIndex().any { (index, message) ->
                        message.fromUser != (index % 2 == 0) ||
                            message.text.isBlank() ||
                            message.text.length >
                            if (message.fromUser) {
                                ConversationLimits.INPUT_CHARACTERS
                            } else {
                                ConversationLimits.REPLY_CHARACTERS
                            }
                    }
                ) {
                    throw ConversationException(ConversationProblem.LIMIT)
                }
                coroutineScope {
                    val monitor =
                        if (tools.tools.isNotEmpty()) {
                            launch {
                                while (true) {
                                    delay(100)
                                    guard(lease)
                                }
                            }
                        } else {
                            null
                        }
                    try {
                        prepare(lease)
                        guard(lease)
                        api.completeWithTools(access!!, selectedModel!!, friendId, messages, tools) { guard(lease) }
                    } finally {
                        monitor?.cancel()
                    }
                }
            }
        } ?: ConversationResult.Failure(ConversationProblem.TIMEOUT)

    private suspend fun prepare(
        lease: ConversationCredential,
        forceRefresh: Boolean = false,
    ) {
        val cached = cachedCredential
        if (!forceRefresh &&
            cached?.accountId == lease.accountId &&
            cached.revision == lease.revision &&
            cached.token == lease.token &&
            access?.expiresAtMillis?.let { it > nowMillis() + 60_000 } == true &&
            selectedModel != null
        ) {
            return
        }
        access = null
        selectedModel = null
        cachedCredential = null
        guard(lease)
        val next = api.authorize(lease.token)
        guard(lease)
        val available = api.models(next)
        guard(lease)
        val fixedModel =
            available.firstOrNull {
                it.id == "gpt-4o" && it.enabled && it.api == CopilotChatApi.CHAT_COMPLETIONS
            } ?: throw ConversationException(ConversationProblem.ACCESS)
        if (next.expiresAtMillis <= nowMillis() + 60_000) throw ConversationException(ConversationProblem.PROVIDER)
        access = next
        selectedModel = fixedModel
        cachedCredential = lease
    }

    private suspend fun guard(lease: ConversationCredential) {
        currentCoroutineContext().ensureActive()
        if (!interactionAllowed()) throw ConversationException(ConversationProblem.RESTRICTED)
        if (!isCurrent(lease)) throw ConversationException(ConversationProblem.ACCOUNT)
    }

    private suspend fun <T> operation(
        accountId: Long,
        block: suspend (ConversationCredential) -> T,
    ): ConversationResult<T> =
        mutex.withLock {
            try {
                if (!interactionAllowed()) throw ConversationException(ConversationProblem.RESTRICTED)
                val lease = credential(accountId)
                guard(lease)
                val result =
                    try {
                        block(lease)
                    } catch (error: ConversationException) {
                        if (error.problem == ConversationProblem.ACCOUNT) rejectCredential(lease)
                        throw error
                    }
                guard(lease)
                ConversationResult.Success(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                access = null
                cachedCredential = null
                selectedModel = null
                val problem =
                    when (error) {
                        is ConversationException -> error.problem
                        is AuthenticationException ->
                            when (error.problem) {
                                AuthenticationProblem.NETWORK -> ConversationProblem.NETWORK
                                AuthenticationProblem.RESTRICTED -> ConversationProblem.RESTRICTED
                                AuthenticationProblem.REAUTHENTICATION, AuthenticationProblem.STORAGE ->
                                    ConversationProblem.ACCOUNT
                                else -> ConversationProblem.PROVIDER
                            }
                        else -> ConversationProblem.PROVIDER
                    }
                ConversationResult.Failure(problem)
            }
        }

    companion object {
        fun create(
            authentication: PersistentGitHubAuthentication,
            interactionAllowed: () -> Boolean,
            context: ConversationContextSource,
            tools: ConversationTools,
        ): ConversationProvider {
            val client = httpClient()
            return CopilotConversationProvider(
                authentication::conversationCredential,
                authentication::isCurrent,
                interactionAllowed,
                OkHttpCopilotApi(client, System::currentTimeMillis, context = context),
                System::currentTimeMillis,
                authentication::rejectConversationCredential,
                tools,
            )
        }

        internal fun httpClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build()
    }
}
