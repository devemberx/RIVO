package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class LasTransition(
    val assetPath: String,
) {
    APPEAR("characters/las/normal/appear/las_appear_normal_sprite.png"),
    DISAPPEAR("characters/las/normal/disappear/las_disappear_normal_sprite.png"),
}

internal object LasTransitionSpriteCache {
    private val sprites = mutableMapOf<LasTransition, ImageBitmap>()

    @Synchronized
    fun getOrLoad(
        context: Context,
        transition: LasTransition,
    ): ImageBitmap? {
        sprites[transition]?.let { return it }
        return runCatching {
            context.assets.open(transition.assetPath).use {
                BitmapFactory
                    .decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
                    ?.asImageBitmap()
            }
        }.getOrNull()?.also { sprites[transition] = it }
    }
}

internal object LasTransitionTimeline {
    const val FRAME_COUNT = 24
    private const val FRAME_NANOS = 62_500_000L
    private const val APPEAR_BLEND_NANOS = 150_000_000L
    private const val DISAPPEAR_HANDOFF_NANOS = 150_000_000L
    private const val APPEAR_BLEND_START_NANOS = (FRAME_COUNT - 1) * FRAME_NANOS

    const val APPEAR_DURATION_NANOS = APPEAR_BLEND_START_NANOS + APPEAR_BLEND_NANOS
    const val DISAPPEAR_DURATION_NANOS = DISAPPEAR_HANDOFF_NANOS + FRAME_COUNT * FRAME_NANOS

    fun frameAt(
        transition: LasTransition,
        elapsedNanos: Long,
    ): Float {
        val playback =
            if (transition == LasTransition.APPEAR) elapsedNanos else elapsedNanos - DISAPPEAR_HANDOFF_NANOS
        return (playback.coerceAtLeast(0L) / FRAME_NANOS).toInt().coerceAtMost(FRAME_COUNT - 1).toFloat()
    }

    fun idleAlpha(
        transition: LasTransition,
        elapsedNanos: Long,
    ): Float =
        if (transition == LasTransition.APPEAR) {
            ((elapsedNanos - APPEAR_BLEND_START_NANOS).toFloat() / APPEAR_BLEND_NANOS).coerceIn(0f, 1f)
        } else {
            1f - (elapsedNanos.toFloat() / DISAPPEAR_HANDOFF_NANOS).coerceIn(0f, 1f)
        }

    fun showsDepartureBubble(elapsedNanos: Long): Boolean =
        elapsedNanos - DISAPPEAR_HANDOFF_NANOS in DEPARTURE_SURPRISE_NANOS
}

/** Plays Las's portal entrance or exit once, then reports completion to the floating overlay. */
@Composable
internal fun LasTransitionAnimation(
    modifier: Modifier,
    transition: LasTransition,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val finished by rememberUpdatedState(onFinished)
    val sheet by produceState<Pair<Boolean, ImageBitmap?>>(false to null, context, transition) {
        value = true to withContext(Dispatchers.IO) { LasTransitionSpriteCache.getOrLoad(context, transition) }
    }
    val elapsed = remember(transition) { mutableLongStateOf(0L) }
    LaunchedEffect(sheet, transition) {
        if (!sheet.first) return@LaunchedEffect
        val duration =
            if (transition == LasTransition.APPEAR) {
                LasTransitionTimeline.APPEAR_DURATION_NANOS
            } else {
                LasTransitionTimeline.DISAPPEAR_DURATION_NANOS
            }
        if (sheet.second != null) {
            val start = withFrameNanos { it }
            while (elapsed.longValue < duration) {
                elapsed.longValue = withFrameNanos { it } - start
            }
        }
        finished()
    }

    val bitmap = sheet.second
    Box(modifier) {
        if (bitmap != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("las-${transition.name.lowercase()}-frame")
                    .graphicsLayer {
                        if (transition == LasTransition.APPEAR) {
                            scaleX = 1.02f
                            scaleY = 1.085f
                            translationX = -size.width * 0.015f
                            translationY = -size.height * 0.075f
                            alpha = 1f - LasTransitionTimeline.idleAlpha(transition, elapsed.longValue)
                        } else {
                            scaleX = 0.93f
                            scaleY = 0.95f
                            translationX = -size.width * 0.018f
                            translationY = -size.height * 0.03f
                            alpha = 1f - LasTransitionTimeline.idleAlpha(transition, elapsed.longValue)
                        }
                        clip = false
                    }.mobiSpriteFrames(
                        sheet = bitmap,
                        columns = 6,
                        rows = 4,
                        loop = false,
                        blendFrames = false,
                        filterQuality = FilterQuality.High,
                    ) { LasTransitionTimeline.frameAt(transition, elapsed.longValue) },
            )
        }
        if (transition == LasTransition.DISAPPEAR || bitmap != null) {
            LasIdleAnimation(
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha =
                            if (bitmap == null) 1f else LasTransitionTimeline.idleAlpha(transition, elapsed.longValue)
                    },
                animate = false,
            )
        }
        if (transition == LasTransition.DISAPPEAR &&
            bitmap != null &&
            LasTransitionTimeline.showsDepartureBubble(elapsed.longValue)
        ) {
            DepartureSurpriseBubble(Modifier.align(Alignment.TopStart))
        }
    }
}
