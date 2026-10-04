package com.monsters.mobimon.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale
import kotlin.math.roundToInt

enum class BackgroundPeriod(
    val key: String,
) {
    MIDNIGHT("midnight"),
    SUNRISE("sunrise"),
    MORNING("morning"),
    DAY("day"),
    AFTERNOON("afternoon"),
    SUNSET("sunset"),
    NIGHT("night"),
}

/** Normalizes interpreted VSS time; missing time uses Night without reading a clock. */
fun companionBackgroundPeriod(timeOfDay: String?): BackgroundPeriod {
    val value = timeOfDay?.trim()?.lowercase(Locale.ROOT)
    return when (value) {
        "sunrise", "일출", "새벽" -> BackgroundPeriod.SUNRISE
        "morning", "아침" -> BackgroundPeriod.MORNING
        "day", "낮" -> BackgroundPeriod.DAY
        "afternoon", "오후", "늦은 오후" -> BackgroundPeriod.AFTERNOON
        "sunset", "노을", "저녁" -> BackgroundPeriod.SUNSET
        "night", "밤" -> BackgroundPeriod.NIGHT
        "midnight", "한밤", "한밤중", "자정" -> BackgroundPeriod.MIDNIGHT
        else ->
            when (value?.substringBefore(':')?.toIntOrNull()) {
                in 0..4 -> BackgroundPeriod.MIDNIGHT
                in 5..6 -> BackgroundPeriod.SUNRISE
                in 7..11 -> BackgroundPeriod.MORNING
                in 12..15 -> BackgroundPeriod.DAY
                in 16..17 -> BackgroundPeriod.AFTERNOON
                in 18..19 -> BackgroundPeriod.SUNSET
                else -> BackgroundPeriod.NIGHT
            }
    }
}

fun companionTimePeriod(timeOfDay: String?): String = companionBackgroundPeriod(timeOfDay).key

@Immutable
data class BackgroundFrame(
    @DrawableRes val drawableRes: Int,
    val glassOpacity: Float = 0.04f,
    val needsTextShadow: Boolean = false,
)

/** All seven periods are required, rather than silently borrowing another scene's image. */
@Immutable
data class BackgroundFrames(
    val midnight: BackgroundFrame,
    val sunrise: BackgroundFrame,
    val morning: BackgroundFrame,
    val day: BackgroundFrame,
    val afternoon: BackgroundFrame,
    val sunset: BackgroundFrame,
    val night: BackgroundFrame,
) {
    operator fun get(period: BackgroundPeriod): BackgroundFrame =
        when (period) {
            BackgroundPeriod.MIDNIGHT -> midnight
            BackgroundPeriod.SUNRISE -> sunrise
            BackgroundPeriod.MORNING -> morning
            BackgroundPeriod.DAY -> day
            BackgroundPeriod.AFTERNOON -> afternoon
            BackgroundPeriod.SUNSET -> sunset
            BackgroundPeriod.NIGHT -> night
        }
}

@Immutable
sealed interface BackgroundArtwork {
    fun frame(period: BackgroundPeriod): BackgroundFrame

    data class ByPeriod(
        val frames: BackgroundFrames,
    ) : BackgroundArtwork {
        override fun frame(period: BackgroundPeriod): BackgroundFrame = frames[period]
    }

    data class Static(
        val image: BackgroundFrame,
    ) : BackgroundArtwork {
        override fun frame(period: BackgroundPeriod): BackgroundFrame = image
    }
}

/** Preserves the approved Home horizon on resize; scenes may supply a different alignment. */
object CompanionBackgroundAlignment : Alignment {
    override fun align(
        size: IntSize,
        space: IntSize,
        layoutDirection: LayoutDirection,
    ): IntOffset {
        val scale = space.width / 2560f
        val top = ((1268 * scale - size.height) / 2 - 20 * scale).roundToInt()
        return IntOffset((space.width - size.width) / 2, top.coerceIn(minOf(0, space.height - size.height), 0))
    }
}

@Immutable
data class BackgroundScene(
    val id: String,
    val artwork: BackgroundArtwork,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val homeAlignment: Alignment = CompanionBackgroundAlignment,
)

enum class BackgroundProp { STAR_HANGER, STARLIGHT_YARN_BASKET }

@Immutable
sealed interface BackgroundVisual {
    data class Scene(
        val sceneId: String,
    ) : BackgroundVisual

    data class Prop(
        val kind: BackgroundProp,
    ) : BackgroundVisual

    data class Effect(
        val kind: ParticleType,
    ) : BackgroundVisual
}

data class BackgroundItem(
    val id: String,
    val visual: BackgroundVisual,
)

/** Visual definitions only: sale prices, ownership and equipment remain outside core-ui. */
class BackgroundCatalog(
    scenes: List<BackgroundScene>,
    items: List<BackgroundItem>,
    defaultSceneId: String,
) {
    private val scenesById = scenes.associateBy { it.id }
    private val visualsById = items.associate { it.id to it.visual }
    val defaultScene: BackgroundScene

    init {
        require(scenesById.size == scenes.size) { "Duplicate background scene ID" }
        require(visualsById.size == items.size) { "Duplicate background item ID" }
        require(items.all { it.visual !is BackgroundVisual.Scene || it.visual.sceneId in scenesById }) {
            "Background item references an unregistered scene"
        }
        defaultScene = requireNotNull(scenesById[defaultSceneId]) { "Missing default background scene" }
    }

    fun visual(itemId: String?): BackgroundVisual? = visualsById[itemId]

    fun scene(itemId: String?): BackgroundScene? =
        (visual(itemId) as? BackgroundVisual.Scene)?.let { scenesById.getValue(it.sceneId) }
}

/** Crossfade this layer, so the outgoing frame retains its own tint and alignment. */
@Immutable
data class BackgroundLayer(
    val frame: BackgroundFrame,
    val alignment: Alignment,
)

@Immutable
data class CompanionBackground(
    val scene: BackgroundScene,
    val period: BackgroundPeriod,
    val layer: BackgroundLayer,
    val prop: BackgroundProp?,
    val effect: ParticleType?,
)

/** Explicit decoration IDs override legacy single-slot IDs; null retains legacy compatibility. */
fun resolveCompanionBackground(
    timeOfDay: String?,
    backgroundId: String? = null,
    propId: String? = null,
    effectId: String? = null,
    catalog: BackgroundCatalog = CompanionBackgroundCatalog,
): CompanionBackground {
    val legacy = catalog.visual(backgroundId)
    val scene = catalog.scene(backgroundId) ?: catalog.defaultScene
    val period = companionBackgroundPeriod(timeOfDay)
    return CompanionBackground(
        scene = scene,
        period = period,
        layer = BackgroundLayer(scene.artwork.frame(period), scene.homeAlignment),
        prop =
            (catalog.visual(propId) as? BackgroundVisual.Prop)?.kind
                ?: (legacy as? BackgroundVisual.Prop)?.kind?.takeIf { propId == null },
        effect =
            (catalog.visual(effectId) as? BackgroundVisual.Effect)?.kind
                ?: (legacy as? BackgroundVisual.Effect)?.kind?.takeIf { effectId == null },
    )
}

@DrawableRes
fun companionBackgroundRes(
    timeOfDay: String?,
    backgroundId: String? = null,
): Int = resolveCompanionBackground(timeOfDay, backgroundId).layer.frame.drawableRes
