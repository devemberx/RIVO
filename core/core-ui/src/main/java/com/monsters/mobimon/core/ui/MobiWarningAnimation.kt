package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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

internal object MobiCollapsedSpriteCache {
    const val CELL = 408
    const val LOGICAL_CELL = 256
    const val DEFAULT_ASSET_PATH = "characters/mobi/normal/sick/mobi_sick_normal_collapsed_sprite.png"

    private fun assetPathFor(accessoryId: String?): String =
        when (accessoryId) {
            "accessory:mobi_headphones" ->
                "characters/mobi/headphones/sick/mobi_sick_headphones_collapsed_sprite.png"
            "accessory:mobi_goggles" ->
                "characters/mobi/goggles/sick/mobi_sick_goggles_collapsed_sprite.png"
            else -> DEFAULT_ASSET_PATH
        }

    @Volatile private var cached: ImageBitmap? = null

    @Volatile private var cachedAccessoryId: String? = null

    fun peek(accessoryId: String? = null): ImageBitmap? = if (cachedAccessoryId == accessoryId) cached else null

    fun clear() {
        cached = null
        cachedAccessoryId = null
    }

    fun getOrLoad(
        context: Context,
        accessoryId: String? = null,
    ): ImageBitmap? {
        val current = cached
        if (current != null && cachedAccessoryId == accessoryId) return current
        return synchronized(this) {
            val syncCurrent = cached
            if (syncCurrent != null && cachedAccessoryId == accessoryId) return syncCurrent
            val assets = context.applicationContext.assets
            val assetPath = assetPathFor(accessoryId)
            val options = BitmapFactory.Options().apply { inScaled = false }
            try {
                assets.open(assetPath).use { stream ->
                    val bitmap = requireNotNull(BitmapFactory.decodeStream(stream, null, options))
                    require(
                        bitmap.width == CELL * MobiCollapsedTimeline.COLUMNS &&
                            bitmap.height == CELL * MobiCollapsedTimeline.ROWS,
                    )
                    bitmap.asImageBitmap().also {
                        cachedAccessoryId = accessoryId
                        cached = it
                    }
                }
            } catch (_: java.io.IOException) {
                null
            }
        }
    }
}

internal object MobiDizzyStarsSpriteCache {
    const val CELL = 408
    const val ASSET_PATH = "characters/mobi/normal/sick/mobi_sick_normal_stars_sprite.png"

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

/** Vehicle-status poses crossfade in one fixed layout slot. */
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
    val enabled = motionEnabled && LocalMobiMonMotionEnabled.current
    // A new accessory must not inherit the previous producer's loaded warning sprite.
    val sprite by key(accessoryId) {
        produceState<ImageBitmap?>(
            initialValue = MobiCollapsedSpriteCache.peek(accessoryId),
            context,
            vehicleWarning,
        ) {
            if (vehicleWarning) {
                value = withContext(Dispatchers.IO) { MobiCollapsedSpriteCache.getOrLoad(context, accessoryId) }
            }
        }
    }
    val starsSprite by produceState<ImageBitmap?>(
        initialValue = MobiDizzyStarsSpriteCache.peek(),
        context,
        vehicleWarning,
    ) {
        if (vehicleWarning && value == null) {
            value = withContext(Dispatchers.IO) { MobiDizzyStarsSpriteCache.getOrLoad(context) }
        }
    }
    val requestedState =
        when {
            vehicleWarning -> CompanionStatus.SICK
            vehicleHungry -> CompanionStatus.HUNGRY
            else -> CompanionStatus.NORMAL
        }
    var lastReadyState by remember(accessoryId) {
        mutableStateOf(
            if (vehicleWarning && sprite != null) {
                CompanionStatus.SICK
            } else if (vehicleHungry) {
                CompanionStatus.HUNGRY
            } else {
                CompanionStatus.NORMAL
            },
        )
    }
    // Keep the outgoing pose visible until the warning sprite can be drawn.
    val visibleState = if (requestedState == CompanionStatus.SICK && sprite == null) lastReadyState else requestedState
    LaunchedEffect(visibleState) { lastReadyState = visibleState }

    BoxWithConstraints(
        modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val extent =
            minOf(maxWidth, maxHeight) *
                MobiCollapsedSpriteCache.CELL / MobiCollapsedSpriteCache.LOGICAL_CELL
        CompanionStatusCrossfade(
            state = visibleState,
            motionEnabled = enabled,
            modifier = Modifier.matchParentSize(),
        ) { state ->
            when (state) {
                CompanionStatus.NORMAL ->
                    Box(Modifier.fillMaxSize().testTag("mobi-normal-layer")) {
                        NormalMobiIdleAnimation(
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = null,
                            accessoryId = accessoryId,
                            fallbackAsset = fallbackAsset,
                            animateFrames = animateNormal,
                        )
                    }
                CompanionStatus.HUNGRY ->
                    Box(Modifier.fillMaxSize().testTag("mobi-hungry-layer")) {
                        NormalMobiHungryAnimation(
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = null,
                            accessoryId = accessoryId,
                            fallbackAsset = CharacterArtwork.preview("friend:mobi", accessoryId),
                            animateFrames = animateNormal && enabled,
                        )
                    }
                CompanionStatus.SICK -> {
                    val sheet = sprite
                    if (sheet != null) {
                        Box(Modifier.fillMaxSize().testTag("mobi-sick-layer"), contentAlignment = Alignment.Center) {
                            val elapsed = remember { mutableLongStateOf(0L) }
                            LaunchedEffect(Unit) {
                                elapsed.longValue = 0L
                                val origin = withInfiniteAnimationFrameNanos { it }
                                while (isActive) elapsed.longValue = withInfiniteAnimationFrameNanos { it } - origin
                            }
                            Box(
                                Modifier
                                    .requiredSize(extent)
                                    .graphicsLayer {
                                        translationY =
                                            size.minDimension * MobiCollapsedSpriteCache.LOGICAL_CELL /
                                            MobiCollapsedSpriteCache.CELL *
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
            }
        }
    }
}
