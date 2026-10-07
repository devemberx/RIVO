package com.monsters.mobimon.core.ui

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap

internal enum class MobiAtlasAction(
    val directory: String,
    val prefix: String,
) {
    RUN("run", "mobi_run_left"),
    APPEAR("appear", "mobi_appear"),
    DISAPPEAR("disappear", "mobi_disappear"),
}

/** Fixed integer cells at both decode sizes; compression does not determine pixel allocation. */
internal object MobiAnimationAtlas {
    fun sampleSizeFor(requiredFrameSidePx: Int): Int = CharacterAnimationAtlas.sampleSizeFor(requiredFrameSidePx)

    fun load(
        context: Context,
        action: MobiAtlasAction,
        accessoryId: String?,
        requiredFrameSidePx: Int,
    ): ImageBitmap? {
        val appearance = mobiAppearanceName(accessoryId)
        val path = "characters/mobi/$appearance/${action.directory}/${action.prefix}_${appearance}_sprite.webp"
        return CharacterAnimationAtlas.load(context, path, requiredFrameSidePx)
    }
}
