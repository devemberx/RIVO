package com.monsters.mobimon.core.ui

import android.content.Context
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val IDLE_BREATH_FRAME_DURATIONS_MS =
    IntArray(24) { if (it == 23) 130 else 90 }

internal val RUN_FRAME_DURATIONS_MS =
    IntArray(24) { 50 }

// Register the source baseline, then lower the collapsed pose onto the Home platform.
internal const val LUNA_SICK_TRANSLATION_Y_FRACTION = 0.87f * (1172f - 1048f) / 1254f + 0.04f

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

internal enum class LunaActiveAnimation {
    IDLE,
    RUN,
    HUNGRY,
    SICK,
    NONE,
}

/** First poses share the owned artwork caches and their invalidation. */
internal object LunaFirstFrameCache {
    fun getOrLoad(
        context: Context,
        animation: LunaActiveAnimation,
        appearance: LunaAppearance,
    ): ImageBitmap? =
        when (animation) {
            LunaActiveAnimation.IDLE -> LunaIdleArtworkCache.getOrLoadFirstFrame(context, appearance)
            LunaActiveAnimation.SICK -> LunaSickArtworkCache.getOrLoad(context, appearance)?.still
            LunaActiveAnimation.RUN, LunaActiveAnimation.HUNGRY, LunaActiveAnimation.NONE -> null
        }
}

internal object LunaAnimationManager {
    fun retainOnly(active: LunaActiveAnimation) {
        val retainMotion = active == LunaActiveAnimation.IDLE || active == LunaActiveAnimation.RUN
        if (!retainMotion && active != LunaActiveAnimation.HUNGRY) LunaIdleArtworkCache.clear()
        if (!retainMotion) LunaRunAnimationCache.clear()
        if (active != LunaActiveAnimation.HUNGRY) LunaHungryPartsCache.clear()
        if (active != LunaActiveAnimation.SICK) LunaSickArtworkCache.clear()
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
                    LunaStatusCrossfade(
                        state = lunaState,
                        motionEnabled = motionEnabled,
                        modifier = Modifier.fillMaxSize(),
                    ) { state ->
                        LunaStateArtwork(state, appearance, isAnimated)
                    }
                }
            } else {
                CharacterAssetImage(CharacterArtwork.preview(friendId, equippedAccessory), Modifier.fillMaxSize())
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
                    modifier = Modifier.fillMaxSize().lunaStatusViewport(),
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

// The shared 280px margin also contains the cap's 192px top outset during status fades.
private fun Modifier.lunaStatusViewport(): Modifier =
    layout { measurable, constraints ->
        val inset =
            (
                minOf(
                    constraints.maxWidth,
                    constraints.maxHeight,
                ) * LunaSickTimeline.SOURCE_MARGIN / 1254f
            ).roundToInt()
        val child =
            measurable.measure(
                Constraints.fixed(
                    constraints.maxWidth + inset * 2,
                    constraints.maxHeight + inset * 2,
                ),
            )
        layout(constraints.maxWidth, constraints.maxHeight) { child.placeRelative(-inset, -inset) }
    }

@Composable
fun LunaHungryAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    appearance: LunaAppearance = LunaAppearance.NORMAL,
    animateFrames: Boolean = true,
) {
    LunaSourceLayerAnimation(
        modifier = modifier,
        contentDescription = contentDescription,
        appearance = appearance,
        animateFrames = animateFrames,
        hungry = true,
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
                                val (origin, dstSize) = lunaIdleDestination(side, appearance)
                                val dstOffset =
                                    androidx.compose.ui.unit.IntOffset(
                                        ((size.width - side) / 2).toInt() + origin.x,
                                        ((size.height - side) / 2).toInt() + origin.y,
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
            LunaRunAnimationCache.getOrLoad(context, appearance)
        } else if (friendId == "friend:las") {
            LasDanceSpriteCache.getOrLoad(context)
        }
    }
}
