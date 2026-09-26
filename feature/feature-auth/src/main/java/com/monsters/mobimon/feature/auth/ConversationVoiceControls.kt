package com.monsters.mobimon.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

/** Exported geometry stays fixed while microphone levels determine waveform heights. */
@Composable
internal fun ConversationRecordingControl(
    voice: VoiceInputState,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    allowed: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.chat_voice_recording)
    Box(
        modifier
            .drawBehind {
                val radius = CornerRadius(43.5.dp.toPx() * scale)
                drawRoundRect(Color(0xFF091A29), cornerRadius = radius)
                drawRoundRect(Color(0xFF546D85), cornerRadius = radius, style = Stroke(1.dp.toPx() * scale))
            }.testTag("chat-voice-control")
            .semantics {
                contentDescription = description
                stateDescription = voice.partial
            },
    ) {
        VoiceIconButton(
            stringResource(R.string.chat_voice_cancel),
            R.drawable.conversation_voice_cancel,
            onCancel,
            allowed,
            scale,
            Modifier.offset(19.37.dp * scale, 8.6.dp * scale).size(70.dp * scale, 69.8.dp * scale),
            "chat-voice-cancel-visual",
            visualWidth = 70f,
            visualHeight = 69.8f,
        )
        VoiceWaveform(
            voice.levels,
            Modifier.offset(107.5.dp * scale, 20.5.dp * scale).size(1267.dp * scale, 46.dp * scale),
        )
        VoiceIconButton(
            stringResource(R.string.chat_voice_stop),
            R.drawable.conversation_voice_stop,
            onStop,
            allowed && voice.phase == VoiceInputPhase.LISTENING,
            scale,
            Modifier.offset(1396.5.dp * scale, 7.5.dp * scale).size(72.dp * scale),
            "chat-voice-stop-visual",
            targetOffsetX = -(76.dp - 93.36.dp * scale).coerceAtLeast(0.dp),
        )
        VoiceIconButton(
            stringResource(R.string.chat_send),
            R.drawable.conversation_voice_send_recording,
            {},
            false,
            scale,
            Modifier.offset(1489.86.dp * scale, 7.5.dp * scale).size(72.dp * scale),
            "chat-send-visual",
            actionTag = "chat-send",
        )
    }
}

@Composable
private fun VoiceWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.testTag("chat-voice-waveform")) {
        val unit = size.width / 1267f
        val padded = List((64 - levels.size).coerceAtLeast(0)) { 0f } + levels.takeLast(64)
        padded.forEachIndexed { index, level ->
            val height = (8f + 38f * level.coerceIn(0f, 1f)) * unit
            drawRoundRect(
                color = Colors.accent,
                topLeft = Offset(index * 19.984127f * unit, (size.height - height) / 2f),
                size = Size(8f * unit, height),
                cornerRadius = CornerRadius(4f * unit),
            )
        }
    }
}

@Composable
internal fun VoiceIconButton(
    label: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
    visualTag: String = "chat-voice-start-visual",
    visualWidth: Float = 72f,
    visualHeight: Float = 72f,
    actionTag: String = "",
    // A left-shifted touch target separates adjacent 76dp actions without moving exported ink.
    targetOffsetX: Dp = 0.dp,
) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(x = targetOffsetX)
                .requiredSize(
                    (visualWidth.dp * scale).coerceAtLeast(76.dp),
                    (visualHeight.dp * scale).coerceAtLeast(76.dp),
                ).onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .then(if (actionTag.isEmpty()) Modifier else Modifier.testTag(actionTag))
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                null,
                Modifier
                    .offset(x = -targetOffsetX)
                    .size(visualWidth.dp * scale, visualHeight.dp * scale)
                    .then(if (focused) Modifier.border(3.dp, Colors.accent, CircleShape) else Modifier)
                    .testTag(visualTag),
                tint = Color.Unspecified,
            )
        }
    }
}

internal fun voiceHint(voice: VoiceInputState): Int =
    voice.problem?.let {
        when (it) {
            VoiceInputProblem.UNAVAILABLE -> R.string.chat_voice_unavailable
            VoiceInputProblem.PERMISSION -> R.string.chat_voice_permission
            VoiceInputProblem.NO_MATCH -> R.string.chat_voice_no_match
            VoiceInputProblem.AUDIO -> R.string.chat_voice_audio
            VoiceInputProblem.BUSY -> R.string.chat_voice_busy
            VoiceInputProblem.LANGUAGE -> R.string.chat_voice_language
            VoiceInputProblem.NETWORK -> R.string.chat_voice_network
            VoiceInputProblem.SERVICE -> R.string.chat_voice_service
            VoiceInputProblem.TIMEOUT -> R.string.chat_voice_timeout
            VoiceInputProblem.TOO_LONG -> R.string.chat_input_limit
        }
    } ?: when (voice.phase) {
        VoiceInputPhase.PERMISSION -> R.string.chat_voice_permission_pending
        VoiceInputPhase.STARTING -> R.string.chat_voice_starting
        VoiceInputPhase.STOPPING -> R.string.chat_voice_stopping
        VoiceInputPhase.REVIEW -> R.string.chat_voice_review
        else -> R.string.chat_voice_listening
    }

@Composable
internal fun voiceHintText(voice: VoiceInputState): String =
    if (voice.phase == VoiceInputPhase.LISTENING && voice.problem == null) {
        stringResource(R.string.chat_voice_listening, voice.elapsedSeconds / 60, voice.elapsedSeconds % 60)
    } else {
        stringResource(voiceHint(voice))
    }
