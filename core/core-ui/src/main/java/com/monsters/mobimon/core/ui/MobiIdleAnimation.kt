package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal object MobiIdleTimeline {
    const val COLUMNS = 6
    const val ROWS = 4
    const val TILT_PERIOD_MS = 6_200L
    const val BOB_PERIOD_MS = 6_600L
    const val GESTURE_PERIOD_MS = 4_800L

    fun blinkClosedAt(elapsedNanos: Long): Boolean =
        (elapsedNanos.coerceAtLeast(0L) % (GESTURE_PERIOD_MS * 1_000_000L)) in 2_300_000_000L until 2_440_000_000L

    fun sproutShiftAt(elapsedNanos: Long): Float {
        val phase = elapsedNanos.coerceAtLeast(0L) % (GESTURE_PERIOD_MS * 1_000_000L)
        return (17 * sin(2 * PI * phase / (GESTURE_PERIOD_MS * 1_000_000L))).toFloat()
    }

    val cycleMs: Long = 4_050L

    fun breathAt(elapsedNanos: Long): Float {
        val phase = (elapsedNanos.coerceAtLeast(0L) % (cycleMs * 1_000_000L)).toDouble()
        return ((1 - cos(2 * PI * phase / (cycleMs * 1_000_000L))) / 2).toFloat()
    }

    fun bobAt(elapsedNanos: Long): Float {
        val phase = (elapsedNanos.coerceAtLeast(0L) % (BOB_PERIOD_MS * 1_000_000L)).toDouble()
        val wave = sin(2 * PI * phase / (BOB_PERIOD_MS * 1_000_000L))
        return (wave * wave).toFloat()
    }

    fun scaleXAt(elapsedNanos: Long): Float = 1f + 0.012f * breathAt(elapsedNanos) + 0.005f * (1f - bobAt(elapsedNanos))

    fun scaleYAt(elapsedNanos: Long): Float = 1f + 0.024f * breathAt(elapsedNanos) - 0.004f * (1f - bobAt(elapsedNanos))

    // Relative to the fixed sprite box: about 4dp breath + 2.4dp bob at the 600dp Home reference size.
    fun liftFractionAt(elapsedNanos: Long): Float = -0.0067f * breathAt(elapsedNanos) - 0.004f * bobAt(elapsedNanos)

    fun tiltAt(elapsedNanos: Long): Float {
        val phase = (elapsedNanos.coerceAtLeast(0L) % (TILT_PERIOD_MS * 1_000_000L)).toDouble()
        return (2.35 * sin(2 * PI * phase / (TILT_PERIOD_MS * 1_000_000L))).toFloat()
    }
}

/** Canonical fitted first frames stay available before the gesture layers load. */
private object MobiIdleFirstFrameCache {
    private val frames = ConcurrentHashMap<String, ImageBitmap>()

    fun peek(assetPath: String): ImageBitmap? = frames[assetPath]

    @Synchronized
    fun clear() = frames.clear()

    fun getOrLoad(
        context: Context,
        assetPath: String,
    ): ImageBitmap? =
        synchronized(this) {
            frames[assetPath]?.let { return@synchronized it }
            try {
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inScaled = false
                    }
                context.applicationContext.assets.open(assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()?.also { frames[assetPath] = it }
                }
            } catch (_: java.io.IOException) {
                null
            }
        }
}

internal fun mobiAppearanceName(accessoryId: String?): String =
    when (accessoryId) {
        "accessory:mobi_headphones" -> "headphones"
        "accessory:mobi_goggles" -> "goggles"
        else -> "normal"
    }

internal object MobiSpriteCache {
    const val DEFAULT_ASSET_PATH = "characters/mobi/normal/idle_breath/mobi_idle_breath_normal_01.png"

    fun peek(accessoryId: String? = null): ImageBitmap? = MobiIdleArtworkCache.peek(accessoryId)?.original

