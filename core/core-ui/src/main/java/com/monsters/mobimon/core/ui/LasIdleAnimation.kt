package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor

internal object LasIdleTimeline {
    const val CYCLE_MS = 5_600L

    fun armAngle(timeMs: Long): Float {
        val phase = ((timeMs - 800L) / 2_000f).coerceIn(0f, 1f)
        return (5 * sin(4 * PI * phase) * sin(PI * phase)).toFloat()
    }

    fun dimAmount(timeMs: Long): Float =
        when {
            timeMs < 3_000L -> 0f
            timeMs < 3_800L -> smooth((timeMs - 3_000L) / 800f)
            timeMs < 4_200L -> 1f
            timeMs < 5_200L -> 1f - smooth((timeMs - 4_200L) / 1_000f)
            else -> 0f
        }

    private fun smooth(value: Float): Float = value * value * (3f - 2f * value)
}

/** Rigid master artwork: no atlas crossfade, body scaling, bobbing or per-frame redraw. */
@Composable
internal fun LasIdleAnimation(
    modifier: Modifier,
    animate: Boolean,
) {
    val context = LocalContext.current
    var firstFrame by remember(context) { mutableStateOf(LasIdleArtworkCache.peekFirstFrame()) }
    var artwork by remember(context) { mutableStateOf(LasIdleArtworkCache.peek()) }
    LaunchedEffect(context) {
        firstFrame = withContext(Dispatchers.IO) { LasIdleArtworkCache.firstFrame(context) }
        artwork = withContext(Dispatchers.Default) { LasIdleArtworkCache.getOrLoad(context) }
    }
    val readyArtwork = artwork
    if (readyArtwork == null) {
        val still = firstFrame
        if (still == null) {
            Box(modifier.testTag("las-idle-loading"))
        } else {
            Image(still.asImageBitmap(), null, modifier.testTag("las-idle-first-frame"))
        }
        return
    }
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(animate) {
        elapsed.longValue = 0L
        if (animate) {
            val start = withInfiniteAnimationFrameNanos { it }
            while (isActive) elapsed.longValue = withInfiniteAnimationFrameNanos { it } - start
        }
    }
    Canvas(modifier.testTag("las-idle-ready")) {
        val canvas = drawContext.canvas.nativeCanvas
        val side = size.minDimension
        val checkpoint = canvas.save()
        canvas.translate((size.width - side) / 2f, (size.height - side) / 2f)
        canvas.scale(side / readyArtwork.size, side / readyArtwork.size)
        readyArtwork.draw(canvas, if (animate) elapsed.longValue / 1_000_000L % LasIdleTimeline.CYCLE_MS else 0L)
        canvas.restoreToCount(checkpoint)
    }
}

/** One fixed, nodpi source. Only workers acquire the preparation lock; UI reads never wait. */
internal object LasIdleArtworkCache {
    @Volatile private var first: Bitmap? = null

    @Volatile private var prepared: LasRigidArtwork? = null

    fun peekFirstFrame(): Bitmap? = first

    fun peek(): LasRigidArtwork? = prepared

    fun firstFrame(context: Context): Bitmap? =
        first ?: synchronized(this) {
            first ?: BitmapFactory.decodeResource(context.resources, R.drawable.pet_las_normal_preview)?.also {
                it.prepareToDraw()
                first = it
            }
        }

    fun getOrLoad(context: Context): LasRigidArtwork? =
        prepared ?: synchronized(this) {
            prepared ?: firstFrame(context)?.let { LasRigidArtwork(it) }?.also { prepared = it }
        }
}

