package com.monsters.mobimon.core.auth

import android.content.Context
import android.os.SystemClock
import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.GitHubSignIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class PersistentGitHubAuthentication internal constructor(
    private val clientId: String,
    private val api: GitHubApi,
    private val store: CredentialStore,
    private val elapsedMillis: () -> Long,
    private val nowMillis: () -> Long,
    private val interactionAllowed: () -> Boolean,
) : GitHubAuthentication {
    private val mutex = Mutex()
    private val credentialRevision = AtomicLong()
    private var validatedAccessToken: String? = null
    private val mutableSession = MutableStateFlow<GitHubSession>(GitHubSession.Restoring)
    override val session = mutableSession.asStateFlow()
    override val configured = clientId.isNotBlank()

    override suspend fun restore() =
        mutex.withLock {
            invalidateCredential()
            mutableSession.value = GitHubSession.Restoring
            if (!configured) {
                mutableSession.value = GitHubSession.SignedOut
                return@withLock
            }
            try {
                val stored = storage { store.read() }
                if (stored == null) {
                    mutableSession.value = GitHubSession.SignedOut
                    return@withLock
                }
                if (stored.clientId != clientId) {
                    storage { store.clear() }
                    mutableSession.value = GitHubSession.SignedOut
                    return@withLock
                }
                var tokens = stored.tokens
                val expired = tokens.expiresAtMillis?.let { it <= nowMillis() + 60_000 } == true
                if (expired) tokens = refresh(tokens)
                val account =
                    try {
                        api.account(tokens.accessToken)
                    } catch (error: AuthenticationException) {
                        if (!expired &&
                            error.problem == AuthenticationProblem.REAUTHENTICATION &&
                            tokens.refreshToken != null
                        ) {
                            tokens = refresh(tokens)
                            api.account(tokens.accessToken)
                        } else {
                            throw error
                        }
                    }
                currentCoroutineContext().ensureActive()
                validatedAccessToken = tokens.accessToken
                mutableSession.value = GitHubSession.Authenticated(account)
            } catch (error: AuthenticationException) {
                if (error.problem == AuthenticationProblem.REAUTHENTICATION) {
                    try {
                        storage { store.clear() }
                    } catch (failure: AuthenticationException) {
                        mutableSession.value = GitHubSession.Failure(failure.problem)
                        return@withLock
                    }
                }
                mutableSession.value = GitHubSession.Failure(error.problem)
            }
        }

    override fun signIn() =
        flow {
            mutex.withLock {
                try {
                    if (!configured) throw AuthenticationException(AuthenticationProblem.CONFIGURATION)
                    requireInteraction()
                    emit(GitHubSignIn.Requesting)
                    val started = elapsedMillis()
                    val code = api.requestCode()
                    val deadline = started + code.expiresInSeconds * 1000L
                    var interval = code.intervalSeconds * 1000L
                    var nextPoll = elapsedMillis() + interval
                    var retrying = false
                    while (elapsedMillis() < deadline) {
                        requireInteraction()
                        emit(
                            GitHubSignIn.Waiting(
                                code.userCode,
                                code.verificationUri,
                                ((deadline - elapsedMillis() + 999) / 1000).toInt(),
                                retrying,
                            ),
                        )
                        if (elapsedMillis() >= nextPoll) {
                            val result =
                                try {
                                    withTimeoutOrNull((deadline - elapsedMillis()).coerceAtLeast(1)) {
                                        api.poll(code.deviceCode)
                                    }.also { retrying = false }
                                        ?: TokenPoll.Expired
                                } catch (error: AuthenticationException) {
                                    if (error.problem != AuthenticationProblem.NETWORK) throw error
                                    retrying = true
                                    interval = (interval * 2).coerceAtMost(60_000).coerceAtLeast(interval)
                                    TokenPoll.Pending
                                }
                            requireInteraction()
                            when (result) {
                                is TokenPoll.Approved -> {
                                    if (elapsedMillis() >= deadline) break
                                    currentCoroutineContext().ensureActive()
                                    emit(GitHubSignIn.Requesting)
                                    invalidateCredential()
                                    storage { store.write(StoredCredential(clientId, result.tokens)) }
                                    val account = api.account(result.tokens.accessToken)
                                    requireInteraction()
                                    currentCoroutineContext().ensureActive()
                                    validatedAccessToken = result.tokens.accessToken
                                    mutableSession.value = GitHubSession.Authenticated(account)
                                    emit(GitHubSignIn.Complete)
                                    return@withLock
                                }
                                TokenPoll.Expired -> break
                                is TokenPoll.SlowDown ->
                                    interval =
                                        maxOf(interval + 5000, (result.intervalSeconds ?: 0) * 1000L)
                                TokenPoll.Pending -> Unit
                            }
                            nextPoll = elapsedMillis() + interval
                        }
                        delay(minOf(1000L, deadline - elapsedMillis()).coerceAtLeast(1))
                    }
                    emit(GitHubSignIn.Expired)
                } catch (error: AuthenticationException) {
                    emit(GitHubSignIn.Failed(error.problem))
                }
            }
        }

    override suspend fun disconnect() =
        mutex.withLock {
            invalidateCredential()
            try {
                storage { store.clear() }
                mutableSession.value = GitHubSession.SignedOut
            } catch (error: AuthenticationException) {
                mutableSession.value = GitHubSession.Failure(error.problem)
            }
        }

    fun conversationProvider(
        context: com.monsters.mobimon.core.domain.ConversationContextSource =
            com.monsters.mobimon.core.domain
                .ConversationContextSource {
                    com.monsters.mobimon.core.domain
                        .ConversationContext()
                },
        tools: ConversationTools = ConversationTools.None,
    ): ConversationProvider = CopilotConversationProvider.create(this, interactionAllowed, context, tools)

    internal suspend fun conversationCredential(accountId: Long): ConversationCredential =
        mutex.withLock {
            requireInteraction()
            if ((session.value as? GitHubSession.Authenticated)?.account?.id != accountId) {
                throw AuthenticationException(AuthenticationProblem.REAUTHENTICATION)
            }
            val stored = storage { store.read() }
            if (stored == null || stored.clientId != clientId) {
                throw AuthenticationException(AuthenticationProblem.REAUTHENTICATION)
            }
            var tokens = stored.tokens
            try {
                if (tokens.expiresAtMillis?.let { it <= nowMillis() + 60_000 } == true) {
                    tokens = refresh(tokens)
                }
                // A rotated token may have been persisted before identity validation failed or was cancelled.
                if (tokens.accessToken != validatedAccessToken) {
                    val account = api.account(tokens.accessToken)
                    if (account.id != accountId) throw AuthenticationException(AuthenticationProblem.REAUTHENTICATION)
                    currentCoroutineContext().ensureActive()
                    requireInteraction()
                    validatedAccessToken = tokens.accessToken
                }
            } catch (error: AuthenticationException) {
                if (error.problem == AuthenticationProblem.REAUTHENTICATION) {
                    invalidateCredential()
                    storage { store.clear() }
                    mutableSession.value = GitHubSession.Failure(error.problem)
                }
                throw error
            }
            requireInteraction()
            ConversationCredential(accountId, credentialRevision.get(), tokens.accessToken)
        }

    internal fun isCurrent(credential: ConversationCredential): Boolean =
        credential.revision == credentialRevision.get() &&
            (session.value as? GitHubSession.Authenticated)?.account?.id == credential.accountId

    internal suspend fun rejectConversationCredential(credential: ConversationCredential) =
        mutex.withLock {
            if (!isCurrent(credential)) return@withLock
            invalidateCredential()
            // Copilot's rejection does not prove GitHub revocation. Keep storage for explicit restore/refresh.
            mutableSession.value = GitHubSession.Failure(AuthenticationProblem.PROVIDER)
        }

    private fun invalidateCredential() {
        credentialRevision.incrementAndGet()
        validatedAccessToken = null
    }

    private suspend fun refresh(tokens: GitHubTokens): GitHubTokens {
        invalidateCredential()
        val refresh = tokens.refreshToken ?: throw AuthenticationException(AuthenticationProblem.REAUTHENTICATION)
        if (tokens.refreshExpiresAtMillis?.let { it <= nowMillis() } == true) {
            throw AuthenticationException(AuthenticationProblem.REAUTHENTICATION)
        }
        val replacement = api.refresh(refresh)
        // Refresh rotates both tokens. Persist immediately so a later identity/network failure cannot lose the replacement.
        storage { store.write(StoredCredential(clientId, replacement)) }
        return replacement
    }

    internal fun requireInteraction() {
        if (!interactionAllowed()) throw AuthenticationException(AuthenticationProblem.RESTRICTED)
    }

    private suspend fun <T> storage(block: suspend () -> T): T =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw AuthenticationException(AuthenticationProblem.STORAGE)
        }

    companion object {
        fun create(
            context: Context,
            clientId: String,
            interactionAllowed: () -> Boolean,
        ): PersistentGitHubAuthentication {
            val client =
                OkHttpClient
                    .Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .callTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .retryOnConnectionFailure(false)
                    .build()
            return PersistentGitHubAuthentication(
                clientId,
                OkHttpGitHubApi(client, clientId, System::currentTimeMillis),
                EncryptedCredentialStore(context.applicationContext),
                SystemClock::elapsedRealtime,
                System::currentTimeMillis,
                interactionAllowed,
            )
        }
    }
}
