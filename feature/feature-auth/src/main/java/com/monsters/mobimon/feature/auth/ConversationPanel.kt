package com.monsters.mobimon.feature.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.core.domain.ConversationLimits
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonReferenceText
import com.monsters.mobimon.core.ui.mobiMonReferenceTextStyle
import kotlin.math.PI
import kotlin.math.cos
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

@Composable
internal fun ConversationPanel(
    state: ConversationUiState,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit,
    onCancelReply: () -> Unit,
    onOpenConnection: () -> Unit,
    friend: String,
    allowed: Boolean,
    shortened: Boolean,
    scale: Float,
    wide: Boolean,
    modifier: Modifier = Modifier,
    onNewConversation: (() -> Unit)? = null,
    onRetry: () -> Unit = {},
    onDismissFailure: () -> Unit = {},
    onStartVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    onCancelVoice: () -> Unit = {},
    onDismissVoiceProblem: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val seenMessageIds = remember { mutableStateListOf<String>().apply { addAll(state.messages.map { it.id }) } }
    val keyboard = LocalSoftwareKeyboardController.current
    val chooseSuggestion: (String) -> Unit = { text ->
        if (allowed && !state.replyPending) {
            onDraftChange(TextFieldValue(text, TextRange(text.length)))
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    if (wide) {
        ReferenceConversationPanel(
            state,
            draft,
            onDraftChange,
            onSend,
            onCancelReply,
            onOpenConnection,
            friend,
            allowed,
            shortened,
            scale,
            focusRequester,
            chooseSuggestion,
            modifier,
            onNewConversation,
            onRetry,
            onDismissFailure,
            seenMessageIds,
            onStartVoice,
            onStopVoice,
            onCancelVoice,
            onDismissVoiceProblem,
        )
        return
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(Colors.panel, ConversationPanelShape(54.581f / 1692f))
            .testTag("chat-panel")
            .padding(
                start = (if (wide) 56.dp else 24.dp) * scale,
                end = (if (wide) 72.dp else 24.dp) * scale,
                top = (if (wide) 32.dp else 24.dp) * scale,
                bottom = (if (shortened) 32.dp else 20.dp) * scale,
            ),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp * scale), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.chat_today, friend),
                Modifier.weight(1f).semantics { heading() },
                style = mobiMonReferenceTextStyle(36f, scale, true),
                color = Colors.text,
            )
            Box(
                Modifier
                    .then(if (wide) Modifier.size(244.dp * scale, 60.dp * scale) else Modifier)
                    .background(Colors.raised, RoundedCornerShape(50))
                    .padding(
                        horizontal = (if (wide) 0.dp else 24.dp) * scale,
                        vertical =
                            10.dp * scale,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(
                        when (state.connection) {
                            ConversationConnection.READY -> R.string.chat_ready
                            ConversationConnection.SIGNED_OUT -> R.string.chat_signed_out
                            ConversationConnection.CHECKING -> R.string.chat_checking
                            ConversationConnection.UNAVAILABLE -> R.string.chat_unavailable
                        },
                    ),
                    style = mobiMonReferenceTextStyle(28f, scale),
                    color = if (state.connection == ConversationConnection.READY) Colors.success else Colors.muted,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            if (state.messages.isEmpty() && !state.replyPending) {
                ConversationEmpty(scale, shortened, Modifier.fillMaxSize())
            } else {
                ConversationMessages(state, friend, scale, shortened, seenMessageIds, Modifier.fillMaxSize())
            }
        }
        if (state.failed) {
            CompactConversationInlineFailure(state.problem, onRetry, onDismissFailure, allowed, scale)
        }
        if (!shortened &&
            !state.replyPending &&
            !state.failed &&
            !state.voice.capturing &&
            state.voice.phase != VoiceInputPhase.REVIEW
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (state.messages.isNotEmpty() && onNewConversation != null) {
                    ConversationAction(
                        stringResource(R.string.chat_new),
                        onNewConversation,
                        allowed,
                        scale,
                        Modifier.width(240.dp * scale).testTag("chat-new-action"),
                    )
                } else if (state.messages.isEmpty()) {
                    listOf(R.string.chat_suggestion_mood, R.string.chat_suggestion_story).forEach { resource ->
                        val text = stringResource(resource)
                        ConversationAction(text, { chooseSuggestion(text) }, allowed, scale, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(24.dp * scale))
        }
        if (state.voice.capturing || state.voice.phase == VoiceInputPhase.PERMISSION || state.voice.problem != null) {
            Text(
                voiceHintText(state.voice),
                Modifier.padding(bottom = 24.dp * scale).semantics { liveRegion = LiveRegionMode.Polite },
                style = mobiMonReferenceTextStyle(24f, scale),
                color = Color(0xFFA9BDCF),
            )
        }
        if (state.voice.capturing) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val recordingScale = maxWidth.value / 1577f
                ConversationRecordingControl(
                    state.voice,
                    onStopVoice,
                    onCancelVoice,
                    allowed,
                    recordingScale,
                    Modifier.fillMaxWidth().height(87.dp * recordingScale),
                )
            }
        } else {
            ConversationComposer(
                state,
                draft,
                onDraftChange,
                onSend,
                onCancelReply,
                friend,
                allowed,
                scale,
                wide,
                focusRequester,
                onStartVoice = onStartVoice,
                onDismissVoiceProblem = onDismissVoiceProblem,
            )
        }
        if (draft.text.length > ConversationLimits.INPUT_CHARACTERS) {
            Text(
                stringResource(R.string.chat_input_limit),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = mobiMonReferenceTextStyle(24f, scale),
                color = Colors.muted,
            )
        }
        if (!shortened && !state.voice.capturing && state.voice.phase != VoiceInputPhase.REVIEW) {
            Text(
                stringResource(
                    if (state.connection ==
                        ConversationConnection.SIGNED_OUT
                    ) {
                        R.string.chat_sign_in_note
                    } else {
                        R.string.chat_disclaimer
                    },
                ),
                Modifier.padding(top = 12.dp * scale),
                style = mobiMonReferenceTextStyle(24f, scale).copy(lineHeight = (32f * scale).sp),
                color = Colors.muted,
            )
        }
        if (state.connection == ConversationConnection.SIGNED_OUT && !shortened) {
            ConversationAction(
                stringResource(R.string.conversation_connect),
                onOpenConnection,
                allowed,
                scale,
                Modifier.fillMaxWidth().padding(top = 12.dp * scale),
            )
        }
    }
}

