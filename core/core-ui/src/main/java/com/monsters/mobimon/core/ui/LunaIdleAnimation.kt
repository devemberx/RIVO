package com.monsters.mobimon.core.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal object LunaIdleTimeline {
    const val FRAME_COUNT = 24
    const val CYCLE_NANOS = 2_200_000_000L

    // Keep the original loop duration, but give the blink a brisk close/open and
    // remove the extra hold on the final breathing pose.
    private val durationsMs = IntArray(FRAME_COUNT) { if (it in 9..13) 60 else 100 }
    private val boundariesMs = durationsMs.runningFold(0L) { sum, duration -> sum + duration }

    fun frameAt(elapsedNanos: Long): Int {
        val timeMs = (elapsedNanos.coerceAtLeast(0L) % CYCLE_NANOS) / 1_000_000.0
        return (0 until FRAME_COUNT).first { timeMs < boundariesMs[it + 1] }
    }

    fun blendAt(
        elapsedNanos: Long,
        appearance: LunaAppearance,
    ): Float {
        val frame = frameAt(elapsedNanos)
        // Eye shapes change across these pairs. Dissolving them creates doubled
        // pupils; use their authored poses. Sunglasses completely cover the eyes.
        if (appearance != LunaAppearance.SUNGLASSES && frame in 8..13) return 0f
        val timeMs = (elapsedNanos.coerceAtLeast(0L) % CYCLE_NANOS) / 1_000_000.0
        return ((timeMs - boundariesMs[frame]) / durationsMs[frame]).toFloat()
    }
}

/** Blend only adjacent, aligned breathing poses; retain crisp authored blinks. */
@Composable
fun LunaIdleBreathAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    appearance: LunaAppearance = LunaAppearance.NORMAL,
    animateFrames: Boolean = true,
) {
    val context = LocalContext.current
    val firstFrame =
        remember(context, appearance) {
            LunaFirstFrameCache.getOrLoad(context, LunaActiveAnimation.IDLE, appearance)
        }
    var frames by remember(context, appearance) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    LaunchedEffect(context, appearance, animateFrames) {
        if (animateFrames) {
            frames = withContext(Dispatchers.IO) { LunaAnimationCache.getOrLoadFrames(context, appearance) }
        }
    }
    val elapsed = remember(context, appearance) { mutableLongStateOf(0L) }
    LaunchedEffect(frames, animateFrames) {
        elapsed.longValue = 0L
        if (!animateFrames || frames.size != LunaIdleTimeline.FRAME_COUNT) return@LaunchedEffect
        var previous = withInfiniteAnimationFrameNanos { it }
        while (isActive) {
            val now = withInfiniteAnimationFrameNanos { it }
            elapsed.longValue += (now - previous).coerceIn(0L, 100_000_000L)
            previous = now
        }
    }
    val asset = CharacterArtwork.characters.getValue("friend:luna")
    Box(
        modifier
            .graphicsLayer {
                scaleX = asset.visualScale
                scaleY = asset.visualScale
                translationX = size.width * asset.translationXFraction
                translationY = size.height * asset.translationYFraction
            }.testTag(
                "luna-animation-${if (firstFrame == null && frames.isEmpty()) "loading" else "frame"}-${appearance.assetName}",
            ).semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .drawWithCache {
                val side = size.minDimension.toInt()
                val destination = IntSize(side, side)
                val offset = IntOffset(((size.width - side) / 2).toInt(), ((size.height - side) / 2).toInt())
                val layerPaint = Paint()
                val layerBounds = Rect(0f, 0f, size.width, size.height)
                onDrawBehind {
                    val ready = animateFrames && frames.size == LunaIdleTimeline.FRAME_COUNT
                    val time = if (ready) elapsed.longValue else 0L
                    val index = LunaIdleTimeline.frameAt(time)
                    val current = if (ready) frames[index] else firstFrame
                    val blend = if (ready) LunaIdleTimeline.blendAt(time, appearance) else 0f
                    if (current != null) {
                        // Add premultiplied pixels in an isolated layer so the
                        // blend preserves opacity on both light and dark backgrounds.
                        if (blend > 0f) drawContext.canvas.saveLayer(layerBounds, layerPaint)
                        drawImage(
                            current,
                            dstOffset = offset,
                            dstSize = destination,
                            alpha = 1f - blend,
                            filterQuality = FilterQuality.Low,
                        )
                        if (blend > 0f) {
                            drawImage(
                                frames[(index + 1) % frames.size],
                                dstOffset = offset,
                                dstSize = destination,
                                alpha = blend,
                                filterQuality = FilterQuality.Low,
                                blendMode = BlendMode.Plus,
                            )
                            drawContext.canvas.restore()
                        }
                    }
                }
            },
    )
}
