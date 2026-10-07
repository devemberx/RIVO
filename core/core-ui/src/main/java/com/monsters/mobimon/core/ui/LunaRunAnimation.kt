package com.monsters.mobimon.core.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun LunaRunAnimation(
    modifier: Modifier = Modifier,
    movingLeft: Boolean = true,
    contentDescription: String? = null,
    appearance: LunaAppearance = LunaAppearance.NORMAL,
) {
    if (!LocalMobiMonMotionEnabled.current) {
        LunaIdleBreathAnimation(modifier, contentDescription, appearance, animateFrames = false)
        return
    }
    val currentMovingLeft by rememberUpdatedState(movingLeft)
    BoxWithConstraints(modifier) {
        val requiredFrameSidePx = minOf(constraints.maxWidth, constraints.maxHeight)
        val sampleSize = CharacterAnimationAtlas.sampleSizeFor(requiredFrameSidePx)
        val context = LocalContext.current.applicationContext
        var sheet by remember(context, appearance, sampleSize) {
            mutableStateOf(LunaRunAnimationCache.peek(appearance, requiredFrameSidePx))
        }
        LaunchedEffect(context, appearance, sampleSize) {
            sheet =
                withContext(Dispatchers.IO) {
                    LunaRunAnimationCache.getOrLoad(context, appearance, requiredFrameSidePx)
                }
        }
        val bitmap = sheet
        if (bitmap == null) {
            LunaIdleBreathAnimation(Modifier.fillMaxSize(), contentDescription, appearance, animateFrames = false)
            return@BoxWithConstraints
        }
        val frame = remember(bitmap) { mutableIntStateOf(0) }
        LaunchedEffect(bitmap) {
            var previous = withFrameNanos { it }
            var elapsed = 0L
            while (isActive) {
                val time = withInfiniteAnimationFrameNanos { it }
                elapsed += (time - previous).coerceAtMost(100_000_000L)
                previous = time
                var next = frame.intValue
                while (elapsed >= RUN_FRAME_DURATIONS_MS[next] * 1_000_000L) {
                    elapsed -= RUN_FRAME_DURATIONS_MS[next] * 1_000_000L
                    next = (next + 1) % CharacterAnimationAtlas.FRAME_COUNT
                }
                frame.intValue = next
            }
        }
        val baseAsset = CharacterArtwork.characters.getValue("friend:luna")
        Box(Modifier.fillMaxSize().testTag("luna-run-atlas-${appearance.assetName}")) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val flip = if (currentMovingLeft) 1f else -1f
                        scaleX = baseAsset.visualScale * flip
                        scaleY = baseAsset.visualScale
                        translationX = size.width * baseAsset.translationXFraction * flip
                        translationY = size.height * baseAsset.translationYFraction
                    }.testTag("luna-animation-frame-${appearance.assetName}")
                    .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
                    .characterSpriteFrames(
                        bitmap,
                        CharacterAnimationAtlas.COLUMNS,
                        CharacterAnimationAtlas.ROWS,
                        blendFrames = false,
                    ) {
                        frame.intValue.toFloat()
                    },
            )
        }
    }
}
