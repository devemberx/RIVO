package com.monsters.mobimon.core.ui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/** Fixed-canvas entrance/exit registration relative to Luna's unchanged avatar slot. */
internal object LunaScene {
    const val FRAME_COUNT = CharacterAnimationAtlas.FRAME_COUNT
    const val FPS = 16

    // Subpixel scene extents may upscale by less than one destination pixel.
    fun requiredFrameSidePx(
        slotSidePx: Float,
        sceneScale: Float,
    ): Int = (slotSidePx * sceneScale).toInt()
}

internal fun Modifier.lunaSceneFrames(
    slot: Dp,
    sheet: ImageBitmap,
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
        }.characterSpriteFrames(
            sheet,
            CharacterAnimationAtlas.COLUMNS,
            CharacterAnimationAtlas.ROWS,
            loop = false,
            blendFrames = false,
        ) { frameIndex().toFloat() }
