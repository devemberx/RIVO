package com.monsters.mobimon.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompanionStatusCrossfadeTest {
    @get:Rule val compose = createComposeRule()

    private var status by mutableStateOf(CompanionStatus.NORMAL)
    private var motionEnabled by mutableStateOf(true)
    private val animationStates = mutableMapOf<CompanionStatus, Any>()

    @Test
    fun fadeKeepsOutgoingAndIncomingAnimationState() {
        show()
        val normal = animationStates.getValue(CompanionStatus.NORMAL)

        update { status = CompanionStatus.HUNGRY }
        compose.mainClock.advanceTimeBy(80)
        assertSame(normal, animationStates.getValue(CompanionStatus.NORMAL))
        val hungry = animationStates.getValue(CompanionStatus.HUNGRY)

        compose.mainClock.advanceTimeBy(240)
        compose.waitForIdle()
        assertSame(hungry, animationStates.getValue(CompanionStatus.HUNGRY))
    }

    @Test
    fun reversingFadeKeepsBothAnimationStates() {
        show()
        update { status = CompanionStatus.HUNGRY }
        compose.mainClock.advanceTimeBy(80)
        val normal = animationStates.getValue(CompanionStatus.NORMAL)
        val hungry = animationStates.getValue(CompanionStatus.HUNGRY)

        update { status = CompanionStatus.NORMAL }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        assertSame(normal, animationStates.getValue(CompanionStatus.NORMAL))
        assertSame(hungry, animationStates.getValue(CompanionStatus.HUNGRY))
    }

    @Test
    fun thirdStatusKeepsDominantPoseAnimationState() {
        show()
        update { status = CompanionStatus.HUNGRY }
        compose.mainClock.advanceTimeBy(144)
        val hungry = animationStates.getValue(CompanionStatus.HUNGRY)

        update { status = CompanionStatus.SICK }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertSame(hungry, animationStates.getValue(CompanionStatus.HUNGRY))
    }

    @Test
    fun reducingMotionKeepsTargetAnimationState() {
        show()
        update { status = CompanionStatus.HUNGRY }
        compose.mainClock.advanceTimeBy(80)
        val hungry = animationStates.getValue(CompanionStatus.HUNGRY)

        update { motionEnabled = false }
        assertSame(hungry, animationStates.getValue(CompanionStatus.HUNGRY))
        update { motionEnabled = true }
        assertSame(hungry, animationStates.getValue(CompanionStatus.HUNGRY))
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompanionStatusCrossfade(status, motionEnabled, Modifier.size(180.dp)) { renderedStatus ->
                // Sprite clocks and frame indices are remembered inside the pose content.
                val animationState = remember { Any() }
                SideEffect { animationStates[renderedStatus] = animationState }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun update(change: () -> Unit) {
        compose.runOnIdle {
            change()
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }
}
