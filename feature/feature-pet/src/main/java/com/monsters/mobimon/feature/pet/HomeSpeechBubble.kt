package com.monsters.mobimon.feature.pet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val fontSize = (38.4f * scale).coerceAtLeast(28f)
    val lineHeight = fontSize * 1.4f
    // The artwork must stop shrinking when either minimum text dimension is reached.
    val bubbleScale = maxOf(scale, fontSize / 38.4f) * textScale
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
    Box(
        modifier
            .widthIn(min = (260 * bubbleScale).dp, max = (560 * bubbleScale).dp)
            .heightIn(min = (140 * bubbleScale).dp)
            .testTag("home-companion-message")
            .graphicsLayer {
                val fraction = if (motionEnabled) progress else 1f
                alpha = (fraction * 2).coerceIn(0f, 1f)
                scaleX = 0.82f + 0.18f * fraction
                scaleY = scaleX
                transformOrigin = TransformOrigin(0.08f, 1f)
            },
    ) {
        Image(
            painterResource(R.drawable.pet_speech_bubble),
            null,
            Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
        )
        Text(
            currentText.ifEmpty { stringResource(R.string.pet_home_message) },
            modifier =
                Modifier
                    .testTag("home-companion-message-text")
                    .padding(
                        start = (58.5f * bubbleScale).dp,
                        end = (36 * bubbleScale).dp,
                        top = (26 * bubbleScale).dp,
                        bottom = (46 * bubbleScale).dp,
                    ),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = fontSize.sp,
                    lineHeight = lineHeight.sp,
                    letterSpacing = (0.2f * scale).sp,
                    fontWeight = FontWeight.Bold,
                ),
            color = MobiMonColors.onButton,
        )
    }
}
