package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object LunaSickTimeline {
    const val CYCLE_NANOS = 2_200_000_000L
    const val GROUND_Y = 1048f

    private fun phase(elapsedNanos: Long): Double =
        (elapsedNanos.coerceAtLeast(0L) % CYCLE_NANOS).toDouble() / CYCLE_NANOS * 2 * PI

    // Small new breath in the existing collapsed pose; do not refit the head or equipment.
    fun scaleAt(elapsedNanos: Long): Float = 1f + (0.003 * sin(phase(elapsedNanos))).toFloat()

    fun heatAlphaAt(elapsedNanos: Long): Float = (0.8 + 0.2 * cos(phase(elapsedNanos))).toFloat()

    fun heatRiseAt(elapsedNanos: Long): Float = (-4 * sin(phase(elapsedNanos))).toFloat()
}

internal data class LunaSickArtwork(
    val body: ImageBitmap,
    val heat: ImageBitmap,
    val sweat: ImageBitmap,
    val still: ImageBitmap,
)

/** Three equipped bodies share one extracted heat texture, rather than 72 full-frame poses. */
internal object LunaSickArtworkCache {
    private val entries = ConcurrentHashMap<LunaAppearance, LunaSickArtwork>()
    private var heat: ImageBitmap? = null

    fun peek(appearance: LunaAppearance = LunaAppearance.NORMAL): LunaSickArtwork? = entries[appearance]

    @Synchronized
    fun clear() {
        entries.clear()
        heat = null
    }

