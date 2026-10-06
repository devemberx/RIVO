package com.monsters.mobimon.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** Keeps the body scale and common floor while allowing equipment above the avatar slot. */
@Composable
internal fun GroundedCharacterAssetImage(
    bitmap: ImageBitmap,
    crop: AssetCrop?,
    framing: AssetFraming,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .drawWithCache {
                val source = crop ?: AssetCrop(0, 0, bitmap.width, bitmap.height)
                val side = size.minDimension
                val pixelScale = side / framing.referenceSidePx
                val left = (size.width - source.width * pixelScale) / 2f
                val top =
                    (size.height - side) / 2f + side * CharacterArtwork.HAPPY_GROUND_FRACTION -
                        (framing.groundYPx - source.y) * pixelScale
                onDrawBehind {
                    withTransform({
                        translate(left, top)
                        scale(pixelScale, pixelScale, pivot = Offset.Zero)
                    }) {
                        drawImage(
                            bitmap,
                            srcOffset = IntOffset(source.x, source.y),
                            srcSize = IntSize(source.width, source.height),
                            dstSize = IntSize(source.width, source.height),
                            filterQuality = FilterQuality.Low,
                        )
                    }
                }
            },
    )
}
