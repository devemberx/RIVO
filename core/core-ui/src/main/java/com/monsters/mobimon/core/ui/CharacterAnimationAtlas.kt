package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Shared 24-pose overlay format. Physical scene size, including enlargement, selects the decode tier. */
internal object CharacterAnimationAtlas {
    const val COLUMNS = 6
    const val ROWS = 4
    const val FRAME_COUNT = COLUMNS * ROWS
    const val CELL_SIDE_PX = 512

    fun sampleSizeFor(requiredFrameSidePx: Int): Int = if (requiredFrameSidePx > 256) 1 else 2

    fun load(
        context: Context,
        path: String,
        requiredFrameSidePx: Int,
    ): ImageBitmap? =
        try {
            context.assets.open(path).use { stream ->
                val sampleSize = sampleSizeFor(requiredFrameSidePx)
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inScaled = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                val bitmap = requireNotNull(BitmapFactory.decodeStream(stream, null, options))
                val cell = CELL_SIDE_PX / sampleSize
                if (bitmap.width != cell * COLUMNS || bitmap.height != cell * ROWS) {
                    bitmap.recycle()
                    null
                } else {
                    bitmap.asImageBitmap()
                }
            }
        } catch (_: java.io.IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
