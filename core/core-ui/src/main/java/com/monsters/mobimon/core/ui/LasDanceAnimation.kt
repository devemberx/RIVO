package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal object LasDanceSpriteCache {
    private const val PATH = "characters/las/normal/run/las_robot_dance_sprite.webp"

    @Volatile
    private var sprite: ImageBitmap? = null

    fun peek(): ImageBitmap? = sprite

    @Synchronized
    fun getOrLoad(context: Context): ImageBitmap? {
        sprite?.let { return it }
        return runCatching {
            context.assets.open(PATH).use {
                BitmapFactory
                    .decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
                    ?.asImageBitmap()
            }
        }.getOrNull()?.also { sprite = it }
    }
}

/** Only the first two rows have clean cell padding. Reverse playback closes the loop. */
internal object LasDanceTimeline {
    private const val FRAME_DURATION_MS = 85L
    private const val LAST_FRAME = 11
    private const val STEPS = LAST_FRAME * 2

    fun frame(elapsedMs: Long): Int {
        val step = ((elapsedMs.coerceAtLeast(0L) / FRAME_DURATION_MS) % STEPS).toInt()
        return if (step <= LAST_FRAME) step else STEPS - step
    }
}

/** Displays the robot dance only while Las is moving. */
@Composable
internal fun LasDanceAnimation(
    modifier: Modifier,
    movingLeft: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val sprite by produceState(LasDanceSpriteCache.peek(), context) {
        value = withContext(Dispatchers.IO) { LasDanceSpriteCache.getOrLoad(context) }
    }
    val sheet = sprite
    if (sheet == null) {
        LasIdleAnimation(modifier, animate = false)
        return
    }

    var frame by remember(sheet) { mutableIntStateOf(3) }
    LaunchedEffect(sheet) {
        val start = withInfiniteAnimationFrameNanos { it }
        while (isActive) {
            val elapsedMs = (withInfiniteAnimationFrameNanos { it } - start) / 1_000_000L
            frame = LasDanceTimeline.frame(elapsedMs + 3 * 85L)
        }
    }
    Box(
        modifier
            .testTag("las-dance-frame")
            .graphicsLayer {
                val direction = if (movingLeft) 1f else -1f
                scaleX = 1.03f * direction
                scaleY = 1.05f
                translationX = -size.width * 0.05f * direction
                translationY = -size.height * 0.05f
            }.characterSpriteFrames(
                sheet = sheet,
                columns = 6,
                rows = 4,
                blendFrames = false,
                filterQuality = FilterQuality.High,
                position = { frame.toFloat() },
            ),
    )
}