// Prepared once on a worker. The mutable draw scratch (paint/vertices) is used only on the UI thread.
internal class LasRigidArtwork(
    private val master: Bitmap,
) {
    val size = master.width.toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Coordinates are tied to the unchanged 475px canonical master, never generated poses.
    private val eye = Rect(184, 148, 302, 184)
    private val eyeDestination = RectF(eye)
    private val eyeAxis = measureEyeAxis(master, eye)

    // Warp the complete original image, never cut out the hand or forearm.
    // Motion tapers to zero through the upper arm before reaching the shoulder.
    private val divisions = 128
    private val original = FloatArray((divisions + 1) * (divisions + 1) * 2)
    private val vertices = FloatArray(original.size)
    private val weights = FloatArray(original.size / 2)
    private val body: Bitmap
    private val arm: Bitmap

    init {
        val outline =
            android.graphics.Path().apply {
                moveTo(27f, 137f)
                lineTo(95f, 137f)
                lineTo(95f, 155f)
                lineTo(108f, 160f)
                lineTo(117f, 172f)
                lineTo(117f, 195f)
                lineTo(116f, 205f)
                lineTo(138f, 236f)
                lineTo(154f, 247f)
                lineTo(174f, 257f)
                lineTo(174f, 304f)
                lineTo(124f, 292f)
                lineTo(72f, 271f)
                lineTo(27f, 208f)
                close()
            }
        // Remove the entire moving arm from the stationary layer once. The arm is
        // rendered exactly once; mesh edges can no longer expose its original copy.
        val mask = Bitmap.createBitmap(master.width, master.height, Bitmap.Config.ARGB_8888)
        AndroidCanvas(mask).drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE })
        body = Bitmap.createBitmap(master.width, master.height, Bitmap.Config.ARGB_8888)
        arm = Bitmap.createBitmap(master.width, master.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until master.height) {
            for (x in 0 until master.width) {
                val source = master.getPixel(x, y)
                val alpha = AndroidColor.alpha(source)
                // The ear stays in the stationary layer, even beside the thumb.
                val moving = if (x >= 116 && y < 205) 0 else AndroidColor.alpha(mask.getPixel(x, y))
                val rgb = source and 0x00FFFFFF
                body.setPixel(x, y, ((alpha * (255 - moving) / 255) shl 24) or rgb)
                arm.setPixel(x, y, ((alpha * moving / 255) shl 24) or rgb)
            }
        }
        mask.recycle()
        // The master contains an occluded ear. Rebuild its continuous rear surface
        // behind the body, then draw the moving arm in front of it as usual.
        val earPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    android.graphics.LinearGradient(
                        114f,
                        176f,
                        151f,
                        176f,
                        intArrayOf(0xFF654B29.toInt(), 0xFF251D12.toInt(), 0xFF181610.toInt()),
                        floatArrayOf(0f, 0.35f, 1f),
                        android.graphics.Shader.TileMode.CLAMP,
                    )
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OVER)
            }
        AndroidCanvas(body).drawOval(RectF(114f, 132f, 157f, 220f), earPaint)
        for (row in 0..divisions) {
            for (column in 0..divisions) {
                val index = row * (divisions + 1) + column
                val x = column * size / divisions
                val y = row * size / divisions
                original[index * 2] = x
                original[index * 2 + 1] = y
                if (x < 174f) {
                    val amount = ((174f - x) / 44f).coerceIn(0f, 1f)
                    weights[index] = amount * amount * (3f - 2f * amount)
                }
            }
        }
    }

    // Only the eye's glow is cleared; samples on either side retain the visor's shading.
    private val visor =
        Bitmap.createBitmap(eye.width(), eye.height(), Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                val weight = y.toFloat() / (height - 1)
                for (x in 0 until width) {
                    val top = master.getPixel(eye.left + x, eye.top)
                    val bottom = master.getPixel(eye.left + x, eye.bottom - 1)

                    fun mix(
                        a: Int,
                        b: Int,
                    ) = (a + (b - a) * weight).toInt()
                    setPixel(
                        x,
                        y,
                        AndroidColor.argb(
                            mix(AndroidColor.alpha(top), AndroidColor.alpha(bottom)),
                            mix(AndroidColor.red(top), AndroidColor.red(bottom)),
                            mix(AndroidColor.green(top), AndroidColor.green(bottom)),
                            mix(AndroidColor.blue(top), AndroidColor.blue(bottom)),
                        ),
                    )
                }
            }
        }

    // This overlay changes light intensity only. Every silhouette pixel stays at its source coordinate.
    private val lamps =
        Bitmap.createBitmap(master.width, master.height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until master.height) {
                for (x in 0 until master.width) {
                    if (eye.contains(x, y)) continue
                    val color = master.getPixel(x, y)
                    val red = AndroidColor.red(color)
                    val green = AndroidColor.green(color)
                    val blue = AndroidColor.blue(color)
                    val nx = (x - 255f) / 145f
                    val ny = (y - 156f) / 125f
                    val edge = ((sqrt(nx * nx + ny * ny) - 0.85f) / 0.45f).coerceIn(0f, 1f)
                    val feather = 1f - edge * edge * (3f - 2f * edge)
                    val warmth = ((red - blue - 25) / 65f).coerceIn(0f, 1f) * feather
                    val gray = ((red * 0.3f + green * 0.59f + blue * 0.11f) * 0.32f).toInt()
                    setPixel(x, y, AndroidColor.argb((AndroidColor.alpha(color) * warmth).toInt(), gray, gray, gray))
                }
            }
        }

    fun draw(
        canvas: AndroidCanvas,
        timeMs: Long,
    ) {
        val angle = LasIdleTimeline.armAngle(timeMs) * PI / 180.0
        val dim = LasIdleTimeline.dimAmount(timeMs)
        val cosine = cos(angle).toFloat()
        val sine = sin(angle).toFloat()
        for (index in weights.indices) {
            val x = original[index * 2]
            val y = original[index * 2 + 1]
            val dx = x - 174f
            val dy = y - 277f
            val weight = weights[index]
            vertices[index * 2] = x + weight * (dx * cosine - dy * sine - dx)
            vertices[index * 2 + 1] = y + weight * (dx * sine + dy * cosine - dy)
        }
        canvas.drawBitmap(body, 0f, 0f, paint)
        canvas.drawBitmapMesh(arm, divisions, divisions, vertices, 0, null, 0, paint)

        if (dim > 0f) {
            paint.alpha = (255 * dim).toInt()
            canvas.drawBitmap(lamps, 0f, 0f, paint)
            paint.alpha = 255
            // Replace the original bar before drawing its symmetrically scaled source pixels.
            canvas.drawBitmap(visor, eye.left.toFloat(), eye.top.toFloat(), paint)
            val widthScale = 1f - dim
            if (widthScale > 0f) {
                val saved = canvas.save()
                canvas.translate(eyeAxis.centerX, eyeAxis.centerY)
                canvas.rotate(eyeAxis.degrees)
                canvas.scale(widthScale, 1f)
                canvas.rotate(-eyeAxis.degrees)
                canvas.translate(-eyeAxis.centerX, -eyeAxis.centerY)
                canvas.drawBitmap(master, eye, eyeDestination, paint)
                canvas.restoreToCount(saved)
            }
        }
    }
}

