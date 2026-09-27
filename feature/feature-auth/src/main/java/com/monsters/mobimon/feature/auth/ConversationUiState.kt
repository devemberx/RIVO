package com.monsters.mobimon.feature.auth

import com.monsters.mobimon.core.domain.ConversationProblem

/** Presentation only. Authentication does not establish [ConversationConnection.READY]. */
enum class ConversationConnection { SIGNED_OUT, CHECKING, UNAVAILABLE, READY }

data class ConversationMessage(
    val id: String,
    val text: String,
    val fromUser: Boolean,
)

data class ConversationUiState(
    val connection: ConversationConnection = ConversationConnection.UNAVAILABLE,
    val messages: List<ConversationMessage> = emptyList(),
    val replyPending: Boolean = false,
    val failed: Boolean = false,
    val problem: ConversationProblem? = null,
    val connectionProblem: ConversationProblem? = null,
    val connectionRetrying: Boolean = false,
    val voice: VoiceInputState = VoiceInputState(),
    val hasConversationHistory: Boolean = false,
)
