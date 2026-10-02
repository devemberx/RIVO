package com.monsters.mobimon.core.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal object LasSickTimeline {
    const val CYCLE_MS = 3_600L
    const val FRAMES = 24

    fun position(elapsedMs: Long): Float = (elapsedMs.coerceAtLeast(0L) % CYCLE_MS).toFloat() / CYCLE_MS * FRAMES
}

/** Atlas baked from one fixed body and rigid head; only three lamp sectors, ECG and rotation change. */
@Composable
internal fun LasSickAnimation(
    modifier: Modifier,
    animate: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val sheet by produceState<ImageBitmap?>(null, context) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("characters/las/sick/idle_breath/las_idle_breath_sick_sprite.png").use {
                        BitmapFactory
                            .decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
                            ?.asImageBitmap()
                    }
                }.getOrNull()
            }
    }
    val artwork = sheet
    if (artwork == null) {
        LasIdleAnimation(modifier, animate = false)
        return
    }
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(artwork, animate) {
        elapsed.longValue = 0L
        if (animate) {
            val start = withInfiniteAnimationFrameNanos { it }
            while (isActive) {
                elapsed.longValue = withInfiniteAnimationFrameNanos { it } - start
            }
        }
    }
    Box(
        modifier
            .graphicsLayer {
                // Compensate for 32px safety padding around the 512px character canvas.
                scaleX = 1.125f
                scaleY = 1.125f
                // Isolate additive frame blending from the home scene behind the sprite.
                compositingStrategy = CompositingStrategy.Offscreen
            }.mobiSpriteFrames(
                sheet = artwork,
                columns = 6,
                rows = 4,
                filterQuality = FilterQuality.High,
            ) {
                if (animate) LasSickTimeline.position(elapsed.longValue / 1_000_000L) else 4f
            },
    )
}
