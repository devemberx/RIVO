package com.monsters.mobimon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.monsters.mobimon.R
import com.monsters.mobimon.core.ui.BackgroundLayer
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.core.ui.MobiMonFontFamily
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
internal fun StartupScene(
    layer: BackgroundLayer,
    time: () -> Float,
    still: Boolean,
) {
    val clip = remember { Path() }
    Image(
        painterResource(layer.frame.drawableRes),
        null,
        Modifier.fillMaxSize().testTag("startup-scene").drawWithContent {
            val reveal = startupEase((time() - 1750f) / 600f)
            if (still) {
                drawContent()
                drawRect(StartupBlue.copy(alpha = 1f - reveal))
            } else {
                clip.reset()
                val radius = hypot(size.width, size.height) * reveal
                clip.addOval(Rect(center, radius))
                clipPath(clip) { this@drawWithContent.drawContent() }
            }
            drawRect(MobiMonColors.background.copy(alpha = .36f * reveal))
        },
        contentScale = ContentScale.Crop,
        alignment = layer.alignment,
    )
}

@Composable
internal fun StartupFace(
    friendId: String,
    time: () -> Float,
    still: Boolean,
) {
    val face =
        when (friendId) {
            "friend:luna", "friend:runa" -> R.drawable.menu_runa_face
            "friend:las" -> R.drawable.menu_las_face
            else -> R.drawable.menu_mobi_face
        }
    val caption =
        when (friendId) {
            "friend:luna", "friend:runa" -> R.string.startup_meet_luna
            "friend:las" -> R.string.startup_meet_las
            else -> R.string.startup_meet_mobi
        }
    val orbit = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, still) {
        if (!still) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    orbit.snapTo(0f)
                    orbit.animateTo(1f, tween(1800, easing = LinearEasing))
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = (maxWidth.value / 2560f).coerceIn(.5f, 1.25f)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .graphicsLayer { alpha = startupEase((time() - 2450) / 360) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size((420 * scale).dp, (292 * scale).dp), contentAlignment = Alignment.TopCenter) {
                Image(
                    painterResource(face),
                    null,
                    Modifier
                        .size((288 * scale).dp, (224 * scale).dp)
                        .testTag("startup-face")
                        .graphicsLayer {
                            translationY =
                                if (still) 0f else sin(orbit.value * 2f * PI).toFloat() * 5f * scale * density
                        },
                    contentScale = ContentScale.Fit,
                )
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2, size.height * .81f)
                    val rx = size.width * .48f
                    val ry = size.height * .15f
                    val tint = MobiMonColors.accent
                    drawOval(
                        tint.copy(alpha = .15f),
                        c - Offset(rx, ry),
                        Size(rx * 2, ry * 2),
                        style = Stroke(1.5.dp.toPx()),
                    )
                    if (!still) {
                        val angle = orbit.value * 2 * PI.toFloat()
                        repeat(24) { i ->
                            val a = angle - 1.4f + i / 24f * 1.4f
                            val b = a + 1.4f / 24
                            drawLine(
                                tint.copy(alpha = i / 24f * .8f),
                                c + Offset(cos(a) * rx, sin(a) * ry),
                                c + Offset(cos(b) * rx, sin(b) * ry),
                                3.dp.toPx(),
                            )
                        }
                        drawCircle(tint, 4.dp.toPx(), c + Offset(cos(angle) * rx, sin(angle) * ry))
                    }
                }
            }
            Text(
                stringResource(caption),
                Modifier.padding(top = (30 * scale).dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = MobiMonColors.text,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = MobiMonFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = (44 * scale).sp,
                        lineHeight = (62 * scale).sp,
                    ),
                textAlign = TextAlign.Center,
            )
        }
    }
}
