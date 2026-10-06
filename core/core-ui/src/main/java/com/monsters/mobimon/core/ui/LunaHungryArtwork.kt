package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small shared overlays; the character and fitted equipment remain the idle sources. */
internal data class LunaHungryParts(
    val skin: Bitmap,
    val skinGlasses: Bitmap,
    val mouth: Bitmap,
    val thought: Bitmap,
)

internal object LunaHungryPartsCache {
    @Volatile private var parts: LunaHungryParts? = null

    fun peek(): LunaHungryParts? = parts

    @Synchronized
    fun clear() {
        parts = null
    }

    @Synchronized
    fun getOrLoad(context: Context): LunaHungryParts? {
        parts?.let { return it }
        val decoded = mutableListOf<Bitmap>()
        try {
            fun decode(
                name: String,
                width: Int,
                height: Int,
            ): Bitmap {
                val appearance = if (name == "skin_glasses") "sunglasses" else "shared"
                val part = if (name == "skin_glasses") "skin" else name
                val path = "characters/luna/$appearance/hungry/luna_hungry_${appearance}_$part.webp"
                val bitmap =
                    context.assets.open(path).use {
                        requireNotNull(
                            BitmapFactory.decodeStream(
                                it,
                                null,
                                BitmapFactory.Options().apply {
                                    inScaled = false
                                    inSampleSize = 2
                                },
                            ),
                        )
                    }
                decoded.add(bitmap)
                require(bitmap.width == width && bitmap.height == height)
                return bitmap
            }
            return LunaHungryParts(
                decode("skin", 123, 69),
                decode("skin_glasses", 123, 69),
                decode("mouth", 59, 60),
                decode("thought", 206, 199),
            ).also { parts = it }
        } catch (_: java.io.IOException) {
            decoded.forEach(Bitmap::recycle)
            return null
        } catch (_: IllegalArgumentException) {
            decoded.forEach(Bitmap::recycle)
            return null
        }
    }
}

internal object LunaHungryTimeline {
    const val CYCLE_NANOS = LunaIdleTimeline.CYCLE_NANOS

    private fun phase(time: Long): Double = 2.0 * PI * (time.coerceAtLeast(0L) % CYCLE_NANOS).toDouble() / CYCLE_NANOS

    fun mouthScaleAt(time: Long): Float = (.84 + .16 * cos(phase(time))).toFloat()

    fun thoughtLiftAt(time: Long): Float = (6.0 * sin(phase(time))).toFloat()
}

/** Draw after the idle body in its original, unpadded 1254px coordinate system. */
internal class LunaHungryRenderer(
    private val parts: LunaHungryParts,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val skinBounds = RectF(492f, 534f, 738f, 672f)
    private val mouthBounds = RectF(550f, 547f, 678f, 677f)
    private val thoughtBounds = RectF(1060f, -150f, 1330f, 111f)

    fun draw(
        canvas: Canvas,
        elapsedNanos: Long,
        appearance: LunaAppearance,
    ) {
        canvas.drawBitmap(
            if (appearance ==
                LunaAppearance.SUNGLASSES
            ) {
                parts.skinGlasses
            } else {
                parts.skin
            },
            null,
            skinBounds,
            paint,
        )
        val mouthSave = canvas.save()
        canvas.scale(1f, LunaHungryTimeline.mouthScaleAt(elapsedNanos), 614f, 600f)
        canvas.drawBitmap(parts.mouth, null, mouthBounds, paint)
        canvas.restoreToCount(mouthSave)
        val thoughtSave = canvas.save()
        canvas.translate(0f, -LunaHungryTimeline.thoughtLiftAt(elapsedNanos))
        canvas.drawBitmap(parts.thought, null, thoughtBounds, paint)
        canvas.restoreToCount(thoughtSave)
    }
}
