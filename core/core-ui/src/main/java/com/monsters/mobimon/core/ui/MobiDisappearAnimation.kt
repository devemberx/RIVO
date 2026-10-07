package com.monsters.mobimon.core.ui

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

internal const val MOBI_DEPARTURE_HANDOFF_NANOS = 160_000_000L
internal const val MOBI_DEPARTURE_DURATION_NANOS = MOBI_DEPARTURE_HANDOFF_NANOS + 1_600_000_000L

/** Retain the live idle while loading, then hand off to the size/ground-aligned exit. */
@Composable
internal fun MobiDisappearAnimation(
    modifier: Modifier,
    onFinished: () -> Unit,
    isDisappearing: Boolean,
    accessoryId: String?,
    idleContent: @Composable () -> Unit,
) {
    var slotSidePx by remember { mutableIntStateOf(0) }
    val requiredFrameSidePx = ceil(slotSidePx * 1.12f).toInt()
    val context = LocalContext.current.applicationContext
    val finished by rememberUpdatedState(onFinished)
    val appearanceName = mobiAppearanceName(accessoryId)
    var sheet by remember(context, appearanceName, isDisappearing, requiredFrameSidePx) {
        mutableStateOf<Pair<Boolean, ImageBitmap?>>(false to null)
    }
    LaunchedEffect(context, appearanceName, isDisappearing, requiredFrameSidePx) {
        sheet = false to null
        if (!isDisappearing || slotSidePx == 0) return@LaunchedEffect
        val bitmap =
            withContext(Dispatchers.IO) {
                MobiAnimationAtlas.load(context, MobiAtlasAction.DISAPPEAR, accessoryId, requiredFrameSidePx)
            }
        sheet = true to bitmap
    }
    val elapsed = remember(appearanceName) { mutableLongStateOf(0L) }
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
    Box(modifier.onSizeChanged { slotSidePx = minOf(it.width, it.height) }) {
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
