package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Small shared overlays; the character and fitted equipment remain the idle sources. */
internal data class LunaHungryParts(
    val skin: Bitmap,
    val skinGlasses: Bitmap,
    val mouth: Bitmap,
    val drop: Bitmap,
    val cloud: Bitmap,
    val dot: Bitmap,
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
                decode("mouth", 60, 47),
                decode("drop", 14, 20),
                decode("cloud", 183, 147),
                decode("dot", 29, 29),
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
    const val CYCLE_NANOS = 4_400_000_000L
    const val STILL_NANOS = 2_200_000_000L

    private fun milliseconds(time: Long): Float =
        ((time.coerceAtLeast(0L) % CYCLE_NANOS).toDouble() / 1_000_000.0).toFloat()

    private fun smooth(
        start: Float,
        end: Float,
        value: Float,
    ): Float {
        val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return (t * t * t * (t * (t * 6f - 15f) + 10f)).coerceIn(0f, 1f)
    }

    fun mouthScaleAt(time: Long): Float {
        val t = milliseconds(time)
        return smooth(1060f, 1660f, t) * (1f - smooth(3330f, 3730f, t))
    }

    fun faceAlphaAt(time: Long): Float = if (mouthScaleAt(time) > .03f) 1f else 0f

    fun thoughtAlphaAt(
        time: Long,
        part: Int,
    ): Float {
        val t = milliseconds(time)
        val start = if (part == 3) 780f else 100f + part * 230f
        return smooth(start, start + 180f, t) * (1f - smooth(3230f, 3530f, t))
    }

    fun droolGrowthAt(time: Long): Float = smooth(1780f, 2380f, milliseconds(time))

    fun stemAlphaAt(time: Long): Float {
        val t = milliseconds(time)
        return smooth(1780f, 1940f, t) * (1f - smooth(2630f, 2780f, t))
    }

    fun stemEndAt(time: Long): Float {
        val t = milliseconds(time)
        return (648f + 48f * droolGrowthAt(time)) * (1f - smooth(2380f, 2520f, t)) +
            640f * smooth(2380f, 2520f, t)
    }

    fun dropAlphaAt(time: Long): Float {
        val t = milliseconds(time)
        return smooth(1780f, 1940f, t) * (1f - smooth(2980f, 3080f, t))
    }

    // One droplet grows on the thread and keeps its position, size and velocity at release.
    fun dropYAt(time: Long): Float {
        val t = milliseconds(time)
        val fallingSeconds = ((t - 2380f) / 1000f).coerceAtLeast(0f)
        return 642f + 48f * droolGrowthAt(time) + 600f * fallingSeconds * fallingSeconds
    }
}

/** Draw after the idle body in its original, unpadded 1254px coordinate system. */
internal class LunaHungryRenderer(
    private val parts: LunaHungryParts,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val skinBounds = RectF(492f, 534f, 738f, 672f)
    private val mouthBounds = RectF(550f, 541f, 680f, 643f)
    private val cloudBounds = RectF(956f, -294f, 1322f, 0f)
    private val dotBounds =
        arrayOf(RectF(1006f, 79f, 1034f, 107f), RectF(1020f, 35f, 1062f, 77f), RectF(1036f, -20f, 1094f, 38f))
    private val dropBounds = RectF()
    private val stem = Path()
    private val stemPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = 0xff75d2e6.toInt()
        }

    fun draw(
        canvas: Canvas,
        elapsedNanos: Long,
        appearance: LunaAppearance,
    ) {
        val opening = LunaHungryTimeline.mouthScaleAt(elapsedNanos)
        val faceAlpha = LunaHungryTimeline.faceAlphaAt(elapsedNanos)
        // At full closure the original canonical w-shaped mouth is exposed exactly.
        // Use a thin lip key instead of overlapping translucent open and closed mouths.
        paint.alpha = (255 * faceAlpha).toInt()
        canvas.drawBitmap(
            if (appearance == LunaAppearance.SUNGLASSES) parts.skinGlasses else parts.skin,
            null,
            skinBounds,
            paint,
        )
        val mouthSave = canvas.save()
        canvas.scale(1.45f - .45f * opening, opening.coerceAtLeast(.03f), 614f, 600f)
        canvas.drawBitmap(parts.mouth, null, mouthBounds, paint)
        canvas.restoreToCount(mouthSave)

        val growth = LunaHungryTimeline.droolGrowthAt(elapsedNanos)
        stem.rewind()
        stem.moveTo(630f, 630f)
        stem.cubicTo(641f, 632f, 654f, 632f, 655f, 642f)
        stem.quadTo(656f, 647f, 656f, LunaHungryTimeline.stemEndAt(elapsedNanos))
        stemPaint.strokeWidth = 9f - 3f * growth
        stemPaint.alpha = (255 * LunaHungryTimeline.stemAlphaAt(elapsedNanos)).toInt()
        canvas.drawPath(stem, stemPaint)

        val width = 24f + 4f * growth
        val height = 20f + 16f * growth
        val dropY = LunaHungryTimeline.dropYAt(elapsedNanos)
        dropBounds.set(656f - width / 2f, dropY, 656f + width / 2f, dropY + height)
        paint.alpha = (255 * LunaHungryTimeline.dropAlphaAt(elapsedNanos)).toInt()
        canvas.drawBitmap(parts.drop, null, dropBounds, paint)

        dotBounds.forEachIndexed { index, bounds ->
            paint.alpha = (255 * LunaHungryTimeline.thoughtAlphaAt(elapsedNanos, index)).toInt()
            canvas.drawBitmap(parts.dot, null, bounds, paint)
        }
        paint.alpha = (255 * LunaHungryTimeline.thoughtAlphaAt(elapsedNanos, 3)).toInt()
        canvas.drawBitmap(parts.cloud, null, cloudBounds, paint)
    }
}
