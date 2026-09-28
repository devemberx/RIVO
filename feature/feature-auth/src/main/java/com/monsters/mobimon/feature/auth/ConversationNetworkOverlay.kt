package com.monsters.mobimon.feature.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonReferenceText
import com.monsters.mobimon.core.ui.mobiMonReferenceTextStyle
import com.monsters.mobimon.core.ui.MobiMonColors as Colors

/** Connection checks never submit a conversation draft. */
@Composable
internal fun ConversationNetworkOverlay(
    checking: Boolean,
    problem: ConversationProblem?,
    onHome: () -> Unit,
    onRetry: () -> Unit,
) {
    val accountAction = problem == ConversationProblem.ACCOUNT
    val accessError = problem == ConversationProblem.ACCESS
    val networkError = problem in setOf(ConversationProblem.NETWORK, ConversationProblem.TIMEOUT)
    val usageError = problem == ConversationProblem.USAGE
    val showAction = checking || !accessError && !usageError
    val referenceCopy = networkError || checking
    val title =
        stringResource(
            when {
                checking -> R.string.chat_network_rechecking_title
                networkError -> R.string.chat_network_title
                usageError -> R.string.chat_connection_usage_title
                accessError -> R.string.chat_connection_access_title
                accountAction -> R.string.chat_connection_account_title
                else -> R.string.chat_connection_error_title
            },
        )
    val body =
        stringResource(
            when {
                checking -> R.string.chat_network_rechecking_body
                networkError -> R.string.chat_network_body
                else -> conversationFailureNote(problem)
            },
        )
    val instruction =
        stringResource(
            when {
                checking -> R.string.chat_network_rechecking_instruction
                accountAction -> R.string.chat_connection_account_instruction
                accessError -> R.string.chat_connection_access_instruction
                usageError -> R.string.chat_connection_usage_instruction
                networkError -> R.string.chat_network_instruction
                else -> R.string.chat_connection_retry_instruction
            },
        )
    val preserved =
        stringResource(
            if (checking) R.string.chat_network_rechecking_preserved else R.string.chat_network_preserved,
        )
    val action =
        stringResource(
            when {
                checking -> R.string.chat_network_rechecking_action
                accountAction -> R.string.conversation_connect
                else -> R.string.chat_network_recheck
            },
        )
    val extraBodyLineOffset = if (referenceCopy) 0f else body.count { it == '\n' } * 50f
    BoxWithConstraints(
        Modifier.fillMaxSize().semantics {
            paneTitle = title
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        val source = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Colors.background.copy(alpha = 0.72f))
                .clickable(interactionSource = source, indication = null) {}
                .clearAndSetSemantics {},
        )
        val wide = maxWidth >= 1200.dp && LocalDensity.current.fontScale <= 1.1f
        if (wide) {
            val scale = minOf(maxWidth.value / 2560f, maxHeight.value / 1184f)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = 32.dp * scale)
                    .size(1360.dp * scale, 740.dp * scale)
                    .background(Colors.panel, RoundedCornerShape(32.dp * scale))
                    .border(2.dp * scale, Colors.border, RoundedCornerShape(32.dp * scale))
                    .testTag("chat-network-dialog"),
            ) {
                NetworkIcon(checking, networkError, Modifier.offset(64.dp * scale, 64.dp * scale), scale)
                MobiMonReferenceText(
                    title,
                    64f,
                    258f,
                    48f,
                    scale = scale,
                    bold = true,
                    modifier = Modifier.semantics { heading() },
                )
                MobiMonReferenceText(
                    body,
                    64f,
                    336f,
                    36f,
                    modifier = Modifier.testTag("chat-connection-body"),
                    scale = scale,
                    color = Colors.muted,
                )
                MobiMonReferenceText(
                    instruction,
                    64f,
                    390f + extraBodyLineOffset,
                    36f,
                    modifier = Modifier.testTag("chat-connection-instruction"),
                    scale = scale,
                    color = Colors.muted,
                )
                MobiMonReferenceText(
                    preserved,
                    64f,
                    466f + extraBodyLineOffset,
                    28f,
                    modifier = Modifier.testTag("chat-connection-preserved"),
                    scale = scale,
                    color = Colors.muted,
                )
                NetworkHomeButton(
                    onHome,
                    scale,
                    Modifier
                        .offset(64.dp * scale, 560.dp * scale)
                        .size((if (showAction) 592.dp else 1232.dp) * scale, 116.dp * scale),
                )
                if (showAction) {
                    MobiMonButton(
                        onClick = onRetry,
                        enabled = !checking,
                        modifier =
                            Modifier
                                .offset(680.dp * scale, 560.dp * scale)
                                .size(616.dp * scale, 116.dp * scale)
                                .testTag("chat-network-retry"),
                    ) {
                        Text(action, style = mobiMonReferenceTextStyle(40f, scale, true))
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .width((maxWidth - 48.dp).coerceAtMost(720.dp))
                    .heightIn(max = maxHeight - 32.dp)
                    .background(Colors.panel, RoundedCornerShape(24.dp))
                    .border(2.dp, Colors.border, RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .testTag("chat-network-dialog"),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                NetworkIcon(checking, networkError, Modifier, 0.65f)
                Text(
                    title,
                    style = mobiMonReferenceTextStyle(48f, 0.65f, true),
                    color = Colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(body + "\n" + instruction, style = mobiMonReferenceTextStyle(36f, 0.65f), color = Colors.muted)
                Text(preserved, style = mobiMonReferenceTextStyle(28f, 0.65f), color = Colors.muted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetworkHomeButton(onHome, 0.65f, Modifier.weight(1f).height(76.dp))
                    if (showAction) {
                        MobiMonButton(
                            onClick = onRetry,
                            enabled = !checking,
                            modifier = Modifier.weight(1f).height(76.dp).testTag("chat-network-retry"),
                        ) {
                            Text(
                                action,
                                style = mobiMonReferenceTextStyle(36f, 0.65f, true),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkIcon(
    checking: Boolean,
    networkError: Boolean,
    modifier: Modifier,
    scale: Float,
) {
    val motionEnabled = LocalMobiMonMotionEnabled.current
    val rotation =
        if (checking && motionEnabled) {
            rememberInfiniteTransition(label = "connection ring")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
                    label = "connection ring rotation",
                )
        } else {
            null
        }
    Box(
        modifier.size(112.dp * scale).background(Colors.raised, RoundedCornerShape(32.dp * scale)),
        contentAlignment = Alignment.Center,
    ) {
        if (checking) {
            Canvas(Modifier.size(64.dp * scale).testTag("chat-connection-ring")) {
                val stroke = 7.dp.toPx() * scale
                val radius = 28.dp.toPx() * scale
                drawCircle(Color(0xFF64839F).copy(alpha = 0.42f), radius = radius, style = Stroke(stroke))
                rotate(rotation?.value ?: 0f) {
                    drawArc(
                        color = Color(0xFF87DAF5),
                        startAngle = -90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        } else {
            Icon(
                painterResource(
                    if (networkError) R.drawable.conversation_network_off else R.drawable.conversation_warning,
                ),
                null,
                Modifier
                    .size((if (networkError) 64.dp else 112.dp) * scale)
                    .testTag("chat-connection-icon"),
                tint = Color.Unspecified,
            )
        }
    }
}

@Composable
private fun NetworkHomeButton(
    onClick: () -> Unit,
    scale: Float,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused }.testTag("chat-network-home"),
        shape = RoundedCornerShape(58.dp * scale),
        color = Colors.raised,
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Colors.accent else Colors.border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.chat_parking_home),
                style = mobiMonReferenceTextStyle(40f, scale, true),
                color = Colors.text,
            )
        }
    }
}
