package com.monsters.mobimon.feature.pet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/** Pop in on entry or pet tap, then reappear periodically without moving the scene. */
@Composable
internal fun HomeSpeechBubble(
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    triggerKey: Int = 0,
    friendId: String? = "friend:mobi",
    isSick: Boolean = false,
    isHungry: Boolean = false,
    backgroundTimeOfDay: String? = null,
) {
    val motionEnabled = LocalMobiMonMotionEnabled.current
    var visible by remember { mutableStateOf(!motionEnabled) }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec =
            if (motionEnabled) {
                tween(durationMillis = if (visible) 160 else 90)
            } else {
                snap()
            },
        label = "home_speech_bubble_entrance",
    )

    val deckManager = remember { PetSpeechDeckManager() }
    var currentText by remember { mutableStateOf("") }
    var previousTriggerKey by remember { mutableStateOf(triggerKey) }

    LaunchedEffect(triggerKey, friendId, isSick, isHungry, backgroundTimeOfDay, motionEnabled) {
        val isTap = triggerKey > 0 && triggerKey != previousTriggerKey
        previousTriggerKey = triggerKey

        if (!motionEnabled) {
            val pool =
                PetSpeechPhrases.getPool(
                    friendId = friendId,
                    isTap = isTap,
                    isSick = isSick,
                    isHungry = isHungry,
                    timeOfDay = backgroundTimeOfDay,
                )
            currentText = deckManager.nextPhrase(pool)
            visible = true
            return@LaunchedEffect
        }

        var currentIsTap = isTap
        while (isActive) {
            if (currentIsTap) {
                visible = false
                delay(100L)
            }
            val pool =
                PetSpeechPhrases.getPool(
                    friendId = friendId,
                    isTap = currentIsTap,
                    isSick = isSick,
                    isHungry = isHungry,
                    timeOfDay = backgroundTimeOfDay,
                )
            currentText = deckManager.nextPhrase(pool)
            visible = true
            delay(4500L)
            visible = false
            currentIsTap = false
            delay(Random.nextLong(10_000L, 15_000L))
        }
    }
    HomeSpeechBubbleContent(
        message = currentText.ifEmpty { stringResource(R.string.pet_home_message) },
        modifier = modifier,
        scale = scale,
        progress = progress,
        motionEnabled = motionEnabled,
    )
}

internal fun homeSpeechBubbleScale(
    scale: Float,
    fontScale: Float,
): Float {
    val fontSize = (32.4f * scale).coerceAtLeast(24f)
    val lineHeight = (43.2f * scale).coerceAtLeast(34f)
    return maxOf(fontSize / 32.4f, lineHeight / 43.2f) * fontScale.coerceAtLeast(1f)
}