@Composable
private fun CompactConversationInlineFailure(
    problem: ConversationProblem?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    allowed: Boolean,
    scale: Float,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp * scale)
            .testTag("chat-inline-failure")
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp * scale),
    ) {
        Text(
            stringResource(R.string.chat_inline_failure_title),
            style = mobiMonReferenceTextStyle(24f, scale, true),
            color = Color(0xFFEAB8AA),
        )
        Text(
            stringResource(problem?.let(::conversationFailureNote) ?: R.string.chat_inline_failure_note),
            style = mobiMonReferenceTextStyle(20f, scale),
            color = Color(0xFFB5C5D5),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
            ConversationAction(
                stringResource(conversationRetryLabel(problem)),
                onRetry,
                allowed,
                scale,
                Modifier.weight(1f),
            )
            ConversationAction(
                stringResource(R.string.chat_return),
                onDismiss,
                true,
                scale,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReferenceConversationPanel(
    state: ConversationUiState,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit,
    onCancelReply: () -> Unit,
    onOpenConnection: () -> Unit,
    friend: String,
    allowed: Boolean,
    shortened: Boolean,
    scale: Float,
    focusRequester: FocusRequester,
    chooseSuggestion: (String) -> Unit,
    modifier: Modifier,
    onNewConversation: (() -> Unit)?,
    onRetry: () -> Unit,
    onDismissFailure: () -> Unit,
    seenMessageIds: MutableList<String>,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onDismissVoiceProblem: () -> Unit,
) {
    BoxWithConstraints(
        modifier
            .background(Colors.panel, ConversationPanelShape(54.581f / 1692f))
            .clipToBounds()
            .testTag("chat-panel"),
    ) {
        val composerTop = maxHeight - (if (shortened) 112.dp else 140.dp) * scale
        val bodyBottom =
            composerTop -
                (
                    if (state.failed ||
                        state.voice.capturing ||
                        state.voice.phase == VoiceInputPhase.REVIEW ||
                        state.voice.problem != null
                    ) {
                        108.dp
                    } else if (state.messages.isNotEmpty() && !shortened && !state.replyPending) {
                        80.dp
                    } else {
                        20.dp
                    }
                ) * scale
        Icon(
            painterResource(R.drawable.conversation_chat),
            null,
            Modifier.offset(80.dp * scale, 36.dp * scale).size(40.dp * scale),
            tint = Colors.accent,
        )
        MobiMonReferenceText(
            stringResource(R.string.chat_today, friend),
            141f,
            70f,
            36f,
            scale = scale,
            bold = true,
            modifier = Modifier.semantics { heading() },
        )
        Box(
            Modifier
                .offset(56.dp * scale, 99.dp * scale)
                .size(1580.dp * scale, 2.dp * scale)
                .background(Colors.border),
        )

        if (state.messages.isEmpty() && !state.replyPending && !state.failed) {
            val titleTop = if (shortened) (composerTop - 270.dp * scale) else 459.dp * scale
            Column(
                Modifier.offset(y = titleTop).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.chat_empty_title),
                    style = mobiMonReferenceTextStyle(48f, scale, true),
                    color = Colors.text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp * scale))
                Text(
                    stringResource(R.string.chat_empty_note),
                    style = mobiMonReferenceTextStyle(28f, scale),
                    color = Colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
            if (!shortened) {
                ReferenceSuggestion(
                    stringResource(R.string.chat_suggestion_mood),
                    372f,
                    293f,
                    { chooseSuggestion("오늘 하루 이야기할래") },
                    allowed,
                    scale,
                )
                val story = stringResource(R.string.chat_suggestion_story)
                ReferenceSuggestion(story, 689f, 326f, { chooseSuggestion(story) }, allowed, scale)
                val friendSuggestion = stringResource(R.string.chat_suggestion_friend, friend)
                ReferenceSuggestion(
                    friendSuggestion,
                    1039f,
                    281f,
                    { chooseSuggestion(friendSuggestion) },
                    allowed,
                    scale,
                )
            }
        } else {
            val bodyHeight = (bodyBottom - 120.dp * scale).coerceAtLeast(0.dp)
            Column(
                Modifier
                    .offset(56.dp * scale, 115.dp * scale)
                    .size(1580.dp * scale, bodyHeight + 5.dp * scale),
            ) {
                ConversationMessages(
                    state,
                    friend,
                    scale,
                    shortened,
                    seenMessageIds,
                    Modifier.weight(1f).fillMaxWidth(),
                    reference = true,
                )
            }
            if (state.failed) {
                ConversationInlineFailure(
                    state.problem,
                    onRetry,
                    onDismissFailure,
                    allowed,
                    scale,
                    Modifier
                        .offset(56.dp * scale, composerTop - 91.dp * scale)
                        .size(1580.dp * scale, 70.dp * scale),
                )
            }
            if (!shortened &&
                !state.replyPending &&
                !state.failed &&
                state.messages.isNotEmpty() &&
                onNewConversation != null &&
                !state.voice.capturing &&
                state.voice.phase != VoiceInputPhase.REVIEW
            ) {
                ReferenceAction(
                    stringResource(R.string.chat_new),
                    onNewConversation,
                    allowed,
                    scale,
                    Modifier
                        .offset(1416.dp * scale, composerTop - 72.dp * scale)
                        .width(220.dp * scale)
                        .testTag("chat-new-action"),
                    visualHeight = 58f,
                )
            }
        }

        if (state.voice.capturing || state.voice.phase == VoiceInputPhase.PERMISSION || state.voice.problem != null) {
            MobiMonReferenceText(
                voiceHintText(state.voice),
                58f,
                composerTop.value / scale + 120.5f,
                24f,
                scale = scale,
                color = Color(0xFFA9BDCF),
                modifier =
                    Modifier.width(1580.dp * scale).testTag("chat-voice-hint").semantics {
                        liveRegion =
                            LiveRegionMode.Polite
                    },
            )
        }
        if (state.voice.capturing) {
            ConversationRecordingControl(
                state.voice,
                onStopVoice,
                onCancelVoice,
                allowed,
                scale,
                Modifier.offset(57.5.dp * scale, composerTop + 0.5.dp * scale).size(1577.dp * scale, 87.dp * scale),
            )
        } else {
            ConversationComposer(
                state,
                draft,
                onDraftChange,
                onSend,
                onCancelReply,
                friend,
                allowed,
                scale,
                true,
                focusRequester,
                Modifier
                    .offset(
                        57.5.dp * scale,
                        composerTop + 0.5.dp * scale,
                    ).size(1577.dp * scale, 87.dp * scale),
                onStartVoice,
                onDismissVoiceProblem,
            )
        }
        if (draft.text.length > ConversationLimits.INPUT_CHARACTERS) {
            Text(
                stringResource(R.string.chat_input_limit),
                Modifier
                    .offset(60.dp * scale, (composerTop - 34.dp * scale))
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = mobiMonReferenceTextStyle(22f, scale),
                color = Colors.warning,
            )
        }
        if (!state.voice.capturing && state.voice.phase != VoiceInputPhase.PERMISSION && state.voice.problem == null) {
            MobiMonReferenceText(
                stringResource(
                    if (state.connection ==
                        ConversationConnection.SIGNED_OUT
                    ) {
                        R.string.chat_sign_in_note
                    } else {
                        R.string.chat_disclaimer
                    },
                ),
                58f,
                composerTop.value / scale + (if (shortened) 103.5f else 112f),
                18f,
                scale = scale,
                color = Color(0xFFA9BDCF),
            )
        }
        if (state.connection == ConversationConnection.SIGNED_OUT && !shortened) {
            ReferenceAction(
                stringResource(R.string.conversation_connect),
                onOpenConnection,
                allowed,
                scale,
                Modifier.offset(1100.dp * scale, (composerTop - 88.dp * scale)).width(536.dp * scale),
            )
        }
    }
}

@Composable
private fun ReferenceSuggestion(
    text: String,
    x: Float,
    width: Float,
    onClick: () -> Unit,
    enabled: Boolean,
    scale: Float,
) {
    ReferenceAction(
        text,
        onClick,
        enabled,
        scale,
        Modifier.offset(x.dp * scale, 646.dp * scale).width(width.dp * scale),
    )
}

@Composable
private fun ReferenceAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
    visualHeight: Float = 70f,
    backgroundColor: Color = Color(0xFF203C58),
) {
    Box(modifier.height(visualHeight.dp * scale), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .requiredHeight((visualHeight.dp * scale).coerceAtLeast(76.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(visualHeight.dp * scale)
                    .background(backgroundColor, RoundedCornerShape(35.dp * scale)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    style = mobiMonReferenceTextStyle(28f, scale),
                    color = if (enabled) Color(0xFFDCE9F4) else Colors.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ConversationInlineFailure(
    problem: ConversationProblem?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    allowed: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.testTag("chat-inline-failure")) {
        Icon(
            painterResource(R.drawable.conversation_inline_warning),
            null,
            Modifier.offset(y = -2.dp * scale).size(32.dp * scale).testTag("chat-inline-warning-icon"),
            tint = Color.Unspecified,
        )
        Text(
            stringResource(R.string.chat_inline_failure_title),
            Modifier.offset(48.dp * scale, -9.dp * scale).width(262.dp * scale),
            style = mobiMonReferenceTextStyle(30f, scale).copy(lineHeight = (36f * scale).sp),
            color = Color(0xFFEAB8AA),
            maxLines = 1,
            softWrap = false,
        )
        Text(
            stringResource(
                if (problem ==
                    null
                ) {
                    R.string.chat_inline_failure_note
                } else {
                    conversationFailureNote(problem)
                },
            ),
            Modifier.offset(48.dp * scale, 31.dp * scale).width(1020.dp * scale),
            style = mobiMonReferenceTextStyle(26f, scale).copy(lineHeight = (31f * scale).sp),
            color = Color(0xFFB5C5D5),
            maxLines = 1,
            softWrap = false,
        )
        ReferenceFailureAction(
            stringResource(conversationRetryLabel(problem)),
            onRetry,
            allowed,
            scale,
            Modifier.offset(1122.dp * scale, 4.dp * scale).width(230.dp * scale),
            R.drawable.conversation_retry,
            "chat-inline-retry-visual",
            26f,
            backgroundColor = Color(0xFF223F59),
            textColor = Color(0xFFD4F4F5),
        )
        ReferenceFailureAction(
            stringResource(R.string.chat_return),
            onDismiss,
            true,
            scale,
            Modifier.offset(1376.dp * scale, 4.dp * scale).width(206.dp * scale),
            R.drawable.conversation_edit,
            "chat-inline-edit-visual",
            23f,
            backgroundColor = Color(0xFF22394E),
            textColor = Color(0xFFD6E2ED),
        )
    }
}

@Composable
private fun ReferenceFailureAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    scale: Float,
    modifier: Modifier,
    iconRes: Int,
    visualTag: String,
    leadingPadding: Float,
    backgroundColor: Color,
    textColor: Color,
) {
    Box(modifier.height(52.dp * scale), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .requiredHeight((52.dp * scale).coerceAtLeast(76.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp * scale)
                    .background(backgroundColor, RoundedCornerShape(16.dp * scale))
                    .testTag(visualTag)
                    .padding(start = leadingPadding.dp * scale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(iconRes), null, Modifier.size(36.dp * scale), tint = Color.Unspecified)
                Spacer(Modifier.width(8.dp * scale))
                Text(
                    text,
                    style = mobiMonReferenceTextStyle(28f, scale),
                    color = if (enabled) textColor else Colors.muted,
                )
            }
        }
    }
}

@Composable
private fun ConversationEmpty(
    scale: Float,
    shortened: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).offset(x = 16.dp * scale).padding(
            top =
                (if (shortened) 20.dp else 100.dp) * scale,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!shortened) {
            Box(
                Modifier.size(112.dp * scale).background(Colors.raised, RoundedCornerShape(36.dp * scale)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.copilot_chat), null, Modifier.size(56.dp * scale), tint = Colors.accent)
            }
            Spacer(Modifier.height(56.dp * scale))
        }
        Text(
            stringResource(R.string.chat_empty_title),
            style = mobiMonReferenceTextStyle(if (shortened) 36f else 52f, scale, true),
            color = Colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp * scale))
        Text(
            stringResource(R.string.chat_empty_note),
            style = mobiMonReferenceTextStyle(32f, scale),
            color = Colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConversationMessages(
    state: ConversationUiState,
    friend: String,
    scale: Float,
    shortened: Boolean,
    seenMessageIds: MutableList<String>,
    modifier: Modifier = Modifier,
    reference: Boolean = false,
) {
    val scroll = rememberLazyListState()
    val motionEnabled = LocalMobiMonMotionEnabled.current
    val count = state.messages.size + if (state.replyPending) 1 else 0
    LaunchedEffect(count, shortened) {
        // BoxWithConstraints can launch this during measurement; scrolling forces a remeasure.
        withFrameNanos { }
        if (count > 0) {
            scroll.scrollToItem(0)
            withFrameNanos { }
            val visible = scroll.layoutInfo.visibleItemsInfo
            val last = visible.lastOrNull()
            val allFit =
                visible.firstOrNull()?.index == 0 &&
                    last != null &&
                    last.index == count - 1 &&
                    last.offset + last.size <= scroll.layoutInfo.viewportEndOffset
            if (!allFit) scroll.scrollToItem(count - 1)
        }
    }
    LazyColumn(
        modifier.testTag("chat-messages"),
        state = scroll,
        contentPadding =
            PaddingValues(
                top =
                    (
                        if (reference) {
                            0.dp
                        } else if (shortened) {
                            4.dp
                        } else {
                            16.dp
                        }
                    ) * scale,
                bottom =
                    (if (reference) 8.dp else 24.dp) * scale,
            ),
        verticalArrangement = Arrangement.spacedBy((if (reference) 0.dp else 54.dp) * scale),
    ) {
        itemsIndexed(state.messages, key = { _, message -> message.id }) { index, message ->
            val animateEntry = motionEnabled && message.id !in seenMessageIds
            var entered by remember(message.id) { mutableStateOf(!animateEntry) }
            LaunchedEffect(message.id) {
                if (message.id !in seenMessageIds) seenMessageIds.add(message.id)
                entered = true
            }
            val progress by animateFloatAsState(
                targetValue = if (entered || !motionEnabled) 1f else 0f,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                label = "message arrival",
            )
            val entryProgress = progress
            Column(
                Modifier.graphicsLayer {
                    alpha = entryProgress
                    translationY = (1f - entryProgress) * 24.dp.toPx() * scale
                    scaleX = 0.97f + entryProgress * 0.03f
                    scaleY = 0.97f + entryProgress * 0.03f
                },
            ) {
                MessageBubble(
                    message.text,
                    message.fromUser,
                    friend,
                    scale,
                    shortened = shortened,
                    reference = reference,
                )
                if (reference && (index < state.messages.lastIndex || state.replyPending)) {
                    val gap =
                        when {
                            index == 0 && message.fromUser -> 73.dp
                            index == 1 && !message.fromUser -> 17.dp
                            message.fromUser -> 32.dp
                            else -> 27.dp
                        }
                    Spacer(Modifier.height(gap * scale))
                }
            }
        }
        if (state.replyPending) {
            item(key = "reply-pending") {
                MessageBubble(
                    stringResource(R.string.chat_preparing, friend),
                    false,
                    friend,
                    scale,
                    pending = true,
                    reference = reference,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    fromUser: Boolean,
    friend: String,
    scale: Float,
    pending: Boolean = false,
    shortened: Boolean = false,
    reference: Boolean = false,
) {
    val referencePending = reference && pending
    val motionEnabled = LocalMobiMonMotionEnabled.current
    val dotPhase =
        if (pending && motionEnabled) {
            rememberInfiniteTransition(label = "reply typing").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
                label = "typing dot phase",
            )
        } else {
            null
        }
    val labelGap =
        when {
            referencePending -> 10.dp
            reference -> 8.dp
            else -> 16.dp
        } * scale
    val bubbleStartPadding =
        when {
            referencePending -> 31.dp
            reference -> 22.dp
            else -> 44.dp
        } * scale
    val bubbleEndPadding =
        when {
            referencePending -> 33.dp
            reference -> 22.dp
            else -> 44.dp
        } * scale
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start) {
        Text(
            if (fromUser) stringResource(R.string.chat_user) else friend,
            Modifier
                .padding(horizontal = (if (reference) 22.dp else 16.dp) * scale)
                .offset(y = if (reference) (-3).dp * scale else 0.dp),
            style = mobiMonReferenceTextStyle(if (reference) 20f else 28f, scale, !reference),
            color = Colors.accent,
        )
        Spacer(Modifier.height(labelGap))
        Column(
            Modifier
                .then(
                    if (reference) {
                        if (pending) {
                            Modifier.width(116.dp * scale)
                        } else {
                            Modifier.widthIn(
                                max =
                                    (if (fromUser) 850.dp else 900.dp) * scale,
                            )
                        }
                    } else {
                        Modifier.widthIn(max = (if (fromUser) 960.dp else 1192.dp) * scale)
                    },
                ).heightIn(
                    min =
                        (
                            if (pending) {
                                if (reference) 52.dp else 208.dp
                            } else {
                                0.dp
                            }
                        ) * scale,
                ).then(
                    if (reference) {
                        Modifier.drawBehind {
                            val bubbleColor = if (fromUser) Colors.button else Colors.raised
                            drawRoundRect(bubbleColor, cornerRadius = CornerRadius((24.dp * scale).toPx()))
                            val edge = if (fromUser) size.width - (17.dp * scale).toPx() else (17.dp * scale).toPx()
                            val base = if (fromUser) size.width - (33.dp * scale).toPx() else (33.dp * scale).toPx()
                            drawPath(
                                Path().apply {
                                    moveTo(edge, size.height)
                                    lineTo(base, size.height)
                                    lineTo(edge, size.height + (8.dp * scale).toPx())
                                    close()
                                },
                                bubbleColor,
                            )
                        }
                    } else {
                        Modifier.background(
                            if (fromUser) Colors.button else Colors.raised,
                            RoundedCornerShape(28.dp * scale),
                        )
                    },
                ).padding(
                    start = bubbleStartPadding,
                    end = bubbleEndPadding,
                ).padding(
                    vertical =
                        (
                            if (reference) {
                                14.dp
                            } else if (fromUser) {
                                38.dp
                            } else if (shortened) {
                                22.dp
                            } else {
                                28.dp
                            }
                        ) * scale,
                ).testTag(
                    if (pending) {
                        "chat-pending-bubble"
                    } else if (fromUser) {
                        "chat-user-bubble"
                    } else {
                        "chat-friend-bubble"
                    },
                ).semantics {
                    if (pending) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = text
                    }
                },
        ) {
            if (pending) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((if (reference) 8.dp else 32.dp) * scale),
                    modifier =
                        Modifier.padding(
                            top = (if (reference) 6.dp else 26.dp) * scale,
                            bottom = (if (reference) 4.dp else 44.dp) * scale,
                        ),
                ) {
                    repeat(3) { index ->
                        Box(
                            Modifier
                                .size(
                                    (if (reference) 12.dp else 16.dp) * scale,
                                ).graphicsLayer {
                                    val progress = dotPhase?.value
                                    if (progress != null) {
                                        val pulse =
                                            (1f - cos((progress - index * 0.2f) * (2f * PI).toFloat())) / 2f
                                        alpha = 0.4f + 0.6f * pulse
                                        translationY = -5.dp.toPx() * scale * pulse
                                    }
                                }.background(Colors.accent, CircleShape),
                        )
                    }
                }
            }
            if (!pending || !reference) {
                SelectionContainer {
                    Text(
                        text,
                        style =
                            mobiMonReferenceTextStyle(
                                if (reference) {
                                    28f
                                } else if (shortened) {
                                    34f
                                } else {
                                    36f
                                },
                                scale,
                            ).copy(
                                lineHeight =
                                    (
                                        (
                                            if (reference) {
                                                40f
                                            } else if (shortened) {
                                                56f
                                            } else {
                                                60f
                                            }
                                        ) * scale
                                    ).sp,
                            ),
                        color =
                            when {
                                reference && fromUser -> Colors.onButton
                                reference -> Color(0xFFEAF2F8)
                                fromUser -> Colors.onButton
                                else -> Colors.text
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationComposer(
    state: ConversationUiState,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit,
    onCancelReply: () -> Unit,
    friend: String,
    allowed: Boolean,
    scale: Float,
    wide: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onStartVoice: () -> Unit = {},
    onDismissVoiceProblem: () -> Unit = {},
) {
    val reviewingVoice = state.voice.phase == VoiceInputPhase.REVIEW
    val voicePermissionPending = state.voice.phase == VoiceInputPhase.PERMISSION
    val canSend =
        allowed &&
            state.connection == ConversationConnection.READY &&
            !state.failed &&
            !state.replyPending &&
            !voicePermissionPending &&
            draft.text.length <= ConversationLimits.INPUT_CHARACTERS &&
            draft.text.isNotBlank()
    val submit = {
        if (canSend) {
            // Explicit Send commits the visible syllable/word without discarding IME input.
            onDraftChange(draft.copy(composition = null))
            onSend(draft.text)
        }
    }
    val label = stringResource(R.string.chat_input)
    val voiceReviewDescription = stringResource(R.string.chat_voice_review)
    val actionLabel = stringResource(if (state.replyPending) R.string.chat_stop else R.string.chat_send)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (wide) Modifier.height(87.dp * scale) else Modifier.heightIn(min = 116.dp * scale))
            .testTag("chat-composer")
            .background(if (wide) Color(0xFF091A29) else Colors.background, RoundedCornerShape(43.5.dp * scale))
            .border(
                1.dp * scale,
                if (wide) Color(0xFF546D85) else Colors.border,
                RoundedCornerShape(
                    43.5.dp * scale,
                ),
            ).padding(
                start =
                    (
                        if (wide) {
                            26.dp
                        } else {
                            44.dp
                        }
                    ) * scale,
                end =
                    (
                        if (wide && reviewingVoice) {
                            18.87.dp
                        } else if (wide) {
                            16.87.dp
                        } else {
                            28.dp
                        }
                    ) * scale,
                top = (if (wide) 7.dp else 12.dp) * scale,
                bottom = (if (wide) 7.dp else 12.dp) * scale,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                (
                    if (wide && reviewingVoice) {
                        21.1.dp
                    } else if (wide) {
                        23.1.dp
                    } else {
                        20.dp
                    }
                ) * scale,
            ),
    ) {
        BasicTextField(
            value = draft,
            onValueChange = {
                if (allowed && !state.replyPending && !state.failed && !voicePermissionPending) {
                    onDismissVoiceProblem()
                    onDraftChange(it)
                }
            },
            modifier =
                Modifier
                    .weight(1f)
                    .padding(
                        end =
                            if (state.voice.available &&
                                wide
                            ) {
                                (76.dp - 72.dp * scale).coerceAtLeast(0.dp)
                            } else {
                                0.dp
                            },
                    ).heightIn(min = (if (wide) 60.dp else 64.dp) * scale)
                    .focusRequester(focusRequester)
                    .testTag(
                        "chat-input",
                    ).semantics {
                        contentDescription = label
                        if (reviewingVoice) stateDescription = voiceReviewDescription
                    },
            enabled = allowed,
            readOnly = state.replyPending || state.failed || voicePermissionPending,
            textStyle =
                mobiMonReferenceTextStyle(
                    if (wide) 26f else 32f,
                    scale,
                ).copy(color = if (reviewingVoice) Color(0xFFF6F2E8) else Colors.text),
            cursorBrush = SolidColor(Colors.accent),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            decorationBox = { input ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (draft.text.isEmpty()) {
                        Text(
                            stringResource(
                                if (state.replyPending) R.string.chat_waiting else R.string.chat_placeholder,
                                friend,
                            ),
                            style = mobiMonReferenceTextStyle(if (wide) 26f else 32f, scale),
                            color = Colors.muted,
                        )
                    }
                    input()
                }
            },
        )
        if (state.voice.available) {
            VoiceIconButton(
                stringResource(R.string.chat_voice_start),
                R.drawable.conversation_voice_mic,
                onStartVoice,
                allowed &&
                    state.connection == ConversationConnection.READY &&
                    !state.failed &&
                    !state.replyPending &&
                    !voicePermissionPending,
                scale,
                Modifier.size(if (wide) 72.dp * scale else (72.dp * scale).coerceAtLeast(76.dp)),
                visualWidth = 72f,
                visualHeight = 72f,
                targetOffsetX =
                    if (wide) {
                        -(76.dp - (if (reviewingVoice) 91.36.dp else 93.36.dp) * scale).coerceAtLeast(
                            0.dp,
                        )
                    } else {
                        0.dp
                    },
            )
        }
        val enabled = if (state.replyPending) allowed else canSend
        val action = { if (state.replyPending) onCancelReply() else submit() }
        if (wide) {
            VoiceIconButton(
                actionLabel,
                when {
                    state.replyPending -> R.drawable.conversation_voice_reply_stop
                    canSend -> R.drawable.conversation_voice_send
                    else -> R.drawable.conversation_voice_send_disabled
                },
                action,
                enabled,
                scale,
                Modifier.size(68.53.dp * scale, 72.dp * scale),
                "chat-send-visual",
                visualWidth = 68.53f,
                visualHeight = 72f,
                actionTag = "chat-send",
            )
        } else {
            var sendFocused by remember { mutableStateOf(false) }
            val visualSize = 92.dp * scale
            val targetSize = visualSize.coerceAtLeast(76.dp)
            Surface(
                onClick = action,
                enabled = enabled,
                modifier =
                    Modifier
                        .requiredSize(targetSize)
                        .onFocusChanged { sendFocused = it.isFocused }
                        .testTag("chat-send")
                        .semantics { contentDescription = actionLabel },
                shape = CircleShape,
                color = Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        Modifier.size(visualSize).testTag("chat-send-visual"),
                        shape = CircleShape,
                        color = if (enabled) Colors.button else Color(0xFF33465B),
                        border = if (sendFocused) BorderStroke(3.dp, Colors.accent) else null,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.replyPending) {
                                Box(
                                    Modifier.size(32.dp * scale).background(
                                        Colors.onButton,
                                        RoundedCornerShape(
                                            3.dp * scale,
                                        ),
                                    ),
                                )
                            } else {
                                Icon(
                                    painterResource(R.drawable.conversation_send),
                                    stringResource(R.string.chat_send),
                                    Modifier.size(40.dp * scale),
                                    tint = if (enabled) Colors.onButton else Colors.muted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConversationAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
    referenceGeometry: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val visualHeight = 80.dp * scale
    Box(
        modifier.then(if (referenceGeometry) Modifier.height(visualHeight) else Modifier.heightIn(min = 76.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (referenceGeometry) Modifier.requiredHeight(visualHeight.coerceAtLeast(76.dp)) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (referenceGeometry) Modifier.height(visualHeight) else Modifier.heightIn(min = 76.dp))
                    .border(
                        if (focused) 3.dp else 2.dp * scale,
                        if (focused) Colors.accent else Colors.border,
                        RoundedCornerShape(50),
                    ).padding(horizontal = 24.dp * scale, vertical = 12.dp * scale),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    style = mobiMonReferenceTextStyle(30f, scale),
                    color = if (enabled) Colors.text else Colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
