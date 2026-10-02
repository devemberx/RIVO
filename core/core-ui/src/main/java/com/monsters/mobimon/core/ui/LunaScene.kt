package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Luna's entrance and exit are 1024px scenes. Each is scaled relative to the avatar slot and shifted until the
 * scene's seated Luna covers the idle Luna.
 */
internal object LunaScene {
    const val FRAME_COUNT = 24
    const val FPS = 16

    fun load(
        context: Context,
        pathFormat: String,
    ): List<ImageBitmap>? =
        try {
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = 2
                    inScaled = false
                }
            (1..FRAME_COUNT).map { frame ->
                context.assets.open(String.format(Locale.US, pathFormat, frame)).use { stream ->
                    requireNotNull(BitmapFactory.decodeStream(stream, null, options)).asImageBitmap()
                }
            }
        } catch (_: java.io.IOException) {
            null
        }
}

/** Draws [frames] at [sceneScale] times a [slot]-sized avatar, centred on it and then shifted by fractions of it. */
internal fun Modifier.lunaSceneFrames(
    slot: Dp,
    frames: List<ImageBitmap>,
    sceneScale: Float,
    translationXFraction: Float,
    translationYFraction: Float,
    frameIndex: () -> Int,
): Modifier =
    requiredSize(slot * sceneScale)
        .graphicsLayer {
            val slotPx = size.minDimension / sceneScale
            translationX = slotPx * translationXFraction
            translationY = slotPx * translationYFraction
            clip = false
        }.drawWithCache {
            val destination = IntSize(size.width.roundToInt(), size.height.roundToInt())
            onDrawBehind {
                val frame = frames[frameIndex()]
                drawImage(
                    frame,
                    IntOffset.Zero,
                    IntSize(frame.width, frame.height),
                    IntOffset.Zero,
                    destination,
                    filterQuality = FilterQuality.Low,
                )
            }
        }
