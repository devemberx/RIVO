package com.monsters.mobimon.core.domain

/** Android's application sandbox supplies the outer user boundary; display names never identify owners. */
data class ConversationKey(
    val profileId: String,
    val friendId: String,
)

class StoredConversation(
    val id: String,
    val accountId: Long,
    val revision: Long,
    val turns: List<ConversationTurn>,
) {
    override fun toString() = "StoredConversation(REDACTED)"
}

interface ConversationStore {
    /** A different provider account replaces the current thread; there is no account archive. */
    suspend fun load(
        key: ConversationKey,
        accountId: Long,
    ): StoredConversation

    suspend fun reset(
        key: ConversationKey,
        accountId: Long,
    ): StoredConversation

    /** Compare-and-set protects reset and owner changes from late replies. Null means ownership changed. */
    suspend fun append(
        key: ConversationKey,
        expected: StoredConversation,
        user: String,
        reply: String,
    ): StoredConversation?
}
