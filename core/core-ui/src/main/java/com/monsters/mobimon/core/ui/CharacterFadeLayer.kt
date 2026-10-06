package com.monsters.mobimon.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Includes equipment overflow in the alpha buffer without moving or resizing the body slot. */
@Composable
internal fun CharacterFadeLayer(
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    topOutsetFraction: Float = 0f,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val headroom = minOf(maxWidth, maxHeight) * topOutsetFraction
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset(y = -headroom)
                .size(maxWidth, maxHeight + headroom)
                .graphicsLayer { this.alpha = alpha() }
                .padding(top = headroom),
        ) {
            content()
        }
    }
}
