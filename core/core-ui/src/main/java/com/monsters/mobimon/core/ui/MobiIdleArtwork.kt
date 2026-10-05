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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Canonical coordinates shared with the selected idle preview; never fit parts to their alpha bounds. */
internal object MobiIdleGeometry {
    const val CANVAS = 1254f
    const val SPROUT_ROOT_Y = 315f
    const val SPROUT_LEVER = 260f

    fun sproutMask(): Path =
        Path().apply {
            moveTo(510f, 0f)
            lineTo(980f, 0f)
            lineTo(980f, 292f)
            lineTo(750f, 292f)
            lineTo(738f, 315f)
            cubicTo(710f, 307f, 680f, 301f, 652f, 298f)
            lineTo(652f, 289f)
            lineTo(510f, 289f)
            close()
        }

    fun eyeMask(): Path =
        Path().apply {
            listOf(469f, 764f).forEach { x ->
                addOval(RectF(x - 66f, 515f, x + 66f, 649f), Path.Direction.CW)
            }
        }
}

internal data class MobiIdleArtwork(
    val original: ImageBitmap,
    val body: Bitmap,
    val closedEyesBody: Bitmap,
    val sprout: Bitmap,
    val sproutInFront: Boolean,
)

/** Build source-part layers once on IO, rather than retaining 24 full-body poses per appearance. */
internal object MobiIdleArtworkCache {
    private val entries = ConcurrentHashMap<String, MobiIdleArtwork>()
    private var sharedSprout: Bitmap? = null

    // Composition reads must not wait for the construction lock held during decoding.
    fun peek(accessoryId: String?): MobiIdleArtwork? = entries[mobiAppearanceName(accessoryId)]

    @Synchronized
    fun clear() {
        entries.clear()
        sharedSprout = null
    }

    @Synchronized
    fun getOrLoad(
        context: Context,
        accessoryId: String?,
    ): MobiIdleArtwork? {
        val appearance = mobiAppearanceName(accessoryId)
        entries[appearance]?.let { return it }
        val original = MobiSpriteCache.firstFrame(context, accessoryId) ?: return null
        val normal = MobiSpriteCache.firstFrame(context) ?: return null
        val eyes = decode(context, "closed_eyes") ?: return null
        val underlay = if (appearance == "normal") null else decode(context, "${appearance}_underlay")
        if (appearance != "normal" && underlay == null) {
            eyes.recycle()
            return null
        }
        try {
            val mask = MobiIdleGeometry.sproutMask()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val destination = RectF(0f, 0f, MobiIdleGeometry.CANVAS, MobiIdleGeometry.CANVAS)
            val sprout =
                sharedSprout ?: layer { canvas ->
                    canvas.clipPath(mask)
                    canvas.drawBitmap(normal.asAndroidBitmap(), null, destination, paint)
                }.also { sharedSprout = it }
            val body =
                layer { canvas ->
                    canvas.drawBitmap(original.asAndroidBitmap(), null, destination, paint)
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    canvas.drawPath(mask, paint)
                    paint.xfermode = null
                    if (underlay != null) {
                        canvas.clipPath(mask)
                        canvas.drawBitmap(underlay, null, destination, paint)
                    }
                }
            val closed =
                layer { canvas ->
                    canvas.drawBitmap(body, null, destination, paint)
                    canvas.clipPath(MobiIdleGeometry.eyeMask())
                    // The lower goggles rim reaches y=524; never replace it with the normal face patch.
                    if (appearance == "goggles") canvas.clipRect(0f, 526f, MobiIdleGeometry.CANVAS, 649f)
                    canvas.drawBitmap(eyes, null, destination, paint)
                }
            return MobiIdleArtwork(original, body, closed, sprout, appearance == "headphones")
                .also { entries[appearance] = it }
        } finally {
            eyes.recycle()
            underlay?.recycle()
        }
    }

    private fun layer(draw: (Canvas) -> Unit): Bitmap =
        Bitmap.createBitmap(627, 627, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.scale(0.5f, 0.5f)
            draw(canvas)
        }

    private fun decode(
        context: Context,
        name: String,
    ): Bitmap? =
        try {
            context.assets.open("characters/mobi/idle_layers/$name.webp").use { stream ->
                BitmapFactory
                    .decodeStream(
                        stream,
                        null,
                        BitmapFactory.Options().apply {
                            inSampleSize = 2
                            inScaled = false
                        },
                    )?.let { bitmap ->
                        if (bitmap.width == 627 && bitmap.height == 627) {
                            bitmap
                        } else {
                            // Keep the canonical fallback when a decoder cannot read the gesture asset.
                            bitmap.recycle()
                            null
                        }
                    }
            }
        } catch (_: java.io.IOException) {
            null
        }
}

/** Body transforms remain in the original graphicsLayer; this renderer only adds the selected gestures. */
internal class MobiIdleRenderer(
    private val artwork: MobiIdleArtwork,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF(0f, 0f, MobiIdleGeometry.CANVAS, MobiIdleGeometry.CANVAS)
    private val matrix = Matrix()
    private val values = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    fun draw(
        canvas: Canvas,
        elapsedNanos: Long,
    ) {
        val body = if (MobiIdleTimeline.blinkClosedAt(elapsedNanos)) artwork.closedEyesBody else artwork.body
        if (artwork.sproutInFront) canvas.drawBitmap(body, null, destination, paint)
        val shift = MobiIdleTimeline.sproutShiftAt(elapsedNanos)
        values[1] = -shift / MobiIdleGeometry.SPROUT_LEVER
        values[2] = shift * MobiIdleGeometry.SPROUT_ROOT_Y / MobiIdleGeometry.SPROUT_LEVER
        matrix.setValues(values)
        val save = canvas.save()
        canvas.concat(matrix)
        canvas.drawBitmap(artwork.sprout, null, destination, paint)
        canvas.restoreToCount(save)
        if (!artwork.sproutInFront) canvas.drawBitmap(body, null, destination, paint)
    }
}

internal fun Modifier.mobiIdleParts(
    firstFrame: ImageBitmap,
    artwork: MobiIdleArtwork?,
    elapsedNanos: () -> Long,
): Modifier =
    drawWithCache {
        val renderer = artwork?.let(::MobiIdleRenderer)
        val first = firstFrame.asAndroidBitmap()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val destination = RectF(0f, 0f, MobiIdleGeometry.CANVAS, MobiIdleGeometry.CANVAS)
        val side = size.minDimension.roundToInt().toFloat()
        val left = ((size.width - side) / 2).roundToInt().toFloat()
        val top = ((size.height - side) / 2).roundToInt().toFloat()
        onDrawBehind {
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                val save = canvas.save()
                canvas.translate(left, top)
                canvas.scale(side / MobiIdleGeometry.CANVAS, side / MobiIdleGeometry.CANVAS)
                if (renderer ==
                    null
                ) {
                    canvas.drawBitmap(first, null, destination, paint)
                } else {
                    renderer.draw(canvas, elapsedNanos())
                }
                canvas.restoreToCount(save)
            }
        }
    }
