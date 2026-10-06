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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

internal object LunaIdleTimeline {
    const val CYCLE_NANOS = 2_200_000_000L
    const val GROUND_FRACTION = 1172f / 1254f

    // Measured ear-top lift from the previously reviewed 24 normal poses.
    // Hermite interpolation preserves their inhale/exhale timing without holding raster frames.
    private val lifts =
        floatArrayOf(
            0f,
            2f,
            4f,
            6f,
            7f,
            7f,
            8f,
            7f,
            6f,
            5f,
            4f,
            2f,
            0f,
            -1f,
            -3f,
            -4f,
            -6f,
            -6f,
            -7f,
            -7f,
            -6f,
            -6f,
            -4f,
            -2f,
        )
    private val durationsMs = IntArray(24) { if (it in 9..13) 60 else 100 }
    private val boundariesMs = durationsMs.runningFold(0L) { sum, duration -> sum + duration }

    fun scaleAt(elapsedNanos: Long): Float {
        val ms = (elapsedNanos.coerceAtLeast(0L) % CYCLE_NANOS) / 1_000_000.0
        val i = (0..23).first { ms < boundariesMs[it + 1] }
        val previous = (i + 23) % 24
        val next = (i + 1) % 24
        val duration = durationsMs[i].toFloat()
        val t = ((ms - boundariesMs[i]) / duration).toFloat()
        val a = lifts[i]
        val b = lifts[next]
        val slopeA = (b - lifts[previous]) / (durationsMs[previous] + duration)
        val slopeB = (lifts[(i + 2) % 24] - a) / (duration + durationsMs[next])
        val lift =
            (2 * t * t * t - 3 * t * t + 1) * a + (t * t * t - 2 * t * t + t) * duration * slopeA +
                (-2 * t * t * t + 3 * t * t) * b + (t * t * t - t * t) * duration * slopeB
        return 1f + lift / 1070f
    }

    fun sproutShiftAt(elapsedNanos: Long): Float {
        val phase = (elapsedNanos.coerceAtLeast(0L) % CYCLE_NANOS).toDouble() / CYCLE_NANOS
        return (4.0 * sin(2.0 * PI * phase)).toFloat()
    }

    fun eyesClosedAt(elapsedNanos: Long): Boolean =
        (elapsedNanos.coerceAtLeast(0L) % CYCLE_NANOS) in 1_020_000_000L until 1_140_000_000L
}

// Hat frames add 192 source pixels above the original 1254-square body canvas.
// Restore that origin for animated frames and the first-frame/reduced-motion fallback.
internal fun lunaIdleDestination(
    side: Int,
    appearance: LunaAppearance,
): Pair<IntOffset, IntSize> {
    val top = if (appearance == LunaAppearance.HAT) (side * 192f / 1254f).toInt() else 0
    return IntOffset(0, -top) to IntSize(side, side + top)
}

/** Display-clock breathing from lossless rest/eye poses, shared by every appearance. */
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
    var artwork by remember(context, appearance) { mutableStateOf(LunaIdleArtworkCache.peek(appearance)) }
    LaunchedEffect(context, appearance, animateFrames) {
        if (animateFrames) {
            artwork = withContext(Dispatchers.IO) { LunaIdleArtworkCache.getOrLoad(context, appearance) }
        }
    }
    // Changing equipment or finishing a decode must not restart the breath.
    val elapsed = remember(context) { mutableLongStateOf(0L) }
    LaunchedEffect(animateFrames) {
        elapsed.longValue = 0L
        if (!animateFrames) return@LaunchedEffect
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
                "luna-animation-${if (firstFrame == null && artwork == null) "loading" else "frame"}-${appearance.assetName}",
            ).semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .drawWithCache {
                val side = size.minDimension.toInt()
                val (origin, destination) = lunaIdleDestination(side, appearance)
                val offset =
                    IntOffset(
                        ((size.width - side) / 2).toInt() + origin.x,
                        ((size.height - side) / 2).toInt() + origin.y,
                    )
                val pivot = Offset(size.width / 2f, (size.height - side) / 2f + side * LunaIdleTimeline.GROUND_FRACTION)
                val renderer = artwork?.let(::LunaIdleRenderer)
                onDrawBehind {
                    val time = if (animateFrames) elapsed.longValue else 0L
                    scale(LunaIdleTimeline.scaleAt(time), pivot = pivot) {
                        if (animateFrames && renderer != null) {
                            drawIntoCanvas { canvas ->
                                val native = canvas.nativeCanvas
                                val save = native.save()
                                native.translate(offset.x.toFloat(), offset.y.toFloat())
                                native.scale(
                                    destination.width / 1254f,
                                    destination.height / (artwork!!.body.height * 2f),
                                )
                                renderer.draw(native, time)
                                native.restoreToCount(save)
                            }
                        } else if (firstFrame != null) {
                            drawImage(
                                firstFrame,
                                dstOffset = offset,
                                dstSize = destination,
                                filterQuality = FilterQuality.Low,
                            )
                        }
                    }
                }
            },
    )
}