    fun peekFirstFrame(accessoryId: String? = null): ImageBitmap? =
        MobiIdleFirstFrameCache.peek(firstFramePath(accessoryId))

    private fun firstFramePath(accessoryId: String?): String {
        val appearance = mobiAppearanceName(accessoryId)
        return "characters/mobi/$appearance/idle_breath/mobi_idle_breath_${appearance}_01.png"
    }

    fun firstFrame(
        context: Context,
        accessoryId: String? = null,
    ): ImageBitmap? = MobiIdleFirstFrameCache.getOrLoad(context, firstFramePath(accessoryId))

    fun clear() {
        MobiIdleArtworkCache.clear()
        MobiIdleFirstFrameCache.clear()
    }

    fun getOrLoad(
        context: Context,
        accessoryId: String? = null,
    ): ImageBitmap? = MobiIdleArtworkCache.getOrLoad(context, accessoryId)?.original
}

/** One shared motion clock and cached source parts for normal, headphones and goggles. */
@Composable
internal fun NormalMobiIdleAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    accessoryId: String? = null,
    fallbackAsset: CharacterAsset = CharacterArtwork.preview("friend:mobi", accessoryId),
    animateFrames: Boolean = true,
) {
    val context = LocalContext.current.applicationContext
    // Equipment changes reset the producer before either image or gesture layers arrive.
    val firstFrame by androidx.compose.runtime.key(accessoryId) {
        produceState<ImageBitmap?>(MobiSpriteCache.peekFirstFrame(accessoryId), context) {
            value = withContext(Dispatchers.IO) { MobiSpriteCache.firstFrame(context, accessoryId) }
        }
    }
    var artwork by remember(context, accessoryId) { mutableStateOf(MobiIdleArtworkCache.peek(accessoryId)) }
    LaunchedEffect(context, accessoryId, animateFrames) {
        if (!animateFrames) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { MobiIdleArtworkCache.getOrLoad(context, accessoryId) }
        artwork = loaded
    }
    val fittedFirstFrame = firstFrame
    if (fittedFirstFrame == null) {
        Box(modifier.testTag("mobi-animation-loading-${mobiAppearanceName(accessoryId)}"))
        return
    }
    val elapsed = remember { mutableLongStateOf(0L) }
    val animate = animateFrames
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var previous = withInfiniteAnimationFrameNanos { it }
        while (isActive) {
            val now = withInfiniteAnimationFrameNanos { it }
            elapsed.longValue += (now - previous).coerceIn(0L, 100_000_000L)
            previous = now
        }
    }
    Box(
        modifier
            .testTag("mobi-animation-frame-${mobiAppearanceName(accessoryId)}")
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .graphicsLayer {
                val time = if (animate) elapsed.longValue else 0L
                rotationZ = MobiIdleTimeline.tiltAt(time)
                scaleX = MobiIdleTimeline.scaleXAt(time)
                scaleY = MobiIdleTimeline.scaleYAt(time)
                translationY =
                    size.minDimension * (MobiIdleTimeline.liftFractionAt(time) + fallbackAsset.translationYFraction)
                // Preserve the selected body motion and item fit as a single layer.
                compositingStrategy = CompositingStrategy.Offscreen
                transformOrigin = TransformOrigin(0.5f, 0.9f)
                clip = false
            }.mobiIdleParts(fittedFirstFrame, if (animate) artwork else null) {
                if (animate) elapsed.longValue else 0L
            },
    )
}

