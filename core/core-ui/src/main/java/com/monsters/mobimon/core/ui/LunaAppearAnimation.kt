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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object LunaAppearTimeline {
    const val FRAME_COUNT = LunaScene.FRAME_COUNT
    const val FPS = LunaScene.FPS

    // Frame 24 (from 1.4375s) is the seated pose; it then crossfades into the idle artwork.
    private const val IDLE_BLEND_START_NANOS = 1_450_000_000L
    private const val IDLE_BLEND_NANOS = 150_000_000L
    const val DURATION_NANOS = IDLE_BLEND_START_NANOS + IDLE_BLEND_NANOS

    // Frame 24's Luna is the idle artwork enlarged 1.07x with its bottom centre at (720, 940), so the scene is drawn
    // 1/1.07 the exit scene's scale for an exact handoff. The box lands to her left.
    const val SCENE_SCALE = 1.9340f
    const val TRANSLATION_X_FRACTION = -0.3589f
    const val TRANSLATION_Y_FRACTION = -0.4302f

    fun frameAt(elapsedNanos: Long): Int =
        (elapsedNanos.coerceAtLeast(0L) * FPS / 1_000_000_000L).toInt().coerceAtMost(FRAME_COUNT - 1)

    fun idleBlendAt(elapsedNanos: Long): Float =
        ((elapsedNanos - IDLE_BLEND_START_NANOS).toFloat() / IDLE_BLEND_NANOS).coerceIn(0f, 1f)

    fun load(
        context: Context,
        appearance: LunaAppearance = LunaAppearance.NORMAL,
        requiredFrameSidePx: Int =
            LunaScene.requiredFrameSidePx(124 * context.resources.displayMetrics.density, SCENE_SCALE),
    ): ImageBitmap? =
        LunaAnimationAtlas.load(context, LunaAtlasAction.APPEAR, appearance, requiredFrameSidePx)
            ?: LunaAnimationAtlas.load(context, LunaAtlasAction.APPEAR, LunaAppearance.NORMAL, requiredFrameSidePx)
}

/** One shot at 16fps: a box drops in, Luna peeks out, hops to her seat and hands off to the idle pose. */
@Composable
internal fun LunaAppearAnimation(
    modifier: Modifier,
    appearance: LunaAppearance,
    onFinished: () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val requiredFrameSidePx =
            LunaScene.requiredFrameSidePx(
                minOf(constraints.maxWidth, constraints.maxHeight).toFloat(),
                LunaAppearTimeline.SCENE_SCALE,
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
                    LunaAppearTimeline.load(context, appearance, requiredFrameSidePx)
                }
        }
        val elapsed = remember(appearance, sampleSize) { mutableLongStateOf(0L) }
        LaunchedEffect(frames) {
            if (!frames.first) return@LaunchedEffect
            if (frames.second != null) {
                val start = withFrameNanos { it }
                while (elapsed.longValue < LunaAppearTimeline.DURATION_NANOS) {
                    elapsed.longValue = withFrameNanos { it } - start
                }
            }
            finished()
        }
        val loaded = frames.second
        if (loaded == null) {
            if (frames.first) {
                LunaIdleBreathAnimation(Modifier.fillMaxSize(), appearance = appearance, animateFrames = false)
            } else {
                Box(Modifier.fillMaxSize())
            }
            return@BoxWithConstraints
        }
        Box(
            Modifier
                .testTag("luna-appear-atlas-${appearance.assetName}")
                .graphicsLayer { alpha = 1f - LunaAppearTimeline.idleBlendAt(elapsed.longValue) }
                .lunaSceneFrames(
                    minOf(maxWidth, maxHeight),
                    loaded,
                    LunaAppearTimeline.SCENE_SCALE,
                    LunaAppearTimeline.TRANSLATION_X_FRACTION,
                    LunaAppearTimeline.TRANSLATION_Y_FRACTION,
                ) { LunaAppearTimeline.frameAt(elapsed.longValue) },
        )
        // Keep the destination composed, so the handoff cannot flash while idle frames decode.
        CharacterFadeLayer(
            alpha = { LunaAppearTimeline.idleBlendAt(elapsed.longValue) },
            modifier = Modifier.fillMaxSize(),
            topOutsetFraction =
                if (appearance ==
                    LunaAppearance.HAT
                ) {
                    LunaIdleTimeline.HAT_TOP_OUTSET_FRACTION
                } else {
                    0f
                },
        ) {
            LunaIdleBreathAnimation(
                modifier = Modifier.fillMaxSize(),
                appearance = appearance,
                animateFrames = false,
            )
        }
    }
}
