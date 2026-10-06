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

internal object MobiCollapsedSpriteCache {
    const val CELL = 408
    const val LOGICAL_CELL = 256
    const val DEFAULT_ASSET_PATH = "characters/mobi/normal/sick/mobi_sick_normal.webp"

    private fun assetPathFor(accessoryId: String?): String =
        when (accessoryId) {
            "accessory:mobi_headphones" ->
                "characters/mobi/headphones/sick/mobi_sick_headphones.webp"
            "accessory:mobi_goggles" ->
                "characters/mobi/goggles/sick/mobi_sick_goggles.webp"
            else -> DEFAULT_ASSET_PATH
        }

    private data class Entry(
        val accessoryId: String?,
        val image: ImageBitmap,
    )

    @Volatile private var entry: Entry? = null

    fun peek(accessoryId: String? = null): ImageBitmap? = entry?.takeIf { it.accessoryId == accessoryId }?.image

    @Synchronized
    fun clear() {
        entry = null
    }

    fun getOrLoad(
        context: Context,
        accessoryId: String? = null,
    ): ImageBitmap? {
        peek(accessoryId)?.let { return it }
        return synchronized(this) {
            peek(accessoryId)?.let { return it }
            val assets = context.applicationContext.assets
            val assetPath = assetPathFor(accessoryId)
            val sourceSize = MobiSickArtworkSpec.forAccessory(accessoryId).sourceSize
            val options = BitmapFactory.Options().apply { inScaled = false }
            try {
                assets.open(assetPath).use { stream ->
                    val bitmap = requireNotNull(BitmapFactory.decodeStream(stream, null, options))
                    require(
                        bitmap.width == sourceSize && bitmap.height == sourceSize,
                    )
                    bitmap.asImageBitmap().also {
                        entry = Entry(accessoryId, it)
                    }
                }
            } catch (_: java.io.IOException) {
                null
            }
        }
    }
}

internal object MobiDizzyStarsSpriteCache {
    const val CELL = 1254
    const val ASSET_PATH = "characters/mobi/normal/sick/mobi_sick_star.webp"

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
                        bitmap.width == CELL && bitmap.height == CELL,
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
    animateSick: Boolean = true,
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
            if (vehicleWarning && sprite != null && starsSprite != null) {
                CompanionStatus.SICK
            } else if (vehicleHungry) {
                CompanionStatus.HUNGRY
            } else {
                CompanionStatus.NORMAL
            },
        )
    }
    // Keep the outgoing pose visible until the warning sprite can be drawn.
    val visibleState =
        if (requestedState == CompanionStatus.SICK &&
            (sprite == null || starsSprite == null)
        ) {
            lastReadyState
        } else {
            requestedState
        }
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
                    val stars = starsSprite
                    if (sheet != null && stars != null) {
                        Box(Modifier.fillMaxSize().testTag("mobi-sick-layer"), contentAlignment = Alignment.Center) {
                            val elapsed = remember { mutableLongStateOf(0L) }
                            LaunchedEffect(animateSick) {
                                elapsed.longValue = 0L
                                if (!animateSick) return@LaunchedEffect
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
                                            fallbackAsset.translationYFraction
                                        compositingStrategy = CompositingStrategy.Offscreen
                                        clip = false
                                    }.mobiSickArtwork(sheet, stars, MobiSickArtworkSpec.forAccessory(accessoryId)) {
                                        if (animateSick) elapsed.longValue else 0L
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
