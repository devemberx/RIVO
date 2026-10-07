package com.monsters.mobimon.core.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object LunaDisappearTimeline {
    const val FRAME_COUNT = LunaScene.FRAME_COUNT
    const val FPS = LunaScene.FPS

    // Frames 1–2 are the idle pose; holding them keeps the "departing?" bubble readable. The tower stays above
    // the bubble through frame 5 and reaches it in frame 6, which starts when the bubble ends.
    val SURPRISE_NANOS = DEPARTURE_SURPRISE_NANOS
    const val HOLD_NANOS = 887_500_000L
    const val DURATION_NANOS = HOLD_NANOS + FRAME_COUNT * 1_000_000_000L / FPS

    // Frame 1's Luna is the idle artwork at (108, 573)-(452, 940); 367px tall matches 0.742 of the slot once the
    // idle 0.87 visual scale applies. The cat tower extends above and to the right of the slot.
    const val SCENE_SCALE = 2.0694f
    const val TRANSLATION_X_FRACTION = 0.5028f
    const val TRANSLATION_Y_FRACTION = -0.4868f

    fun frameAt(elapsedNanos: Long): Int =
        ((elapsedNanos - HOLD_NANOS).coerceAtLeast(0L) * FPS / 1_000_000_000L).toInt().coerceAtMost(FRAME_COUNT - 1)

    fun load(
        context: Context,
        appearance: LunaAppearance = LunaAppearance.NORMAL,
        requiredFrameSidePx: Int =
            LunaScene.requiredFrameSidePx(124 * context.resources.displayMetrics.density, SCENE_SCALE),
    ): ImageBitmap? =
        LunaAnimationAtlas.load(context, LunaAtlasAction.DISAPPEAR, appearance, requiredFrameSidePx)
            ?: LunaAnimationAtlas.load(context, LunaAtlasAction.DISAPPEAR, LunaAppearance.NORMAL, requiredFrameSidePx)
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
    BoxWithConstraints(modifier) {
        val requiredFrameSidePx =
            LunaScene.requiredFrameSidePx(
                minOf(constraints.maxWidth, constraints.maxHeight).toFloat(),
                LunaDisappearTimeline.SCENE_SCALE,
            )
        val sampleSize = CharacterAnimationAtlas.sampleSizeFor(requiredFrameSidePx)
        val context = LocalContext.current.applicationContext
        val finished by rememberUpdatedState(onFinished)
        var frames by remember(context, appearance, sampleSize) {
            mutableStateOf<Pair<Boolean, ImageBitmap?>>(false to null)
        }
        LaunchedEffect(context, appearance, sampleSize) {
            frames = true to
                withContext(Dispatchers.IO) {
                    LunaDisappearTimeline.load(context, appearance, requiredFrameSidePx)
                }
        }
        val elapsed = remember(appearance, sampleSize) { mutableLongStateOf(0L) }
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
            Box(
                Modifier.lunaSceneFrames(
                    minOf(maxWidth, maxHeight),
                    loaded,
                    LunaDisappearTimeline.SCENE_SCALE,
                    LunaDisappearTimeline.TRANSLATION_X_FRACTION,
                    LunaDisappearTimeline.TRANSLATION_Y_FRACTION,
                ) { LunaDisappearTimeline.frameAt(elapsed.longValue) },
            )
            if (elapsed.longValue in LunaDisappearTimeline.SURPRISE_NANOS) {
                DepartureSurpriseBubble(Modifier.align(Alignment.TopStart))
            }
        } else if (!frames.first) {
            LunaIdleBreathAnimation(Modifier.fillMaxSize(), appearance = appearance, animateFrames = false)
        }
    }
}
