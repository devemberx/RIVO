package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.util.concurrent.ConcurrentHashMap

/** Fixed source coordinates; the hat adds top padding without rescaling the seated body. */
internal object LunaIdleGeometry {
    const val CANVAS = 1254f
    const val HAT_HEIGHT = 1446f
    const val SPROUT_ROOT_Y = 247.356f
    const val SPROUT_LEVER = 192.889f

    fun sproutMask(overlap: Float): Path =
        Path().apply {
            moveTo(608.614f, 18.281f)
            lineTo(1026.118f, 9.549f)
            lineTo(1033.167f, 346.534f + overlap)
            lineTo(932.784f, 301.892f + overlap)
            lineTo(872.745f, 284.253f + overlap)
            lineTo(832.629f, 268.185f + overlap)
            lineTo(812.582f, 260.649f + overlap)
            lineTo(782.532f, 250.338f + overlap)
            lineTo(752.481f, 240.027f + overlap)
            lineTo(722.431f, 229.716f + overlap)
            lineTo(692.38f, 219.405f + overlap)
            lineTo(652.369f, 208.308f + overlap)
            lineTo(612.419f, 200.193f + overlap)
            close()
        }
}

internal data class LunaIdleArtwork(
    val original: ImageBitmap,
    val body: Bitmap,
    val closedEyesBody: Bitmap?,
    val sprout: Bitmap?,
)

/** Like Mobi idle, construct source layers once on IO and animate them on the display clock. */
internal object LunaIdleArtworkCache {
    private val entries = ConcurrentHashMap<LunaAppearance, LunaIdleArtwork>()

    fun peek(appearance: LunaAppearance = LunaAppearance.NORMAL): LunaIdleArtwork? = entries[appearance]

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun getOrLoad(
        context: Context,
        appearance: LunaAppearance = LunaAppearance.NORMAL,
    ): LunaIdleArtwork? {
        entries[appearance]?.let { return it }
        val original = LunaFirstFrameCache.getOrLoad(context, LunaActiveAnimation.IDLE, appearance) ?: return null
        val height = if (appearance == LunaAppearance.HAT) 723 else 627
        val destination = RectF(0f, 0f, 1254f, height * 2f)
        val eyes =
            if (appearance ==
                LunaAppearance.SUNGLASSES
            ) {
                null
            } else {
                decodeEyes(context) ?: return null
            }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        fun layer(draw: (Canvas) -> Unit): Bitmap =
            Bitmap.createBitmap(627, height, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                canvas.scale(0.5f, 0.5f)
                draw(canvas)
            }
        try {
            val sprout =
                if (appearance == LunaAppearance.HAT) {
                    layer { canvas ->
                        canvas.clipPath(LunaIdleGeometry.sproutMask(2f))
                        canvas.drawBitmap(original.asAndroidBitmap(), null, destination, paint)
                    }
                } else {
                    null
                }
            val body =
                if (sprout == null) {
                    original.asAndroidBitmap()
                } else {
                    layer { canvas ->
                        canvas.drawBitmap(original.asAndroidBitmap(), null, destination, paint)
                        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                        // Retain a root band in front of the moving leaves; no newly exposed cap surface.
                        canvas.drawPath(LunaIdleGeometry.sproutMask(-4f), paint)
                        paint.xfermode = null
                    }
                }
            val closed =
                eyes?.let {
                    layer { canvas ->
                        canvas.drawBitmap(body, null, destination, paint)
                        val top = if (appearance == LunaAppearance.HAT) 192f else 0f
                        canvas.drawBitmap(it, null, RectF(0f, top, 1254f, top + 1254f), paint)
                    }
                }
            return LunaIdleArtwork(original, body, closed, sprout).also { entries[appearance] = it }
        } finally {
            eyes?.recycle()
        }
    }

    private fun decodeEyes(context: Context): Bitmap? =
        try {
            context.assets.open("characters/luna/shared/idle_breath/luna_idle_breath_shared_closed_eyes.webp").use {
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inScaled = false
                    }
                BitmapFactory.decodeStream(it, null, options)?.let { bitmap ->
                    if (bitmap.width == 627 && bitmap.height == 627) {
                        bitmap
                    } else {
                        bitmap.recycle()
                        null
                    }
                }
            }
        } catch (_: java.io.IOException) {
            null
        }
}

internal class LunaIdleRenderer(
    private val artwork: LunaIdleArtwork,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF(0f, 0f, 1254f, artwork.body.height * 2f)
    private val matrix = Matrix()
    private val values = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    fun draw(
        canvas: Canvas,
        elapsedNanos: Long,
    ) {
        artwork.sprout?.let {
            val shift = LunaIdleTimeline.sproutShiftAt(elapsedNanos)
            values[1] = -shift / LunaIdleGeometry.SPROUT_LEVER
            values[2] = shift * LunaIdleGeometry.SPROUT_ROOT_Y / LunaIdleGeometry.SPROUT_LEVER
            matrix.setValues(values)
            val save = canvas.save()
            canvas.concat(matrix)
            canvas.drawBitmap(it, null, destination, paint)
            canvas.restoreToCount(save)
        }
        val body =
            if (LunaIdleTimeline.eyesClosedAt(
                    elapsedNanos,
                )
            ) {
                artwork.closedEyesBody ?: artwork.body
            } else {
                artwork.body
            }
        canvas.drawBitmap(body, null, destination, paint)
    }
}
