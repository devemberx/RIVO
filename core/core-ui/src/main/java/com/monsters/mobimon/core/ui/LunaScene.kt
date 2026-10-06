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

    // Byte-identical art shares storage, while every timed frame remains in the 24-frame list.
    private val sharedFrames =
        mapOf(
            "hat/appear/luna_appear_hat_01.png" to
                "normal/appear/luna_appear_normal_01.png",
            "hat/disappear/luna_disappear_hat_02.png" to
                "hat/disappear/luna_disappear_hat_01.png",
            "hat/disappear/luna_disappear_hat_09.png" to
                "hat/disappear/luna_disappear_hat_08.png",
            "hat/disappear/luna_disappear_hat_19.png" to
                "hat/disappear/luna_disappear_hat_18.png",
            "hat/disappear/luna_disappear_hat_23.png" to
                "normal/appear/luna_appear_normal_01.png",
            "hat/disappear/luna_disappear_hat_24.png" to
                "normal/appear/luna_appear_normal_01.png",
            "normal/appear/luna_appear_normal_07.png" to
                "normal/appear/luna_appear_normal_06.png",
            "normal/disappear/luna_disappear_normal_02.png" to
                "normal/disappear/luna_disappear_normal_01.png",
            "normal/disappear/luna_disappear_normal_09.png" to
                "normal/disappear/luna_disappear_normal_08.png",
            "normal/disappear/luna_disappear_normal_19.png" to
                "normal/disappear/luna_disappear_normal_18.png",
            "normal/disappear/luna_disappear_normal_23.png" to
                "normal/appear/luna_appear_normal_01.png",
            "normal/disappear/luna_disappear_normal_24.png" to
                "normal/appear/luna_appear_normal_01.png",
            "sunglasses/appear/luna_appear_sunglasses_07.png" to
                "sunglasses/appear/luna_appear_sunglasses_06.png",
            "sunglasses/disappear/luna_disappear_sunglasses_02.png" to
                "sunglasses/disappear/luna_disappear_sunglasses_01.png",
            "sunglasses/disappear/luna_disappear_sunglasses_09.png" to
                "sunglasses/disappear/luna_disappear_sunglasses_08.png",
            "sunglasses/disappear/luna_disappear_sunglasses_19.png" to
                "sunglasses/disappear/luna_disappear_sunglasses_18.png",
            "sunglasses/disappear/luna_disappear_sunglasses_23.png" to
                "sunglasses/appear/luna_appear_sunglasses_01.png",
            "sunglasses/disappear/luna_disappear_sunglasses_24.png" to
                "sunglasses/appear/luna_appear_sunglasses_01.png",
        )

    internal fun sourcePath(path: String): String =
        sharedFrames[path.removePrefix("characters/luna/")]?.let { "characters/luna/$it" } ?: path

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
            val decoded = mutableMapOf<String, ImageBitmap>()
            (1..FRAME_COUNT).map { frame ->
                val path = sourcePath(String.format(Locale.US, pathFormat, frame))
                decoded.getOrPut(path) {
                    context.assets.open(path).use { stream ->
                        requireNotNull(BitmapFactory.decodeStream(stream, null, options)).asImageBitmap()
                    }
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
