package com.monsters.mobimon.core.ui

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.roundToInt

internal enum class LunaAtlasAction(
    val directory: String,
    val prefix: String,
) {
    RUN("run", "luna_run_left"),
    APPEAR("appear", "luna_appear"),
    DISAPPEAR("disappear", "luna_disappear"),
}

internal object LunaAnimationAtlas {
    fun path(
        action: LunaAtlasAction,
        appearance: LunaAppearance,
    ): String =
        "characters/luna/${appearance.assetName}/${action.directory}/" +
            "${action.prefix}_${appearance.assetName}_sprite.webp"

    fun load(
        context: Context,
        action: LunaAtlasAction,
        appearance: LunaAppearance,
        requiredFrameSidePx: Int,
    ): ImageBitmap? = CharacterAnimationAtlas.load(context, path(action, appearance), requiredFrameSidePx)
}

/** Retain one atlas; appearance and decode tier must both match before publishing cached artwork. */
internal object LunaRunAnimationCache {
    private data class Entry(
        val appearance: LunaAppearance,
        val sampleSize: Int,
        val sheet: ImageBitmap,
    )

    @Volatile private var cached: Entry? = null

    fun peek(
        appearance: LunaAppearance = LunaAppearance.NORMAL,
        requiredFrameSidePx: Int = 256,
    ): ImageBitmap? =
        cached
            ?.takeIf {
                it.appearance == appearance &&
                    it.sampleSize == CharacterAnimationAtlas.sampleSizeFor(requiredFrameSidePx)
            }?.sheet

    fun clear() {
        cached = null
    }

    fun getOrLoad(
        context: Context,
        appearance: LunaAppearance = LunaAppearance.NORMAL,
        requiredFrameSidePx: Int = (124 * context.resources.displayMetrics.density).roundToInt(),
    ): ImageBitmap? {
        peek(appearance, requiredFrameSidePx)?.let { return it }
        return synchronized(this) {
            peek(appearance, requiredFrameSidePx)?.let { return@synchronized it }
            LunaAnimationAtlas.load(context, LunaAtlasAction.RUN, appearance, requiredFrameSidePx)?.also {
                cached = Entry(appearance, CharacterAnimationAtlas.sampleSizeFor(requiredFrameSidePx), it)
            }
        }
    }
}
