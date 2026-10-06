package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The selected 4.8-second review clock, independent of display frame rate. */
internal object MobiSickMotion {
    const val CYCLE_MS = 4_800L
    private const val TAU = 2 * PI

    data class Pose(
        val breath: Float,
        val lean: Float,
        val eyes: Float,
        val orbit: Double,
    )

    fun at(elapsedNanos: Long): Pose {
        val t = (elapsedNanos.coerceAtLeast(0L) % (CYCLE_MS * 1_000_000L)) / 1_000_000.0
        val phase = TAU * t / CYCLE_MS
        val eyes =
            when {
                t < 1450 || t >= 3800 -> 0.0
                t < 1850 -> smooth((t - 1450) / 400)
                t < 3350 -> 1.0
                else -> 1 - smooth((t - 3350) / 450)
            }
        return Pose(((1 - cos(phase)) / 2).toFloat(), (.028 * sin(phase)).toFloat(), eyes.toFloat(), TAU * t / 2400)
    }

    private fun smooth(t: Double) = t * t * (3 - 2 * t)

    fun x(a: Double): Double = 205 + 58 * cos(a) * cos(-.20) - 20 * sin(a) * sin(-.20)

    fun y(a: Double): Double = 122 + 58 * cos(a) * sin(-.20) + 20 * sin(a) * cos(-.20)

    fun radius(a: Double): Double = 11 + 9 * (sin(a) + 1) / 2

    /** Five source pixels remain clear after accounting for the rounded mask cap. */
    fun gap(
        a: Double,
        direction: Int,
    ): Double {
        val distance = radius(a) + 5 + 10 / 2
        var lo = 0.0
        var hi = PI
        repeat(28) {
            val mid = (lo + hi) / 2
            val next = a + direction * mid
            if (hypot(x(next) - x(a), y(next) - y(a)) < distance) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }
}

/** Source placement keeps restored equipment on the same logical ground line and eye anchors. */
internal enum class MobiSickArtworkSpec(
    val sourceSize: Int,
    val left: Float,
    val top: Float,
    val extent: Float,
    val eyes: FloatArray,
) {
    Normal(408, 0f, 0f, 408f, floatArrayOf(190.8f, 214.8f, 16.4f, 8.7f, 234f, 243.8f, 18f, 9.1f)),
    Headphones(1254, 5f, -20f, 408f, floatArrayOf(190.1f, 215.2f, 17f, 9.8f, 234.4f, 242.6f, 17.3f, 10.4f)),
    Goggles(1254, 12f, -12f, 386f, floatArrayOf(189.9f, 213.3f, 17.2f, 11.4f, 233.9f, 242.9f, 17.9f, 11.1f)),
    ;

    companion object {
        fun forAccessory(accessoryId: String?): MobiSickArtworkSpec =
            when (accessoryId) {
                "accessory:mobi_headphones" -> Headphones
                "accessory:mobi_goggles" -> Goggles
                else -> Normal
            }
    }
}

/** Prepare the fixed ring on IO; every appearance and renderer shares its immutable pixels. */
internal object MobiSickRingCache {
    @Volatile private var cached: Bitmap? = null

    fun peek(): Bitmap? = cached

    @Synchronized
    fun getOrLoad(): Bitmap = cached ?: makeRing().also { cached = it }

    private fun makeRing(): Bitmap {
        val ellipse = RectF(-58f, -20f, 58f, 20f)
        val orbitMatrix =
            Matrix().apply {
                setRotate((-.20 * 180 / PI).toFloat())
                postTranslate(205f, 122f)
            }
        val bitmap = Bitmap.createBitmap(816, 816, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(2f, 2f)
        val full =
            Path().apply {
                addOval(ellipse, Path.Direction.CW)
                transform(orbitMatrix)
            }
        val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        canvas.save()
        canvas.translate(.3f, 1f)
        brush.color = Color.rgb(175, 197, 236)
        brush.strokeWidth = 8.3f
        brush.setShadowLayer(1.1f, 0f, 0f, 0x3387b9ff)
        canvas.drawPath(full, brush)
        canvas.restore()
        brush.clearShadowLayer()
        brush.shader =
            LinearGradient(
                0f,
                94f,
                0f,
                153f,
                intArrayOf(0xffdce7fa.toInt(), 0xffd2e0f6.toInt(), 0xffb7ceef.toInt()),
                floatArrayOf(0f, .43f, 1f),
                Shader.TileMode.CLAMP,
            )
        brush.strokeWidth = 7.7f
        canvas.drawPath(full, brush)
        canvas.save()
        canvas.translate(-.25f, -.65f)
        brush.shader =
            LinearGradient(
                0f,
                100f,
                0f,
                148f,
                intArrayOf(0xffedf4ff.toInt(), 0xffe6efff.toInt(), 0xffd9e7fb.toInt()),
                floatArrayOf(0f, .6f, 1f),
                Shader.TileMode.CLAMP,
            )
        brush.alpha = 166
        brush.strokeWidth = 5.2f
        brush.setShadowLayer(1.2f, 0f, 0f, 0x59f1f7ff)
        canvas.drawPath(full, brush)
        canvas.restore()
        return bitmap
    }
}

/** Fixed ring pixels are revealed by moving gaps; no animated color, glow or overlapping strokes. */
internal class MobiSickRenderer(
    body: ImageBitmap,
    star: ImageBitmap,
    private val spec: MobiSickArtworkSpec,
    private val ring: Bitmap,
) {
    private val body = body.asAndroidBitmap()
    private val star = star.asAndroidBitmap()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(9, 11, 17) }
    private val maskPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val path = Path()
    private val mask = Path()
    private val eye = Path()
    private val ellipse = RectF(-58f, -20f, 58f, 20f)
    private val orbitMatrix =
        Matrix().apply {
            setRotate((-.20 * 180 / PI).toFloat())
            postTranslate(205f, 122f)
        }
    private val bodyMatrix = Matrix()
    private val bodyValues = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val bounds = RectF(0f, 0f, 408f, 408f)
    private val bodyBounds = RectF(spec.left, spec.top, spec.left + spec.extent, spec.top + spec.extent)
    private val starBounds = RectF()
    private val starOrder = intArrayOf(0, 1, 2)

