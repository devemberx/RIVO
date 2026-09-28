package com.monsters.mobimon.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.domain.ConversationKey
import com.monsters.mobimon.core.domain.ConversationLimits
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationStore
import com.monsters.mobimon.core.domain.ConversationTurn
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.StoredConversation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Current thread is durable when a store is supplied; draft and voice remain in Activity memory. */
class ConversationViewModel(
    private val authentication: GitHubAuthentication,
    private val provider: ConversationProvider,
    private val networkStatus: ConversationNetworkStatus = AssumedOnlineConversationNetworkStatus,
    private val speechInput: ConversationSpeechInput = UnavailableConversationSpeechInput,
    private val conversationStore: ConversationStore? = null,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(ConversationUiState(voice = VoiceInputState(available = voiceAvailable())))
    val state = mutableState.asStateFlow()
    var draft by mutableStateOf(TextFieldValue())
        private set
    private var accountId: Long? = null
    private var profileId: String? = null
    private var friendId = "friend:mobi"
    private var active = false
    private var allowed = false
    private var foregroundAllowed = false
    private var foregroundManaged = false
    private var generation = 0L
    private var checkGeneration = 0L
    private var conversationId = UUID.randomUUID().toString()
    private var messageId = 0L
    private var work: Job? = null
    private var checkWork: Job? = null
    private var authenticationRetry: Job? = null
    private var history = emptyList<ConversationMessage>()
    private var stored: StoredConversation? = null
    private var savingRequest: Long? = null
    private var storageWork: Job? = null
    private var storageGeneration = 0L

    private fun storageKey() = profileId?.let { ConversationKey(it, friendId) }

    private var confirmedVoiceText = ""
    private var voiceGeneration = 0L
    private var voiceTimeout: Job? = null

    // RESUMED is independent of Park-driven activation restarts; only the lifecycle owner changes it.
    private var voiceResumed = false
    private var voicePermissionGranted = false
    private var returnToVoiceReview = false

    init {
        viewModelScope.launch {
            networkStatus.online.collect { online ->
                if (online) return@collect
                if (work?.isActive == true) {
                    if (savingRequest != null) mutableState.value = state.value.copy(storageBusy = true)
                    generation++
                    work?.cancel()
                    work = null
                    fail(ConversationProblem.NETWORK)
                }
                val interruptedCheck = checkWork?.isActive == true || authenticationRetry?.isActive == true
                if (interruptedCheck) cancelCheck()
                if ((accountId != null || interruptedCheck) &&
                    state.value.connectionProblem in
                    setOf(null, ConversationProblem.NETWORK, ConversationProblem.TIMEOUT)
                ) {
                    mutableState.value =
                        state.value.copy(
                            connection = ConversationConnection.UNAVAILABLE,
                            connectionProblem = ConversationProblem.NETWORK,
                            connectionRetrying = false,
                        )
                }
            }
        }
        viewModelScope.launch {
            authentication.session.collect { session ->
                updateVoice(state.value.voice.copy(available = voiceAvailable()))
                val retryingAuthentication = authenticationRetry?.isActive == true
                val next =
                    when (session) {
                        is GitHubSession.Authenticated -> session.account.id
                        GitHubSession.Restoring -> accountId
                        is GitHubSession.Failure ->
                            if (session.problem in
                                listOf(
                                    AuthenticationProblem.NETWORK,
                                    AuthenticationProblem.PROVIDER,
                                )
                            ) {
                                accountId
                            } else {
                                null
                            }
                        GitHubSession.SignedOut -> null
                    }
                if (next != accountId) {
                    changeAccount(next)
                }
                when (session) {
                    is GitHubSession.Authenticated -> {
                        if (state.value.connection == ConversationConnection.SIGNED_OUT) {
                            mutableState.value = state.value.copy(connection = ConversationConnection.UNAVAILABLE)
                        }
                        if (checkAllowed() &&
                            (
                                retryingAuthentication ||
                                    state.value.connection == ConversationConnection.UNAVAILABLE &&
                                    state.value.connectionProblem == null
                            )
                        ) {
                            checkConnection(retrying = retryingAuthentication)
                        }
                    }
                    GitHubSession.Restoring -> {
                        cancelVoice()
                        if (!retryingAuthentication) {
                            cancel()
                            cancelCheck()
                            mutableState.value = state.value.copy(connection = ConversationConnection.CHECKING)
                        }
                    }
                    is GitHubSession.Failure -> {
                        cancelVoice()
                        if (!retryingAuthentication ||
                            session.problem !in setOf(AuthenticationProblem.NETWORK, AuthenticationProblem.PROVIDER)
                        ) {
                            cancel()
                            cancelCheck()
                            mutableState.value =
                                state.value.copy(
                                    connection =
                                        if (session.problem in
                                            setOf(AuthenticationProblem.NETWORK, AuthenticationProblem.PROVIDER)
                                        ) {
                                            ConversationConnection.UNAVAILABLE
                                        } else {
                                            ConversationConnection.SIGNED_OUT
                                        },
                                    connectionProblem =
                                        when (session.problem) {
                                            AuthenticationProblem.NETWORK -> ConversationProblem.NETWORK
                                            AuthenticationProblem.PROVIDER ->
                                                if (networkStatus.isOnline()) {
                                                    ConversationProblem.SERVICE
                                                } else {
                                                    ConversationProblem.NETWORK
                                                }
                                            else -> null
                                        },
                                    connectionRetrying = false,
                                )
                        }
                    }
                    GitHubSession.SignedOut -> {
                        cancelVoice()
                        cancel()
                        cancelCheck()
                        mutableState.value =
                            state.value.copy(connection = ConversationConnection.SIGNED_OUT, connectionProblem = null)
                    }
                }
            }
        }
    }

    fun bind(
        profile: String,
        friend: String,
    ) {
        if (profileId != profile || friendId != friend) {
            clear()
            profileId = profile
            friendId = friend
            restoreConversation()
        }
        if (checkAllowed() &&
            state.value.connection == ConversationConnection.UNAVAILABLE &&
            state.value.connectionProblem == null
        ) {
            checkConnection()
        }
    }

    fun activate(interactionAllowed: Boolean) {
        active = true
        allowed = interactionAllowed
        if (!allowed) {
            cancelVoice()
            cancel()
            if (!foregroundAllowed) cancelCheck()
        } else if (state.value.connection == ConversationConnection.UNAVAILABLE &&
            state.value.connectionProblem in
            setOf(null, ConversationProblem.USAGE, ConversationProblem.ACCESS)
        ) {
            checkConnection()
        }
    }

    /** The app shell calls this on foreground entry and whenever trusted Park/AAOS evidence changes. */
    fun setForegroundAllowed(
        interactionAllowed: Boolean,
        refresh: Boolean = false,
    ) {
        foregroundManaged = true
        foregroundAllowed = interactionAllowed
        if (!checkAllowed()) {
            cancelVoice()
            cancelCheck()
            cancel()
        } else if (interactionAllowed &&
            (
                refresh ||
                    (
                        state.value.connection == ConversationConnection.UNAVAILABLE &&
                            state.value.connectionProblem == null
                    )
            )
        ) {
            checkConnection(force = refresh)
        }
    }

    fun deactivate() {
        cancelVoice()
        active = false
        allowed = false
        cancel()
        if (!foregroundAllowed) cancelCheck()
    }

    fun edit(value: TextFieldValue) {
        if (!active ||
            !allowed ||
            state.value.replyPending ||
            state.value.failed ||
            state.value.voice.capturing ||
            state.value.voice.phase == VoiceInputPhase.PERMISSION
        ) {
            return
        }
        draft = value
        if (state.value.voice.problem != null) updateVoice(state.value.voice.copy(problem = null))
    }

    fun clearDraftForHome() {
        cancelVoice()
        cancel()
        draft = TextFieldValue()
    }

    fun setVoiceResumed(resumed: Boolean) {
        voiceResumed = resumed
        if (resumed && !state.value.voice.capturing) {
            updateVoice(state.value.voice.copy(available = voiceAvailable()))
        }
        if (!resumed && state.value.voice.capturing) {
            cancelVoice()
        } else if (resumed && voicePermissionGranted && state.value.voice.phase == VoiceInputPhase.PERMISSION) {
            startVoice(state.value.voice.sessionId)
        }
    }

    /** A permission reply is accepted only for this foreground request, including after the native dialog pauses us. */
    fun requestVoice(permissionGranted: Boolean): Long? {
        if (!canUseVoice() ||
            !voiceResumed ||
            state.value.voice.capturing ||
            state.value.voice.phase == VoiceInputPhase.PERMISSION
        ) {
            return null
        }
        val available = voiceAvailable()
        if (!available) {
            updateVoice(state.value.voice.copy(available = false, problem = VoiceInputProblem.UNAVAILABLE))
            return null
        }
        draft = TextFieldValue()
        returnToVoiceReview = state.value.voice.phase == VoiceInputPhase.REVIEW
        voicePermissionGranted = permissionGranted
        confirmedVoiceText = ""
        val session = ++voiceGeneration
        updateVoice(VoiceInputState(available = true, phase = VoiceInputPhase.PERMISSION, sessionId = session))
        if (permissionGranted) {
            startVoice(session)
            return null
        }
        return session
    }

    fun voicePermissionResult(
        session: Long,
        granted: Boolean,
        showSettingsHint: Boolean = false,
    ) {
        if (session != voiceGeneration ||
            state.value.voice.phase != VoiceInputPhase.PERMISSION ||
            !canUseVoice()
        ) {
            return
        }
        if (!granted) {
            finishVoice(problem = if (showSettingsHint) VoiceInputProblem.PERMISSION else null)
            return
        }
        voicePermissionGranted = true
        if (voiceResumed) startVoice(session)
    }

    fun stopVoice() {
        if (state.value.voice.phase != VoiceInputPhase.LISTENING || !canUseVoice()) return
        waitForVoiceResult(state.value.voice.sessionId)
        speechInput.stop()
    }

    fun cancelVoice() {
        confirmedVoiceText = ""
        val wasCapturing = state.value.voice.capturing
        voiceGeneration++
        voicePermissionGranted = false
        voiceTimeout?.cancel()
        if (wasCapturing) speechInput.cancel()
        updateVoice(
            VoiceInputState(
                available = state.value.voice.available,
                phase =
                    if ((returnToVoiceReview || state.value.voice.phase == VoiceInputPhase.REVIEW) &&
                        draft.text.isNotBlank()
                    ) {
                        VoiceInputPhase.REVIEW
                    } else {
                        VoiceInputPhase.IDLE
                    },
            ),
        )
    }

    fun dismissVoiceProblem() = updateVoice(state.value.voice.copy(problem = null))

    fun finishVoiceReview() {
        returnToVoiceReview = false
        updateVoice(state.value.voice.copy(phase = VoiceInputPhase.IDLE))
    }

    private fun startVoice(session: Long) {
        if (!canUseVoice() || !voiceResumed || session != voiceGeneration) {
            cancelVoice()
            return
        }
        updateVoice(state.value.voice.copy(phase = VoiceInputPhase.STARTING))
        voiceTimeout?.cancel()
        voiceTimeout =
            viewModelScope.launch {
                delay(20_000)
                if (voiceCurrent(session)) finishVoice(problem = VoiceInputProblem.TIMEOUT)
            }
        speechInput.start(
            object : ConversationSpeechInput.Listener {
                override fun onReady() {
                    if (!voiceCurrent(session) || state.value.voice.phase != VoiceInputPhase.STARTING) return
                    updateVoice(state.value.voice.copy(phase = VoiceInputPhase.LISTENING))
                    voiceTimeout?.cancel()
                    voiceTimeout =
                        viewModelScope.launch {
                            delay(60_000)
                            if (voiceCurrent(session)) stopVoice()
                        }
                }

                override fun onLevel(level: Float) {
                    if (voiceCurrent(session) &&
                        state.value.voice.phase == VoiceInputPhase.LISTENING &&
                        level.isFinite()
                    ) {
                        updateVoice(
                            state.value.voice.copy(
                                levels = (state.value.voice.levels + level.coerceIn(0f, 1f)).takeLast(64),
                            ),
                        )
                    }
                }

                override fun onPartial(text: String) {
                    if (voiceCurrent(session)) {
                        updateVoice(state.value.voice.copy(partial = text.take(ConversationLimits.INPUT_CHARACTERS)))
                    }
                }

                override fun onCommitted(text: String) {
                    if (voiceCurrent(session)) confirmedVoiceText = text
                }

                override fun onEndOfSpeech() {
                    if (voiceCurrent(session)) waitForVoiceResult(session)
                }

                override fun onResult(text: String) {
                    if (!voiceCurrent(session)) return
                    when {
                        text.isBlank() -> finishVoice(problem = VoiceInputProblem.NO_MATCH)
                        text.length > ConversationLimits.INPUT_CHARACTERS ->
                            finishVoice(
                                problem = VoiceInputProblem.TOO_LONG,
                            )
                        else -> {
                            if (state.value.failed) resumeEditing()
                            draft = TextFieldValue(text, TextRange(text.length))
                            returnToVoiceReview = true
                            finishVoice()
                        }
                    }
                }

                override fun onFailure(problem: VoiceInputProblem) {
                    if (voiceCurrent(session)) finishVoice(problem)
                }
            },
        )
    }

    private fun waitForVoiceResult(session: Long) {
        if (state.value.voice.phase == VoiceInputPhase.STOPPING) return
        updateVoice(state.value.voice.copy(phase = VoiceInputPhase.STOPPING))
        voiceTimeout?.cancel()
        voiceTimeout =
            viewModelScope.launch {
                delay(20_000)
                if (voiceCurrent(session)) finishVoice(problem = VoiceInputProblem.TIMEOUT)
            }
    }

    private fun finishVoice(problem: VoiceInputProblem? = null) {
        if (problem != null && confirmedVoiceText.isNotBlank()) {
            if (state.value.failed) resumeEditing()
            draft = TextFieldValue(confirmedVoiceText, TextRange(confirmedVoiceText.length))
            returnToVoiceReview = true
        }
        cancelVoice()
        updateVoice(state.value.voice.copy(problem = problem))
    }

    private fun updateVoice(voice: VoiceInputState) {
        mutableState.value = state.value.copy(voice = voice)
    }

    private fun voiceCurrent(session: Long) =
        session == voiceGeneration && state.value.voice.capturing && voiceResumed && canUseVoice()

    private fun voiceAvailable() =
        authentication.session.value is GitHubSession.Authenticated && speechInput.isAvailable()

    private fun canUseVoice() =
        active &&
            allowed &&
            interactionAvailable() &&
            profileId != null &&
            accountId != null &&
            (authentication.session.value as? GitHubSession.Authenticated)?.account?.id == accountId &&
            state.value.connectionProblem in setOf(null, ConversationProblem.NETWORK, ConversationProblem.TIMEOUT) &&
            !state.value.replyPending

    fun send(text: String = draft.text) = sendInternal(text, retry = false)

    private fun sendInternal(
        text: String,
        retry: Boolean,
    ) {
        if (!canInteract() ||
            state.value.voice.capturing ||
            state.value.voice.phase == VoiceInputPhase.PERMISSION ||
            work?.isActive == true ||
            (state.value.failed && !retry) ||
            text.isBlank() ||
            text != if (retry) failedTurnText() else draft.text
        ) {
            return
        }
        if (text.length > ConversationLimits.INPUT_CHARACTERS) {
            fail(ConversationProblem.LIMIT)
            return
        }
        val account = accountId ?: return
        val key = storageKey() ?: return
        val lease = stored
        cancelVoice()
        returnToVoiceReview = false
        updateVoice(state.value.voice.copy(phase = VoiceInputPhase.IDLE))
        val request = ++generation
        val user = ConversationMessage((++messageId).toString(), text, true)
        draft = TextFieldValue()
        mutableState.value =
            state.value.copy(messages = history + user, replyPending = true, failed = false, problem = null)
        if (!networkStatus.isOnline()) {
            fail(ConversationProblem.NETWORK)
            return
        }
        work =
            viewModelScope.launch {
                val result =
                    safelyWithinWait {
                        provider.reply(
                            account,
                            conversationId,
                            friendId,
                            (history + user).map { ConversationTurn(it.text, it.fromUser) },
                        )
                    }
                if (request != generation || !canInteract() || account != accountId) return@launch
                when (result) {
                    is ConversationResult.Success -> {
                        if (result.value.isBlank() || result.value.length > ConversationLimits.REPLY_CHARACTERS) {
                            fail(ConversationProblem.PROVIDER)
                        } else {
                            if (conversationStore != null) {
                                savingRequest = request
                                val saved =
                                    try {
                                        conversationStore.append(key, lease ?: return@launch, text, result.value)
                                    } catch (cancelled: CancellationException) {
                                        // A disk commit may have completed before cancellation discarded its result.
                                        if (key == storageKey() &&
                                            account == accountId &&
                                            storageWork?.isActive != true
                                        ) {
                                            restoreConversation()
                                        }
                                        throw cancelled
                                    } catch (_: Exception) {
                                        if (request == generation) {
                                            storageFailure()
                                        } else if (key == storageKey() &&
                                            account == accountId &&
                                            storageWork?.isActive != true
                                        ) {
                                            restoreConversation()
                                        }
                                        return@launch
                                    } finally {
                                        if (savingRequest == request) savingRequest = null
                                    }
                                if (request != generation) {
                                    if (key == storageKey() &&
                                        account == accountId &&
                                        storageWork?.isActive != true
                                    ) {
                                        restoreConversation()
                                    }
                                    return@launch
                                }
                                if (!canInteract() || account != accountId) return@launch
                                if (saved == null) {
                                    restoreConversation()
                                    return@launch
                                }
                                stored = saved
                            }
                            history =
                                history + user + ConversationMessage((++messageId).toString(), result.value, false)
                            draft = TextFieldValue()
                            mutableState.value =
                                state.value.copy(
                                    messages = history,
                                    replyPending = false,
                                    connection = ConversationConnection.READY,
                                )
                        }
                    }
                    is ConversationResult.Failure -> fail(result.problem)
                }
            }
    }

    fun retry() {
        if (state.value.problem == ConversationProblem.STORAGE) {
            restoreConversation()
            return
        }
        if (!canInteract() || work?.isActive == true) return
        failedTurnText()?.let { sendInternal(it, retry = true) }
    }

    fun retryConnection() {
        if (!interactionAvailable() || checkWork?.isActive == true || authenticationRetry?.isActive == true) return
        if (!networkStatus.isOnline()) {
            mutableState.value =
                state.value.copy(
                    connection = ConversationConnection.UNAVAILABLE,
                    connectionProblem = ConversationProblem.NETWORK,
                    connectionRetrying = false,
                )
            return
        }
        when (val session = authentication.session.value) {
            is GitHubSession.Authenticated -> checkConnection(retrying = true)
            is GitHubSession.Failure, GitHubSession.Restoring -> {
                if (session is GitHubSession.Failure &&
                    session.problem !in setOf(AuthenticationProblem.NETWORK, AuthenticationProblem.PROVIDER)
                ) {
                    return
                }
                if (session == GitHubSession.Restoring &&
                    state.value.connectionProblem != ConversationProblem.NETWORK
                ) {
                    return
                }
                val request = ++checkGeneration
                mutableState.value =
                    state.value.copy(
                        connection = ConversationConnection.CHECKING,
                        connectionProblem = null,
                        connectionRetrying = true,
                    )
                authenticationRetry =
                    viewModelScope.launch {
                        try {
                            authentication.restore()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (request == checkGeneration && interactionAvailable()) {
                                authenticationRetry = null
                                mutableState.value =
                                    state.value.copy(
                                        connection = ConversationConnection.UNAVAILABLE,
                                        connectionProblem =
                                            if (networkStatus.isOnline()) {
                                                ConversationProblem.SERVICE
                                            } else {
                                                ConversationProblem.NETWORK
                                            },
                                        connectionRetrying = false,
                                    )
                            }
                            return@launch
                        }
                        if (request != checkGeneration || !interactionAvailable()) return@launch
                        authenticationRetry = null
                        when (val restored = authentication.session.value) {
                            is GitHubSession.Authenticated -> {
                                if (accountId != restored.account.id) {
                                    changeAccount(restored.account.id)
                                }
                                checkConnection(retrying = true)
                            }
                            is GitHubSession.Failure -> {
                                mutableState.value =
                                    state.value.copy(
                                        connection = ConversationConnection.UNAVAILABLE,
                                        connectionProblem =
                                            if (restored.problem == AuthenticationProblem.NETWORK ||
                                                !networkStatus.isOnline()
                                            ) {
                                                ConversationProblem.NETWORK
                                            } else {
                                                ConversationProblem.SERVICE
                                            },
                                        connectionRetrying = false,
                                    )
                            }
                            else -> Unit
                        }
                    }
            }
            else -> Unit
        }
    }

    fun dismissFailure() {
        if (state.value.problem == ConversationProblem.STORAGE) {
            newConversation()
            return
        }
        if (state.value.failed) resumeEditing()
    }

    fun cancel() {
        val pendingTurn = if (state.value.replyPending) unansweredTurnText() else null
        if (savingRequest != null) mutableState.value = state.value.copy(storageBusy = true)
        generation++
        work?.cancel()
        work = null
        mutableState.value =
            state.value.copy(
                messages =
                    if (state.value.failed &&
                        !state.value.replyPending
                    ) {
                        state.value.messages
                    } else {
                        history
                    },
                replyPending = false,
            )
        pendingTurn?.let { draft = TextFieldValue(it, TextRange(it.length)) }
    }

    fun newConversation() {
        if (!active || !allowed || !interactionAvailable() || state.value.storageBusy) return
        if (conversationStore != null) {
            restoreConversation(reset = true)
            return
        }
        cancelVoice()
        returnToVoiceReview = false
        updateVoice(state.value.voice.copy(phase = VoiceInputPhase.IDLE))
        cancel()
        history = emptyList()
        conversationId = UUID.randomUUID().toString()
        draft = TextFieldValue()
        mutableState.value =
            state.value.copy(
                messages = history,
                failed = false,
                problem = null,
            )
    }

    private fun restoreConversation(reset: Boolean = false) {
        val repository = conversationStore ?: return
        val key = storageKey() ?: return
        val account = accountId ?: return
        val previous = stored
        val unsavedText =
            if (!reset && state.value.failed) {
                unansweredTurnText()
            } else {
                null
            }
        val request = ++storageGeneration
        storageWork?.cancel()
        cancelVoice()
        cancel()
        mutableState.value = state.value.copy(storageBusy = true)
        storageWork =
            viewModelScope.launch {
                try {
                    val loaded = if (reset) repository.reset(key, account) else repository.load(key, account)
                    if (request != storageGeneration || key != storageKey() || account != accountId) return@launch
                    stored = loaded
                    conversationId = loaded.id
                    history = loaded.turns.map { ConversationMessage((++messageId).toString(), it.text, it.fromUser) }
                    if (reset) draft = TextFieldValue()
                    val unchanged = loaded.id == previous?.id && loaded.revision == previous.revision
                    if (unsavedText != null && unchanged) {
                        draft = TextFieldValue(unsavedText, TextRange(unsavedText.length))
                    } else if (!unchanged &&
                        loaded.id == previous?.id &&
                        draft.text == loaded.turns.getOrNull(loaded.turns.size - 2)?.text
                    ) {
                        draft = TextFieldValue()
                    }
                    mutableState.value =
                        state.value.copy(
                            messages = history,
                            storageBusy = false,
                            failed = false,
                            problem = null,
                            replyPending = false,
                        )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (request == storageGeneration) storageFailure()
                }
            }
    }

    private fun storageFailure() {
        mutableState.value =
            state.value.copy(
                storageBusy = false,
                replyPending = false,
                failed = true,
                problem = ConversationProblem.STORAGE,
            )
    }

    private fun clear() {
        storageGeneration++
        storageWork?.cancel()
        stored = null
        cancelVoice()
        returnToVoiceReview = false
        cancel()
        cancelCheck()
        history = emptyList()
        conversationId = UUID.randomUUID().toString()
        draft = TextFieldValue()
        mutableState.value = ConversationUiState(voice = VoiceInputState(available = voiceAvailable()))
    }

    override fun onCleared() {
        cancelVoice()
        super.onCleared()
    }

    private fun changeAccount(next: Long?) {
        val provisionalDraft = if (accountId == null && next != null && history.isEmpty()) draft else null
        clear()
        accountId = next
        restoreConversation()
        if (provisionalDraft != null) draft = provisionalDraft
    }

    private fun resumeEditing() {
        val recheckAccess = state.value.problem == ConversationProblem.ACCESS
        failedTurnText()?.let { draft = TextFieldValue(it, TextRange(it.length)) }
        mutableState.value = state.value.copy(messages = history, failed = false, problem = null)
        if (recheckAccess) checkConnection()
    }

    private fun failedTurnText(): String? = unansweredTurnText().takeIf { state.value.failed }

    private fun unansweredTurnText(): String? =
        state.value.messages
            .takeIf { it.size == history.size + 1 }
            ?.lastOrNull()
            ?.takeIf { it.fromUser }
            ?.text

    private fun canInteract() =
        active &&
            allowed &&
            interactionAvailable() &&
            state.value.connection == ConversationConnection.READY &&
            !state.value.storageBusy &&
            savingRequest == null &&
            (conversationStore == null || stored != null) &&
            state.value.problem != ConversationProblem.STORAGE &&
            profileId != null &&
            (authentication.session.value as? GitHubSession.Authenticated)?.account?.id == accountId &&
            accountId != null

    private fun interactionAvailable() = if (foregroundManaged) foregroundAllowed else active && allowed

    private fun checkAllowed() = interactionAvailable() && authentication.session.value is GitHubSession.Authenticated

    private fun checkConnection(
        force: Boolean = false,
        retrying: Boolean = false,
    ) {
        if (!checkAllowed()) return
        if (checkWork?.isActive == true) {
            if (!force) return
            cancelCheck()
        }
        val account = (authentication.session.value as? GitHubSession.Authenticated)?.account?.id ?: return
        if (account != accountId) return
        if (!networkStatus.isOnline()) {
            mutableState.value =
                state.value.copy(
                    connection = ConversationConnection.UNAVAILABLE,
                    connectionProblem = ConversationProblem.NETWORK,
                    connectionRetrying = false,
                )
            return
        }
        val request = ++checkGeneration
        mutableState.value =
            state.value.copy(
                connection = ConversationConnection.CHECKING,
                connectionProblem = null,
                connectionRetrying = retrying,
            )
        checkWork =
            viewModelScope.launch {
                val result = safelyWithinWait { provider.connect(account) }
                if (request != checkGeneration || !checkAllowed() || account != accountId) return@launch
                checkWork = null
                if (result is ConversationResult.Failure &&
                    result.problem !in setOf(ConversationProblem.NETWORK, ConversationProblem.TIMEOUT)
                ) {
                    cancelVoice()
                }
                mutableState.value =
                    when (result) {
                        is ConversationResult.Success -> {
                            val current = state.value
                            val recoveredAccess = current.failed && current.problem == ConversationProblem.ACCESS
                            if (recoveredAccess) {
                                failedTurnText()?.let { draft = TextFieldValue(it, TextRange(it.length)) }
                            }
                            current.copy(
                                connection = ConversationConnection.READY,
                                connectionProblem = null,
                                connectionRetrying = false,
                                messages = if (recoveredAccess) history else current.messages,
                                failed = if (recoveredAccess) false else current.failed,
                                problem = if (recoveredAccess) null else current.problem,
                            )
                        }
                        is ConversationResult.Failure ->
                            state.value.copy(
                                connection = ConversationConnection.UNAVAILABLE,
                                connectionProblem = result.problem,
                                connectionRetrying = false,
                            )
                    }
            }
    }

    private fun cancelCheck() {
        checkGeneration++
        checkWork?.cancel()
        checkWork = null
        authenticationRetry?.cancel()
        authenticationRetry = null
        if (state.value.connection == ConversationConnection.CHECKING ||
            state.value.connection == ConversationConnection.READY
        ) {
            mutableState.value =
                state.value.copy(connection = ConversationConnection.UNAVAILABLE, connectionRetrying = false)
        }
    }

    private fun fail(problem: ConversationProblem) {
        if (state.value.replyPending) draft = TextFieldValue()
        mutableState.value =
            state.value.copy(
                messages = if (state.value.replyPending) state.value.messages else history,
                replyPending = false,
                failed = true,
                problem = problem,
                connection =
                    if (problem !in setOf(ConversationProblem.LIMIT, ConversationProblem.RESTRICTED)) {
                        ConversationConnection.UNAVAILABLE
                    } else {
                        state.value.connection
                    },
                connectionProblem =
                    if (problem !in setOf(ConversationProblem.LIMIT, ConversationProblem.RESTRICTED)) {
                        problem
                    } else {
                        state.value.connectionProblem
                    },
            )
    }

    private suspend fun <T> safely(block: suspend () -> ConversationResult<T>): ConversationResult<T> =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ConversationResult.Failure(ConversationProblem.PROVIDER)
        }

    private suspend fun <T> safelyWithinWait(block: suspend () -> ConversationResult<T>): ConversationResult<T> {
        if (!networkStatus.isOnline()) return ConversationResult.Failure(ConversationProblem.NETWORK)
        val result =
            try {
                withTimeout(30_000) { safely(block) }
            } catch (_: TimeoutCancellationException) {
                ConversationResult.Failure(ConversationProblem.TIMEOUT)
            }
        return if (result is ConversationResult.Failure && !networkStatus.isOnline()) {
            ConversationResult.Failure(ConversationProblem.NETWORK)
        } else {
            result
        }
    }
}
