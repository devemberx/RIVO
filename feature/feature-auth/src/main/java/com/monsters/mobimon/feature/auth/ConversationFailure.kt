package com.monsters.mobimon.feature.auth

import com.monsters.mobimon.core.domain.ConversationProblem

internal fun conversationFailureNote(problem: ConversationProblem?): Int =
    when (problem) {
        ConversationProblem.NETWORK -> R.string.chat_network_error
        ConversationProblem.SERVICE -> R.string.chat_service_error
        ConversationProblem.TIMEOUT -> R.string.chat_timeout_error
        ConversationProblem.ACCESS -> R.string.chat_access_error
        ConversationProblem.ACCOUNT -> R.string.chat_account_error
        ConversationProblem.USAGE -> R.string.chat_usage_error
        ConversationProblem.PROVIDER -> R.string.chat_provider_error
        ConversationProblem.RESTRICTED -> R.string.chat_restricted_error
        ConversationProblem.STORAGE -> R.string.chat_storage_error
        ConversationProblem.LIMIT -> R.string.chat_limit_error
        ConversationProblem.NO_EVIDENCE -> R.string.chat_no_evidence
        ConversationProblem.TOOL_UNAVAILABLE -> R.string.chat_tool_unavailable
        null -> R.string.chat_inline_failure_note
    }

internal fun conversationRetryLabel(problem: ConversationProblem?): Int =
    when (problem) {
        ConversationProblem.ACCOUNT -> R.string.conversation_connect
        ConversationProblem.ACCESS -> R.string.chat_network_recheck
        ConversationProblem.STORAGE -> R.string.chat_storage_retry
        ConversationProblem.LIMIT -> R.string.chat_new
        else -> R.string.chat_retry
    }
