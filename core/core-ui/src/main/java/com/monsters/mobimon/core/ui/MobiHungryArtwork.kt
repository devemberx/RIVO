package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.io.DataInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

internal data class MobiHungryArtwork(
    val base: Bitmap,
    val thought: Bitmap,
    val rumble: Bitmap,
    val rumbleBounds: RectF,
    val face: List<MobiHungryFacePart>,
)

/** Only the selected appearance is retained. Preparation happens on IO, never in draw. */
internal object MobiHungryArtworkCache {
    private data class Entry(
        val appearance: String,
        val artwork: MobiHungryArtwork,
    )

    @Volatile private var entry: Entry? = null

    fun peek(accessoryId: String?): MobiHungryArtwork? =
        entry
            ?.takeIf {
                it.appearance ==
                    mobiAppearanceName(accessoryId)
            }?.artwork

    @Synchronized
    fun clear() {
        entry = null
    }

    @Synchronized
    fun getOrLoad(
        context: Context,
        accessoryId: String?,
    ): MobiHungryArtwork? {
        peek(accessoryId)?.let { return it }
        val appearance = mobiAppearanceName(accessoryId)
        val decoded = mutableListOf<Bitmap>()
        var loaded = false
        try {
            fun decode(
                part: String,
                width: Int,
                height: Int,
            ): Bitmap {
                // Share only byte-identical exports; timed poses and equipment stay independent.
                val source =
                    when {
                        appearance == "headphones" && part in listOf("face", "thought") -> "normal"
                        appearance == "goggles" && part == "rumble" -> "normal"
                        else -> appearance
                    }
                // Decode straight alpha, then use Android's rounded premultiplication. The WebP
                // decoder's direct premultiplication differs from PNG at translucent edges.
                val straight =
                    context.assets.open("characters/mobi/$source/hungry/mobi_hungry_${source}_$part.webp").use {
                        requireNotNull(
                            BitmapFactory.decodeStream(
                                it,
                                null,
                                BitmapFactory.Options().apply {
                                    inScaled = false
                                    inPremultiplied = false
                                },
                            ),
                        )
                    }
                decoded.add(straight)
                require(straight.width == width && straight.height == height)
                val pixels = IntArray(width * height)
                straight.getPixels(pixels, 0, width, 0, 0, width, height)
                val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                decoded.add(bitmap)
                straight.recycle()
                return bitmap
            }
            val base = decode("base", 627, 627)
            val thought = decode("thought", 59, 64)
            val endpoints = decode("face", 240, 176)
            val faceSource = if (appearance == "headphones") "normal" else appearance
            // MHGP v2: logical effect bounds, 627px face bounds, signed fields/unsigned RGB in 1/256 units.
            val artwork =
                DataInputStream(
                    GZIPInputStream(
                        context.assets.open(
                            "characters/mobi/$faceSource/hungry/mobi_hungry_${faceSource}_face.morph",
                        ),
                    ),
                ).use { input ->
                    require(input.readInt() == 0x4d484750 && input.readInt() == 2)

                    fun readRect(limit: Int): Rect =
                        Rect(input.readInt(), input.readInt(), input.readInt(), input.readInt()).also {
                            require(
                                it.left >= 0 && it.top >= 0 && it.right <= limit && it.bottom <= limit && !it.isEmpty,
                            )
                        }
                    val rumbleRect = readRect(256)
                    val rumble = decode("rumble", rumbleRect.width(), rumbleRect.height())
                    require(input.readInt() == 3)
                    val face =
                        List(3) { index ->
                            val rect = readRect(627)
                            require(rect.width() <= 80 && rect.height() <= 88)
                            val count = rect.width() * rect.height()
                            val sources =
                                List(2) { endpoint ->
                                    IntArray(count).also {
                                        endpoints.getPixels(
                                            it,
                                            0,
                                            rect.width(),
                                            index * 80,
                                            endpoint * 88,
                                            rect.width(),
                                            rect.height(),
                                        )
                                    }
                                }

                            fun fixed(
                                size: Int,
                                signed: Boolean,
                            ): FloatArray =
                                FloatArray(size) {
                                    (if (signed) input.readShort().toInt() else input.readUnsignedShort()) / 256f
                                }
                            MobiHungryFacePart(rect, sources, List(2) { fixed(count, true) }, fixed(count * 3, false))
                        }
                    require(input.read() == -1)
                    MobiHungryArtwork(base, thought, rumble, RectF(rumbleRect), face)
                }
            endpoints.recycle()
            loaded = true
            return artwork.also { entry = Entry(appearance, it) }
        } catch (_: IOException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        } finally {
            if (!loaded) decoded.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}

/** Original endpoints and precomputed morph fields; no source extraction or skin reconstruction at runtime. */
internal class MobiHungryFacePart(
    val rect: Rect,
    val sources: List<IntArray>,
    private val fields: List<FloatArray>,
    private val skin: FloatArray,
) {
    val destination =
        RectF(
            rect.left * 256f / 627f,
            rect.top * 256f / 627f,
            rect.right * 256f / 627f,
            rect.bottom * 256f / 627f,
        )
    private val count = rect.width() * rect.height()

    // Prepared on the loader's worker before publishing the cache entry. The two
    // original endpoints retain their alpha; 64 opaque interior samples can blend
    // directly over each other without a saveLayer or rewritten texture.
    val expressions: List<Bitmap> =
        run {
            val pixels = IntArray(count)
            List(EXPRESSION_SAMPLES + 2) { index ->
                val amount =
                    when (index) {
                        0 -> 0f
                        EXPRESSION_SAMPLES + 1 -> 1f
                        else -> ((index - 1f) / (EXPRESSION_SAMPLES - 1)).coerceIn(Float.MIN_VALUE, Math.nextDown(1f))
                    }
                pixelsAt(amount, pixels)
                Bitmap.createBitmap(pixels, rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
            }
        }

    fun draw(
        canvas: Canvas,
        paint: Paint,
        amount: Float,
    ) {
        val expression = amount.coerceIn(0f, 1f)
        if (expression == 0f || expression == 1f) {
            canvas.drawBitmap(expressions[if (expression == 0f) 0 else expressions.lastIndex], null, destination, paint)
            return
        }
        val position = 1 + expression * (EXPRESSION_SAMPLES - 1)
        val lower = position.toInt()
        canvas.drawBitmap(expressions[lower], null, destination, paint)
        val alpha = ((position - lower) * 255).roundToInt()
        if (alpha > 0 && lower < EXPRESSION_SAMPLES) {
            paint.alpha = alpha
            canvas.drawBitmap(expressions[lower + 1], null, destination, paint)
            paint.alpha = 255
        }
    }

    private companion object {
        const val EXPRESSION_SAMPLES = 64
    }

    fun pixelsAt(
        amount: Float,
        out: IntArray,
    ) {
        if (amount == 0f || amount == 1f) {
            sources[if (amount == 0f) 0 else 1].copyInto(out)
            return
        }
        val weight = MobiHungryTimeline.smooth(0f, .15f, amount) * (1 - MobiHungryTimeline.smooth(.85f, 1f, amount))
        val endpoint = sources[if (amount < .5f) 0 else 1]
        for (i in 0 until count) {
            val distance = fields[0][i] * (1 - amount) + fields[1][i] * amount
            val coverage = MobiHungryTimeline.smooth(-.65f, .65f, distance)
            var pixel = -0x1000000
            for (c in 0..2) {
                val shift = 16 - c * 8
                val ink = if (c == 2) 30f else 24f
                val morphed = skin[i * 3 + c] * (1 - coverage) + ink * coverage
                val channel =
                    (morphed * weight + ((endpoint[i] ushr shift) and 255) * (1 - weight))
                        .roundToInt()
                        .coerceIn(
                            0,
                            255,
                        )
                pixel = pixel or (channel shl shift)
            }
            out[i] = pixel
        }
    }
}

internal class MobiHungryRenderer(
    private val artwork: MobiHungryArtwork,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val full = RectF(0f, 0f, 256f, 256f)

    private val thoughtBounds = RectF(197f, 8f, 256f, 72f)
    private val wheel = Path().apply { addOval(RectF(86f, 149f, 169f, 223f), Path.Direction.CW) }

    fun draw(
        canvas: Canvas,
        elapsedNanos: Long,
    ) {
        val pose = MobiHungryTimeline.poseAt(elapsedNanos)
        val body = canvas.save()
        canvas.clipRect(0f, 170f, 256f, 256f)
        canvas.drawBitmap(artwork.base, null, full, paint)
        canvas.restoreToCount(body)
        val head = canvas.save()
        canvas.translate(0f, pose.dip)
        canvas.clipRect(0f, 0f, 256f, 170f)
        canvas.drawBitmap(artwork.base, null, full, paint)
        if (pose.expression > 0f) {
            artwork.face.forEach { part -> part.draw(canvas, paint, pose.expression) }
        }
        canvas.restoreToCount(head)
        val foreground = canvas.save()
        canvas.clipPath(wheel)
        canvas.drawBitmap(artwork.base, null, full, paint)
        canvas.restoreToCount(foreground)
        paint.alpha = (pose.thought * 255).roundToInt()
        canvas.drawBitmap(artwork.thought, null, thoughtBounds, paint)
        paint.alpha = (pose.rumble * 255).roundToInt()
        if (pose.rumble > 0f) {
            for (side in 0..1) {
                val sway = MobiHungryTimeline.swayAt(elapsedNanos, side)
                val px = if (side == 0) 36.561f else 219.075f
                val py = if (side == 0) 178.774f else 179.280f
                val effect = canvas.save()
                canvas.translate(sway.x, sway.y)
                canvas.rotate(sway.degrees, px, py)
                canvas.clipRect(side * 128f, 0f, (side + 1) * 128f, 256f)
                canvas.drawBitmap(artwork.rumble, null, artwork.rumbleBounds, paint)
                canvas.restoreToCount(effect)
            }
        }
        paint.alpha = 255
    }
}

internal fun Modifier.mobiHungryParts(
    artwork: MobiHungryArtwork,
    elapsedNanos: () -> Long,
): Modifier =
    drawWithCache {
        val renderer = MobiHungryRenderer(artwork)
        val side = size.minDimension
        onDrawBehind {
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                val save = canvas.save()
                canvas.translate((size.width - side) / 2, (size.height - side) / 2)
                canvas.scale(side / 256f, side / 256f)
                renderer.draw(canvas, elapsedNanos())
                canvas.restoreToCount(save)
            }
        }
    }