@Composable
internal fun NormalMobiHungryAnimation(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    accessoryId: String? = null,
    fallbackAsset: CharacterAsset = CharacterArtwork.preview("friend:mobi", accessoryId),
    animateFrames: Boolean = true,
) {
    val context = LocalContext.current.applicationContext
    // Reset the producer immediately on equipment changes; never display the old item while loading.
    val artwork by androidx.compose.runtime.key(accessoryId) {
        produceState<MobiHungryArtwork?>(MobiHungryArtworkCache.peek(accessoryId), context) {
            value = withContext(Dispatchers.IO) { MobiHungryArtworkCache.getOrLoad(context, accessoryId) }
        }
    }
    val loaded = artwork
    if (loaded == null) {
        CharacterAssetImage(CharacterArtwork.preview("friend:mobi", accessoryId), modifier, contentDescription)
        return
    }
    val elapsed = remember(accessoryId) { mutableLongStateOf(0L) }
    val animate = animateFrames && LocalMobiMonMotionEnabled.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(loaded, animate, lifecycleOwner) {
        if (!animate) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            var previous = withInfiniteAnimationFrameNanos { it }
            while (isActive) {
                val now = withInfiniteAnimationFrameNanos { it }
                elapsed.longValue += (now - previous).coerceIn(0L, 100_000_000L)
                previous = now
            }
        }
    }
    Box(
        modifier
            .testTag("mobi-hungry-${mobiAppearanceName(accessoryId)}")
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .graphicsLayer {
                scaleX = MobiIdleTimeline.scaleXAt(0L)
                scaleY = MobiIdleTimeline.scaleYAt(0L)
                translationY = size.minDimension * fallbackAsset.translationYFraction
                compositingStrategy = CompositingStrategy.Offscreen
                transformOrigin = TransformOrigin(0.5f, 0.9f)
                clip = false
            }.mobiHungryParts(loaded) {
                if (animate) elapsed.longValue else MobiHungryTimeline.STILL_MS * 1_000_000L
            },
    )
}

internal object MobiRunSpriteCache {
    const val DEFAULT_ASSET_PATH = "characters/mobi/normal/run/mobi_run_left_normal_sprite.webp"

    private data class Entry(
        val accessoryId: String?,
        val sampleSize: Int,
        val sheet: ImageBitmap,
    )

    @Volatile private var cached: Entry? = null

    fun peek(
        accessoryId: String? = null,
        requiredFrameSidePx: Int = 256,
    ): ImageBitmap? =
        cached
            ?.takeIf {
                it.accessoryId == accessoryId && it.sampleSize == MobiAnimationAtlas.sampleSizeFor(requiredFrameSidePx)
            }?.sheet

    fun clear() {
        cached = null
    }

    fun getOrLoad(
        context: Context,
        accessoryId: String? = null,
        requiredFrameSidePx: Int = (124 * context.resources.displayMetrics.density).roundToInt(),
    ): ImageBitmap? {
        peek(accessoryId, requiredFrameSidePx)?.let { return it }
        return synchronized(this) {
            peek(accessoryId, requiredFrameSidePx)?.let { return@synchronized it }
            MobiAnimationAtlas.load(context, MobiAtlasAction.RUN, accessoryId, requiredFrameSidePx)?.also {
                cached = Entry(accessoryId, MobiAnimationAtlas.sampleSizeFor(requiredFrameSidePx), it)
            }
        }
    }
}

const val MOBI_RUN_FRAME_DURATION_MS = 70