    @Synchronized
    fun getOrLoad(
        context: Context,
        appearance: LunaAppearance = LunaAppearance.NORMAL,
    ): LunaSickArtwork? {
        entries[appearance]?.let { return it }
        val body =
            decode(context, "${appearance.assetName}/sick/luna_sick_${appearance.assetName}_base.webp") ?: return null
        val sharedHeat = heat ?: decode(context, "shared/sick/luna_sick_shared_heat.webp")?.also { heat = it }
        if (sharedHeat == null) return null
        val sweat =
            decode(context, "${appearance.assetName}/sick/luna_sick_${appearance.assetName}_sweat.webp", 732, 1062)
                ?: return null
        val bitmap = Bitmap.createBitmap(627, 627, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(.5f, .5f)
        val artwork = LunaSickArtwork(body, sharedHeat, sweat, body)
        LunaSickRenderer(artwork, appearance).draw(canvas, 0L)
        return artwork.copy(still = bitmap.asImageBitmap()).also { entries[appearance] = it }
    }

    private fun decode(
        context: Context,
        relativePath: String,
        width: Int = 627,
        height: Int = 627,
    ): ImageBitmap? =
        try {
            (context.applicationContext?.assets ?: context.assets).open("characters/luna/$relativePath").use {
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inScaled = false
                    }
                BitmapFactory.decodeStream(it, null, options)?.let { bitmap ->
                    if (bitmap.width == width && bitmap.height == height) {
                        bitmap.asImageBitmap()
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

internal class LunaSickRenderer(
    private val artwork: LunaSickArtwork,
    private val appearance: LunaAppearance,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF(0f, 0f, 1254f, 1254f)
    private val sweatSource = Rect()
    private val foreheadSweat = RectF(630f, 350f, 820f, 560f)
    private val cheekSweat = RectF(945f, 475f, 1115f, 825f)

    fun draw(
        canvas: Canvas,
        elapsedNanos: Long,
    ) {
        val save = canvas.save()
        val breath = LunaSickTimeline.scaleAt(elapsedNanos)
        canvas.scale(breath, breath, 627f, LunaSickTimeline.GROUND_Y)
        canvas.drawBitmap(artwork.body.asAndroidBitmap(), null, destination, paint)
        // Local authored patches include the skin revealed behind the flowing drops.
        val ms = (elapsedNanos.coerceAtLeast(0L) % LunaSickTimeline.CYCLE_NANOS) / 1_000_000.0
        val index = (ms / 90).toInt().coerceAtMost(23)
        val duration = if (index == 23) 130 else 90
        val blend = ((ms - index * 90) / duration).toFloat()
        drawSweat(canvas, index)
        paint.alpha = (255 * blend).toInt()
        drawSweat(canvas, (index + 1) % 24)
        paint.alpha = 255
        val heatSave = canvas.save()
        val x = if (appearance == LunaAppearance.HAT) -40f else 0f
        val y = if (appearance == LunaAppearance.HAT) -110f else 0f
        canvas.translate(x, y + LunaSickTimeline.heatRiseAt(elapsedNanos))
        paint.alpha = (255 * LunaSickTimeline.heatAlphaAt(elapsedNanos)).toInt()
        canvas.drawBitmap(artwork.heat.asAndroidBitmap(), null, destination, paint)
        paint.alpha = 255
        canvas.restoreToCount(heatSave)
        canvas.restoreToCount(save)
    }

    private fun drawSweat(
        canvas: Canvas,
        index: Int,
    ) {
        val x = (index % 4) * 183
        val y = (index / 4) * 177
        sweatSource.set(x + 1, y + 1, x + 96, y + 106)
        canvas.drawBitmap(artwork.sweat.asAndroidBitmap(), sweatSource, foreheadSweat, paint)
        sweatSource.set(x + 97, y + 1, x + 182, y + 176)
        canvas.drawBitmap(artwork.sweat.asAndroidBitmap(), sweatSource, cheekSweat, paint)
    }
}

@Composable
fun LunaSickAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    appearance: LunaAppearance = LunaAppearance.NORMAL,
    animateFrames: Boolean = true,
) {
    val context = LocalContext.current
    val firstFrame =
        remember(context, appearance) {
            LunaFirstFrameCache.getOrLoad(context, LunaActiveAnimation.SICK, appearance)
        }
    var artwork by remember(context, appearance) { mutableStateOf(LunaSickArtworkCache.peek(appearance)) }
    LaunchedEffect(context, appearance) {
        artwork = withContext(Dispatchers.IO) { LunaSickArtworkCache.getOrLoad(context, appearance) }
    }
    val elapsed = remember(context) { mutableLongStateOf(0L) }
    LaunchedEffect(animateFrames) {
        elapsed.longValue = 0L
        if (!animateFrames) return@LaunchedEffect
        var previous = withInfiniteAnimationFrameNanos { it }
        while (isActive) {
            val now = withInfiniteAnimationFrameNanos { it }
            elapsed.longValue += (now - previous).coerceIn(0L, 100_000_000L)
            previous = now
        }
    }
    val asset = CharacterArtwork.characters.getValue("friend:luna")
    Box(
        modifier
            .graphicsLayer {
                scaleX = asset.visualScale
                scaleY = asset.visualScale
                translationX = size.width * asset.translationXFraction
                translationY = size.height * (asset.translationYFraction + LUNA_SICK_TRANSLATION_Y_FRACTION)
            }.testTag(
                "luna-animation-${if (firstFrame == null && artwork == null) "loading" else "frame"}-${appearance.assetName}",
            ).semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .drawWithCache {
                val side = size.minDimension
                val renderer = artwork?.let { LunaSickRenderer(it, appearance) }
                onDrawBehind {
                    val time = if (animateFrames) elapsed.longValue else 0L
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val save = native.save()
                        native.translate((size.width - side) / 2f, (size.height - side) / 2f)
                        native.scale(side / 1254f, side / 1254f)
                        if (renderer != null) {
                            renderer.draw(native, time)
                        } else if (firstFrame != null) {
                            native.drawBitmap(firstFrame.asAndroidBitmap(), null, RectF(0f, 0f, 1254f, 1254f), null)
                        }
                        native.restoreToCount(save)
                    }
                }
            },
    )
}
