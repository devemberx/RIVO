package com.monsters.mobimon.feature.auth

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelStore
import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.domain.ConversationKey
import com.monsters.mobimon.core.domain.ConversationLimits
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationStore
import com.monsters.mobimon.core.domain.ConversationTurn
import com.monsters.mobimon.core.domain.GitHubAccount
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.GitHubSignIn
import com.monsters.mobimon.core.domain.StoredConversation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    private val authentication = FakeAuthentication()
    private val provider = FakeProvider()
    private val networkStatus =
        object : ConversationNetworkStatus {
            override val online = MutableStateFlow(true)

            override fun isOnline() = online.value
        }
    private val store = ViewModelStore()

    @After fun close() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun acceptedMicrophoneRequestClearsTypedInputBeforePermissionResult() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model(FakeSpeech())
            runCurrent()
            model.edit(TextFieldValue("unsent text"))
            model.setVoiceResumed(true)

            val permission = requireNotNull(model.requestVoice(false))
            assertEquals(TextFieldValue(), model.draft)
            model.voicePermissionResult(permission, false)
            assertEquals(TextFieldValue(), model.draft)
            assertEquals(null, model.state.value.voice.problem)

            model.edit(TextFieldValue("new draft"))
            model.setVoiceResumed(false)
            model.requestVoice(true)
            assertEquals("new draft", model.draft.text)
        }

    @Test fun leavingForHomeClearsOnlyTheComposer() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            model.edit(TextFieldValue("first"))
            model.send()
            runCurrent()
            val conversation = provider.conversationIds.single()
            model.edit(TextFieldValue("unsent text"))

            model.clearDraftForHome()
            assertEquals(TextFieldValue(), model.draft)
            assertEquals(
                listOf("first", "answer"),
                model.state.value.messages
                    .map { it.text },
            )

            model.edit(TextFieldValue("second"))
            model.send()
            runCurrent()
            assertEquals(conversation, provider.conversationIds.last())
            assertEquals(listOf("first", "answer", "second"), provider.requests.last().map { it.text })
        }

    @Test fun leavingForHomeWhileWaitingDoesNotRestoreTheSentText() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { CompletableDeferred<ConversationResult<String>>().await() }
            model.edit(TextFieldValue("sent before Home"))
            model.send()
            runCurrent()
            assertTrue(model.state.value.replyPending)

            model.clearDraftForHome()
            model.deactivate()

            assertFalse(model.state.value.replyPending)
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )
            assertEquals("", model.draft.text)
        }

    @Test fun modelCheckConnectsBeforeSendAndDuplicateSendIsBlocked() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            model.edit(TextFieldValue("hello"))
            assertEquals(1, provider.connections)
            assertEquals(0, provider.requests.size)
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            val reply = CompletableDeferred<ConversationResult<String>>()
            provider.answer = { reply.await() }
            model.send()
            model.send()
            runCurrent()
            assertEquals(1, provider.requests.size)
            assertTrue(model.state.value.replyPending)
            assertEquals("", model.draft.text)
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            reply.complete(ConversationResult.Success("hi"))
            runCurrent()
            assertEquals(
                listOf("hello", "hi"),
                model.state.value.messages
                    .map { it.text },
            )
            assertEquals("", model.draft.text)
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            model.edit(TextFieldValue("more"))
            model.send()
            runCurrent()
            assertEquals(listOf("hello", "hi", "more"), provider.requests.last().map { it.text })
        }

    @Test fun networkFailureRechecksModelWithoutSendingDraftAndBlocksDuplicateChecks() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            provider.connectionAnswer = { ConversationResult.Failure(ConversationProblem.NETWORK) }
            val model = model()
            runCurrent()
            assertEquals(ConversationConnection.UNAVAILABLE, model.state.value.connection)
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            model.edit(TextFieldValue("keep this draft"))
            model.send()
            runCurrent()
            assertTrue(provider.requests.isEmpty())

            val recheck = CompletableDeferred<ConversationResult<String>>()
            provider.connectionAnswer = { recheck.await() }
            model.retryConnection()
            model.retryConnection()
            runCurrent()
            assertEquals(2, provider.connections)
            assertEquals(ConversationConnection.CHECKING, model.state.value.connection)
            assertTrue(model.state.value.connectionRetrying)
            assertTrue(provider.requests.isEmpty())
            recheck.complete(ConversationResult.Success("gpt-4o"))
            runCurrent()
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            assertEquals(null, model.state.value.connectionProblem)
            assertEquals("keep this draft", model.draft.text)
        }

    @Test fun cancellingPendingReplyReturnsItsTextToTheComposer() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { CompletableDeferred<ConversationResult<String>>().await() }
            model.edit(TextFieldValue("cancel this message"))
            model.send()
            runCurrent()

            assertTrue(model.state.value.replyPending)
            assertEquals("", model.draft.text)
            model.cancel()

            assertFalse(model.state.value.replyPending)
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )
            assertEquals("cancel this message", model.draft.text)
        }

    @Test fun parkingLossCancelsLateModelCheck() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val late = CompletableDeferred<ConversationResult<String>>()
            provider.connectionAnswer = { withContext(NonCancellable) { late.await() } }
            val model = model()
            runCurrent()
            assertEquals(ConversationConnection.CHECKING, model.state.value.connection)
            model.activate(false)
            late.complete(ConversationResult.Success("gpt-4o"))
            runCurrent()
            assertFalse(model.state.value.connection == ConversationConnection.READY)
        }

    @Test fun backgroundCancelsLateModelCheckAndForegroundRechecks() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val late = CompletableDeferred<ConversationResult<String>>()
            provider.connectionAnswer = { withContext(NonCancellable) { late.await() } }
            val model = model()
            model.setForegroundAllowed(true)
            runCurrent()
            assertEquals(ConversationConnection.CHECKING, model.state.value.connection)
            model.setForegroundAllowed(false)
            late.complete(ConversationResult.Success("gpt-4o"))
            runCurrent()
            assertFalse(model.state.value.connection == ConversationConnection.READY)
            model.edit(TextFieldValue("keep"))
            model.send()
            assertTrue(provider.requests.isEmpty())
            provider.connectionAnswer = { ConversationResult.Success("gpt-4o") }
            model.setForegroundAllowed(true)
            runCurrent()
            assertEquals(ConversationConnection.READY, model.state.value.connection)
        }

    @Test fun authenticationNetworkFailureCanRecheckWithoutSendingDraft() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            model.edit(TextFieldValue("keep this draft"))
            authentication.session.value = GitHubSession.Failure(AuthenticationProblem.NETWORK)
            runCurrent()
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            val priorChecks = provider.connections
            authentication.restoreAction = {
                authentication.session.value = GitHubSession.Authenticated(GitHubAccount(1, "first"))
            }
            model.retryConnection()
            runCurrent()
            assertEquals(1, authentication.restores)
            assertEquals(priorChecks + 1, provider.connections)
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            assertEquals("keep this draft", model.draft.text)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun provisionalDraftSurvivesFirstIdentityValidationAfterRecoverableFailure() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            authentication.session.value = GitHubSession.Restoring
            val model = model()
            runCurrent()
            val draft = TextFieldValue("초안", TextRange(2), TextRange(1, 2))
            model.edit(draft)
            authentication.session.value = GitHubSession.Failure(AuthenticationProblem.NETWORK)
            runCurrent()
            authentication.restoreAction = {
                authentication.session.value = GitHubSession.Authenticated(GitHubAccount(1, "first"))
            }

            model.retryConnection()
            runCurrent()

            assertEquals(draft, model.draft)
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )
        }

    @Test fun interruptedInitialCredentialRetryRemainsRecoverableWithoutKnownAccount() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            authentication.session.value = GitHubSession.Failure(AuthenticationProblem.NETWORK)
            val model = model()
            runCurrent()
            model.edit(TextFieldValue("보존할 초안"))
            authentication.restoreAction = {
                authentication.session.value = GitHubSession.Restoring
                CompletableDeferred<Unit>().await()
            }
            model.retryConnection()
            runCurrent()
            networkStatus.online.value = false
            runCurrent()
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            assertFalse(model.state.value.connectionRetrying)
            assertTrue(provider.requests.isEmpty())

            networkStatus.online.value = true
            authentication.restoreAction = {
                authentication.session.value = GitHubSession.Authenticated(GitHubAccount(1, "first"))
            }
            model.retryConnection()
            runCurrent()
            assertEquals(2, authentication.restores)
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            assertEquals("보존할 초안", model.draft.text)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun unexpectedAuthenticationRestoreFailureNeverChecksTheModel() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            val priorChecks = provider.connections
            authentication.session.value = GitHubSession.Failure(AuthenticationProblem.NETWORK)
            authentication.restoreAction = { throw IllegalStateException("restore failed") }
            runCurrent()
            model.retryConnection()
            runCurrent()
            assertEquals(ConversationConnection.UNAVAILABLE, model.state.value.connection)
            assertEquals(ConversationProblem.SERVICE, model.state.value.connectionProblem)
            assertEquals(priorChecks, provider.connections)
        }

    @Test fun failureRetainsHistoryAndAttemptAndRetryDoesNotDuplicateUserMessage() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            model.edit(TextFieldValue("first"))
            model.send()
            runCurrent()
            val prior = model.state.value.messages
            provider.answer = { ConversationResult.Failure(ConversationProblem.TIMEOUT) }
            model.edit(TextFieldValue("second", TextRange(6)))
            model.send()
            runCurrent()
            assertEquals(
                prior.map { it.text } + "second",
                model.state.value.messages
                    .map { it.text },
            )
            assertEquals("", model.draft.text)
            assertEquals(ConversationProblem.TIMEOUT, model.state.value.problem)
            assertEquals(ConversationProblem.TIMEOUT, model.state.value.connectionProblem)
            provider.answer = { ConversationResult.Success("answer") }
            model.retryConnection()
            runCurrent()
            assertTrue(model.state.value.failed)
            model.retry()
            runCurrent()
            assertFalse(model.state.value.failed)
            assertEquals(
                listOf("first", "answer", "second", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
        }

    @Test fun editingFailedTurnAfterHomeMovesItsTextFromTheBubbleIntoTheComposer() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { ConversationResult.Failure(ConversationProblem.TIMEOUT) }
            model.edit(TextFieldValue("before"))
            model.send()
            runCurrent()
            assertEquals(
                listOf("before"),
                model.state.value.messages
                    .map { it.text },
            )

            model.edit(TextFieldValue("after"))

            assertTrue(model.state.value.failed)
            assertEquals("", model.draft.text)
            assertEquals(
                listOf("before"),
                model.state.value.messages
                    .map { it.text },
            )
            model.clearDraftForHome()
            model.dismissFailure()
            assertEquals("before", model.draft.text)
            model.edit(TextFieldValue("after"))

            assertFalse(model.state.value.failed)
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )
            assertEquals("after", model.draft.text)
        }

    @Test fun replyNetworkFailureRequiresRecheckAndKeepsFailedTurnUntilExplicitAction() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { ConversationResult.Failure(ConversationProblem.NETWORK) }
            model.edit(TextFieldValue("keep me"))
            model.send()
            runCurrent()

            assertEquals(ConversationConnection.UNAVAILABLE, model.state.value.connection)
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            assertTrue(model.state.value.failed)
            model.edit(TextFieldValue("keep me", TextRange(0)))
            assertTrue(model.state.value.failed)
            assertEquals(
                listOf("keep me"),
                model.state.value.messages
                    .map { it.text },
            )

            model.retryConnection()
            runCurrent()
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            assertEquals(null, model.state.value.connectionProblem)
            assertTrue(model.state.value.failed)
            assertEquals(
                listOf("keep me"),
                model.state.value.messages
                    .map { it.text },
            )
            assertEquals(1, provider.requests.size)
        }

    @Test fun offlineSendAndRecheckFailImmediatelyWithoutCallingCopilot() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            val checksBeforeDisconnect = provider.connections
            networkStatus.online.value = false
            model.edit(TextFieldValue("keep this"))
            model.send()
            runCurrent()

            assertEquals(0, provider.requests.size)
            assertEquals(ConversationProblem.NETWORK, model.state.value.problem)
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            assertFalse(model.state.value.replyPending)
            assertEquals(
                listOf("keep this"),
                model.state.value.messages
                    .map { it.text },
            )

            model.retryConnection()
            runCurrent()
            assertEquals(checksBeforeDisconnect, provider.connections)
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            assertFalse(model.state.value.connectionRetrying)

            networkStatus.online.value = true
            model.retryConnection()
            runCurrent()
            assertEquals(checksBeforeDisconnect + 1, provider.connections)
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            assertTrue(model.state.value.failed)
        }

    @Test fun idleReadyRecordingReportsNetworkLossForPopupWithoutRestoringOldDraft() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.edit(TextFieldValue("보존할 초안"))
            model.setVoiceResumed(true)
            model.requestVoice(true)
            requireNotNull(speech.listener).onReady()
            assertEquals(ConversationConnection.READY, model.state.value.connection)

            networkStatus.online.value = false
            runCurrent()
            assertEquals(ConversationConnection.UNAVAILABLE, model.state.value.connection)
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            assertFalse(model.state.value.failed)
            model.cancelVoice()
            assertEquals(1, speech.cancellations)
            assertEquals("", model.draft.text)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun disconnectDuringPendingReplyStopsWaitingAndShowsNetworkFailure() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { CompletableDeferred<ConversationResult<String>>().await() }
            model.edit(TextFieldValue("sent before disconnect"))
            model.send()
            runCurrent()
            assertTrue(model.state.value.replyPending)

            networkStatus.online.value = false
            runCurrent()

            assertFalse(model.state.value.replyPending)
            assertEquals(ConversationProblem.NETWORK, model.state.value.problem)
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            assertEquals(1, provider.requests.size)
        }

    @Test fun usageFailureBlocksSendUntilCopilotCheckSucceeds() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { ConversationResult.Failure(ConversationProblem.USAGE) }
            model.edit(TextFieldValue("quota"))
            model.send()
            runCurrent()

            assertEquals(ConversationProblem.USAGE, model.state.value.problem)
            assertEquals(ConversationProblem.USAGE, model.state.value.connectionProblem)
            assertEquals(ConversationConnection.UNAVAILABLE, model.state.value.connection)
            assertTrue(model.state.value.failed)
            val checksBeforeReturn = provider.connections
            model.deactivate()
            model.activate(true)
            runCurrent()
            assertEquals(checksBeforeReturn + 1, provider.connections)
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            assertTrue(model.state.value.failed)
        }

    @Test fun stalledReplyTimesOutAndRetainsEditableAttempt() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { CompletableDeferred<ConversationResult<String>>().await() }
            model.edit(TextFieldValue("waiting"))
            model.send()
            runCurrent()
            assertTrue(model.state.value.replyPending)

            advanceTimeBy(30_000)
            runCurrent()

            assertFalse(model.state.value.replyPending)
            assertEquals(ConversationProblem.TIMEOUT, model.state.value.problem)
            assertEquals("", model.draft.text)
            assertEquals(
                listOf("waiting"),
                model.state.value.messages
                    .map { it.text },
            )
            model.dismissFailure()
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )
        }

    @Test fun stalledConnectionCheckTimesOutAndAllowsExplicitRecheck() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            provider.connectionAnswer = { CompletableDeferred<ConversationResult<String>>().await() }
            val model = model()
            runCurrent()
            assertEquals(ConversationConnection.CHECKING, model.state.value.connection)

            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(ConversationConnection.UNAVAILABLE, model.state.value.connection)
            assertEquals(ConversationProblem.TIMEOUT, model.state.value.connectionProblem)
            provider.connectionAnswer = { ConversationResult.Success("gpt-4o") }
            model.retryConnection()
            runCurrent()
            assertEquals(ConversationConnection.READY, model.state.value.connection)
        }

    @Test fun failedTurnRemainsVisibleAfterLeavingAndReturningToChat() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { ConversationResult.Failure(ConversationProblem.TIMEOUT) }
            model.edit(TextFieldValue("keep this attempt"))
            model.send()
            runCurrent()
            model.clearDraftForHome()
            model.deactivate()
            model.activate(true)

            assertTrue(model.state.value.failed)
            assertEquals(
                listOf("keep this attempt"),
                model.state.value.messages
                    .map { it.text },
            )
            assertEquals("", model.draft.text)
        }

    @Test fun failedTurnCanBeRetriedAfterHomeAndConnectionRecovery() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { ConversationResult.Failure(ConversationProblem.TIMEOUT) }
            model.edit(TextFieldValue("retry after home"))
            model.send()
            runCurrent()
            model.clearDraftForHome()
            model.deactivate()
            model.activate(true)
            provider.answer = { ConversationResult.Success("answer") }
            model.retryConnection()
            runCurrent()

            assertEquals("", model.draft.text)
            assertEquals(1, provider.requests.size)
            model.retry()
            runCurrent()

            assertEquals(2, provider.requests.size)
            assertEquals(listOf("retry after home"), provider.requests.last().map { it.text })
            assertEquals(
                listOf("retry after home", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
            assertEquals("", model.draft.text)
        }

    @Test fun accessFailureRecheckDoesNotResendAndRestoresExplicitSend() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { ConversationResult.Failure(ConversationProblem.ACCESS) }
            model.edit(TextFieldValue("private draft"))
            model.send()
            runCurrent()
            assertEquals(ConversationConnection.UNAVAILABLE, model.state.value.connection)
            assertEquals(
                listOf("private draft"),
                model.state.value.messages
                    .map { it.text },
            )

            val sentBeforeRecheck = provider.requests.size
            model.retryConnection()
            runCurrent()
            assertEquals(sentBeforeRecheck, provider.requests.size)
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            assertFalse(model.state.value.failed)
            assertEquals("private draft", model.draft.text)

            provider.answer = { ConversationResult.Success("answer") }
            model.send()
            runCurrent()
            assertEquals(
                listOf("private draft", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
        }

    @Test fun editingAfterAccessFailureRechecksWithoutSendingTheDraft() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            provider.answer = { ConversationResult.Failure(ConversationProblem.ACCESS) }
            model.edit(TextFieldValue("before"))
            model.send()
            runCurrent()
            val sentBeforeEdit = provider.requests.size
            val checksBeforeEdit = provider.connections

            model.dismissFailure()
            model.edit(TextFieldValue("after"))
            runCurrent()

            assertEquals(checksBeforeEdit + 1, provider.connections)
            assertEquals(sentBeforeEdit, provider.requests.size)
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            assertEquals("after", model.draft.text)
        }

    @Test fun cancelLeaveParkingLossAndContextChangeRejectLateReplies() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            val changes: List<() -> Unit> =
                listOf(
                    { model.cancel() },
                    { model.deactivate() },
                    { model.activate(false) },
                    { model.bind("profile", "friend:luna") },
                )
            for ((index, change) in changes.withIndex()) {
                model.activate(true)
                val late = CompletableDeferred<ConversationResult<String>>()
                provider.answer = { withContext(NonCancellable) { late.await() } }
                model.edit(TextFieldValue("keep"))
                model.send()
                runCurrent()
                change()
                late.complete(ConversationResult.Success("must be ignored"))
                runCurrent()
                assertFalse(model.state.value.replyPending)
                assertTrue(
                    model.state.value.messages
                        .isEmpty(),
                )
                assertEquals(if (index == 3) "" else "keep", model.draft.text)
            }
        }

    @Test fun ownerChangeDisconnectAndNewSessionClearPrivateData() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            model.edit(TextFieldValue("private"))
            model.send()
            runCurrent()
            model.newConversation()
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )
            val changes: List<() -> Unit> =
                listOf(
                    { model.bind("other", "friend:mobi") },
                    { authentication.session.value = GitHubSession.Authenticated(GitHubAccount(2, "second")) },
                    { authentication.session.value = GitHubSession.SignedOut },
                )
            for (change in changes) {
                model.edit(TextFieldValue("private"))
                change()
                runCurrent()
                assertEquals("", model.draft.text)
                assertTrue(
                    model.state.value.messages
                        .isEmpty(),
                )
            }
        }

    @Test fun compositionSurvivesNavigationAndTemporaryAuthenticationErrorsButNotRevocation() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            val draft = TextFieldValue("오늘 하", TextRange(4), TextRange(3, 4))
            model.edit(draft)
            model.deactivate()
            model.bind("profile", "friend:mobi")
            assertEquals(draft, model.draft)
            authentication.session.value = GitHubSession.Failure(AuthenticationProblem.NETWORK)
            runCurrent()
            assertEquals(draft, model.draft)
            authentication.session.value = GitHubSession.Failure(AuthenticationProblem.REAUTHENTICATION)
            runCurrent()
            assertEquals(TextFieldValue(), model.draft)
        }

    @Test fun perInputLimitRemainsButExchangeCountIsNotAnArtificialContextLimit() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            model.edit(TextFieldValue("x".repeat(ConversationLimits.INPUT_CHARACTERS + 1)))
            model.send()
            runCurrent()
            assertEquals(ConversationProblem.LIMIT, model.state.value.problem)
            assertEquals(0, provider.requests.size)
            model.newConversation()
            repeat(20) {
                model.edit(TextFieldValue("turn $it"))
                model.send()
                runCurrent()
            }
            model.edit(TextFieldValue("too many"))
            model.send()
            runCurrent()
            assertEquals(21, provider.requests.size)
            assertEquals(null, model.state.value.problem)
            val restarted = model()
            assertEquals(TextFieldValue(), restarted.draft)
            assertTrue(
                restarted.state.value.messages
                    .isEmpty(),
            )
        }

    @Test fun signedOutAndParkingLossNeverDispatchTheDraft() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()
            model.edit(TextFieldValue("keep"))
            model.activate(false)
            model.send()
            runCurrent()
            assertEquals(0, provider.requests.size)
            assertEquals("keep", model.draft.text)
            model.activate(true)
            authentication.session.value = GitHubSession.SignedOut
            model.send()
            runCurrent()
            assertEquals(0, provider.requests.size)
            assertEquals(ConversationConnection.SIGNED_OUT, model.state.value.connection)
        }

    @Test fun durableThreadsRestorePerCompanionAndNewConversationRemovesOnlyActiveThread() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val disk = FakeConversationStore()

            fun create() =
                ConversationViewModel(
                    authentication,
                    provider,
                    networkStatus,
                    conversationStore = disk,
                ).also {
                    store.put("durable-${System.identityHashCode(it)}", it)
                    it.bind("profile", "friend:mobi")
                    it.activate(true)
                }
            val first = create()
            runCurrent()
            first.edit(TextFieldValue("mobi private"))
            first.send()
            runCurrent()
            first.bind("profile", "friend:luna")
            runCurrent()
            assertTrue(
                first.state.value.messages
                    .isEmpty(),
            )
            first.edit(TextFieldValue("luna private"))
            first.send()
            runCurrent()
            first.bind("profile", "friend:mobi")
            runCurrent()
            assertEquals(
                "mobi private",
                first.state.value.messages
                    .first()
                    .text,
            )
            val restart = create()
            runCurrent()
            assertEquals(
                "mobi private",
                restart.state.value.messages
                    .first()
                    .text,
            )
            restart.newConversation()
            runCurrent()
            assertTrue(
                create()
                    .also { runCurrent() }
                    .state.value.messages
                    .isEmpty(),
            )
            restart.bind("profile", "friend:luna")
            runCurrent()
            assertEquals(
                "luna private",
                restart.state.value.messages
                    .first()
                    .text,
            )
        }

    @Test fun interruptedCommittedSaveReconcilesBeforeTheNextProviderRequest() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val disk = FakeConversationStore()
            val committed = CompletableDeferred<Unit>()
            disk.afterAppend = {
                withContext(NonCancellable) { committed.await() }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
            }
            val model = ConversationViewModel(authentication, provider, networkStatus, conversationStore = disk)
            store.put("save-race", model)
            model.bind("profile", "friend:mobi")
            model.activate(true)
            runCurrent()
            model.edit(TextFieldValue("committed"))
            model.send()
            runCurrent()
            model.deactivate()
            committed.complete(Unit)
            runCurrent()
            model.activate(true)
            runCurrent()
            assertEquals(
                listOf("committed", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
            disk.afterAppend = {}
            model.edit(TextFieldValue("next"))
            model.send()
            runCurrent()
            assertEquals(3, provider.requests.last().size)
        }

    @Test fun networkRecoveryWaitsForInterruptedSaveEvenWhenItFailsAfterCancellation() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val disk = FakeConversationStore()
            val finish = CompletableDeferred<Unit>()
            disk.afterAppend = {
                withContext(NonCancellable) { finish.await() }
                throw java.io.IOException("uncertain save")
            }
            val model = ConversationViewModel(authentication, provider, networkStatus, conversationStore = disk)
            store.put("network-save-race", model)
            model.bind("profile", "friend:mobi")
            model.activate(true)
            runCurrent()
            model.edit(TextFieldValue("committed"))
            model.send()
            runCurrent()
            networkStatus.online.value = false
            runCurrent()
            networkStatus.online.value = true
            model.retryConnection()
            runCurrent()
            model.retry()
            runCurrent()
            assertEquals(1, provider.requests.size)
            assertTrue(model.state.value.storageBusy)
            finish.complete(Unit)
            runCurrent()
            assertFalse(model.state.value.storageBusy)
            assertEquals(
                listOf("committed", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
        }

    @Test fun offlineBeforeSaveCommitRestoresTheUncommittedInput() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val disk = FakeConversationStore()
            val finish = CompletableDeferred<Unit>()
            disk.beforeAppend = { finish.await() }
            val model = ConversationViewModel(authentication, provider, networkStatus, conversationStore = disk)
            store.put("uncommitted-network-save", model)
            model.bind("profile", "friend:mobi")
            model.activate(true)
            runCurrent()
            model.edit(TextFieldValue("keep unsaved input"))
            model.send()
            runCurrent()
            networkStatus.online.value = false
            runCurrent()
            assertEquals("keep unsaved input", model.draft.text)
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )
            assertFalse(model.state.value.storageBusy)
            assertEquals(1, provider.requests.size)
        }

    @Test fun saveFailureRecoveryRestoresUserInputWithoutResending() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val disk = FakeConversationStore().apply { failAppend = true }
            val model = ConversationViewModel(authentication, provider, networkStatus, conversationStore = disk)
            store.put("save-failure", model)
            model.bind("profile", "friend:mobi")
            model.activate(true)
            runCurrent()
            model.edit(TextFieldValue("keep this"))
            model.send()
            runCurrent()
            assertEquals(ConversationProblem.STORAGE, model.state.value.problem)
            model.retry()
            runCurrent()
            assertEquals("keep this", model.draft.text)
            assertEquals(1, provider.requests.size)
        }

    private class FakeConversationStore : ConversationStore {
        val values = mutableMapOf<ConversationKey, StoredConversation>()
        var failAppend = false
        var beforeAppend: suspend () -> Unit = {}
        var afterAppend: suspend () -> Unit = {}

        override suspend fun load(
            key: ConversationKey,
            accountId: Long,
        ): StoredConversation = values[key]?.takeIf { it.accountId == accountId } ?: reset(key, accountId)

        override suspend fun reset(
            key: ConversationKey,
            accountId: Long,
        ): StoredConversation =
            StoredConversation(
                java.util.UUID
                    .randomUUID()
                    .toString(),
                accountId,
                0,
                emptyList(),
            ).also {
                values[key] =
                    it
            }

        override suspend fun append(
            key: ConversationKey,
            expected: StoredConversation,
            user: String,
            reply: String,
        ): StoredConversation? {
            beforeAppend()
            if (failAppend) throw java.io.IOException("storage failure")
            if (values[key] !== expected) return null
            return StoredConversation(
                expected.id,
                expected.accountId,
                expected.revision + 1,
                expected.turns + ConversationTurn(user, true) + ConversationTurn(reply, false),
            ).also {
                values[key] = it
                afterAppend()
            }
        }
    }

    private fun model(speech: ConversationSpeechInput = UnavailableConversationSpeechInput) =
        ConversationViewModel(authentication, provider, networkStatus, speech).also {
            store.put("model-${System.identityHashCode(it)}", it)
            it.bind("profile", "friend:mobi")
            it.activate(true)
        }

    @Test fun routingIdentitySurvivesNavigationButChangesForNewConversationAndOwner() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val model = model()
            runCurrent()

            fun send() {
                model.edit(TextFieldValue("same prompt"))
                model.send()
            }
            send()
            runCurrent()
            val first = provider.conversationIds.last()
            model.deactivate()
            model.activate(true)
            send()
            runCurrent()
            assertEquals(first, provider.conversationIds.last())
            model.newConversation()
            send()
            runCurrent()
            val second = provider.conversationIds.last()
            assertNotEquals(first, second)
            model.bind("other profile", "friend:mobi")
            runCurrent()
            send()
            runCurrent()
            assertNotEquals(second, provider.conversationIds.last())
        }

    @Test fun recordingKeepsVisibleMessagesAndContextButClearsComposer() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.edit(TextFieldValue("first"))
            model.send()
            runCurrent()
            val conversation = provider.conversationIds.last()
            model.edit(TextFieldValue("keep draft"))
            model.setVoiceResumed(true)
            val denied = requireNotNull(model.requestVoice(false))
            model.voicePermissionResult(denied, false)
            assertEquals(
                listOf("first", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
            model.requestVoice(true)
            requireNotNull(speech.listener).onFailure(VoiceInputProblem.AUDIO)
            assertEquals(
                listOf("first", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
            model.requestVoice(true)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            assertEquals(
                listOf("first", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
            assertEquals("", model.draft.text)
            model.cancelVoice()
            model.deactivate()
            model.activate(true)
            runCurrent()
            assertEquals(
                listOf("first", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
            model.edit(TextFieldValue("second"))
            model.send()
            runCurrent()
            assertEquals(listOf("first", "answer", "second"), provider.requests.last().map { it.text })
            assertEquals(conversation, provider.conversationIds.last())
            assertEquals(
                listOf("first", "answer", "second", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
            model.newConversation()
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )
            assertEquals("", model.draft.text)
            model.edit(TextFieldValue("fresh"))
            model.send()
            runCurrent()
            assertEquals(listOf("fresh"), provider.requests.last().map { it.text })
            assertNotEquals(conversation, provider.conversationIds.last())
        }

    @Test fun historyStaysVisibleAfterFailedTurnEditAndRecheck() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.edit(TextFieldValue("previous"))
            model.send()
            runCurrent()
            model.setVoiceResumed(true)
            model.requestVoice(true)
            requireNotNull(speech.listener).onReady()
            requireNotNull(speech.listener).onResult("attempt")
            provider.answer = { ConversationResult.Failure(ConversationProblem.ACCESS) }
            model.send()
            runCurrent()
            assertEquals(
                listOf("previous", "answer", "attempt"),
                model.state.value.messages
                    .map { it.text },
            )
            model.retryConnection()
            runCurrent()
            assertFalse(model.state.value.failed)
            assertEquals(
                listOf("previous", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
            provider.answer = { ConversationResult.Failure(ConversationProblem.TIMEOUT) }
            model.send()
            runCurrent()
            model.dismissFailure()
            model.cancel()
            assertEquals(
                listOf("previous", "answer"),
                model.state.value.messages
                    .map { it.text },
            )
            assertEquals("attempt", model.draft.text)
            model.retryConnection()
            runCurrent()
            provider.answer = { ConversationResult.Success("recovered") }
            model.send()
            runCurrent()
            assertEquals(listOf("previous", "answer", "attempt"), provider.requests.last().map { it.text })
            assertEquals(
                listOf("previous", "answer", "attempt", "recovered"),
                model.state.value.messages
                    .map { it.text },
            )
        }

    @Test fun speechStopProducesReviewOnlyAndExplicitSendUsesTheExistingGuard() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("original draft"))
            val permission = requireNotNull(model.requestVoice(false))
            assertEquals(VoiceInputPhase.PERMISSION, model.state.value.voice.phase)
            assertEquals(0, speech.starts)
            model.voicePermissionResult(permission, true)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            listener.onPartial("안녕하세요")
            assertEquals("", model.draft.text)
            model.send()
            runCurrent()
            assertTrue(provider.requests.isEmpty())
            model.stopVoice()
            model.stopVoice()
            assertEquals(1, speech.stops)
            assertEquals(VoiceInputPhase.STOPPING, model.state.value.voice.phase)
            listener.onResult("안녕하세요 오늘 날씨가 좋습니다")
            assertEquals(VoiceInputPhase.REVIEW, model.state.value.voice.phase)
            assertEquals("안녕하세요 오늘 날씨가 좋습니다", model.draft.text)
            runCurrent()
            assertTrue(provider.requests.isEmpty())
            model.edit(TextFieldValue("수정한 인식문"))
            model.send()
            runCurrent()
            assertEquals(
                "수정한 인식문",
                provider.requests
                    .single()
                    .single()
                    .text,
            )
        }

    @Test fun speechCancellationKeepsClearedComposerAndRejectsOldSessionsAfterParkingLoss() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("keep this"))
            model.requestVoice(true)
            val old = requireNotNull(speech.listener)
            old.onReady()
            model.cancelVoice()
            assertEquals(1, speech.cancellations)
            model.requestVoice(true)
            val current = requireNotNull(speech.listener)
            current.onReady()
            old.onPartial("discard")
            old.onResult("discard")
            assertEquals("", model.draft.text)
            assertEquals("", model.state.value.voice.partial)
            model.setForegroundAllowed(false)
            current.onResult("also discard")
            assertEquals("", model.draft.text)
            assertFalse(model.state.value.voice.capturing)
            assertEquals(2, speech.cancellations)
            model.requestVoice(true)
            assertEquals(2, speech.starts)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun parkingReactivationKeepsTheResumedLifecycleAndCanStartANewMicrophoneSession() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("keep this"))
            model.requestVoice(true)
            requireNotNull(speech.listener).onReady()
            model.deactivate()
            model.activate(false)
            model.requestVoice(true)
            assertEquals(1, speech.starts)
            model.deactivate()
            model.activate(true)
            model.requestVoice(true)
            assertEquals(2, speech.starts)
            assertEquals("", model.draft.text)
            model.setVoiceResumed(false)
            model.deactivate()
            model.activate(true)
            model.requestVoice(true)
            assertEquals(2, speech.starts)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun permissionRepliesCannotRestartAnExitedConversationAndResumeWaitsForTheDialog() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            val denied = requireNotNull(model.requestVoice(false))
            model.voicePermissionResult(denied, false, showSettingsHint = true)
            assertEquals(VoiceInputProblem.PERMISSION, model.state.value.voice.problem)
            assertEquals(0, speech.starts)
            val obsolete = requireNotNull(model.requestVoice(false))
            model.deactivate()
            model.activate(true)
            model.setVoiceResumed(true)
            model.voicePermissionResult(obsolete, true)
            assertEquals(0, speech.starts)
            val permission = requireNotNull(model.requestVoice(false))
            model.setVoiceResumed(false)
            model.voicePermissionResult(permission, true)
            assertEquals(0, speech.starts)
            model.setVoiceResumed(true)
            assertEquals(1, speech.starts)
            assertEquals(VoiceInputPhase.STARTING, model.state.value.voice.phase)
        }

    @Test fun pausingStopsMicrophoneWithoutAutomaticRestartAndOwnershipLossClearsVoice() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("keep on pause"))
            model.requestVoice(true)
            val first = requireNotNull(speech.listener)
            first.onReady()
            model.setVoiceResumed(false)
            first.onResult("discard")
            model.setVoiceResumed(true)
            assertEquals(1, speech.starts)
            assertEquals("", model.draft.text)
            model.requestVoice(true)
            val next = requireNotNull(speech.listener)
            next.onReady()
            authentication.session.value = GitHubSession.SignedOut
            runCurrent()
            next.onResult("old owner")
            assertEquals("", model.draft.text)
            assertFalse(model.state.value.voice.capturing)
            assertEquals(2, speech.cancellations)
        }

    @Test fun failedOrOversizedSpeechLeavesClearedDraftAndStoppingHasABoundedWait() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("preserved"))
            model.requestVoice(true)
            requireNotNull(speech.listener).onResult("")
            assertEquals(VoiceInputProblem.NO_MATCH, model.state.value.voice.problem)
            assertEquals("", model.draft.text)
            model.requestVoice(true)
            requireNotNull(speech.listener).onResult("x".repeat(ConversationLimits.INPUT_CHARACTERS + 1))
            assertEquals(VoiceInputProblem.TOO_LONG, model.state.value.voice.problem)
            assertEquals("", model.draft.text)
            model.requestVoice(true)
            requireNotNull(speech.listener).onReady()
            repeat(70) { requireNotNull(speech.listener).onLevel(0.5f) }
            requireNotNull(speech.listener).onLevel(Float.NaN)
            assertEquals(64, model.state.value.voice.levels.size)
            advanceTimeBy(8_001)
            runCurrent()
            model.stopVoice()
            advanceTimeBy(20_001)
            runCurrent()
            assertEquals(VoiceInputProblem.TIMEOUT, model.state.value.voice.problem)
            assertFalse(model.state.value.voice.capturing)
            assertEquals("", model.draft.text)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun confirmedSpeechSurvivesServiceFailureAndFinalizationTimeoutWithoutSending() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("previous draft"))
            model.requestVoice(true)
            val first = requireNotNull(speech.listener)
            first.onReady()
            first.onCommitted("확정된 첫 문장")
            first.onPartial("확정된 첫 문장 미확정")
            first.onFailure(VoiceInputProblem.SERVICE)
            assertEquals("확정된 첫 문장", model.draft.text)
            assertEquals(VoiceInputPhase.REVIEW, model.state.value.voice.phase)
            assertEquals(VoiceInputProblem.SERVICE, model.state.value.voice.problem)
            model.requestVoice(true)
            val second = requireNotNull(speech.listener)
            second.onReady()
            second.onCommitted("다음 녹음의 확정 문장")
            model.stopVoice()
            advanceTimeBy(20_001)
            runCurrent()
            assertEquals("다음 녹음의 확정 문장", model.draft.text)
            assertEquals(VoiceInputProblem.TIMEOUT, model.state.value.voice.problem)
            second.onCommitted("늦은 결과")
            assertEquals("다음 녹음의 확정 문장", model.draft.text)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun cancellingStillDiscardsTheNewRecordingEvenAfterConfirmedSpeech() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("previous draft"))
            model.requestVoice(true)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            listener.onCommitted("취소할 확정 문장")
            model.cancelVoice()
            listener.onCommitted("늦은 결과")
            listener.onFailure(VoiceInputProblem.SERVICE)
            assertEquals("", model.draft.text)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun totalVoiceBoundStopsForReviewInsteadOfDiscardingRecognizedPhrases() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.requestVoice(true)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            listener.onPartial("첫 문장 다음 문장")
            advanceTimeBy(60_001)
            runCurrent()
            assertEquals(VoiceInputPhase.STOPPING, model.state.value.voice.phase)
            assertEquals(1, speech.stops)
            listener.onResult("첫 문장 다음 문장")
            assertEquals("첫 문장 다음 문장", model.draft.text)
            assertEquals(VoiceInputPhase.REVIEW, model.state.value.voice.phase)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun slowStartupKeepsTheFullCaptureBudgetAndDuplicateReadyDoesNotExtendIt() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.requestVoice(true)
            val listener = requireNotNull(speech.listener)
            advanceTimeBy(19_000)
            runCurrent()
            assertEquals(VoiceInputPhase.STARTING, model.state.value.voice.phase)
            listener.onReady()
            advanceTimeBy(59_999)
            runCurrent()
            assertEquals(VoiceInputPhase.LISTENING, model.state.value.voice.phase)
            assertEquals(0, speech.stops)
            listener.onReady()
            advanceTimeBy(1)
            runCurrent()
            assertEquals(VoiceInputPhase.STOPPING, model.state.value.voice.phase)
            assertEquals(1, speech.stops)
        }

    @Test fun missingReadyTimesOutStartupAndRejectsLateReadyAndResults() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("preserved"))
            model.requestVoice(true)
            val listener = requireNotNull(speech.listener)
            advanceTimeBy(19_999)
            runCurrent()
            assertEquals(VoiceInputPhase.STARTING, model.state.value.voice.phase)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(VoiceInputProblem.TIMEOUT, model.state.value.voice.problem)
            assertEquals(1, speech.cancellations)
            listener.onReady()
            listener.onResult("late result")
            assertFalse(model.state.value.voice.capturing)
            assertEquals("", model.draft.text)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun finalRecognitionCanFinishAfterFiveSecondsWithoutSending() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.requestVoice(true)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            listener.onEndOfSpeech()
            advanceTimeBy(19_999)
            runCurrent()
            assertEquals(VoiceInputPhase.STOPPING, model.state.value.voice.phase)
            listener.onReady()
            listener.onResult("긴 문장을 모두 인식했습니다")
            assertEquals(VoiceInputPhase.REVIEW, model.state.value.voice.phase)
            assertEquals("긴 문장을 모두 인식했습니다", model.draft.text)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun duplicateEndCallbackCannotExtendTheFinalizationDeadline() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("preserved"))
            model.requestVoice(true)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            model.stopVoice()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(VoiceInputPhase.STOPPING, model.state.value.voice.phase)
            listener.onEndOfSpeech()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(VoiceInputProblem.TIMEOUT, model.state.value.voice.problem)
            listener.onResult("too late")
            assertEquals("", model.draft.text)
            assertFalse(model.state.value.voice.capturing)
        }

    @Test fun voiceAvailabilityRequiresAuthenticatedIdentityAndRefreshesWhenItChanges() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            authentication.session.value = GitHubSession.Failure(AuthenticationProblem.NETWORK)
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            assertFalse(model.state.value.voice.available)
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            model.requestVoice(true)
            assertEquals(0, speech.starts)
            authentication.session.value = GitHubSession.Authenticated(GitHubAccount(1, "first"))
            runCurrent()
            assertTrue(model.state.value.voice.available)
            model.requestVoice(true)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            authentication.session.value = GitHubSession.Restoring
            runCurrent()
            listener.onResult("unverified")
            assertFalse(model.state.value.voice.available)
            assertFalse(model.state.value.voice.capturing)
            assertEquals(1, speech.cancellations)
            assertEquals("", model.draft.text)
            model.setVoiceResumed(true)
            assertFalse(model.state.value.voice.available)
        }

    @Test fun blockingCopilotFailuresImmediatelyCancelVoiceAndRejectFurtherStartsAndLateResults() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            for (problem in listOf(
                ConversationProblem.ACCOUNT,
                ConversationProblem.ACCESS,
                ConversationProblem.SERVICE,
            )) {
                val check = CompletableDeferred<ConversationResult<String>>()
                provider.connectionAnswer = { check.await() }
                val speech = FakeSpeech()
                val model = model(speech)
                runCurrent()
                model.setVoiceResumed(true)
                model.edit(TextFieldValue("preserved"))
                model.requestVoice(true)
                val listener = requireNotNull(speech.listener)
                listener.onReady()
                check.complete(ConversationResult.Failure(problem))
                runCurrent()
                assertFalse("Blocking failure: $problem", model.state.value.voice.capturing)
                assertEquals(1, speech.cancellations)
                listener.onResult("late result")
                model.requestVoice(true)
                assertEquals(1, speech.starts)
                assertEquals("", model.draft.text)
                assertTrue(provider.requests.isEmpty())
            }
        }

    @Test fun onlineTimeoutRecoveryKeepsSendBlockedAndLocalRecordingAliveUntilFreshSuccess() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            provider.connectionAnswer = { ConversationResult.Failure(ConversationProblem.TIMEOUT) }
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("보존할 초안"))
            model.requestVoice(true)
            assertEquals(1, speech.starts)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            assertEquals(VoiceInputPhase.LISTENING, model.state.value.voice.phase)
            model.retryConnection()
            runCurrent()
            assertEquals(ConversationConnection.UNAVAILABLE, model.state.value.connection)
            assertEquals(ConversationProblem.TIMEOUT, model.state.value.connectionProblem)
            assertEquals(VoiceInputPhase.LISTENING, model.state.value.voice.phase)
            assertEquals(0, speech.cancellations)
            model.send()
            assertTrue(provider.requests.isEmpty())
            listener.onResult("녹음한 초안")
            val recovery = CompletableDeferred<ConversationResult<String>>()
            provider.connectionAnswer = { recovery.await() }
            model.retryConnection()
            runCurrent()
            assertEquals(ConversationConnection.CHECKING, model.state.value.connection)
            model.send()
            assertTrue(provider.requests.isEmpty())
            recovery.complete(ConversationResult.Success("gpt-4o"))
            runCurrent()
            assertEquals(ConversationConnection.READY, model.state.value.connection)
            assertEquals("녹음한 초안", model.draft.text)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun offlineDraftRecognitionSurvivesConnectionRetryAndSendStaysBlocked() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            networkStatus.online.value = false
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            model.setVoiceResumed(true)
            model.requestVoice(true)
            assertEquals(1, speech.starts)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            model.retryConnection()
            assertEquals(VoiceInputPhase.LISTENING, model.state.value.voice.phase)
            listener.onResult("오프라인에서 작성한 초안")
            assertEquals(VoiceInputPhase.REVIEW, model.state.value.voice.phase)
            assertEquals("오프라인에서 작성한 초안", model.draft.text)
            model.send()
            model.retry()
            runCurrent()
            assertTrue(provider.requests.isEmpty())
            assertFalse(model.state.value.replyPending)
        }

    @Test fun connectionCheckFailureAndNetworkLossDoNotCancelLocalRecording() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val check = CompletableDeferred<ConversationResult<String>>()
            provider.connectionAnswer = { check.await() }
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.requestVoice(true)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            check.complete(ConversationResult.Failure(ConversationProblem.NETWORK))
            runCurrent()
            assertEquals(VoiceInputPhase.LISTENING, model.state.value.voice.phase)
            val retry = CompletableDeferred<ConversationResult<String>>()
            provider.connectionAnswer = { retry.await() }
            model.retryConnection()
            runCurrent()
            assertTrue(model.state.value.connectionRetrying)
            listener.onPartial("이어 말하기")
            assertEquals("이어 말하기", model.state.value.voice.partial)
            networkStatus.online.value = false
            runCurrent()
            assertEquals(VoiceInputPhase.LISTENING, model.state.value.voice.phase)
            model.setForegroundAllowed(true, refresh = true)
            assertEquals(VoiceInputPhase.LISTENING, model.state.value.voice.phase)
            assertEquals(0, speech.cancellations)
            listener.onResult("연결과 무관한 초안")
            assertEquals("연결과 무관한 초안", model.draft.text)
            model.send()
            runCurrent()
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun successfulLocalDraftReplacesAFailedTurnAndRemainsEditableWithoutEnablingSend() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            provider.answer = { ConversationResult.Failure(ConversationProblem.NETWORK) }
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.edit(TextFieldValue("failed attempt"))
            model.send()
            runCurrent()
            assertTrue(model.state.value.failed)
            model.setVoiceResumed(true)
            model.requestVoice(true)
            assertEquals(1, speech.starts)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            listener.onResult("새로운 초안")
            assertEquals("새로운 초안", model.draft.text)
            assertFalse(model.state.value.failed)
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )
            model.edit(TextFieldValue("수정한 새 초안"))
            assertEquals("수정한 새 초안", model.draft.text)
            assertEquals(ConversationConnection.UNAVAILABLE, model.state.value.connection)
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            model.send()
            model.retry()
            runCurrent()
            assertEquals(1, provider.requests.size)
        }

    @Test fun confirmedDraftAfterRecognitionFailureRemainsEditableAfterAFailedChatTurn() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            provider.answer = { ConversationResult.Failure(ConversationProblem.NETWORK) }
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.edit(TextFieldValue("failed attempt"))
            model.send()
            runCurrent()
            assertTrue(model.state.value.failed)
            model.setVoiceResumed(true)
            model.requestVoice(true)
            assertEquals(1, speech.starts)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            listener.onCommitted("새로운 초안")
            listener.onFailure(VoiceInputProblem.AUDIO)
            assertEquals("새로운 초안", model.draft.text)
            assertFalse(model.state.value.failed)
            assertTrue(
                model.state.value.messages
                    .isEmpty(),
            )
            model.edit(TextFieldValue("수정한 새 초안"))
            assertEquals("수정한 새 초안", model.draft.text)
            assertEquals(ConversationConnection.UNAVAILABLE, model.state.value.connection)
            assertEquals(ConversationProblem.NETWORK, model.state.value.connectionProblem)
            model.send()
            model.retry()
            runCurrent()
            assertEquals(1, provider.requests.size)
        }

    @Test fun offlineStartupAndFinalizationStillCancelOnBackgroundParkingAndAuthenticationLoss() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            networkStatus.online.value = false
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            model.edit(TextFieldValue("preserved"))
            model.requestVoice(true)
            assertEquals(1, speech.starts)
            val startup = requireNotNull(speech.listener)
            model.setVoiceResumed(false)
            startup.onReady()
            startup.onResult("discard background")
            assertEquals("", model.draft.text)
            assertFalse(model.state.value.voice.capturing)
            model.setVoiceResumed(true)
            model.requestVoice(true)
            val finalizing = requireNotNull(speech.listener)
            finalizing.onReady()
            model.stopVoice()
            model.activate(false)
            finalizing.onResult("discard parking")
            assertEquals("", model.draft.text)
            assertFalse(model.state.value.voice.capturing)
            model.activate(true)
            model.requestVoice(true)
            val unauthenticated = requireNotNull(speech.listener)
            authentication.session.value = GitHubSession.Failure(AuthenticationProblem.NETWORK)
            runCurrent()
            unauthenticated.onReady()
            unauthenticated.onResult("discard unverified identity")
            model.requestVoice(true)
            assertEquals(3, speech.starts)
            assertEquals(3, speech.cancellations)
            assertEquals("", model.draft.text)
            assertFalse(model.state.value.voice.capturing)
            assertTrue(provider.requests.isEmpty())
        }

    @Test fun missingRecognizerAndViewModelClearingCannotLeaveAMicrophoneSession() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val speech = FakeSpeech()
            val model = model(speech)
            runCurrent()
            model.setVoiceResumed(true)
            speech.available = false
            model.requestVoice(true)
            assertEquals(VoiceInputProblem.UNAVAILABLE, model.state.value.voice.problem)
            assertEquals(0, speech.starts)
            speech.available = true
            model.requestVoice(true)
            val listener = requireNotNull(speech.listener)
            listener.onReady()
            store.clear()
            listener.onResult("discard after clearing")
            assertEquals(1, speech.cancellations)
            assertEquals("", model.draft.text)
            assertFalse(model.state.value.voice.capturing)
        }

    private class FakeSpeech : ConversationSpeechInput {
        var available = true
        var starts = 0
        var stops = 0
        var cancellations = 0
        var listener: ConversationSpeechInput.Listener? = null

        override fun isAvailable() = available

        override fun start(listener: ConversationSpeechInput.Listener) {
            starts++
            this.listener = listener
        }

        override fun stop() {
            stops++
        }

        override fun cancel() {
            cancellations++
        }
    }

    private class FakeProvider : ConversationProvider {
        var connections = 0
        var connectionAnswer: suspend () -> ConversationResult<String> = { ConversationResult.Success("gpt-4o") }
        val conversationIds = mutableListOf<String>()
        val requests = mutableListOf<List<ConversationTurn>>()
        var answer: suspend () -> ConversationResult<String> = { ConversationResult.Success("answer") }

        override suspend fun connect(accountId: Long): ConversationResult<String> {
            connections++
            return connectionAnswer()
        }

        override suspend fun reply(
            accountId: Long,
            conversationId: String,
            friendId: String,
            messages: List<ConversationTurn>,
        ): ConversationResult<String> {
            conversationIds += conversationId
            requests += messages
            return answer()
        }
    }

    private class FakeAuthentication : GitHubAuthentication {
        override val session = MutableStateFlow<GitHubSession>(GitHubSession.Authenticated(GitHubAccount(1, "first")))
        override val configured = true
        var restores = 0
        var restoreAction: suspend () -> Unit = {}

        override suspend fun restore() {
            restores++
            restoreAction()
        }

        override suspend fun disconnect() {
            session.value = GitHubSession.SignedOut
        }

        override fun signIn() = emptyFlow<GitHubSignIn>()
    }
}
