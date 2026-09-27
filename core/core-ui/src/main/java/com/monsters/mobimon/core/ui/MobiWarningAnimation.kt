package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal object MobiCollapsedTimeline {
    const val COLUMNS = 6
    const val ROWS = 4
    const val FRAME_COUNT = COLUMNS * ROWS
    const val CYCLE_MS = 4_050L

    fun frameAt(elapsedNanos: Long): Int {
        val cycleNanos = CYCLE_MS * 1_000_000L
        val phase = elapsedNanos.coerceAtLeast(0L) % cycleNanos
        val progress = phase.toDouble() / cycleNanos
        return (progress * FRAME_COUNT).toInt().coerceIn(0, FRAME_COUNT - 1)
    }

    fun blendAt(
        elapsedNanos: Long,
        frame: Int = frameAt(elapsedNanos),
    ): Float {
        val cycleNanos = CYCLE_MS * 1_000_000L
        val phase = elapsedNanos.coerceAtLeast(0L) % cycleNanos
        val progress = phase.toDouble() / cycleNanos
        val frameProgress = (progress * FRAME_COUNT) - frame
        return frameProgress.toFloat().coerceIn(0f, 1f)
    }
}

internal object MobiDizzyStarsTimeline {
    const val COLUMNS = 6
    const val ROWS = 2
    const val FRAME_COUNT = COLUMNS * ROWS
    const val CYCLE_MS = 1_538L

    fun frameAt(elapsedNanos: Long): Int {
        val cycleNanos = CYCLE_MS * 1_000_000L
        val phase = elapsedNanos.coerceAtLeast(0L) % cycleNanos
        val progress = phase.toDouble() / cycleNanos
        return (progress * FRAME_COUNT).toInt().coerceIn(0, FRAME_COUNT - 1)
    }
}

/** Opacity only: toggling warning during a fade continues from the current opacity. */
internal class MobiWarningBlend {
    val opacity = Animatable(0f)

    suspend fun target(
        warning: Boolean,
        motionEnabled: Boolean,
    ) {
        val end = if (warning) 1f else 0f
        if (motionEnabled) opacity.animateTo(end, tween(200, easing = LinearEasing)) else opacity.snapTo(end)
    }
}

internal class MobiHungryBlend(
    initialValue: Float = 0f,
) {
    val opacity = Animatable(initialValue)

    suspend fun target(
        hungry: Boolean,
        motionEnabled: Boolean,
    ) {
        val end = if (hungry) 1f else 0f
        if (motionEnabled) opacity.animateTo(end, tween(200, easing = LinearEasing)) else opacity.snapTo(end)
    }
}

internal object MobiCollapsedSpriteCache {
    const val CELL = 408
    const val LOGICAL_CELL = 256
    const val ASSET_PATH = "characters/mobi/unhealthy/mobi_collapsed_sprite.png"

    @Volatile private var cached: ImageBitmap? = null

    fun peek(): ImageBitmap? = cached

    fun getOrLoad(context: Context): ImageBitmap? {
        cached?.let { return it }
        return synchronized(this) {
            cached?.let { return it }
            val assets = context.applicationContext.assets
            val options = BitmapFactory.Options().apply { inScaled = false }
            try {
                assets.open(ASSET_PATH).use { stream ->
                    val bitmap = requireNotNull(BitmapFactory.decodeStream(stream, null, options))
                    require(
                        bitmap.width == CELL * MobiCollapsedTimeline.COLUMNS &&
                            bitmap.height == CELL * MobiCollapsedTimeline.ROWS,
                    )
                    bitmap.asImageBitmap().also { cached = it }
                }
            } catch (_: java.io.IOException) {
                null
            }
        }
    }
}

internal object MobiDizzyStarsSpriteCache {
    const val CELL = 408
    const val ASSET_PATH = "characters/mobi/unhealthy/mobi_dizzy_stars_sprite.png"

    @Volatile private var cached: ImageBitmap? = null

    fun peek(): ImageBitmap? = cached

    fun getOrLoad(context: Context): ImageBitmap? {
        cached?.let { return it }
        return synchronized(this) {
            cached?.let { return it }
            val assets = context.applicationContext.assets
            val options = BitmapFactory.Options().apply { inScaled = false }
            try {
                assets.open(ASSET_PATH).use { stream ->
                    val bitmap = requireNotNull(BitmapFactory.decodeStream(stream, null, options))
                    require(
                        bitmap.width == CELL * MobiDizzyStarsTimeline.COLUMNS &&
                            bitmap.height == CELL * MobiDizzyStarsTimeline.ROWS,
                    )
                    bitmap.asImageBitmap().also { cached = it }
                }
            } catch (_: java.io.IOException) {
                null
            }
        }
    }
}

