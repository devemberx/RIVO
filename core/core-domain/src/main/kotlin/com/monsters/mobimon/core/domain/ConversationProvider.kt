package com.monsters.mobimon.core.domain

/** Credentials stay inside the provider implementation. Current dialogue storage is owned by ConversationStore. */
interface ConversationProvider {
    /** Checks account access and returns the selected model ID. */
    suspend fun connect(accountId: Long): ConversationResult<String>

    /** A stable current-thread ID identifies the local dialogue. */
    suspend fun reply(
        accountId: Long,
        conversationId: String,
        friendId: String,
        messages: List<ConversationTurn>,
    ): ConversationResult<String>
}

class ConversationTurn(
    val text: String,
    val fromUser: Boolean,
) {
    override fun toString() = "ConversationTurn(REDACTED)"
}

sealed interface ConversationResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ConversationResult<T>

    data class Failure(
        val problem: ConversationProblem,
    ) : ConversationResult<Nothing>
}

enum class ConversationProblem {
    NETWORK,
    SERVICE,
    TIMEOUT,
    ACCESS,
    ACCOUNT,
    USAGE,
    PROVIDER,
    RESTRICTED,
    LIMIT,
    STORAGE,
}

object ConversationLimits {
    const val INPUT_CHARACTERS = 4_000
    const val REPLY_CHARACTERS = 12_000
}