    fun draw(
        canvas: Canvas,
        elapsedNanos: Long,
    ) {
        val pose = MobiSickMotion.at(elapsedNanos)
        canvas.save()
        canvas.translate(0f, 16.32f)
        canvas.save()
        bodyValues[1] = pose.lean
        bodyValues[2] = -304 * pose.lean
        bodyValues[4] = 1 + .038f * pose.breath
        bodyValues[5] = 304 * (1 - bodyValues[4])
        bodyMatrix.setValues(bodyValues)
        canvas.concat(bodyMatrix)
        canvas.drawBitmap(body, null, bodyBounds, paint)
        for (offset in 0..4 step 4) {
            drawEye(
                canvas,
                spec.eyes[offset],
                spec.eyes[offset + 1],
                spec.eyes[offset + 2],
                spec.eyes[offset + 3],
                pose.eyes,
            )
        }
        canvas.restore()
        drawRing(canvas, pose.orbit)
        // Only three stars; stable insertion sort avoids per-frame collection allocation.
        for (i in 0..2) starOrder[i] = i
        for (i in 1..2) {
            var j = i
            while (j > 0 && sin(angle(pose.orbit, starOrder[j])) < sin(angle(pose.orbit, starOrder[j - 1]))) {
                val previous = starOrder[j - 1]
                starOrder[j - 1] = starOrder[j]
                starOrder[j] = previous
                j--
            }
        }
        for (i in starOrder) {
            val a = angle(pose.orbit, i)
            val depth = (sin(a) + 1) / 2
            val half = (MobiSickMotion.radius(a) / .72).toFloat()
            starBounds.set(-half, -half, half, half)
            canvas.save()
            canvas.translate(MobiSickMotion.x(a).toFloat(), MobiSickMotion.y(a).toFloat())
            canvas.rotate((.16 * sin(a) * 180 / PI).toFloat())
            paint.alpha = ((.94 + .06 * depth) * 255).toInt()
            canvas.drawBitmap(star, null, starBounds, paint)
            canvas.restore()
        }
        paint.alpha = 255
        canvas.restore()
    }

    private fun angle(
        orbit: Double,
        index: Int,
    ) = orbit + index * 2 * PI / 3

    private fun drawRing(
        canvas: Canvas,
        orbit: Double,
    ) {
        path.rewind()
        for (i in 0..2) {
            val a = angle(orbit, i)
            val b = a + 2 * PI / 3
            val start = a + MobiSickMotion.gap(a, 1)
            val end = b - MobiSickMotion.gap(b, -1)
            if (end > start) {
                path.arcTo(ellipse, (start * 180 / PI).toFloat(), ((end - start) * 180 / PI).toFloat(), true)
            }
        }
        path.transform(orbitMatrix)
        mask.rewind()
        maskPaint.getFillPath(path, mask)
        canvas.save()
        canvas.clipPath(mask)
        canvas.drawBitmap(ring, null, bounds, paint)
        canvas.restore()
    }

    private fun drawEye(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        open: Float,
    ) {
        if (open <= 0f) return
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(width / 17, height / 9)
        eye.rewind()
        eye.moveTo(0f, 0f)
        eye.cubicTo(3 + 2 * open, 7 - 4 * open, 8 + 3 * open, 9 - 3 * open, 17f, 9f)
        eye.cubicTo(8f, 9 + 4 * open, 3 - 2 * open, 7 + 5 * open, 0f, 0f)
        eye.close()
        canvas.drawPath(eye, eyePaint)
        canvas.restore()
    }
}

internal fun Modifier.mobiSickArtwork(
    body: ImageBitmap,
    star: ImageBitmap,
    spec: MobiSickArtworkSpec,
    ring: Bitmap,
    elapsedNanos: () -> Long,
): Modifier =
    drawWithCache {
        val renderer = MobiSickRenderer(body, star, spec, ring)
        onDrawBehind {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(size.width / 408f, size.height / 408f)
                renderer.draw(native, elapsedNanos())
                native.restore()
            }
        }
    }
