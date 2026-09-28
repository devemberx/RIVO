package com.monsters.mobimon.feature.pet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w500dp-h300dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeSpeechBubbleTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val motion = mutableStateOf(true)
    private val visible = mutableStateOf(true)
    private val revision = mutableStateOf(0)

    @Test
    fun entranceAnimatesOnceAndReplaysOnlyAfterReentry() {
        show()
        val entering = pixels()
        compose.mainClock.advanceTimeBy(120)
        val middle = pixels()
        compose.mainClock.advanceTimeBy(900)
        val settled = pixels()
        assertNotEquals("Bubble must animate on entry", entering, middle)
        assertNotEquals("Bubble must settle after its pop", middle, settled)
        update { revision.value++ }
        compose.mainClock.advanceTimeBy(500)
        assertEquals("Unrelated state must not restart the entrance", settled, pixels())
        update { visible.value = false }
        update { visible.value = true }
        assertNotEquals("Returning Home starts a new entrance", settled, pixels())
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("home-companion-message").fetchSemanticsNode()
    }

    @Test
    fun reducedMotionDisplaysImmediatelyAndDoesNotReplayWhenEnabled() {
        motion.value = false
        show()
        val first = pixels()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(first, pixels())
        update { motion.value = true }
        compose.mainClock.advanceTimeBy(200)
        val enabled = pixels()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(enabled, pixels())
    }

    @Test
    fun enablingReducedMotionDuringEntranceSnapsToSettledBubble() {
        show()
        compose.mainClock.advanceTimeBy(80)
        val entering = pixels()
        update { motion.value = false }
        compose.mainClock.advanceTimeBy(32)
        val stopped = pixels()
        assertNotEquals(entering, stopped)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(stopped, pixels())
    }

    @Test
    @Config(qualifiers = "ko-rKR-w800dp-h600dp-mdpi")
    fun enlargedTextLetsTheSpeechBubbleFollowItsContent() {
        motion.value = false
        show(fontScale = 1.5f)
        val bounds = compose.onNodeWithTag("home-companion-message").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width in 390f..800f)
        assertTrue(bounds.height >= 195f)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w800dp-h600dp-mdpi")
    fun messageLengthChangesOnlyTheBubbleBoundsAndKeepsOriginalTypography() {
        val message = mutableStateOf("안녕!")
        compose.setContent {
            MobiMonTheme {
                Box(Modifier.fillMaxSize()) {
                    HomeSpeechBubbleContent(message.value)
                }
            }
        }
        val short = compose.onNodeWithTag("home-companion-message").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            message.value = "오늘은 같이 이야기를 나누고 싶어. 네가 어떤 하루를 보냈는지 천천히 들려줘! " +
                "조금 더 오래 함께 이야기하고 싶어."
        }
        val long = compose.onNodeWithTag("home-companion-message").fetchSemanticsNode().boundsInRoot

        assertEquals(short.left, long.left, 1f)
        assertEquals(short.top, long.top, 1f)
        assertTrue(long.width > short.width)
        assertTrue(long.height > short.height)
        assertTrue(long.width <= 560f)
        compose
            .onNodeWithTag("home-companion-message-text", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                val layout = layouts.single()
                assertEquals(32.4f.sp, layout.layoutInput.style.fontSize)
                assertEquals(43.2f.sp, layout.layoutInput.style.lineHeight)
                assertEquals(FontWeight.Normal, layout.layoutInput.style.fontWeight)
                assertTrue(layout.lineCount >= 3)
                repeat(layout.lineCount) { line ->
                    assertTrue(!layout.isLineEllipsized(line))
                    assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                    assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f)
                }
            }
    }

    @Test
    @Config(qualifiers = "ko-rKR-w800dp-h600dp-mdpi")
    fun wrappedDialogueKeepsTailInPlaceAndCentersBothLines() {
        val message = mutableStateOf("짠! 모비 준비 완료! 뭐 할까?")
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            MobiMonTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    HomeSpeechBubbleContent(message.value)
                }
            }
        }

        fun tailStart(): Int {
            val bounds = compose.onNodeWithTag("home-companion-message").fetchSemanticsNode().boundsInRoot
            var start = -1
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val row = (bounds.bottom - 5).toInt()
                start =
                    (bounds.left.toInt() until bounds.right.toInt()).first { x ->
                        bitmap.getPixel(x, row) != android.graphics.Color.BLACK
                    } - bounds.left.toInt()
                bitmap.recycle()
            }
            return start
        }

        val short = compose.onNodeWithTag("home-companion-message").fetchSemanticsNode().boundsInRoot
        val originalTailStart = tailStart()
        captureReview("short")
        compose.runOnIdle { message.value = "히히, 찌르니까 간지러워! 무슨 일 있어?" }
        val wrapped = compose.onNodeWithTag("home-companion-message").fetchSemanticsNode().boundsInRoot
        captureReview("wrapped")

        assertEquals(short.left, wrapped.left, 1f)
        assertEquals(short.top, wrapped.top, 1f)
        assertEquals(130f, short.height, 2f)
        assertEquals(173.2f, wrapped.height, 2f)
        assertEquals(43.2f, wrapped.height - short.height, 2f)
        assertEquals(originalTailStart, tailStart())
        compose
            .onNodeWithTag("home-companion-message-text", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                val layout = layouts.single()
                assertEquals(2, layout.lineCount)
                val firstWidth = layout.getLineRight(0) - layout.getLineLeft(0)
                val secondWidth = layout.getLineRight(1) - layout.getLineLeft(1)
                assertTrue(secondWidth >= firstWidth * 0.4f)
                repeat(layout.lineCount) { line ->
                    assertTrue(abs(layout.getLineLeft(line) - (layout.size.width - layout.getLineRight(line))) <= 1f)
                }
            }
    }

    private fun captureReview(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/home-speech-bubble").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }

    private fun show(fontScale: Float = 1f) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(
                LocalMobiMonMotionEnabled provides motion.value,
                LocalDensity provides Density(1f, fontScale),
            ) {
                MobiMonTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        // Read changing host state without changing the bubble's identity.
                        if (visible.value && revision.value >= 0) HomeSpeechBubble()
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun update(block: () -> Unit) {
        compose.runOnIdle {
            block()
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun pixels(): List<Int> {
        lateinit var pixels: List<Int>
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            pixels =
                IntArray(
                    view.width * view.height,
                ).also { bitmap.getPixels(it, 0, view.width, 0, 0, view.width, view.height) }.toList()
            bitmap.recycle()
        }
        return pixels
    }
}
