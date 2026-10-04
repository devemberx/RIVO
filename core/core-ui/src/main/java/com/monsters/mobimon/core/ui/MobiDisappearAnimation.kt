package com.monsters.mobimon.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MOBI_DEPARTURE_HANDOFF_NANOS = 160_000_000L
internal const val MOBI_DEPARTURE_DURATION_NANOS = MOBI_DEPARTURE_HANDOFF_NANOS + 1_600_000_000L

/** Retain the live idle while loading, then hand off to the size/ground-aligned exit. */
@Composable
internal fun MobiDisappearAnimation(
    modifier: Modifier,
    onFinished: () -> Unit,
    isDisappearing: Boolean,
    idleContent: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val finished by rememberUpdatedState(onFinished)
    val sheet by produceState<Pair<Boolean, ImageBitmap?>>(false to null, context, isDisappearing) {
        value = false to null
        if (!isDisappearing) return@produceState
        val bitmap =
            withContext(Dispatchers.IO) {
                try {
                    context.assets.open("characters/mobi/normal/disappear/mobi_disappear_normal_sprite.png").use {
                        val options =
                            BitmapFactory.Options().apply {
                                inSampleSize = 2
                                inScaled = false
                            }
                        BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                    }
                } catch (_: java.io.IOException) {
                    null
                }
            }
        value = true to bitmap
    }
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isDisappearing, sheet) {
        elapsed.longValue = 0L
        if (!isDisappearing || !sheet.first) return@LaunchedEffect
        if (sheet.second != null) {
            val start = withFrameNanos { it }
            while (elapsed.longValue < MOBI_DEPARTURE_DURATION_NANOS) {
                elapsed.longValue = withFrameNanos { it } - start
            }
        }
        finished()
    }
    val bitmap = sheet.second
    val handoff =
        if (isDisappearing && bitmap != null) {
            (elapsed.longValue.toFloat() / MOBI_DEPARTURE_HANDOFF_NANOS).coerceIn(0f, 1f)
        } else {
            0f
        }
    Box(modifier) {
        if (handoff < 1f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - handoff }) { idleContent() }
        }
        if (isDisappearing && bitmap != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag("mobi-departure-sprite")
                    .graphicsLayer {
                        // Uniform correction for the exit atlas's extra padding. Never animate scale down.
                        scaleX = 1.12f
                        scaleY = 1.12f
                        transformOrigin = TransformOrigin(635.5f / 1254f, 1170f / 1254f)
                        translationX = size.minDimension * (-14f / 1254f)
                        translationY = size.minDimension * (-48.24f / 1254f)
                        alpha = handoff
                        clip = false
                    }.mobiSpriteFrames(bitmap, 6, 4, loop = false, blendFrames = false) {
                        val playback = (elapsed.longValue - MOBI_DEPARTURE_HANDOFF_NANOS).coerceAtLeast(0L)
                        (playback / (1_000_000_000.0 / 15)).toInt().coerceAtMost(23).toFloat()
                    },
            )
            val playback = elapsed.longValue - MOBI_DEPARTURE_HANDOFF_NANOS
            if (playback in DEPARTURE_SURPRISE_NANOS) {
                DepartureSurpriseBubble(Modifier.align(Alignment.TopStart))
            }
        }
    }
}

/** Shared departure bubble window, measured from each character's sprite playback start. */
internal val DEPARTURE_SURPRISE_NANOS = 66_666_667L until 1_200_000_000L

/** Speech bubble beside the companion's head, aligned to the top start of its avatar slot. */
@Composable
internal fun DepartureSurpriseBubble(
    modifier: Modifier,
    messageRes: Int = R.string.mobimon_departure_surprise,
    width: Dp = 120.dp,
    offsetX: Dp = 110.dp,
) {
    Text(
        text = stringResource(messageRes),
        color = Color(0xFF132238),
        fontSize = 18.sp,
        lineHeight = 24.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier =
            modifier
                .offset(x = offsetX, y = (-4).dp)
                .requiredWidth(width)
                .background(
                    Color(0xFFFCFBF9),
                    SpeechBubbleShape(
                        cornerRadius = 12.dp,
                        tailWidth = 12.dp,
                        tailHeight = 12.dp,
                        tailOffsetYFromBottom = 8.dp,
                    ),
                ).padding(start = 22.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
    )
}
