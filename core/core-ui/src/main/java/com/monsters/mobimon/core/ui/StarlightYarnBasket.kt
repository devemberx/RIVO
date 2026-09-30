package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val BASKET_SIZE = 1254f
private const val BASKET_GRID = 96

private fun softEdge(distance: Float): Float {
    val t = ((1.18f - distance) / 0.18f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun ellipseWeight(
    x: Float,
    y: Float,
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
): Float {
    val dx = (x - cx) / rx
    val dy = (y - cy) / ry
    return softEdge(sqrt(dx * dx + dy * dy))
}

// Only the bell and upper charm deform the base. Ball and front pendant are rigid layers.
private fun basketWeights(
    x: Float,
    y: Float,
): FloatArray =
    floatArrayOf(
        0f,
        ellipseWeight(x, y, 1143f, 788f, 46f, 49f),
        0f,
        ellipseWeight(x, y, 675f, 486f, 60f, 61f),
    )

private val basketPivots = arrayOf(Offset(1064f, 1026f), Offset(1125f, 744f), Offset(766f, 742f), Offset(691f, 400f))
private val basketVertices =
    FloatArray((BASKET_GRID + 1) * (BASKET_GRID + 1) * 2) { i ->
        val vertex = i / 2
        (if (i % 2 == 0) vertex % (BASKET_GRID + 1) else vertex / (BASKET_GRID + 1)) * BASKET_SIZE / BASKET_GRID
    }
private val basketInfluences =
    Array(basketVertices.size / 2) { i ->
        basketWeights(
            basketVertices[i * 2],
            basketVertices[
                i *
                    2 +
                    1,
            ],
        )
    }

internal class YarnBasketMotion(
    phase: Float?,
) {
    val roll = if (phase == null) 0f else 12f * sin(phase)
    private val angles =
        if (phase == null) {
            FloatArray(4)
        } else {
            floatArrayOf(
                roll / 150f,
                0.055f * sin(phase * 3f - 0.6f),
                0.028f * sin(phase * 3f - 1.1f),
                0.038f * sin(phase * 3f - 0.3f),
            )
        }
    private val sines = FloatArray(4) { sin(angles[it]) }
    private val cosines = FloatArray(4) { cos(angles[it]) }

    val ballRotationDegrees: Float get() = angles[0] * 180f / PI.toFloat()
    val pendantRotationDegrees: Float get() = angles[2] * 180f / PI.toFloat()

    fun ballPoint(
        x: Float,
        y: Float,
    ): Offset = point(x, y, floatArrayOf(1f, 0f, 0f, 0f))

    fun pendantPoint(
        x: Float,
        y: Float,
    ): Offset = point(x, y, floatArrayOf(0f, 0f, 1f, 0f))

    fun point(
        x: Float,
        y: Float,
    ): Offset = point(x, y, basketWeights(x, y))

    private fun point(
        x: Float,
        y: Float,
        weights: FloatArray,
    ): Offset {
        var dx = 0f
        var dy = 0f
        for (i in weights.indices) {
            if (weights[i] == 0f) continue
            val px = x - basketPivots[i].x
            val py = y - basketPivots[i].y
            dx += ((cosines[i] - 1f) * px - sines[i] * py + if (i == 0) roll else 0f) * weights[i]
            dy += (sines[i] * px + (cosines[i] - 1f) * py) * weights[i]
        }
        return Offset(x + dx, y + dy)
    }

    fun mesh(): FloatArray =
        FloatArray(basketVertices.size).also { mesh ->
            for (i in basketInfluences.indices) {
                val moved = point(basketVertices[i * 2], basketVertices[i * 2 + 1], basketInfluences[i])
                mesh[i * 2] = moved.x
                mesh[i * 2 + 1] = moved.y
            }
        }
}

internal fun basketStarGlow(phase: Float?): Float =
    if (phase == null) 0.4f else (0.5f - 0.5f * cos(phase * 2f)).coerceIn(0f, 1f)

/** Isolate gold artwork for a color-only overlay; keep the original alpha and canvas registration. */
private fun starColorLayer(
    source: Bitmap,
    regions: List<Rect>,
): Bitmap {
    val pixels = IntArray(source.width * source.height)
    for (region in regions) {
        val colors = IntArray(region.width() * region.height())
        source.getPixels(colors, 0, region.width(), region.left, region.top, region.width(), region.height())
        for (y in 0 until region.height()) {
            for (x in 0 until region.width()) {
                val color = colors[y * region.width() + x]
                val yellow =
                    minOf(
                        (Color.red(color) - Color.blue(color) - 8f) / 18f,
                        (Color.green(color) - Color.blue(color) - 3f) / 12f,
                    ).coerceIn(0f, 1f)
                pixels[(y + region.top) * source.width + x + region.left] =
                    (color and 0x00ffffff) or ((Color.alpha(color) * yellow).toInt() shl 24)
            }
        }
    }
    return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
}

@Composable
fun StarlightYarnBasket(
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    isAnimated: Boolean = true,
) {
    val animated = isAnimated && LocalMobiMonMotionEnabled.current
    val phase: Float?
    if (animated) {
        val transition = rememberInfiniteTransition(label = "yarn_basket")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart),
            label = "yarn_basket_motion",
        )
        phase = value
    } else {
        phase = null
    }
    val resources = LocalContext.current.resources
    val bitmap = remember(resources) { BitmapFactory.decodeResource(resources, R.drawable.yarn_basket_base) }
    val ball = remember(resources) { BitmapFactory.decodeResource(resources, R.drawable.yarn_basket_ball) }
    val pendant = remember(resources) { BitmapFactory.decodeResource(resources, R.drawable.yarn_basket_pendant) }
    val baseStars =
        remember(bitmap) {
            val mask =
                starColorLayer(
                    bitmap,
                    listOf(Rect(922, 238, 1075, 390), Rect(605, 423, 739, 552), Rect(350, 839, 468, 953)),
                )

            fun tint(
                multiplier: Float,
                lift: Float,
            ): Bitmap {
                val result = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
                val tintPaint =
                    Paint().apply {
                        colorFilter =
                            ColorMatrixColorFilter(
                                floatArrayOf(
                                    multiplier,
                                    0f,
                                    0f,
                                    0f,
                                    lift,
                                    0f,
                                    multiplier,
                                    0f,
                                    0f,
                                    lift,
                                    0f,
                                    0f,
                                    multiplier,
                                    0f,
                                    lift,
                                    0f,
                                    0f,
                                    0f,
                                    1f,
                                    0f,
                                ),
                            )
                    }
                android.graphics.Canvas(result).drawBitmap(mask, 0f, 0f, tintPaint)
                return result
            }
            // Bitmap-mesh paint filters vary across renderers; bake the two color endpoints once.
            val endpoints = tint(0.65f, 0f) to tint(1f, 65f)
            mask.recycle()
            endpoints
        }
    val pendantStar = remember(pendant) { starColorLayer(pendant, listOf(Rect(820, 850, 975, 1005))) }
    val starPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val brightness = basketStarGlow(phase)
    val starFilter =
        remember(brightness) {
            val multiplier = 0.65f + brightness * 0.35f
            val lift = brightness * 65f
            ColorMatrixColorFilter(
                floatArrayOf(
                    multiplier,
                    0f,
                    0f,
                    0f,
                    lift,
                    0f,
                    multiplier,
                    0f,
                    0f,
                    lift,
                    0f,
                    0f,
                    multiplier,
                    0f,
                    lift,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                ),
            )
        }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val motion = remember(phase) { YarnBasketMotion(phase) }
    val vertices = remember(motion) { motion.mesh() }
    val stars = remember { arrayOf(Offset(996f, 319f), Offset(675f, 487f), Offset(411f, 895f), Offset(891f, 925f)) }
    val glowPaint =
        remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        0f,
                        0f,
                        98f,
                        intArrayOf(Color.argb(220, 255, 255, 210), Color.argb(120, 255, 208, 91), Color.TRANSPARENT),
                        floatArrayOf(0f, 0.38f, 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        }
    Canvas(modifier.clipToBounds()) {
        val side = minOf(size.width * (if (centered) 0.9f else 0.18f), size.height * (if (centered) 0.94f else 0.36f))
        val scale = side / BASKET_SIZE
        val left = size.width * (if (centered) 0.5f else 0.22f) - side / 2f
        val top = if (centered) (size.height - side) / 2f else size.height * 0.86f - side * (1177f / BASKET_SIZE)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(left, top)
            native.scale(scale, scale)
            native.drawBitmapMesh(bitmap, BASKET_GRID, BASKET_GRID, vertices, 0, null, 0, paint)
            starPaint.colorFilter = starFilter
            native.drawBitmapMesh(baseStars.first, BASKET_GRID, BASKET_GRID, vertices, 0, null, 0, paint)
            native.saveLayerAlpha(0f, 0f, BASKET_SIZE, BASKET_SIZE, (brightness * 255f).toInt())
            native.drawBitmapMesh(baseStars.second, BASKET_GRID, BASKET_GRID, vertices, 0, null, 0, paint)
            native.restore()
            native.save()
            native.translate(motion.roll, 0f)
            native.rotate(motion.ballRotationDegrees, 1064f, 1026f)
            native.drawBitmap(ball, Rect(52, 150, 1230, 1154), RectF(892f, 878f, 1237f, 1172f), paint)
            native.restore()
            native.save()
            native.rotate(motion.pendantRotationDegrees, 766f, 742f)
            native.drawBitmap(pendant, 0f, 0f, paint)
            native.drawBitmap(pendantStar, 0f, 0f, starPaint)
            native.restore()
            for (i in stars.indices) {
                val star =
                    if (i ==
                        3
                    ) {
                        motion.pendantPoint(stars[i].x, stars[i].y)
                    } else {
                        motion.point(stars[i].x, stars[i].y)
                    }
                glowPaint.alpha = (brightness * 255f).toInt()
                native.save()
                native.translate(star.x, star.y)
                native.drawCircle(0f, 0f, 98f, glowPaint)
                native.restore()
            }
            native.restore()
        }
    }
}