/** Normal and collapsed idle share one fixed layout slot and crossfade only. */
@Composable
fun MobiIdleBreathAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    accessoryId: String? = null,
    fallbackAsset: CharacterAsset = CharacterArtwork.characters.getValue("friend:mobi"),
    vehicleWarning: Boolean = false,
    vehicleHungry: Boolean = false,
    animateNormal: Boolean = true,
    motionEnabled: Boolean = LocalMobiMonMotionEnabled.current,
) {
    val context = LocalContext.current.applicationContext
    val blend = remember { MobiWarningBlend() }
    val hungryBlend = remember { MobiHungryBlend(if (vehicleHungry) 1f else 0f) }
    val enabled = motionEnabled && LocalMobiMonMotionEnabled.current
    val sprite by produceState<ImageBitmap?>(initialValue = MobiCollapsedSpriteCache.peek(), context, vehicleWarning) {
        if (vehicleWarning &&
            value == null
        ) {
            value = withContext(Dispatchers.IO) { MobiCollapsedSpriteCache.getOrLoad(context) }
        }
    }
    val starsSprite by produceState<ImageBitmap?>(
        initialValue = MobiDizzyStarsSpriteCache.peek(),
        context,
        vehicleWarning,
    ) {
        if (vehicleWarning &&
            value == null
        ) {
            value = withContext(Dispatchers.IO) { MobiDizzyStarsSpriteCache.getOrLoad(context) }
        }
    }
    LaunchedEffect(vehicleWarning, enabled, sprite) {
        if (sprite != null) blend.target(vehicleWarning, enabled)
    }
    LaunchedEffect(vehicleHungry, enabled) {
        hungryBlend.target(vehicleHungry, enabled)
    }
    val showNormal by remember { derivedStateOf { blend.opacity.value < 1f } }
    val showCollapsed by remember { derivedStateOf { blend.opacity.value > 0f } }
    val showIdle by remember { derivedStateOf { hungryBlend.opacity.value < 1f } }
    val showHungry by remember { derivedStateOf { hungryBlend.opacity.value > 0f } }
    BoxWithConstraints(
        modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (showNormal) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - blend.opacity.value }) {
                if (showIdle) {
                    Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - hungryBlend.opacity.value }) {
                        if (animateNormal) {
                            NormalMobiIdleAnimation(
                                modifier = Modifier.matchParentSize(),
                                contentDescription = null,
                                accessoryId = accessoryId,
                                fallbackAsset = fallbackAsset,
                            )
                        } else {
                            CharacterAssetImage(fallbackAsset, Modifier.matchParentSize(), null)
                        }
                    }
                }
                if (showHungry) {
                    Box(Modifier.matchParentSize().graphicsLayer { alpha = hungryBlend.opacity.value }) {
                        if (animateNormal) {
                            NormalMobiHungryAnimation(
                                modifier = Modifier.matchParentSize(),
                                contentDescription = null,
                                fallbackAsset = CharacterArtwork.hungry("friend:mobi"),
                            )
                        } else {
                            CharacterAssetImage(
                                CharacterArtwork.hungry("friend:mobi"),
                                Modifier.matchParentSize(),
                                null,
                            )
                        }
                    }
                }
            }
        }
        val sheet = sprite
        if (showCollapsed && sheet != null) {
            val elapsed = remember { mutableLongStateOf(0L) }
            LaunchedEffect(Unit) {
                elapsed.longValue = 0L
                val origin = withInfiniteAnimationFrameNanos { it }
                while (isActive) elapsed.longValue = withInfiniteAnimationFrameNanos { it } - origin
            }
            val extent =
                minOf(maxWidth, maxHeight) * MobiCollapsedSpriteCache.CELL / MobiCollapsedSpriteCache.LOGICAL_CELL
            Box(
                Modifier
                    .requiredSize(extent)
                    .graphicsLayer {
                        alpha = blend.opacity.value
                        translationY =
                            size.minDimension * MobiCollapsedSpriteCache.LOGICAL_CELL / MobiCollapsedSpriteCache.CELL *
                            fallbackAsset.translationYFraction + size.minDimension * 0.04f
                        compositingStrategy = CompositingStrategy.Offscreen
                        clip = false
                    }.mobiSpriteFrames(
                        sheet,
                        MobiCollapsedTimeline.COLUMNS,
                        MobiCollapsedTimeline.ROWS,
                        blendFrames = false,
                    ) {
                        if (enabled) {
                            val time = elapsed.longValue
                            val frame = MobiCollapsedTimeline.frameAt(time)
                            frame + MobiCollapsedTimeline.blendAt(time, frame)
                        } else {
                            0f
                        }
                    },
            )
            val stars = starsSprite
            if (stars != null) {
                val starsExtent = extent * 0.45f
                Box(
                    Modifier
                        .requiredSize(starsExtent)
                        .graphicsLayer {
                            alpha = blend.opacity.value
                            translationX = size.width * 0.05f
                            translationY = -size.height * 0.25f
                            compositingStrategy = CompositingStrategy.Offscreen
                            clip = false
                        }.mobiSpriteFrames(
                            stars,
                            MobiDizzyStarsTimeline.COLUMNS,
                            MobiDizzyStarsTimeline.ROWS,
                            blendFrames = false,
                        ) {
                            if (enabled) {
                                val time = elapsed.longValue
                                MobiDizzyStarsTimeline.frameAt(time).toFloat()
                            } else {
                                0f
                            }
                        },
                )
            }
        }
    }
}
