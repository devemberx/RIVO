package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal enum class MobiAtlasAction(
    val directory: String,
    val prefix: String,
) {
    RUN("run", "mobi_run_left"),
    APPEAR("appear", "mobi_appear"),
    DISAPPEAR("disappear", "mobi_disappear"),
}

/** Fixed integer cells at both decode sizes; compression does not determine pixel allocation. */
internal object MobiAnimationAtlas {
    fun sampleSizeFor(requiredFrameSidePx: Int): Int = if (requiredFrameSidePx > 256) 1 else 2

    fun load(
        context: Context,
        action: MobiAtlasAction,
        accessoryId: String?,
        requiredFrameSidePx: Int,
    ): ImageBitmap? {
        val appearance = mobiAppearanceName(accessoryId)
        val path = "characters/mobi/$appearance/${action.directory}/${action.prefix}_${appearance}_sprite.webp"
        val sampleSize = sampleSizeFor(requiredFrameSidePx)
        return try {
            context.assets.open(path).use { stream ->
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inScaled = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                val bitmap = requireNotNull(BitmapFactory.decodeStream(stream, null, options))
                val cell = 512 / sampleSize
                require(bitmap.width == cell * 6 && bitmap.height == cell * 4)
                bitmap.asImageBitmap()
            }
        } catch (_: java.io.IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