@Composable
internal fun HomeSpeechBubbleContent(
    message: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    progress: Float = 1f,
    motionEnabled: Boolean = false,
) {
    val fontSize = (32.4f * scale).coerceAtLeast(24f)
    val lineHeight = (43.2f * scale).coerceAtLeast(34f)
    val bubbleScale = homeSpeechBubbleScale(scale, LocalDensity.current.fontScale)
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle =
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize.sp,
            lineHeight = lineHeight.sp,
            letterSpacing = (0.2f * scale).sp,
            fontWeight = FontWeight.Normal,
        )
    val horizontalInset = (48 * bubbleScale).dp
    val shape = remember(bubbleScale) { HomeSpeechBubbleShape(bubbleScale) }
    BoxWithConstraints(
        modifier
            .widthIn(min = (260 * bubbleScale).dp, max = (560 * bubbleScale).dp)
            .heightIn(min = (130f * bubbleScale).dp)
            .testTag("home-companion-message")
            .graphicsLayer {
                val fraction = if (motionEnabled) progress else 1f
                alpha = (fraction * 2).coerceIn(0f, 1f)
                scaleX = 0.82f + 0.18f * fraction
                scaleY = scaleX
                transformOrigin = TransformOrigin(0f, 1f)
            }.background(Color(0xFFECEBF3), shape),
    ) {
        val maxTextWidth = with(density) { (maxWidth - horizontalInset * 2).roundToPx() }.coerceAtLeast(1)
        val targetTextWidth =
            remember(message, textStyle, maxTextWidth, density) {
                val naturalWidth =
                    textMeasurer
                        .measure(
                            message,
                            style = textStyle,
                            softWrap = false,
                            maxLines = 1,
                        ).size.width
                if (naturalWidth <= maxTextWidth) {
                    naturalWidth
                } else {
                    var lower = (naturalWidth / 2).coerceIn(1, maxTextWidth)
                    var upper = maxTextWidth
                    while (lower < upper) {
                        val candidate = (lower + upper) / 2
                        val lines =
                            textMeasurer
                                .measure(
                                    message,
                                    style = textStyle,
                                    constraints = Constraints(maxWidth = candidate),
                                ).lineCount
                        if (lines > 2) lower = candidate + 1 else upper = candidate
                    }
                    lower
                }
            }
        val lineCount =
            remember(message, textStyle, targetTextWidth, density) {
                textMeasurer
                    .measure(
                        message,
                        style = textStyle,
                        constraints = Constraints(maxWidth = targetTextWidth + 2),
                    ).lineCount
            }
        Box(
            modifier =
                Modifier
                    .widthIn(min = (260 * bubbleScale).dp)
                    .heightIn(min = ((130f + 43.2f * (lineCount - 1)) * bubbleScale).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                message,
                modifier =
                    Modifier
                        .testTag("home-companion-message-text")
                        .padding(
                            start = horizontalInset,
                            end = horizontalInset,
                            top = (26 * bubbleScale).dp,
                            bottom = (45.8f * bubbleScale).dp,
                        ).width(with(density) { (targetTextWidth + 2).toDp() }),
                style = textStyle,
                textAlign = TextAlign.Center,
                color = MobiMonColors.onButton,
            )
        }
    }
}

/** The original vector outline, with its body corners scaled to the content height. */
private class HomeSpeechBubbleShape(
    private val scale: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val unit = with(density) { scale.dp.toPx() }
        val bottom = size.height - 19.8f * unit
        val right = size.width
        val cornerHeightScale = (bottom / (154.8f * unit)).coerceAtMost(1f)
        val path =
            Path().apply {
                moveTo(73.8f * unit, 0f)
                lineTo(right - 73.8f * unit, 0f)
                cubicTo(
                    right - 31.5f * unit,
                    0f,
                    right,
                    31.5f * unit * cornerHeightScale,
                    right,
                    73.8f * unit * cornerHeightScale,
                )
                lineTo(right, bottom - 81f * unit * cornerHeightScale)
                cubicTo(
                    right,
                    bottom - 37.8f * unit * cornerHeightScale,
                    right - 27.9f * unit,
                    bottom,
                    right - 73.8f * unit,
                    bottom,
                )
                lineTo(79.2f * unit, bottom)
                cubicTo(
                    61.2f * unit,
                    bottom + 8.1f * unit,
                    44.1f * unit,
                    bottom + 18.9f * unit,
                    25.2f * unit,
                    bottom + 19.8f * unit,
                )
                cubicTo(
                    35.1f * unit,
                    bottom + 9f * unit,
                    37.8f * unit,
                    bottom - 2.7f * unit,
                    36.9f * unit,
                    bottom - 15.3f * unit,
                )
                cubicTo(
                    13.5f * unit,
                    bottom - 27.9f * unit * cornerHeightScale,
                    0f,
                    bottom - 52.2f * unit * cornerHeightScale,
                    0f,
                    bottom - 81f * unit * cornerHeightScale,
                )
                lineTo(0f, 73.8f * unit * cornerHeightScale)
                cubicTo(0f, 32.4f * unit * cornerHeightScale, 31.5f * unit, 0f, 73.8f * unit, 0f)
                close()
            }
        return Outline.Generic(path)
    }
}
