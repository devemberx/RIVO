package com.monsters.mobimon.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.ui.MobiMonNavigationButton
import com.monsters.mobimon.core.ui.MobiMonReferenceText
import com.monsters.mobimon.core.ui.PetAvatar
import com.monsters.mobimon.core.ui.mobiMonReferenceTextStyle
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

/** Conversation presentation. The caller owns readiness, input and request lifecycles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    state: ConversationUiState,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit,
    onCancelReply: () -> Unit,
    onBack: () -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
    interactionAllowed: Boolean = false,
    simulatedVehicle: Boolean = false,
    friendId: String = "friend:mobi",
    appearanceKey: String = "GOLDEN",
    accessoryId: String? = null,
    outfitId: String? = null,
    backgroundId: String? = null,
    vehicleWarning: Boolean = false,
    vehicleHungry: Boolean = false,
    onRetry: () -> Unit = onOpenConnection,
    onDismissFailure: () -> Unit = {},
    onNewConversation: (() -> Unit)? = null,
    onReturnHome: () -> Unit = onBack,
    onRecheckConnection: () -> Unit = onRetry,
    parkingBadgeConfirmed: Boolean = interactionAllowed,
    onStartVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    onCancelVoice: () -> Unit = {},
    onDismissVoiceProblem: () -> Unit = {},
    onFinishVoiceReview: () -> Unit = {},
) {
    val friend =
        stringResource(
            when (friendId) {
                "friend:luna" -> R.string.copilot_luna
                "friend:las" -> R.string.copilot_las
                else -> R.string.copilot_mobi
            },
        )
    val title = stringResource(R.string.chat_title)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val imeVisible = WindowInsets.isImeVisible
    var connectionRecoveryRequested by rememberSaveable(friendId) { mutableStateOf(false) }
    val connectionRecovered = state.connection == ConversationConnection.READY && state.connectionProblem == null
    val networkChecking = state.connection == ConversationConnection.CHECKING && state.connectionRetrying
    val connectionDialog =
        interactionAllowed &&
            (
                connectionRecoveryRequested &&
                    !connectionRecovered ||
                    state.connectionProblem != null &&
                    !state.connectionProblem.isRecoverableConnectionProblem
            )
    val recheckConnection = {
        connectionRecoveryRequested = true
        onRecheckConnection()
    }
    val leaveConnectionRecovery = {
        connectionRecoveryRequested = false
        onReturnHome()
    }
    LaunchedEffect(connectionRecoveryRequested, interactionAllowed, state.connection, state.connectionProblem) {
        if (!interactionAllowed ||
            connectionRecovered ||
            state.connection == ConversationConnection.SIGNED_OUT &&
            state.connectionProblem == null
        ) {
            connectionRecoveryRequested = false
        }
    }
    val panelState = if (connectionDialog && state.failed) state.copy(failed = false) else state
    val back = {
        // adjustResize can consume Compose's IME bounds; check the window at the time of the action.
        if (ViewCompat.getRootWindowInsets(view.rootView)?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
            keyboard?.hide()
            focus.clearFocus()
        } else if (state.voice.capturing || state.voice.phase == VoiceInputPhase.PERMISSION) {
            onCancelVoice()
        } else if (state.voice.phase == VoiceInputPhase.REVIEW) {
            onFinishVoiceReview()
        } else {
            onBack()
        }
    }
    BackHandler(enabled = imeVisible && interactionAllowed) { back() }
    BackHandler(
        enabled =
            interactionAllowed &&
                (
                    state.voice.capturing ||
                        state.voice.phase in
                        setOf(
                            VoiceInputPhase.PERMISSION,
                            VoiceInputPhase.REVIEW,
                        )
                ),
    ) {
        back()
    }
    BackHandler(enabled = !interactionAllowed) { onReturnHome() }
    BackHandler(enabled = connectionDialog) { leaveConnectionRecovery() }
    LaunchedEffect(interactionAllowed, connectionDialog, onCancelVoice) {
        if (!interactionAllowed || connectionDialog) {
            onCancelVoice()
            keyboard?.hide()
            focus.clearFocus()
        }
    }
    LaunchedEffect(state.voice.capturing, state.voice.phase == VoiceInputPhase.PERMISSION) {
        if (state.voice.capturing || state.voice.phase == VoiceInputPhase.PERMISSION) {
            keyboard?.hide()
            focus.clearFocus()
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Colors.background)
            .imePadding()
            .semantics { paneTitle = title },
    ) {
        // Width determines reference geometry; the IME changes available height, never the whole screen scale.
        val wide = maxWidth >= 1200.dp && LocalDensity.current.fontScale <= 1.1f
        val scale = if (wide) maxWidth.value / 2560f else 0.65f
        val shortened = maxHeight < (if (wide) 1160.dp else 1050.dp) * scale
        val contentHeight = maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .focusProperties { canFocus = interactionAllowed && !connectionDialog }
                .then(if (interactionAllowed && !connectionDialog) Modifier else Modifier.clearAndSetSemantics {}),
        ) {
            if (wide) {
                val panelBottom = contentHeight - 24.dp * scale
                Box(Modifier.fillMaxSize()) {
                    CompanionConversationPanel(
                        friend,
                        friendId,
                        appearanceKey,
                        accessoryId,
                        outfitId,
                        backgroundId,
                        vehicleWarning,
                        vehicleHungry,
                        shortened,
                        scale,
                        Modifier
                            .offset(72.dp * scale, 196.dp * scale)
                            .size(680.dp * scale, (panelBottom - 196.dp * scale).coerceAtLeast(0.dp)),
                    )
                    ConversationPanel(
                        panelState,
                        draft,
                        onDraftChange,
                        onSend,
                        onCancelReply,
                        onOpenConnection,
                        friend,
                        interactionAllowed,
                        shortened,
                        scale,
                        true,
                        Modifier
                            .offset(796.dp * scale, 34.dp * scale)
                            .size(1692.dp * scale, (panelBottom - 34.dp * scale).coerceAtLeast(0.dp)),
                        onNewConversation,
                        onRetry,
                        onDismissFailure,
                        onStartVoice,
                        onStopVoice,
                        onCancelVoice,
                        onDismissVoiceProblem,
                        recheckConnection,
                    )
                    ConversationAuthBadge(
                        connection = state.connection,
                        problem = state.connectionProblem,
                        scale = scale,
                        modifier = Modifier.offset(1887.dp * scale, 51.dp * scale),
                    )
                    ConversationHeader(
                        friend,
                        back,
                        simulatedVehicle,
                        scale,
                        true,
                        shortened,
                        state.failed,
                        Modifier
                            .offset(72.dp * scale, 36.dp * scale)
                            .width(680.dp * scale),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp)) {
                    ConversationHeader(friend, back, simulatedVehicle, scale, false, shortened, state.failed)
                    Spacer(Modifier.height(16.dp))
                    ConversationPanel(
                        panelState,
                        draft,
                        onDraftChange,
                        onSend,
                        onCancelReply,
                        onOpenConnection,
                        friend,
                        interactionAllowed,
                        shortened,
                        scale,
                        false,
                        Modifier.weight(1f),
                        onNewConversation,
                        onRetry,
                        onDismissFailure,
                        onStartVoice,
                        onStopVoice,
                        onCancelVoice,
                        onDismissVoiceProblem,
                        recheckConnection,
                    )
                }
            }
        }
        if (wide) {
            ConversationParkingBadge(
                confirmed = parkingBadgeConfirmed,
                scale = scale,
                modifier =
                    Modifier.offset(
                        (if (parkingBadgeConfirmed) 2146.dp else 2144.dp) * scale,
                        51.dp * scale,
                    ),
            )
        } else {
            ConversationParkingBadge(
                confirmed = parkingBadgeConfirmed,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 16.dp),
                scale = scale,
            )
        }
        if (!interactionAllowed) {
            ConversationParkingOverlay(onReturnHome)
        } else if (connectionDialog) {
            ConversationNetworkOverlay(
                checking = networkChecking,
                problem = state.connectionProblem ?: state.problem,
                onHome = leaveConnectionRecovery,
                onRetry =
                    if (state.connectionProblem == ConversationProblem.ACCOUNT) {
                        onOpenConnection
                    } else {
                        recheckConnection
                    },
            )
        }
    }
}

@Composable
private fun ConversationHeader(
    friend: String,
    onBack: () -> Unit,
    simulated: Boolean,
    scale: Float,
    wide: Boolean,
    shortened: Boolean,
    failed: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (wide) 104.dp * scale else 76.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(if (wide) 32.dp * scale else 16.dp),
        ) {
            val backSize = if (wide) 104.dp * scale else 76.dp
            Box(Modifier.size(backSize), contentAlignment = Alignment.Center) {
                MobiMonNavigationButton(
                    painterResource(R.drawable.copilot_back),
                    stringResource(R.string.copilot_back),
                    onBack,
                    modifier = Modifier.requiredSize(backSize.coerceAtLeast(76.dp)),
                    visualSize = backSize,
                    iconSize = 28.dp * scale,
                    borderWidth = 2.dp * scale,
                )
            }
            if (wide) {
                Box(Modifier.weight(1f).height(104.dp * scale)) {
                    MobiMonReferenceText(
                        stringResource(R.string.chat_title),
                        0f,
                        45.2f,
                        46f,
                        scale = scale,
                        bold = true,
                        modifier = Modifier.semantics { heading() },
                    )
                    MobiMonReferenceText(
                        stringResource(R.string.chat_subtitle, friend),
                        0f,
                        88.2f,
                        28f,
                        scale = scale,
                        color = Colors.muted,
                    )
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.chat_title),
                        Modifier.semantics { heading() },
                        style = mobiMonReferenceTextStyle(46f, scale, true),
                        color = Colors.text,
                    )
                    if (!shortened) {
                        Text(
                            stringResource(
                                if (failed) R.string.chat_failure_subtitle else R.string.chat_subtitle,
                                friend,
                            ),
                            style = mobiMonReferenceTextStyle(28f, scale),
                            color = Colors.muted,
                        )
                    }
                }
            }
        }
        if (simulated) {
            Text(
                stringResource(R.string.copilot_simulated),
                modifier = Modifier.align(Alignment.BottomStart).offset(y = 32.dp * scale),
                style = mobiMonReferenceTextStyle(20f, scale),
                color = Colors.warning,
            )
        }
    }
}

@Composable
private fun CompanionConversationPanel(
    friend: String,
    friendId: String,
    appearanceKey: String,
    accessoryId: String?,
    outfitId: String?,
    backgroundId: String?,
    vehicleWarning: Boolean,
    vehicleHungry: Boolean,
    shortened: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.background(Colors.panel, ConversationPanelShape(36.923f / 680f)).testTag("chat-companion"),
    ) {
        val avatarSize =
            if (shortened) minOf(440.dp * scale, (maxHeight - 280.dp * scale).coerceAtLeast(0.dp)) else 624.dp * scale
        Text(
            stringResource(R.string.chat_companion_title, friend),
            Modifier.fillMaxWidth().padding(top = (if (shortened) 40.dp else 61.dp) * scale),
            style = mobiMonReferenceTextStyle(if (shortened) 44f else 42f, scale, true),
            color = Colors.text,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (if (shortened) 108.dp else 140.dp) * scale)
                .size(avatarSize)
                .testTag("chat-avatar"),
        ) {
            val referenceOffset =
                if (friendId == "friend:mobi" && accessoryId == null && outfitId == null) {
                    avatarSize * (35.24f / 1254f)
                } else {
                    0.dp
                }
            PetAvatar(
                Modifier.fillMaxSize().offset(y = referenceOffset),
                appearanceKey,
                friendId,
                accessoryId,
                outfitId,
                backgroundId = backgroundId,
                vehicleWarning = vehicleWarning,
                vehicleHungry = vehicleHungry,
            )
        }
        Text(
            stringResource(R.string.chat_companion_tagline),
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (if (shortened) 100.dp else 124.dp) * scale),
            style = mobiMonReferenceTextStyle(if (shortened) 36f else 42f, scale, true),
            color = Colors.text,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.chat_companion_note),
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (if (shortened) 54.dp else 63.dp) * scale),
            style = mobiMonReferenceTextStyle(30f, scale),
            color = Colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}
