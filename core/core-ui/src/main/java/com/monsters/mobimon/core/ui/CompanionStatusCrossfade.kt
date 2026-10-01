package com.monsters.mobimon.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt

internal enum class CompanionStatus {
    NORMAL,
    HUNGRY,
    SICK,
}

private data class StatusPair(
    val outgoing: CompanionStatus,
    val incoming: CompanionStatus,
)

/** Both companions use one transition between the outgoing and incoming vehicle-status poses. */
@Composable
internal fun CompanionStatusCrossfade(
    state: CompanionStatus,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (CompanionStatus) -> Unit,
) {
    var pair by remember { mutableStateOf(StatusPair(state, state)) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(state, motionEnabled) {
        if (!motionEnabled) {
            fade.snapTo(1f)
            pair = StatusPair(state, state)
            return@LaunchedEffect
        }
        if (pair.incoming == state) return@LaunchedEffect

        val duration: Int
        if (pair.outgoing == state) {
            // Reversing the same pair keeps both current opacities continuous.
            val remaining = fade.value
            fade.snapTo(1f - remaining)
            pair = StatusPair(pair.incoming, state)
            duration = (200 * remaining).roundToInt().coerceAtLeast(1)
        } else {
            // A third status replaces the less visible pose, keeping the composition bounded to two.
            val outgoing = if (fade.value < 0.5f) pair.outgoing else pair.incoming
            fade.snapTo(0f)
            pair = StatusPair(outgoing, state)
            duration = 200
        }
        fade.animateTo(1f, tween(duration, easing = LinearEasing))
        pair = StatusPair(state, state)
    }

    val visibleStatuses =
        when {
            !motionEnabled -> listOf(state)
            pair.outgoing == pair.incoming -> listOf(pair.incoming)
            else -> listOf(pair.outgoing, pair.incoming)
        }
    Box(modifier) {
        // Keep each pose's sprite clock at the same keyed call site throughout the fade.
        for (status in visibleStatuses) {
            key(status) {
                Box(
                    Modifier.matchParentSize().graphicsLayer {
                        alpha =
                            when {
                                !motionEnabled || pair.outgoing == pair.incoming -> 1f
                                status == pair.incoming -> fade.value
                                else -> 1f - fade.value
                            }
                    },
                ) {
                    content(status)
                }
            }
        }
    }
}
