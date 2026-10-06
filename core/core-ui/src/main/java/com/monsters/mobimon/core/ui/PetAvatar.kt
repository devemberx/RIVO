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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

private val IDLE_BREATH_FRAME_DURATIONS_MS =
    IntArray(24) { if (it == 23) 130 else 90 }

internal val RUN_FRAME_DURATIONS_MS =
    IntArray(24) { 50 }

// Luna sick art ends at y=1048/1254; this lowers it onto Mobi's collapsed baseline (0.926 of the slot).
internal const val LUNA_SICK_TRANSLATION_Y_FRACTION = 0.134f

// Luna hungry body sits ~77px/1254 left of idle; this recenters it on the idle body.
internal const val LUNA_HUNGRY_TRANSLATION_X_FRACTION = 0.053f

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

/** The first sprite frame is drawn before the remaining animation frames finish decoding. */
internal object LunaFirstFrameCache {
    private val frames = mutableMapOf<Pair<LunaActiveAnimation, LunaAppearance>, ImageBitmap>()

    fun getOrLoad(
        context: Context,
        animation: LunaActiveAnimation,
        appearance: LunaAppearance,
    ): ImageBitmap? =
        synchronized(this) {
            val key = animation to appearance
            frames[key]?.let { return@synchronized it }
            val assetName = appearance.assetName
            val fileName =
                when (animation) {
                    LunaActiveAnimation.IDLE -> "$assetName.webp"
                    LunaActiveAnimation.RUN -> "run/luna_run_left_${assetName}_01.png"
                    LunaActiveAnimation.HUNGRY -> "hungry/luna_hungry_${assetName}_01.png"
                    LunaActiveAnimation.SICK -> "sick/luna_sick_${assetName}_01.png"
                    LunaActiveAnimation.NONE -> return@synchronized null
                }
            try {
                val assets = context.applicationContext?.assets ?: context.assets
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val path =
                    if (animation == LunaActiveAnimation.IDLE) {
                        "characters/luna/idle_layers/$assetName.webp"
                    } else {
                        "characters/luna/$assetName/$fileName"
                    }
                assets
                    .open(path)
                    .use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
                    }?.also { frames[key] = it }
            } catch (_: Exception) {
                null
            }
        }
}

