package com.monsters.mobimon.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Custom Shape for dialogue speech bubble with a rounded tail pointing left towards MobiMon's face.
 */
class SpeechBubbleShape(
    private val cornerRadius: Dp = 20.dp,
    private val tailWidth: Dp = 16.dp,
    private val tailHeight: Dp = 16.dp,
    private val tailOffsetYFromBottom: Dp = 22.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cr = with(density) { cornerRadius.toPx() }
        val tw = with(density) { tailWidth.toPx() }
        val th = with(density) { tailHeight.toPx() }
        val tailOffsetY = with(density) { tailOffsetYFromBottom.toPx() }

        val path =
            Path().apply {
                val bodyLeft = tw
                val bodyTop = 0f
                val bodyRight = size.width
                val bodyBottom = size.height

                moveTo(bodyLeft + cr, bodyTop)

                lineTo(bodyRight - cr, bodyTop)
                arcTo(
                    rect = Rect(bodyRight - 2 * cr, bodyTop, bodyRight, bodyTop + 2 * cr),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )

                lineTo(bodyRight, bodyBottom - cr)
                arcTo(
                    rect = Rect(bodyRight - 2 * cr, bodyBottom - 2 * cr, bodyRight, bodyBottom),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )

                lineTo(bodyLeft + cr, bodyBottom)
                arcTo(
                    rect = Rect(bodyLeft, bodyBottom - 2 * cr, bodyLeft + 2 * cr, bodyBottom),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )

                val minTailY = (bodyTop + cr + th).coerceAtMost(bodyBottom)
                val maxTailY = (bodyBottom - cr).coerceAtLeast(minTailY)
                val tailBottomY = (bodyBottom - tailOffsetY).coerceIn(minTailY, maxTailY)
                val tailTopY = tailBottomY - th
                val tailTipY = tailTopY + th * 0.5f

                lineTo(bodyLeft, tailBottomY)

                cubicTo(
                    bodyLeft - tw * 0.45f,
                    tailBottomY - th * 0.1f,
                    0f,
                    tailTipY + th * 0.25f,
                    0f,
                    tailTipY,
                )
                cubicTo(
                    0f,
                    tailTipY - th * 0.25f,
                    bodyLeft - tw * 0.45f,
                    tailTopY + th * 0.1f,
                    bodyLeft,
                    tailTopY,
                )

                lineTo(bodyLeft, bodyTop + cr)
                arcTo(
                    rect = Rect(bodyLeft, bodyTop, bodyLeft + 2 * cr, bodyTop + 2 * cr),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )

                close()
            }

        return Outline.Generic(path)
    }
}

/**
 * Palette colors for Speech Bubble based on time of day.
 */
data class SpeechBubbleColors(
    val backgroundColor: Color,
    val borderColor: Color,
    val textColor: Color,
)

fun getSpeechBubbleColors(timeOfDay: String?): SpeechBubbleColors {
    val period = companionTimePeriod(timeOfDay)
    return if (period == "night" || period == "midnight") {
        SpeechBubbleColors(
            backgroundColor = Color(0xFF182436).copy(alpha = 0.85f),
            borderColor = Color(0xFF425672).copy(alpha = 0.35f),
            textColor = Color(0xFFF2F5FA),
        )
    } else {
        SpeechBubbleColors(
            backgroundColor = Color(0xFFFCFBF9).copy(alpha = 0.82f),
            borderColor = Color(0xFFFFFFFF).copy(alpha = 0.45f),
            textColor = Color(0xFF132238),
        )
    }
}

/**
 * Soft translucent dialogue bubble appearing near MobiMon.
 * Reusable presentation component accepting text, visibility state, and duration.
 */
@Composable
fun MobiMonSpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    backgroundTimeOfDay: String? = null,
    displayDurationMs: Long = 5000L,
    onDismiss: (() -> Unit)? = null,
    tailWidth: Dp = 16.dp,
    tailHeight: Dp = 16.dp,
) {
    val colors = getSpeechBubbleColors(backgroundTimeOfDay)
    val shape =
        remember(tailWidth, tailHeight) {
            SpeechBubbleShape(
                cornerRadius = 20.dp,
                tailWidth = tailWidth,
                tailHeight = tailHeight,
                tailOffsetYFromBottom = 22.dp,
            )
        }

    val motionEnabled = LocalMobiMonMotionEnabled.current

    val alphaAnim = remember { Animatable(if (visible && !motionEnabled) 1f else 0f) }
    val scaleAnim = remember { Animatable(if (visible && !motionEnabled) 1f else 0.96f) }
    val translateYAnim = remember { Animatable(if (visible && !motionEnabled) 0f else 3f) }

    var isCurrentlyShowing by remember { mutableStateOf(visible) }

    LaunchedEffect(text, visible, motionEnabled) {
        if (!visible) {
            if (motionEnabled && alphaAnim.value > 0f) {
                alphaAnim.animateTo(0f, tween(250, easing = FastOutLinearInEasing))
                scaleAnim.animateTo(0.98f, tween(250, easing = FastOutLinearInEasing))
            } else {
                alphaAnim.snapTo(0f)
                scaleAnim.snapTo(0.98f)
            }
            isCurrentlyShowing = false
            return@LaunchedEffect
        }

        isCurrentlyShowing = true

        if (motionEnabled) {
            alphaAnim.snapTo(0f)
            scaleAnim.snapTo(0.96f)
            translateYAnim.snapTo(3f)

            alphaAnim.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            scaleAnim.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            translateYAnim.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        } else {
            alphaAnim.snapTo(1f)
            scaleAnim.snapTo(1f)
            translateYAnim.snapTo(0f)
        }

        if (displayDurationMs > 0) {
            delay(displayDurationMs)
            if (motionEnabled) {
                alphaAnim.animateTo(0f, tween(250, easing = FastOutLinearInEasing))
                scaleAnim.animateTo(0.98f, tween(250, easing = FastOutLinearInEasing))
            } else {
                alphaAnim.snapTo(0f)
            }
            isCurrentlyShowing = false
            onDismiss?.invoke()
        }
    }

    if (isCurrentlyShowing || alphaAnim.value > 0f) {
        Box(
            modifier =
                modifier
                    .graphicsLayer {
                        alpha = alphaAnim.value
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        translationY = translateYAnim.value * density
                        transformOrigin = TransformOrigin(0f, 0.75f)
                    }.shadow(
                        elevation = 3.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.04f),
                    ).background(colors.backgroundColor, shape = shape)
                    .border(width = 0.75.dp, color = colors.borderColor, shape = shape)
                    .padding(
                        start = 26.dp + tailWidth,
                        top = 18.dp,
                        end = 26.dp,
                        bottom = 18.dp,
                    ).widthIn(min = 200.dp, max = 340.dp),
        ) {
            Text(
                text = text,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MobiMonFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = 34.sp,
                    ),
                color = colors.textColor,
            )
        }
    }
}
