package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

internal object LunaDisappearTimeline {
    const val FRAME_COUNT = 24
    const val FPS = 16

    // Frames 1–2 are the idle pose; holding them keeps the "departing?" bubble readable. The tower stays above
    // the bubble through frame 5 and reaches it in frame 6, which starts when the bubble ends.
    val SURPRISE_NANOS = 66_666_667L until 1_200_000_000L
    const val HOLD_NANOS = 887_500_000L
    const val DURATION_NANOS = HOLD_NANOS + FRAME_COUNT * 1_000_000_000L / FPS

    // Luna in the 1024px scene frame 1 spans (108, 573)-(452, 940) and matches the idle pose, so the scene is
    // scaled and shifted to cover the idle Luna exactly; the cat tower extends above and to the right of the slot.
    const val SCENE_SCALE = 2.0694f
    const val TRANSLATION_X_FRACTION = 0.5028f
    const val TRANSLATION_Y_FRACTION = -0.4868f

    fun frameAt(elapsedNanos: Long): Int =
        ((elapsedNanos - HOLD_NANOS).coerceAtLeast(0L) * FPS / 1_000_000_000L).toInt().coerceAtMost(FRAME_COUNT - 1)

    fun load(context: Context): List<ImageBitmap>? =
        try {
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = 2
                    inScaled = false
                }
            (1..FRAME_COUNT).map { frame ->
                val path =
                    String.format(Locale.US, "characters/luna/normal/disappear/luna_disappear_normal_%02d.png", frame)
                context.assets.open(path).use { stream ->
                    requireNotNull(BitmapFactory.decodeStream(stream, null, options)).asImageBitmap()
                }
            }
        } catch (_: java.io.IOException) {
            null
        }
}

/**
 * One shot at 16fps after a short hold for the bubble: a cat tower drops in, Luna climbs it and is lifted away.
 * The last two frames are empty.
 */
@Composable
internal fun LunaDisappearAnimation(
    modifier: Modifier,
    appearance: LunaAppearance,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val finished by rememberUpdatedState(onFinished)
    val frames by produceState<Pair<Boolean, List<ImageBitmap>?>>(false to null, context) {
        value = true to withContext(Dispatchers.IO) { LunaDisappearTimeline.load(context) }
    }
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(frames) {
        if (!frames.first) return@LaunchedEffect
        if (frames.second != null) {
            val start = withFrameNanos { it }
            while (elapsed.longValue < LunaDisappearTimeline.DURATION_NANOS) {
                elapsed.longValue = withFrameNanos { it } - start
            }
        }
        finished()
    }
    val loaded = frames.second
    if (loaded != null) {
        BoxWithConstraints(modifier) {
            val extent = minOf(maxWidth, maxHeight) * LunaDisappearTimeline.SCENE_SCALE
            Box(
                Modifier
                    .requiredSize(extent)
                    .graphicsLayer {
                        val slot = size.minDimension / LunaDisappearTimeline.SCENE_SCALE
                        translationX = slot * LunaDisappearTimeline.TRANSLATION_X_FRACTION
                        translationY = slot * LunaDisappearTimeline.TRANSLATION_Y_FRACTION
                        clip = false
                    }.drawWithCache {
                        val destination = IntSize(size.width.roundToInt(), size.height.roundToInt())
                        onDrawBehind {
                            val frame = loaded[LunaDisappearTimeline.frameAt(elapsed.longValue)]
                            drawImage(
                                frame,
                                IntOffset.Zero,
                                IntSize(frame.width, frame.height),
                                IntOffset.Zero,
                                destination,
                                filterQuality = FilterQuality.Low,
                            )
                        }
                    },
            )
            if (elapsed.longValue in LunaDisappearTimeline.SURPRISE_NANOS) {
                DepartureSurpriseBubble(Modifier.align(Alignment.TopStart))
            }
        }
    } else if (!frames.first) {
        LunaIdleBreathAnimation(modifier, appearance = appearance, animateFrames = false)
    }
}
