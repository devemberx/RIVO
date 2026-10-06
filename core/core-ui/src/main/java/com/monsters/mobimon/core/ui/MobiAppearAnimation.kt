package com.monsters.mobimon.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object MobiAppearTimeline {
    const val DURATION_NANOS = 1_400_000_000L
    private const val REFERENCE_DURATION_NANOS = 1_800_000_000L
    private val frameDurationsMs = IntArray(17) { 90 } + intArrayOf(50, 45, 40, 40, 35, 30, 30)
    private val frameEndsMs = frameDurationsMs.runningFold(0L) { sum, duration -> sum + duration }.drop(1)

    fun frameAt(elapsedNanos: Long): Float {
        val elapsedMs = referenceElapsedNanos(elapsedNanos) / 1_000_000L
        return frameEndsMs.indexOfFirst { elapsedMs < it }.let { if (it < 0) 23f else it.toFloat() }
    }

    fun idleBlendAt(elapsedNanos: Long): Float =
        ((referenceElapsedNanos(elapsedNanos) - 1_650_000_000L) / 150_000_000f).coerceIn(0f, 1f)

    // Rescale playback time once so every frame and the idle handoff retain their proportions.
    private fun referenceElapsedNanos(elapsedNanos: Long): Long =
        elapsedNanos.coerceIn(0L, DURATION_NANOS) * REFERENCE_DURATION_NANOS / DURATION_NANOS
}

/** One 1.4s entrance; the seven similar settling frames occupy only the final 210ms. */
@Composable
internal fun MobiAppearAnimation(
    modifier: Modifier,
    accessoryId: String?,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val finished by rememberUpdatedState(onFinished)
    val appearanceName = mobiAppearanceName(accessoryId)
    var sheet by remember(context, appearanceName) {
        mutableStateOf<Pair<Boolean, ImageBitmap?>>(false to null)
    }
    LaunchedEffect(context, appearanceName) {
        val image =
            withContext(Dispatchers.IO) {
                try {
                    context.assets
                        .open(
                            "characters/mobi/$appearanceName/appear/mobi_appear_${appearanceName}_sprite.png",
                        ).use {
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
        sheet = true to image
    }
    val elapsed = remember(appearanceName) { mutableLongStateOf(0L) }
    LaunchedEffect(sheet) {
        if (!sheet.first) return@LaunchedEffect
        if (sheet.second != null) {
            val start = withFrameNanos { it }
            while (elapsed.longValue < MobiAppearTimeline.DURATION_NANOS) {
                elapsed.longValue = withFrameNanos { it } - start
            }
        }
        finished()
    }
    val bitmap = sheet.second ?: return
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Measured from alpha bounds of the final entrance and first idle frames.
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = 1.2746781f
                    scaleY = 1.3563502f
                    translationX = size.width * -0.1584971f
                    translationY = size.height * -0.3144204f
                    alpha = 1f - MobiAppearTimeline.idleBlendAt(elapsed.longValue)
                    clip = false
                }.mobiSpriteFrames(
                    bitmap,
                    6,
                    4,
                    loop = false,
                    blendFrames = false,
                    filterQuality = FilterQuality.High,
                ) {
                    MobiAppearTimeline.frameAt(elapsed.longValue)
                },
        )
        // Keep the destination composed while loading, so the handoff cannot flash a fallback.
        NormalMobiIdleAnimation(
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = MobiAppearTimeline.idleBlendAt(elapsed.longValue)
                },
            accessoryId = accessoryId,
            animateFrames = false,
        )
    }
}