internal object LunaAnimationManager {
    fun retainOnly(active: LunaActiveAnimation) {
        val retainMotion = active == LunaActiveAnimation.IDLE || active == LunaActiveAnimation.RUN
        if (!retainMotion) {
            LunaIdleArtworkCache.clear()
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
    PetAvatar(
        modifier = modifier,
        appearanceKey = appearanceKey,
        friendId = friendId,
        accessoryId = accessoryId,
        outfitId = outfitId,
        backgroundId = backgroundId,
        isAnimated = isAnimated,
        emotion = emotion,
        isMoving = isMoving,
        movingLeft = movingLeft,
        vehicleWarning = vehicleWarning,
        vehicleHungry = vehicleHungry,
        artworkOverride = artworkOverride,
        isDisappearing = false,
        onDisappeared = {},
    )
}

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
    isDisappearing: Boolean = false,
    onDisappeared: () -> Unit = {},
    isAppearing: Boolean = false,
    onAppeared: () -> Unit = {},
) {
    val cat = friendId == "friend:luna"
    val cream = appearanceKey == "CREAM"
    val motionEnabled = isAnimated && LocalMobiMonMotionEnabled.current
    // Reduced motion keeps the gentle idle breath and drops travel animation only.
    val runEnabled = isAnimated && isMoving && LocalMobiMonMotionEnabled.current
    val description =
        stringResource(
            when (friendId) {
                "friend:las" -> R.string.mobimon_las_description
                "friend:luna" -> R.string.mobimon_luna_description
                else -> R.string.mobimon_mobi_description
            },
        )
    if (isAppearing) {
        if (friendId == "friend:mobi") {
            MobiAppearAnimation(modifier, accessoryId ?: outfitId, onAppeared)
        } else if (friendId == "friend:luna") {
            LunaAppearAnimation(
                modifier.size(120.dp).semantics { contentDescription = description },
                lunaAppearance(accessoryId ?: outfitId),
                onAppeared,
            )
        } else if (friendId == "friend:las") {
            LasTransitionAnimation(
                modifier.size(120.dp).semantics { contentDescription = description },
                LasTransition.APPEAR,
                onAppeared,
            )
        } else {
            LaunchedEffect(Unit) { onAppeared() }
        }
        return
    }
    if (isDisappearing && friendId == "friend:las" && !isMoving) {
        LasTransitionAnimation(
            modifier.size(120.dp).semantics { contentDescription = description },
            LasTransition.DISAPPEAR,
            onDisappeared,
        )
        return
    }
    if (isDisappearing &&
        friendId != "friend:mobi" &&
        friendId != "friend:luna" &&
        friendId != "friend:las" &&
        !isMoving
    ) {
        LaunchedEffect(Unit) { onDisappeared() }
        return
    }
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
    if (friendId == "friend:las") {
        Box(modifier = modifier.size(120.dp).semantics { contentDescription = description }) {
            backgroundId?.let { CharacterArtwork.backgrounds[it] }?.let {
                CharacterAssetImage(it, Modifier.fillMaxSize())
            }
            val sick = vehicleWarning || emotion == PetEmotion.SICK
            val hungry =
                (vehicleHungry || emotion == PetEmotion.HUNGRY) &&
                    !sick
            CompanionStatusCrossfade(
                state =
                    when {
                        sick -> CompanionStatus.SICK
                        hungry -> CompanionStatus.HUNGRY
                        else -> CompanionStatus.NORMAL
                    },
                motionEnabled = motionEnabled,
                modifier = Modifier.fillMaxSize(),
            ) { status ->
                when (status) {
                    CompanionStatus.SICK -> LasSickAnimation(Modifier.fillMaxSize(), motionEnabled)
                    CompanionStatus.HUNGRY -> LasHungryAnimation(Modifier.fillMaxSize(), motionEnabled)
                    else -> {
                        if (runEnabled) {
                            LasDanceAnimation(Modifier.fillMaxSize(), movingLeft)
                        } else {
                            LasIdleAnimation(Modifier.fillMaxSize(), motionEnabled)
                        }
                    }
                }
            }
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

    LaunchedEffect(cat, isAnimated, activeLunaAnimation, appearance) {
        if (cat && isAnimated) LunaAnimationManager.retainOnly(activeLunaAnimation)
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
                val showMobiStatus = (isSick || isHungry) && !isDisappearing

                when {
                    showMobiStatus || !showMobiRun ->
                        MobiDisappearAnimation(
                            modifier = Modifier.fillMaxSize(),
                            onFinished = onDisappeared,
                            isDisappearing = isDisappearing,
                            accessoryId = equippedAccessory,
                        ) {
                            MobiIdleBreathAnimation(
                                modifier = Modifier.fillMaxSize(),
                                accessoryId = equippedAccessory,
                                fallbackAsset = CharacterArtwork.preview(friendId, equippedAccessory),
                                vehicleWarning = isSick,
                                vehicleHungry = isHungry,
                                animateNormal = isAnimated && hasMobiIdleSprite,
                                animateSick = isAnimated,
                                motionEnabled = motionEnabled,
                            )
                        }
                    else ->
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
                }
            } else if (friendId == "friend:luna") {
                val showLunaRun = runEnabled && !isSick && !isHungry
                if (isDisappearing && !showLunaRun) {
                    LunaDisappearAnimation(Modifier.fillMaxSize(), appearance, onDisappeared)
                } else if (showLunaRun) {
                    LunaRunAnimation(
                        modifier = Modifier.fillMaxSize(),
                        movingLeft = movingLeft,
                        appearance = appearance,
                    )
                } else {
                    val lunaState =
                        when {
                            isSick -> CompanionStatus.SICK
                            isHungry -> CompanionStatus.HUNGRY
                            else -> CompanionStatus.NORMAL
                        }
                    CompanionStatusCrossfade(
                        state = lunaState,
                        motionEnabled = motionEnabled,
                        modifier = Modifier.fillMaxSize(),
                    ) { state ->
                        LunaStateArtwork(state, appearance, isAnimated)
                    }
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
private fun LunaStateArtwork(
    state: CompanionStatus,
    appearance: LunaAppearance,
    animateFrames: Boolean,
) {
    val stateName = if (state == CompanionStatus.NORMAL) "idle" else state.name.lowercase()
    Box(Modifier.fillMaxSize().testTag("luna-state-$stateName")) {
        when (state) {
            CompanionStatus.SICK ->
                LunaSickAnimation(
                    modifier = Modifier.fillMaxSize(),
                    appearance = appearance,
                    animateFrames = animateFrames,
                )
            CompanionStatus.HUNGRY ->
                LunaHungryAnimation(
                    modifier = Modifier.fillMaxSize(),
                    appearance = appearance,
                    animateFrames = animateFrames,
                )
            CompanionStatus.NORMAL ->
                LunaIdleBreathAnimation(
                    modifier = Modifier.fillMaxSize(),
                    appearance = appearance,
                    animateFrames = animateFrames,
                )
        }
    }
}

@Composable
fun LunaRunAnimation(
    modifier: Modifier = Modifier,
    movingLeft: Boolean = true,
    contentDescription: String? = null,
    appearance: LunaAppearance = LunaAppearance.NORMAL,
) {
    if (!LocalMobiMonMotionEnabled.current) {
        LunaIdleBreathAnimation(modifier, contentDescription, appearance, animateFrames = false)
        return
    }
    val context = LocalContext.current
    val firstFrame =
        remember(context, appearance) {
            LunaFirstFrameCache.getOrLoad(context, LunaActiveAnimation.RUN, appearance)
        }
    var frames by remember(context, appearance) { mutableStateOf<List<ImageBitmap>?>(null) }
    LaunchedEffect(context, appearance) {
        val loaded = withContext(Dispatchers.IO) { LunaRunAnimationCache.getOrLoadFrames(context, appearance) }
        withContext(Dispatchers.Main.immediate) { frames = loaded }
    }

    if (frames.isNullOrEmpty() && firstFrame == null) {
        Box(modifier.testTag("luna-animation-loading-${appearance.assetName}"))
    } else {
        val loadedFrames = frames.orEmpty()
        val currentFrameIndex = remember(loadedFrames) { mutableIntStateOf(0) }
        LaunchedEffect(loadedFrames) {
            if (loadedFrames.isEmpty()) return@LaunchedEffect
            var previousTime = withInfiniteAnimationFrameNanos { it }
            var elapsedNanos = 0L
            while (isActive) {
                val time = withInfiniteAnimationFrameNanos { it }
                elapsedNanos += (time - previousTime).coerceAtMost(100_000_000L)
                previousTime = time
                var nextFrame = currentFrameIndex.intValue
                while (elapsedNanos >= RUN_FRAME_DURATIONS_MS[nextFrame] * 1_000_000L) {
                    elapsedNanos -= RUN_FRAME_DURATIONS_MS[nextFrame] * 1_000_000L
                    nextFrame = (nextFrame + 1) % loadedFrames.size
                }
                currentFrameIndex.intValue = nextFrame
            }
        }
        val baseAsset = CharacterArtwork.characters.getValue("friend:luna")
        Box(
            modifier =
                modifier
                    .graphicsLayer {
                        val flip = if (movingLeft) 1f else -1f
                        scaleX = baseAsset.visualScale * flip
                        scaleY = baseAsset.visualScale
                        translationX = size.width * baseAsset.translationXFraction * flip
                        translationY = size.height * baseAsset.translationYFraction
                    }.testTag("luna-animation-frame-${appearance.assetName}")
                    .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
                    .drawWithCache {
                        onDrawBehind {
                            val frame = loadedFrames.getOrNull(currentFrameIndex.intValue) ?: firstFrame
                            if (frame != null) {
                                val side =
                                    size.minDimension.toInt()
                                val dstSize =
                                    androidx.compose.ui.unit
                                        .IntSize(side, side)
                                val dstOffset =
                                    androidx.compose.ui.unit.IntOffset(
                                        ((size.width - side) / 2).toInt(),
                                        ((size.height - side) / 2).toInt(),
                                    )
                                drawImage(
                                    image = frame,
                                    dstOffset = dstOffset,
                                    dstSize = dstSize,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                                )
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {}
    }
}

@Composable
fun LunaHungryAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    appearance: LunaAppearance = LunaAppearance.NORMAL,
    animateFrames: Boolean = true,
) {
    IdleBreathAnimation(
        LunaHungryAnimationCache::getOrLoadFrames,
        LunaActiveAnimation.HUNGRY,
        appearance,
        true,
        modifier,
        contentDescription,
        animateFrames,
        extraTranslationXFraction = LUNA_HUNGRY_TRANSLATION_X_FRACTION,
    )
}

@Composable
fun LunaSickAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    appearance: LunaAppearance = LunaAppearance.NORMAL,
    animateFrames: Boolean = true,
) {
    IdleBreathAnimation(
        LunaSickAnimationCache::getOrLoadFrames,
        LunaActiveAnimation.SICK,
        appearance,
        true,
        modifier,
        contentDescription,
        animateFrames,
        extraTranslationYFraction = LUNA_SICK_TRANSLATION_Y_FRACTION,
    )
}

@Composable
private fun IdleBreathAnimation(
    loadFrames: (Context, LunaAppearance) -> List<ImageBitmap>,
    animation: LunaActiveAnimation,
    appearance: LunaAppearance,
    applyAssetTransform: Boolean,
    modifier: Modifier,
    contentDescription: String?,
    animateFrames: Boolean,
    extraTranslationXFraction: Float = 0f,
    extraTranslationYFraction: Float = 0f,
) {
    val context = LocalContext.current
    val firstFrame =
        remember(context, animation, appearance) { LunaFirstFrameCache.getOrLoad(context, animation, appearance) }
    var frames by remember(context, loadFrames, appearance) { mutableStateOf<List<ImageBitmap>?>(null) }
    LaunchedEffect(context, loadFrames, appearance) {
        val loaded = withContext(Dispatchers.IO) { loadFrames(context, appearance) }
        withContext(Dispatchers.Main.immediate) { frames = loaded }
    }

    if (frames.isNullOrEmpty() && firstFrame == null) {
        Box(modifier.testTag("luna-animation-loading-${appearance.assetName}"))
    } else {
        val loadedFrames = frames.orEmpty()
        val currentFrameIndex = remember(loadedFrames) { mutableIntStateOf(0) }
        LaunchedEffect(loadedFrames, animateFrames) {
            if (!animateFrames || loadedFrames.isEmpty()) return@LaunchedEffect
            var previousTime = withInfiniteAnimationFrameNanos { it }
            var elapsedNanos = 0L
            while (isActive) {
                val time = withInfiniteAnimationFrameNanos { it }
                // Resume from a stopped window without jumping through the whole breathing cycle.
                elapsedNanos += (time - previousTime).coerceAtMost(130_000_000L)
                previousTime = time
                var nextFrame = currentFrameIndex.intValue
                while (elapsedNanos >= IDLE_BREATH_FRAME_DURATIONS_MS[nextFrame] * 1_000_000L) {
                    elapsedNanos -= IDLE_BREATH_FRAME_DURATIONS_MS[nextFrame] * 1_000_000L
                    nextFrame = (nextFrame + 1) % loadedFrames.size
                }
                currentFrameIndex.intValue = nextFrame
            }
        }
        val baseAsset = CharacterArtwork.characters.getValue("friend:luna")
        Box(
            modifier =
                (
                    if (applyAssetTransform) {
                        modifier.graphicsLayer {
                            scaleX = baseAsset.visualScale
                            scaleY = baseAsset.visualScale
                            translationX =
                                size.width * (baseAsset.translationXFraction + extraTranslationXFraction)
                            translationY =
                                size.height * (baseAsset.translationYFraction + extraTranslationYFraction)
                        }
                    } else {
                        modifier
                    }
                ).testTag("luna-animation-frame-${appearance.assetName}")
                    .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
                    .drawWithCache {
                        onDrawBehind {
                            val frame = loadedFrames.getOrNull(currentFrameIndex.intValue) ?: firstFrame
                            if (frame != null) {
                                val side =
                                    size.minDimension.toInt()
                                val dstSize =
                                    androidx.compose.ui.unit
                                        .IntSize(side, side)
                                val dstOffset =
                                    androidx.compose.ui.unit.IntOffset(
                                        ((size.width - side) / 2).toInt(),
                                        ((size.height - side) / 2).toInt(),
                                    )
                                drawImage(
                                    image = frame,
                                    dstOffset = dstOffset,
                                    dstSize = dstSize,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                                )
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {}
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
        } else if (friendId == "friend:las") {
            LasDanceSpriteCache.getOrLoad(context)
        }
    }
}
