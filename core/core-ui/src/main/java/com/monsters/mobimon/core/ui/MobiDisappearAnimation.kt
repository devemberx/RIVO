package com.monsters.mobimon.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One shot at 15fps; the last two atlas cells are fully transparent. */
@Composable
internal fun MobiDisappearAnimation(
    modifier: Modifier,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val finished by rememberUpdatedState(onFinished)
    val sheet by produceState<Pair<Boolean, ImageBitmap?>>(false to null, context) {
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
    LaunchedEffect(sheet) {
        if (!sheet.first) return@LaunchedEffect
        if (sheet.second != null) {
            val start = withFrameNanos { it }
            while (elapsed.longValue < 1_600_000_000L) {
                elapsed.longValue = withFrameNanos { it } - start
            }
        }
        finished()
    }
    val bitmap = sheet.second
    if (bitmap != null) {
        Box(
            modifier.mobiSpriteFrames(bitmap, 6, 4, loop = false, blendFrames = false) {
                (elapsed.longValue / (1_000_000_000.0 / 15)).toInt().coerceAtMost(23).toFloat()
            },
        ) {
            // Frames 2–6: surprise, before the burrowing effect starts.
            if (elapsed.longValue in 66_666_667L until 400_000_000L) {
                Text(
                    text = stringResource(R.string.mobimon_departure_surprise),
                    color = Color(0xFF132238),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 110.dp, y = (-4).dp)
                            .requiredWidth(120.dp)
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
        }
    } else if (!sheet.first) {
        CharacterAssetImage(CharacterArtwork.preview("friend:mobi", null), modifier)
    }
}
