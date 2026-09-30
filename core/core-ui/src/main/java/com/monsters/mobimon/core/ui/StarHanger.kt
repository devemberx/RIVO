package com.monsters.mobimon.core.ui

import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val HANGER_COLUMNS = 8
private const val HANGER_ROWS = 192
private const val HANGER_HEIGHT = 1536f
private const val HANGER_WIDTH = 1024f

/** Smooth only the chain links; the ribbon, star and moon remain rigid between joints. */
internal fun starHangerMesh(phase: Float?): FloatArray {
    val angles =
        if (phase == null) {
            FloatArray(4)
        } else {
            floatArrayOf(
                2.4f * sin(phase),
                2.4f * sin(phase) + 3.2f * sin(phase - 0.55f),
                2.4f * sin(phase) + 4.6f * sin(phase - 1.05f),
                2.4f * sin(phase) + 6f * sin(phase - 1.55f),
            ).map { it * PI.toFloat() / 180f }.toFloatArray()
        }
    val joints = floatArrayOf(520f, 1088f, 1384f)

    fun angleAt(y: Float): Float {
        var angle = angles[0]
        for (joint in joints.indices) {
            val t = ((y - joints[joint] + 16f) / 32f).coerceIn(0f, 1f)
            val blend = t * t * (3f - 2f * t)
            angle += (angles[joint + 1] - angles[joint]) * blend
        }
        return angle
    }
    val vertices = FloatArray((HANGER_COLUMNS + 1) * (HANGER_ROWS + 1) * 2)
    var centerX = HANGER_WIDTH / 2f
    var centerY = 0f
    val step = HANGER_HEIGHT / HANGER_ROWS
    for (row in 0..HANGER_ROWS) {
        val y = row * step
        if (row > 0) {
            val midpointAngle = angleAt(y - step / 2f)
            centerX -= sin(midpointAngle) * step
            centerY += cos(midpointAngle) * step
        }
        val angle = angleAt(y)
        for (column in 0..HANGER_COLUMNS) {
            val x = column * HANGER_WIDTH / HANGER_COLUMNS - HANGER_WIDTH / 2f
            val index = (row * (HANGER_COLUMNS + 1) + column) * 2
            vertices[index] = centerX + cos(angle) * x
            vertices[index + 1] = centerY + sin(angle) * x
        }
    }
    return vertices
}

/** Body and glow use one continuous mesh, so bending never opens gaps between segments. */
@Composable
fun StarHanger(
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    isAnimated: Boolean = true,
) {
    val animated = isAnimated && LocalMobiMonMotionEnabled.current
    val phase: Float?
    val glow: Float
    if (animated) {
        val transition = rememberInfiniteTransition(label = "star_hanger")
        val swingPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(tween(6400, easing = LinearEasing), RepeatMode.Restart),
            label = "star_hanger_joints",
        )
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "star_hanger_glow",
        )
        phase = swingPhase
        glow = pulse
    } else {
        phase = null
        glow = 0.45f
    }
    val resources = LocalContext.current.resources
    val body = remember(resources) { BitmapFactory.decodeResource(resources, R.drawable.star_hanger_body) }
    val light = remember(resources) { BitmapFactory.decodeResource(resources, R.drawable.star_hanger_glow) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val vertices = remember(phase) { starHangerMesh(phase) }
    Canvas(modifier.clipToBounds()) {
        val height = minOf(size.height * (if (centered) 0.92f else 0.45f), size.width * (if (centered) 1.2f else 0.6f))
        val scale = height / HANGER_HEIGHT
        val left = size.width * (if (centered) 0.5f else 0.75f) - HANGER_WIDTH * scale / 2f
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(left, 0f)
            native.scale(scale, scale)
            paint.alpha = 255
            native.drawBitmapMesh(body, HANGER_COLUMNS, HANGER_ROWS, vertices, 0, null, 0, paint)
            paint.alpha = (glow * 255).toInt()
            native.drawBitmapMesh(light, HANGER_COLUMNS, HANGER_ROWS, vertices, 0, null, 0, paint)
            native.restore()
        }
    }
}
