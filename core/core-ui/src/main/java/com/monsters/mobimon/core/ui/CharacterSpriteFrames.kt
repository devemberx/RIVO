package com.monsters.mobimon.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Shared fixed-canvas atlas draw. Time/progress is read only in draw, never bitmap allocation. */
internal fun Modifier.characterSpriteFrames(
    sheet: ImageBitmap,
    columns: Int,
    rows: Int,
    loop: Boolean = true,
    blendFrames: Boolean = true,
    filterQuality: FilterQuality = FilterQuality.Low,
    position: () -> Float,
): Modifier =
    drawWithCache {
        val count = columns * rows
        val cell = IntSize(sheet.width / columns, sheet.height / rows)
        val sources = Array(count) { IntOffset(it % columns * cell.width, it / columns * cell.height) }
        val side = size.minDimension.roundToInt()
        val destination = IntSize(side, side)
        val offset = IntOffset(((size.width - side) / 2).roundToInt(), ((size.height - side) / 2).roundToInt())
        onDrawBehind {
            val value = position().coerceIn(0f, count.toFloat())
            val frame = value.toInt().coerceAtMost(count - 1)
            val blend = if (blendFrames) (value - frame).coerceIn(0f, 1f) else 0f
            drawImage(
                sheet,
                sources[frame],
                cell,
                offset,
                destination,
                alpha = 1f - blend,
                filterQuality = filterQuality,
            )
            if (blend > 0f) {
                val next = if (loop) (frame + 1) % count else (frame + 1).coerceAtMost(count - 1)
                drawImage(
                    sheet,
                    sources[next],
                    cell,
                    offset,
                    destination,
                    alpha = blend,
                    filterQuality = filterQuality,
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