@Composable
fun MobiRunAnimation(
    modifier: Modifier = Modifier,
    movingLeft: Boolean = true,
    isMoving: Boolean = true,
    contentDescription: String? = null,
    accessoryId: String? = null,
    fallbackAsset: CharacterAsset = CharacterArtwork.characters.getValue("friend:mobi"),
    frameDurationMs: Int = MOBI_RUN_FRAME_DURATION_MS,
    onHopFinished: () -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val requiredFrameSidePx = minOf(constraints.maxWidth, constraints.maxHeight)
        if (!LocalMobiMonMotionEnabled.current) {
            LaunchedEffect(isMoving) { if (!isMoving) onHopFinished() }
            CharacterAssetImage(fallbackAsset, Modifier.fillMaxSize(), contentDescription)
            return@BoxWithConstraints
        }
        val context = LocalContext.current.applicationContext
        val sprite by produceState<ImageBitmap?>(
            initialValue = MobiRunSpriteCache.peek(accessoryId, requiredFrameSidePx),
            context,
            accessoryId,
            requiredFrameSidePx,
        ) {
            value =
                withContext(Dispatchers.IO) { MobiRunSpriteCache.getOrLoad(context, accessoryId, requiredFrameSidePx) }
        }
        val sheet = sprite
        if (sheet == null) {
            LaunchedEffect(isMoving) { if (!isMoving) onHopFinished() }
            CharacterAssetImage(fallbackAsset, Modifier.fillMaxSize(), contentDescription)
            return@BoxWithConstraints
        }
        var currentFrameIndex by remember(sheet) { mutableIntStateOf(0) }
        val currentIsMoving by rememberUpdatedState(isMoving)
        val currentOnHopFinished by rememberUpdatedState(onHopFinished)

        LaunchedEffect(sheet) {
            var previousTime = withInfiniteAnimationFrameNanos { it }
            var elapsedNanos = 0L
            while (isActive) {
                val time = withInfiniteAnimationFrameNanos { it }
                elapsedNanos += (time - previousTime).coerceAtMost(100_000_000L)
                previousTime = time
                var nextFrame = currentFrameIndex
                while (elapsedNanos >= frameDurationMs * 1_000_000L) {
                    elapsedNanos -= frameDurationMs * 1_000_000L
                    val updatedFrame = (nextFrame + 1) % 24
                    if (updatedFrame == 0) {
                        if (!currentIsMoving) {
                            currentOnHopFinished()
                        }
                    }
                    nextFrame = updatedFrame
                }
                currentFrameIndex = nextFrame
            }
        }
        val baseAsset = CharacterArtwork.characters.getValue("friend:mobi")
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val flip = if (movingLeft) 1f else -1f
                        scaleX = baseAsset.visualScale * flip
                        scaleY = baseAsset.visualScale
                        translationX = size.width * baseAsset.translationXFraction * flip
                        translationY = size.height * baseAsset.translationYFraction
                    }.mobiSpriteFrames(
                        sheet = sheet,
                        columns = MobiIdleTimeline.COLUMNS,
                        rows = MobiIdleTimeline.ROWS,
                        loop = true,
                        blendFrames = false,
                        position = { currentFrameIndex.toFloat() },
                    ),
            contentAlignment = Alignment.Center,
        ) {}
    }
}

/** Shared fixed-canvas atlas draw. Time/progress is read only in draw, never bitmap allocation. */
internal fun Modifier.mobiSpriteFrames(
    sheet: ImageBitmap,
    columns: Int,
    rows: Int,
    loop: Boolean = true,
    blendFrames: Boolean = true,
    filterQuality: FilterQuality = FilterQuality.Low,
    position: () -> Float,
): Modifier =
    drawWithCache {
        val count = columns * rows
        val cell = IntSize(sheet.width / columns, sheet.height / rows)
        val sources = Array(count) { IntOffset(it % columns * cell.width, it / columns * cell.height) }
        val side = size.minDimension.roundToInt()
        val destination = IntSize(side, side)
        val offset = IntOffset(((size.width - side) / 2).roundToInt(), ((size.height - side) / 2).roundToInt())
        onDrawBehind {
            val value = position().coerceIn(0f, count.toFloat())
            val frame = value.toInt().coerceAtMost(count - 1)
            val blend = if (blendFrames) (value - frame).coerceIn(0f, 1f) else 0f
            drawImage(
                sheet,
                sources[frame],
                cell,
                offset,
                destination,
                alpha = 1f - blend,
                filterQuality = filterQuality,
            )
            if (blend > 0f) {
                val next = if (loop) (frame + 1) % count else (frame + 1).coerceAtMost(count - 1)
                drawImage(
                    sheet,
                    sources[next],
                    cell,
                    offset,
                    destination,
                    alpha = blend,
                    filterQuality = filterQuality,
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
