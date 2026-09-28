package com.monsters.mobimon.core.domain

/** Optional data read at send time; never a storage identity or an instruction. */
class ConversationContext(
    val userName: String? = null,
    val vssTimestamp: String? = null,
    val timeOfDay: String? = null,
    val simulatedTime: Boolean = false,
) {
    override fun toString() = "ConversationContext(REDACTED)"
}

fun interface ConversationContextSource {
    fun current(): ConversationContext
}
