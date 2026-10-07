package com.monsters.mobimon.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** Gives alpha layers room for the cap/cloud while retaining the original body slot. */
@Composable
internal fun LunaStatusCrossfade(
    state: CompanionStatus,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (CompanionStatus) -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            CompanionStatusCrossfade(state, motionEnabled) { status ->
                Layout(content = { content(status) }) { measurables, expanded ->
                    // The outer quarter-slot margin is one sixth of the expanded short side.
                    val inset = (minOf(expanded.maxWidth, expanded.maxHeight) / 6f).roundToInt()
                    val child =
                        measurables.single().measure(
                            Constraints.fixed(expanded.maxWidth - inset * 2, expanded.maxHeight - inset * 2),
                        )
                    layout(expanded.maxWidth, expanded.maxHeight) { child.place(inset, inset) }
                }
            }
        },
    ) { measurables, constraints ->
        val inset = (minOf(constraints.maxWidth, constraints.maxHeight) * .25f).roundToInt()
        val layer =
            measurables.single().measure(
                Constraints.fixed(constraints.maxWidth + inset * 2, constraints.maxHeight + inset * 2),
            )
        layout(constraints.maxWidth, constraints.maxHeight) { layer.place(-inset, -inset) }
    }
}
