package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Real transport for explicitly invoked synthetic diagnostics, with the normal identity and interaction guards. */
suspend fun <T> PersistentGitHubAuthentication.withToolDiagnosticProvider(
    tools: ConversationTools,
    run: suspend (ConversationProvider) -> T,
): T {
    val client = CopilotConversationProvider.httpClient()
    try {
        return run(
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
            ),
        )
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
