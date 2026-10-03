package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h450dp-mdpi")
class MobiDepartureMotionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var departing by mutableStateOf(false)
    private var completions = 0

    @Test fun overlayIdleToDepartureKeepsBodySizeAndGround() = verifyHandoff(124)

    @Test fun enlargedIdleToDepartureKeepsBodySizeAndGround() = verifyHandoff(248)

    @Test fun returningToIdleCancelsDepartureCompletion() {
        show(124)
        updateDeparting(true)
        awaitExit()
        compose.mainClock.advanceTimeBy(320)
        updateDeparting(false)
        compose.mainClock.advanceTimeBy(2200)
        assertEquals(0, completions)
        compose.onNodeWithTag("mobi-animation-frame-normal", useUnmergedTree = true).assertExists()
    }

    private fun verifyHandoff(size: Int) {
        show(size)
        compose.mainClock.advanceTimeBy(640)
        val idle = bounds("$size-00-idle")
        updateDeparting(true)
        val loading = bounds("$size-01-loading")
        assertTrue("Keep live idle while the exit atlas loads", loading.width >= idle.width * 0.97f)
        awaitExit()
        compose.mainClock.advanceTimeBy(176)
        val first = bounds("$size-02-handoff")
        assertTrue("No width shrink: $idle -> $first", first.width >= idle.width * 0.95f)
        assertTrue("No height shrink: $idle -> $first", first.height >= idle.height * 0.97f)
        assertTrue("No oversize jump: $idle -> $first", first.height <= idle.height * 1.06f)
        assertTrue("Ground stays aligned: $idle -> $first", kotlin.math.abs(first.bottom - idle.bottom) <= size * 0.03f)
        for (frame in 3..27) {
            compose.mainClock.advanceTimeBy(64)
            bounds("$size-%02d-motion".format(frame))
        }
        compose.mainClock.advanceTimeBy(300)
        val final = bounds("$size-28-finished")
        assertEquals(1, completions)
        assertEquals(0, final.width)
    }

    private fun show(size: Int) {
        MobiSpriteCache.getOrLoad(ApplicationProvider.getApplicationContext())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            Box(Modifier.size(500.dp, 360.dp), contentAlignment = Alignment.Center) {
                PetAvatar(
                    Modifier.size(size.dp).testTag("avatar"),
                    friendId = "friend:mobi",
                    isDisappearing = departing,
                    onDisappeared = { completions++ },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun updateDeparting(value: Boolean) {
        compose.runOnIdle {
            departing = value
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun awaitExit() {
        compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeByFrame()
            compose
                .onAllNodesWithTag(
                    "mobi-departure-sprite",
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private data class BodyBounds(
        val width: Int,
        val height: Int,
        val bottom: Int,
    )

    private fun bounds(name: String): BodyBounds {
        val area = compose.onNodeWithTag("avatar").fetchSemanticsNode().boundsInRoot
        var bounds = BodyBounds(0, 0, 0)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reports/mobi-departure/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            var left = area.right.toInt()
            var top = area.bottom.toInt()
            var right = area.left.toInt() - 1
            var bottom = area.top.toInt() - 1
            for (y in area.top.toInt() until area.bottom.toInt()) {
                for (x in area.left.toInt() until area.right.toInt()) {
                    if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 180) {
                        left = minOf(left, x)
                        top = minOf(top, y)
                        right = maxOf(right, x)
                        bottom = maxOf(bottom, y)
                    }
                }
            }
            if (right >= left) bounds = BodyBounds(right - left + 1, bottom - top + 1, bottom - area.top.toInt())
            bitmap.recycle()
        }
        return bounds
    }
}
