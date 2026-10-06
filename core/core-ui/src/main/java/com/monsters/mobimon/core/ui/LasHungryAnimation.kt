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

/** Frame timing is independent of drawing size; the battery blink has deliberate holds. */
internal object LasHungryTimeline {
    private val durations =
        intArrayOf(
            250,
            90,
            140,
            140,
            140,
            90,
            120,
            90,
            90,
            90,
            90,
            120,
            100,
            90,
            90,
            110,
            110,
            110,
            120,
            120,
            180,
            120,
            120,
            250,
        )
    val cycleMs: Long = durations.sum().toLong()

    fun position(elapsedMs: Long): Float {
        var remaining = elapsedMs.coerceAtLeast(0L) % cycleMs
        for (frame in durations.indices) {
            val duration = durations[frame]
            if (remaining < duration) {
                // Only the idle endpoints and battery blink pause. Arm movement progresses
                // throughout each interval, without stopping/easing again at every keyframe.
                val transition =
                    when (frame) {
                        0, 23 -> 90
                        2, 3, 4 -> 40
                        else -> duration
                    }
                val blend = ((remaining - duration + transition).toFloat() / transition).coerceIn(0f, 1f)
                return frame + blend
            }
            remaining -= duration
        }
        return 0f
    }
}

/** 48-frame interpolated atlas playback; both loop endpoints share the lowered-hand rest pose. */
@Composable
internal fun LasHungryAnimation(
    modifier: Modifier,
    animate: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val sheet by produceState<ImageBitmap?>(null, context) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("characters/las/hungry/idle_breath/las_idle_breath_hungry_sprite.png").use {
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
                // Each 768px tile surrounds a 512px character canvas with 128px of effect padding.
                // Undo padding only; the atlas already fixes crown-to-sole height to the idle master.
                scaleX = 1.5f
                scaleY = 1.5f
                compositingStrategy = CompositingStrategy.Offscreen
            }.mobiSpriteFrames(
                sheet = artwork,
                columns = 6,
                rows = 8,
                filterQuality = FilterQuality.High,
            ) {
                if (animate) LasHungryTimeline.position(elapsed.longValue / 1_000_000L) * 2f else 4f
            },
    )
}
