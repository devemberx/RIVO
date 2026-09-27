package com.monsters.mobimon.core.auth

import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTurn
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class CopilotConversationProviderTest {
    private var now = 1_000L
    private var allowed = true
    private var current = true
    private var revision = 1L
    private var accessLifetime = 120_000L
    private val api = FakeApi()
    private val provider =
        CopilotConversationProvider(
            { ConversationCredential(it, revision, "github-token") },
            { current },
            { allowed },
            api,
            { now },
            { current = false },
        )

    @Test fun readinessRequiresExchangeAndModelAndRefreshesBeforeExpiryOrAccountRevisionChange() =
        runTest {
            assertEquals(ConversationResult.Success("gpt-4o"), provider.connect(1))
            assertEquals(
                ConversationResult.Success("answer"),
                provider.reply(1, "conversation", "friend:mobi", listOf(ConversationTurn("hello", true))),
            )
            assertEquals(1, api.exchanges)
            now = 100_000
            provider.connect(1)
            assertEquals(2, api.exchanges)
            revision++
            provider.connect(1)
            assertEquals(3, api.exchanges)
            provider.connect(2)
            assertEquals(4, api.exchanges)
        }

    @Test fun connectionRecheckRequiresFreshProviderResponsesDespiteValidCache() =
        runTest {
            accessLifetime = 3_600_000
            assertEquals(ConversationResult.Success("gpt-4o"), provider.connect(1))
            api.afterExchange = { throw ConversationException(ConversationProblem.NETWORK) }
            assertEquals(ConversationResult.Failure(ConversationProblem.NETWORK), provider.connect(1))
            api.afterExchange = {}
            api.afterModels = { throw ConversationException(ConversationProblem.TIMEOUT) }
            assertEquals(ConversationResult.Failure(ConversationProblem.TIMEOUT), provider.connect(1))
            api.afterModels = {}
            assertEquals(ConversationResult.Success("gpt-4o"), provider.connect(1))
            assertEquals(4, api.exchanges)
            assertEquals(3, api.models)
            assertEquals(0, api.completions)
        }

    @Test fun parkingAndAccountAreRecheckedBetweenNetworkCallsAndBeforeReturningReply() =
        runTest {
            api.afterExchange = { allowed = false }
            assertEquals(ConversationResult.Failure(ConversationProblem.RESTRICTED), provider.connect(1))
            assertEquals(0, api.models)
            allowed = true
            api.afterExchange = {}
            api.afterComplete = { current = false }
            assertEquals(
                ConversationResult.Failure(ConversationProblem.ACCOUNT),
                provider.reply(1, "conversation", "friend:mobi", listOf(ConversationTurn("hello", true))),
            )
        }

    @Test fun invalidHistoryAndRestrictionsDoNotStartNetworkCalls() =
        runTest {
            for (messages in listOf(
                emptyList(),
                listOf(ConversationTurn("assistant-first", false)),
                listOf(ConversationTurn(" ", true)),
                listOf(ConversationTurn("x".repeat(4001), true)),
            )) {
                assertEquals(
                    ConversationResult.Failure(ConversationProblem.LIMIT),
                    provider.reply(1, "conversation", "friend:mobi", messages),
                )
            }
            allowed = false
            assertEquals(ConversationResult.Failure(ConversationProblem.RESTRICTED), provider.connect(1))
            assertEquals(0, api.exchanges)
        }

    @Test fun catalogIsCachedAcrossConversationsUntilAccountChanges() =
        runTest {
            accessLifetime = 3_600_000
            val messages = listOf(ConversationTurn("same prompt", true))
            provider.connect(1)
            assertEquals(1, api.models)
            assertEquals(0, api.completions)
            provider.reply(1, "first", "friend:mobi", messages)
            provider.reply(1, "first", "friend:mobi", messages)
            provider.reply(1, "new", "friend:mobi", messages)
            provider.reply(1, "new", "friend:luna", messages)
            assertEquals(1, api.models)
            assertEquals(4, api.completions)
            provider.reply(2, "new", "friend:luna", messages)
            assertEquals(2, api.exchanges)
            assertEquals(2, api.models)
        }

    @Test fun guardsBlockCompletionAfterCatalogLookup() =
        runTest {
            val messages = listOf(ConversationTurn("hello", true))
            api.afterModels = { allowed = false }
            assertEquals(
                ConversationResult.Failure(ConversationProblem.RESTRICTED),
                provider.reply(1, "first", "friend:mobi", messages),
            )
            assertEquals(0, api.completions)
            allowed = true
            api.afterModels = { current = false }
            assertEquals(
                ConversationResult.Failure(ConversationProblem.ACCOUNT),
                provider.reply(1, "first", "friend:mobi", messages),
            )
            assertEquals(0, api.completions)
        }

    @Test fun fixedGpt4oUsesTheCatalogEntry() =
        runTest {
            api.availableModels =
                listOf(
                    CopilotModel("other", CopilotChatApi.CHAT_COMPLETIONS),
                    CopilotModel("gpt-4o", CopilotChatApi.CHAT_COMPLETIONS),
                )
            assertEquals(ConversationResult.Success("gpt-4o"), provider.connect(1))
            assertEquals(
                ConversationResult.Success("answer"),
                provider.reply(1, "conversation", "friend:mobi", listOf(ConversationTurn("hello", true))),
            )
            assertEquals(1, api.models)
            assertEquals("gpt-4o", api.completedModelId)
        }

    @Test fun missingFixedModelPreventsACompletionRequest() =
        runTest {
            api.availableModels = listOf(CopilotModel("other", CopilotChatApi.CHAT_COMPLETIONS))
            assertEquals(
                ConversationResult.Failure(ConversationProblem.ACCESS),
                provider.reply(1, "conversation", "friend:mobi", listOf(ConversationTurn("hello", true))),
            )
            assertEquals(0, api.completions)
        }

    private inner class FakeApi : CopilotApi {
        var exchanges = 0
        var models = 0
        var completions = 0
        var availableModels = listOf(CopilotModel("gpt-4o", CopilotChatApi.CHAT_COMPLETIONS))
        var completedModelId: String? = null
        var afterExchange: () -> Unit = {}
        var afterModels: () -> Unit = {}
        var afterComplete: () -> Unit = {}

        override suspend fun authorize(githubToken: String): CopilotAccess {
            exchanges++
            afterExchange()
            return CopilotAccess("short-lived", now + accessLifetime, "https://api.githubcopilot.com/".toHttpUrl())
        }

        override suspend fun models(access: CopilotAccess): List<CopilotModel> {
            models++
            afterModels()
            return availableModels
        }

        override suspend fun complete(
            access: CopilotAccess,
            model: CopilotModel,
            friendId: String,
            messages: List<ConversationTurn>,
        ): String {
            completions++
            completedModelId = model.id
            afterComplete()
            return "answer"
        }
    }
}
