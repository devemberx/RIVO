package com.monsters.mobimon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.R
import com.monsters.mobimon.core.ui.MobiMonColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class GatheringStar(
    val angle: Float,
    val radius: Float,
    val delay: Int,
    val duration: Int,
)

private val stars =
    listOf(
        GatheringStar(-.40f, 690f, 0, 820),
        GatheringStar(.71f, 625f, 42, 825),
        GatheringStar(1.66f, 580f, 90, 855),
        GatheringStar(2.80f, 700f, 25, 870),
        GatheringStar(3.85f, 610f, 120, 820),
        GatheringStar(4.87f, 680f, 66, 910),
    )

private fun GatheringStar.point(p: Float): Offset {
    val start = Offset(cos(angle) * radius, sin(angle) * radius * .5f)
    val c1 = Offset(cos(angle + .72f) * 520, sin(angle + .72f) * 380)
    val c2 = Offset(cos(angle + 1.25f) * 220, sin(angle + 1.25f) * 150)
    val u = 1 - p
    return start * (u * u * u) + c1 * (3 * u * u * p) + c2 * (3 * u * p * p)
}

@Composable
internal fun StartupStarlight(
    time: () -> Float,
    still: Boolean,
    modifier: Modifier = Modifier,
) {
    val starPath =
        remember {
            Path().apply {
                moveTo(0f, -28f)
                cubicTo(5f, -7f, 7f, -5f, 28f, 0f)
                cubicTo(7f, 5f, 5f, 7f, 0f, 28f)
                cubicTo(-5f, 7f, -7f, 5f, -28f, 0f)
                cubicTo(-7f, -5f, -5f, -7f, 0f, -28f)
                close()
            }
        }
    val trail = remember { Path() }
    val view = LocalView.current
    BoxWithConstraints(modifier) {
        // Match the native adaptive splash circle before expanding into the sky.
        val iconSize = minOf(192.dp, maxWidth * .4f, maxHeight * .5f)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.mobimon_launcher),
                null,
                Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        val expansion = startupEase((time() - 100) / 600)
                        alpha = 1f - startupEase((time() - 180) / 430)
                        scaleX = if (still) 1f else 1f + 1.9f * expansion
                        scaleY = scaleX
                        // Native splash centers on the whole window; content excludes the system bars.
                        val bars =
                            view.rootWindowInsets?.getInsetsIgnoringVisibility(
                                android.view.WindowInsets.Type
                                    .systemBars(),
                            )
                        translationY =
                            if (still) 0f else ((bars?.bottom ?: 0) - (bars?.top ?: 0)) / 2f * (1f - expansion)
                    }.clip(CircleShape)
                    .background(MobiMonColors.background),
                contentScale = ContentScale.Fit,
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = time()
            val release = startupEase((t - 1750) / 400)
            val intro = startupEase((t - 180) / 400) * (1f - release)
            if (intro <= 0f) return@Canvas
            val factor = min(size.width / 2560f, size.height / 1184f)
            translate(center.x, center.y) {
                scale(factor, pivot = Offset.Zero) {
                    var arrived = 0f
                    stars.forEachIndexed { index, star ->
                        val p = startupSmooth((t - 650 - star.delay) / star.duration)
                        arrived += startupSmooth((p - .72f) / .28f) / stars.size
                        val position = star.point(if (still) 0f else p)
                        if (!still && p > 0f && p < 1f) {
                            trail.reset()
                            val tail = star.point((p - .25f).coerceAtLeast(0f))
                            trail.moveTo(tail.x, tail.y)
                            for (k in 1..20) {
                                val pt = star.point((p - .25f + k / 20f * .25f).coerceAtLeast(0f))
                                trail.lineTo(pt.x, pt.y)
                            }
                            drawPath(
                                trail,
                                Brush.linearGradient(
                                    listOf(Color.Transparent, MobiMonColors.accent, MobiMonColors.warning),
                                    tail,
                                    position + Offset(.001f, .001f),
                                ),
                                alpha = sin(p * PI).toFloat() * .5f * intro,
                                style = Stroke(4f),
                            )
                        }
                        val alpha = (1f - startupSmooth((p - .9f) / .1f)) * intro
                        translate(position.x, position.y) {
                            rotate(if (still) 0f else p * 16f, pivot = Offset.Zero) {
                                scale((1 - .5f * p) * if (index % 2 == 0) 1f else .72f, pivot = Offset.Zero) {
                                    drawPath(starPath, MobiMonColors.warning.copy(alpha = alpha))
                                }
                            }
                        }
                    }
                    gatheringCore(starPath, arrived, release, intro, still)
                }
            }
        }
    }
}

private fun DrawScope.gatheringCore(
    path: Path,
    arrived: Float,
    release: Float,
    intro: Float,
    still: Boolean,
) {
    val radius = 160f + 200f * arrived + 450f * release
    drawCircle(
        Brush.radialGradient(
            listOf(MobiMonColors.warning.copy(alpha = (.18f + .45f * arrived) * intro), Color.Transparent),
            center = Offset.Zero,
            radius = radius,
        ),
        radius,
        Offset.Zero,
    )
    scale(if (still) 1f else .25f + .8f * arrived + 1.8f * release, pivot = Offset.Zero) {
        drawPath(path, MobiMonColors.text.copy(alpha = startupSmooth(arrived * 1.5f) * intro))
    }
}
