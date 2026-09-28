package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import java.util.Locale

private val IDLE_BREATH_FRAME_DURATIONS_MS =
    IntArray(24) { if (it == 23) 130 else 90 }

internal val RUN_FRAME_DURATIONS_MS =
    IntArray(24) { 50 }

enum class LunaAppearance(
    internal val assetName: String,
) {
    NORMAL("normal"),
    HAT("hat"),
    SUNGLASSES("sunglasses"),
}

private fun lunaAppearance(accessoryId: String?): LunaAppearance =
    when (accessoryId) {
        "accessory:luna_cap" -> LunaAppearance.HAT
        "accessory:luna_sunglasses" -> LunaAppearance.SUNGLASSES
        else -> LunaAppearance.NORMAL
    }

internal object LunaAnimationCache {
    @Volatile
    private var cachedFrames: List<ImageBitmap>? = null

    @Volatile
    private var cachedAppearance: LunaAppearance = LunaAppearance.NORMAL

    fun peek(): List<ImageBitmap>? = cachedFrames

    fun clear() {
        cachedFrames = null
        cachedAppearance = LunaAppearance.NORMAL
    }

    fun getOrLoadFrames(
        context: Context,
        appearance: LunaAppearance = LunaAppearance.NORMAL,
    ): List<ImageBitmap> {
        val current = cachedFrames
        if (current != null && cachedAppearance == appearance) return current
        return synchronized(this) {
            val syncCurrent = cachedFrames
            if (syncCurrent != null && cachedAppearance == appearance) return syncCurrent
            try {
                val assetManager = context.applicationContext?.assets ?: context.assets
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = 2 }
                val basePath =
                    "characters/luna/${appearance.assetName}/idle_breath/" +
                        "luna_idle_breath_${appearance.assetName}_%02d.png"
                val frames =
                    (1..24).map { i ->
                        val path = String.format(Locale.US, basePath, i)
                        assetManager.open(path).use { stream ->
                            BitmapFactory.decodeStream(stream, null, decodeOptions)!!.asImageBitmap()
                        }
                    }
                cachedAppearance = appearance
                cachedFrames = frames
                frames
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

internal object LunaRunAnimationCache {
    @Volatile
    private var cachedFrames: List<ImageBitmap>? = null

    @Volatile
    private var cachedAppearance: LunaAppearance = LunaAppearance.NORMAL

    fun peek(): List<ImageBitmap>? = cachedFrames

    fun clear() {
        cachedFrames = null
        cachedAppearance = LunaAppearance.NORMAL
    }

    fun getOrLoadFrames(
        context: Context,
        appearance: LunaAppearance = LunaAppearance.NORMAL,
    ): List<ImageBitmap> {
        val current = cachedFrames
        if (current != null && cachedAppearance == appearance) return current
        return synchronized(this) {
            val syncCurrent = cachedFrames
            if (syncCurrent != null && cachedAppearance == appearance) return syncCurrent
            try {
                val assetManager = context.applicationContext?.assets ?: context.assets
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = 2 }
                val basePath =
                    "characters/luna/${appearance.assetName}/run/" +
                        "luna_run_left_${appearance.assetName}_%02d.png"
                val frames =
                    (1..24).map { i ->
                        val path = String.format(Locale.US, basePath, i)
                        assetManager.open(path).use { stream ->
                            BitmapFactory.decodeStream(stream, null, decodeOptions)!!.asImageBitmap()
                        }
                    }
                cachedAppearance = appearance
                cachedFrames = frames
                frames
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

internal object LunaHungryAnimationCache {
    @Volatile
    private var cachedFrames: List<ImageBitmap>? = null

    @Volatile
    private var cachedAppearance: LunaAppearance = LunaAppearance.NORMAL

    fun peek(): List<ImageBitmap>? = cachedFrames

    fun clear() {
        cachedFrames = null
        cachedAppearance = LunaAppearance.NORMAL
    }

    fun getOrLoadFrames(
        context: Context,
        appearance: LunaAppearance = LunaAppearance.NORMAL,
    ): List<ImageBitmap> {
        val current = cachedFrames
        if (current != null && cachedAppearance == appearance) return current
        return synchronized(this) {
            val syncCurrent = cachedFrames
            if (syncCurrent != null && cachedAppearance == appearance) return syncCurrent
            try {
                val assetManager = context.applicationContext?.assets ?: context.assets
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = 2 }
                val basePath =
                    "characters/luna/${appearance.assetName}/hungry/" +
                        "luna_hungry_${appearance.assetName}_%02d.png"
                val frames =
                    (1..24).map { i ->
                        val path = String.format(Locale.US, basePath, i)
                        assetManager.open(path).use { stream ->
                            BitmapFactory.decodeStream(stream, null, decodeOptions)!!.asImageBitmap()
                        }
                    }
                cachedAppearance = appearance
                cachedFrames = frames
                frames
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

internal object LunaSickAnimationCache {
    @Volatile
    private var cachedFrames: List<ImageBitmap>? = null

    @Volatile
    private var cachedAppearance: LunaAppearance = LunaAppearance.NORMAL

    fun peek(): List<ImageBitmap>? = cachedFrames

    fun clear() {
        cachedFrames = null
        cachedAppearance = LunaAppearance.NORMAL
    }

    fun getOrLoadFrames(
        context: Context,
        appearance: LunaAppearance = LunaAppearance.NORMAL,
    ): List<ImageBitmap> {
        val current = cachedFrames
        if (current != null && cachedAppearance == appearance) return current
        return synchronized(this) {
            val syncCurrent = cachedFrames
            if (syncCurrent != null && cachedAppearance == appearance) return syncCurrent
            try {
                val assetManager = context.applicationContext?.assets ?: context.assets
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = 2 }
                val basePath =
                    "characters/luna/${appearance.assetName}/sick/" +
                        "luna_sick_${appearance.assetName}_%02d.png"
                val frames =
                    (1..24).map { i ->
                        val path = String.format(Locale.US, basePath, i)
                        assetManager.open(path).use { stream ->
                            BitmapFactory.decodeStream(stream, null, decodeOptions)!!.asImageBitmap()
                        }
                    }
                cachedAppearance = appearance
                cachedFrames = frames
                frames
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

internal enum class LunaActiveAnimation {
    IDLE,
    RUN,
    HUNGRY,
    SICK,
    NONE,
}

internal object LunaAnimationManager {
    fun retainOnly(active: LunaActiveAnimation) {
        val retainMotion = active == LunaActiveAnimation.IDLE || active == LunaActiveAnimation.RUN
        if (!retainMotion) {
            LunaAnimationCache.clear()
            LunaRunAnimationCache.clear()
        }
        if (active != LunaActiveAnimation.HUNGRY) LunaHungryAnimationCache.clear()
        if (active != LunaActiveAnimation.SICK) LunaSickAnimationCache.clear()
    }

    fun clearAll() {
        retainOnly(LunaActiveAnimation.NONE)
    }
}

enum class PetEmotion {
    IDLE,
    HAPPY,
    HUNGRY,
    SICK,
}

/**
 * Renders the selected character from external artwork and retains the historical Cream fallback.
 * [appearanceKey] accepts GOLDEN or CREAM without importing a domain model.
 */
@Composable
fun PetAvatar(
    modifier: Modifier = Modifier,
    appearanceKey: String = "GOLDEN",
    friendId: String = "friend:mobi",
    accessoryId: String? = null,
    outfitId: String? = null,
    backgroundId: String? = null,
    isAnimated: Boolean = true,
    emotion: PetEmotion = PetEmotion.IDLE,
    isMoving: Boolean = false,
    movingLeft: Boolean = true,
    vehicleWarning: Boolean = false,
    vehicleHungry: Boolean = false,
    artworkOverride: Int? = null,
) {
    val cat = friendId == "friend:luna"
    val cream = appearanceKey == "CREAM"
    val motionEnabled = isAnimated && LocalMobiMonMotionEnabled.current
    // Reduced motion keeps the gentle idle breath and drops travel animation only.
    val runEnabled = isAnimated && isMoving && LocalMobiMonMotionEnabled.current
    val description = stringResource(if (cat) R.string.mobimon_luna_description else R.string.mobimon_mobi_description)
    if (artworkOverride != null) {
        Box(modifier = modifier.size(120.dp).semantics { contentDescription = description }) {
            Image(
                painter = painterResource(artworkOverride),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        return
    }
    if (emotion == PetEmotion.HAPPY) {
        val happyAsset = CharacterArtwork.happy(friendId, accessoryId ?: outfitId)
        Box(modifier = modifier.size(120.dp).semantics { contentDescription = description }) {
            backgroundId?.let { CharacterArtwork.backgrounds[it] }?.let {
                CharacterAssetImage(it, Modifier.fillMaxSize())
            }
            CharacterAssetImage(happyAsset, Modifier.fillMaxSize())
        }
        return
    }
    val isSick = vehicleWarning || emotion == PetEmotion.SICK
    val isHungry = vehicleHungry || emotion == PetEmotion.HUNGRY
    val equippedAccessory = accessoryId ?: outfitId
    val equippedLook = CharacterArtwork.equippedLooks[equippedAccessory]
    val appearance = lunaAppearance(equippedAccessory)

    val activeLunaAnimation =
        when {
            !cat || !isAnimated -> LunaActiveAnimation.NONE
            isSick -> LunaActiveAnimation.SICK
            isHungry -> LunaActiveAnimation.HUNGRY
            runEnabled -> LunaActiveAnimation.RUN
            else -> LunaActiveAnimation.IDLE
        }

    LaunchedEffect(cat, activeLunaAnimation, appearance) {
        LunaAnimationManager.retainOnly(activeLunaAnimation)
    }

    if (!cream || cat) {
        Box(modifier = modifier.size(120.dp).semantics { contentDescription = description }) {
            backgroundId?.let { CharacterArtwork.backgrounds[it] }?.let {
                CharacterAssetImage(it, Modifier.fillMaxSize())
            }
            if (friendId == "friend:mobi") {
                val hasMobiIdleSprite =
                    equippedLook == null ||
                        equippedAccessory == "accessory:mobi_headphones" ||
                        equippedAccessory == "accessory:mobi_goggles"

                var mobiHopCompleting by remember { mutableStateOf(false) }

                LaunchedEffect(runEnabled) {
                    if (runEnabled) {
                        mobiHopCompleting = true
                    }
                }

                val showMobiRun = (runEnabled || mobiHopCompleting) && isAnimated

                when {
                    isSick || isHungry ->
                        MobiIdleBreathAnimation(
                            modifier = Modifier.fillMaxSize(),
                            accessoryId = equippedAccessory,
                            fallbackAsset = CharacterArtwork.preview(friendId, equippedAccessory),
                            vehicleWarning = isSick,
                            vehicleHungry = isHungry,
                            animateNormal = isAnimated && hasMobiIdleSprite,
                            motionEnabled = motionEnabled,
                        )
                    showMobiRun ->
                        MobiRunAnimation(
                            modifier = Modifier.fillMaxSize(),
                            movingLeft = movingLeft,
                            isMoving = runEnabled,
                            accessoryId = equippedAccessory,
                            fallbackAsset = CharacterArtwork.preview(friendId, equippedAccessory),
                            onHopFinished = {
                                mobiHopCompleting = false
                            },
                        )
                    else ->
                        MobiIdleBreathAnimation(
                            modifier = Modifier.fillMaxSize(),
                            accessoryId = equippedAccessory,
                            fallbackAsset = CharacterArtwork.preview(friendId, equippedAccessory),
                            vehicleWarning = isSick,
                            vehicleHungry = isHungry,
                            animateNormal = isAnimated && hasMobiIdleSprite,
                            motionEnabled = motionEnabled,
                        )
                }
            } else if (isAnimated && (friendId == "friend:luna")) {
                when {
                    isSick ->
                        LunaSickAnimation(
                            modifier = Modifier.fillMaxSize(),
                            fallbackAsset = CharacterArtwork.sick(friendId, equippedAccessory),
                            appearance = appearance,
                        )
                    isHungry ->
                        LunaHungryAnimation(
                            modifier = Modifier.fillMaxSize(),
                            fallbackAsset = CharacterArtwork.hungry(friendId, equippedAccessory),
                            appearance = appearance,
                        )
                    runEnabled ->
                        LunaRunAnimation(
                            modifier = Modifier.fillMaxSize(),
                            movingLeft = movingLeft,
                            fallbackAsset = CharacterArtwork.preview(friendId, equippedAccessory),
                            appearance = appearance,
                        )
                    else ->
                        LunaIdleBreathAnimation(
                            modifier = Modifier.fillMaxSize(),
                            fallbackAsset = CharacterArtwork.preview(friendId, equippedAccessory),
                            appearance = appearance,
                        )
                }
            } else {
                val asset =
                    when {
                        isSick -> CharacterArtwork.sick(friendId, equippedAccessory)
                        isHungry -> CharacterArtwork.hungry(friendId, equippedAccessory)
                        else -> CharacterArtwork.preview(friendId, equippedAccessory)
                    }
                CharacterAssetImage(asset, Modifier.fillMaxSize())
            }
        }
        return
    }
    val fur = Color(0xFFF2E4C8)
    val ear = Color(0xFFD4C29D)
    Canvas(modifier = modifier.size(120.dp).semantics { contentDescription = description }) {
        val radius = size.minDimension * 0.34f
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(ear, radius * 0.48f, center + Offset(-radius * 0.85f, -radius * 0.4f))
        drawCircle(ear, radius * 0.48f, center + Offset(radius * 0.85f, -radius * 0.4f))
        drawCircle(fur, radius, center)
        drawCircle(Color(0xFF51402C), radius * 0.1f, center + Offset(0f, radius * 0.25f))
    }
}

@Composable
fun LunaRunAnimation(
    modifier: Modifier = Modifier,
    movingLeft: Boolean = true,
    contentDescription: String? = null,
    fallbackAsset: CharacterAsset = CharacterArtwork.characters.getValue("friend:luna"),
    appearance: LunaAppearance = LunaAppearance.NORMAL,
) {
    if (!LocalMobiMonMotionEnabled.current) {
        CharacterAssetImage(fallbackAsset, modifier, contentDescription)
        return
    }
    val context = LocalContext.current
    val frames = remember(context, appearance) { LunaRunAnimationCache.getOrLoadFrames(context, appearance) }

    if (frames.isEmpty()) {
        CharacterAssetImage(
            asset = fallbackAsset,
            modifier = modifier,
            contentDescription = contentDescription,
        )
    } else {
        var currentFrameIndex by remember(frames) { mutableIntStateOf(0) }
        LaunchedEffect(frames) {
            var previousTime = withInfiniteAnimationFrameNanos { it }
            var elapsedNanos = 0L
            while (isActive) {
                val time = withInfiniteAnimationFrameNanos { it }
                elapsedNanos += (time - previousTime).coerceAtMost(100_000_000L)
                previousTime = time
                var nextFrame = currentFrameIndex
                while (elapsedNanos >= RUN_FRAME_DURATIONS_MS[nextFrame] * 1_000_000L) {
                    elapsedNanos -= RUN_FRAME_DURATIONS_MS[nextFrame] * 1_000_000L
                    nextFrame = (nextFrame + 1) % frames.size
                }
                currentFrameIndex = nextFrame
            }
        }
        val baseAsset = CharacterArtwork.characters.getValue("friend:luna")
        Box(
            modifier =
                modifier.graphicsLayer {
                    val flip = if (movingLeft) 1f else -1f
                    scaleX = baseAsset.visualScale * flip
                    scaleY = baseAsset.visualScale
                    translationX = size.width * baseAsset.translationXFraction * flip
                    translationY = size.height * baseAsset.translationYFraction
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = frames[currentFrameIndex],
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun LunaIdleBreathAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fallbackAsset: CharacterAsset = CharacterArtwork.characters.getValue("friend:luna"),
    appearance: LunaAppearance = LunaAppearance.NORMAL,
) {
    IdleBreathAnimation(
        LunaAnimationCache::getOrLoadFrames,
        appearance,
        true,
        modifier,
        contentDescription,
        fallbackAsset,
    )
}

@Composable
fun LunaHungryAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fallbackAsset: CharacterAsset = CharacterArtwork.hungry("friend:luna"),
    appearance: LunaAppearance = LunaAppearance.NORMAL,
) {
    IdleBreathAnimation(
        LunaHungryAnimationCache::getOrLoadFrames,
        appearance,
        true,
        modifier,
        contentDescription,
        fallbackAsset,
    )
}

@Composable
fun LunaSickAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fallbackAsset: CharacterAsset = CharacterArtwork.sick("friend:luna"),
    appearance: LunaAppearance = LunaAppearance.NORMAL,
) {
    IdleBreathAnimation(
        LunaSickAnimationCache::getOrLoadFrames,
        appearance,
        true,
        modifier,
        contentDescription,
        fallbackAsset,
    )
}

@Composable
private fun IdleBreathAnimation(
    loadFrames: (Context, LunaAppearance) -> List<ImageBitmap>,
    appearance: LunaAppearance,
    applyAssetTransform: Boolean,
    modifier: Modifier,
    contentDescription: String?,
    fallbackAsset: CharacterAsset,
) {
    val context = LocalContext.current
    val frames = remember(context, loadFrames, appearance) { loadFrames(context, appearance) }

    if (frames.isEmpty()) {
        CharacterAssetImage(
            asset = fallbackAsset,
            modifier = modifier,
            contentDescription = contentDescription,
        )
    } else {
        var currentFrameIndex by remember(frames) { mutableIntStateOf(0) }
        LaunchedEffect(frames) {
            var previousTime = withInfiniteAnimationFrameNanos { it }
            var elapsedNanos = 0L
            while (isActive) {
                val time = withInfiniteAnimationFrameNanos { it }
                // Resume from a stopped window without jumping through the whole breathing cycle.
                elapsedNanos += (time - previousTime).coerceAtMost(130_000_000L)
                previousTime = time
                var nextFrame = currentFrameIndex
                while (elapsedNanos >= IDLE_BREATH_FRAME_DURATIONS_MS[nextFrame] * 1_000_000L) {
                    elapsedNanos -= IDLE_BREATH_FRAME_DURATIONS_MS[nextFrame] * 1_000_000L
                    nextFrame = (nextFrame + 1) % frames.size
                }
                currentFrameIndex = nextFrame
            }
        }
        val baseAsset = CharacterArtwork.characters.getValue("friend:luna")
        Box(
            modifier =
                if (applyAssetTransform) {
                    modifier.graphicsLayer {
                        scaleX = baseAsset.visualScale
                        scaleY = baseAsset.visualScale
                        translationX = size.width * baseAsset.translationXFraction
                        translationY = size.height * baseAsset.translationYFraction
                    }
                } else {
                    modifier
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = frames[currentFrameIndex],
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** Preloads run animation sprite assets for [friendId] and [accessoryId] on background thread. */
suspend fun preloadPetRunSprite(
    context: Context,
    friendId: String,
    accessoryId: String? = null,
) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (friendId == "friend:mobi") {
            MobiRunSpriteCache.getOrLoad(context, accessoryId)
        } else if (friendId == "friend:luna") {
            val appearance = lunaAppearance(accessoryId)
            LunaRunAnimationCache.getOrLoadFrames(context, appearance)
        }
    }
}