private data class LasEyeAxis(
    val centerX: Float,
    val centerY: Float,
    val degrees: Float,
)

private fun measureEyeAxis(
    bitmap: Bitmap,
    bounds: Rect,
): LasEyeAxis {
    var count = 0
    var sumX = 0.0
    var sumY = 0.0
    var sumXX = 0.0
    var sumYY = 0.0
    var sumXY = 0.0
    for (y in bounds.top until bounds.bottom) {
        for (x in bounds.left until bounds.right) {
            val color = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(color) < 180 ||
                AndroidColor.red(color) < 240 ||
                AndroidColor.green(color) < 190 ||
                AndroidColor.blue(color) > 190
            ) {
                continue
            }
            val px = x + 0.5
            val py = y + 0.5
            count++
            sumX += px
            sumY += py
            sumXX += px * px
            sumYY += py * py
            sumXY += px * py
        }
    }
    if (count < 2) return LasEyeAxis(bounds.exactCenterX(), bounds.exactCenterY(), 0f)
    val cx = sumX / count
    val cy = sumY / count
    val xx = sumXX / count - cx * cx
    val yy = sumYY / count - cy * cy
    val xy = sumXY / count - cx * cy
    return LasEyeAxis(cx.toFloat(), cy.toFloat(), (atan2(2 * xy, xx - yy) * 90 / PI).toFloat())
}
