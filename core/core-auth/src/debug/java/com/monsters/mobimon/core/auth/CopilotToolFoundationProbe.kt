package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationReplyPolicy
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTool
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolDefinition
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.ConversationToolUsage
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ConversationTurn
import com.monsters.mobimon.core.domain.GitHubSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

/** Exercises the same provider and executor used by app bindings, without persisting synthetic turns. */
suspend fun PersistentGitHubAuthentication.probeToolFoundation(
    onUsage: (ConversationToolUsage) -> Unit,
): ConversationResult<Boolean> {
    val account =
        (session.value as? GitHubSession.Authenticated)?.account
            ?: return ConversationResult.Failure(ConversationProblem.ACCOUNT)
    var expected: String? = null
    val tool =
        object : ConversationTool {
            override val definition =
                ConversationToolDefinition(
                    "lookup_demo_value",
                    "Read the current synthetic value for the probe key.",
                    "key",
                    "Use probe.",
                    20,
                )

            override suspend fun execute(call: ConversationToolCall): ConversationToolResult {
                if (call.argument != "probe") return ConversationToolResult.NoEvidence
                val value = UUID.randomUUID().toString()
                expected = value
                return ConversationToolResult.Found(value)
            }
        }
    val tools =
        ConversationTools(
            listOf(tool),
            "For this synthetic protocol test, use lookup_demo_value with key probe, then reply with exactly the returned value.",
            ConversationReplyPolicy { text, result ->
                if (result != null && expected != null && text.trim() == expected) {
                    ConversationResult.Success("VERIFIED")
                } else {
                    ConversationResult.Failure(ConversationProblem.PROVIDER)
                }
            },
            onUsage,
        )
    val client = CopilotConversationProvider.httpClient()
    try {
        val provider =
            CopilotConversationProvider(
                ::conversationCredential,
                ::isCurrent,
                {
                    requireInteraction()
                    true
                },
                OkHttpCopilotApi(client, System::currentTimeMillis),
                System::currentTimeMillis,
                ::rejectConversationCredential,
                tools,
            )
        repeat(2) {
            expected = null
            when (
                val result =
                    provider.reply(
                        account.id,
                        "tool-foundation-probe",
                        "friend:mobi",
                        listOf(
                            ConversationTurn(
                                "Look up the current probe value and return it exactly, without other words.",
                                true,
                            ),
                        ),
                    )
            ) {
                is ConversationResult.Failure -> return result
                is ConversationResult.Success ->
                    if (result.value !=
                        "VERIFIED"
                    ) {
                        return ConversationResult.Failure(ConversationProblem.PROVIDER)
                    }
            }
        }
        return ConversationResult.Success(true)
    } finally {
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                client.connectionPool.evictAll()
            } finally {
                client.dispatcher.executorService.shutdown()
            }
        }
    }
}
