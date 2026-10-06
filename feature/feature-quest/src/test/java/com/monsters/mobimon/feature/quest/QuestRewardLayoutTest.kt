package com.monsters.mobimon.feature.quest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Assert.assertEquals
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
@Config(sdk = [34], qualifiers = "ko-rKR-w1120dp-h940dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuestRewardLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var friend by mutableStateOf("friend:luna")
    private var accessory by mutableStateOf<String?>(null)
    private var fontScale by mutableStateOf(1f)
    private var bonus by mutableStateOf(0L)
    private var pop by mutableStateOf(1f)
    private var confirmations = 0

    private fun render() {
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(
                LocalMobiMonMotionEnabled provides false,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MobiMonTheme {
                    Box(Modifier.fillMaxSize()) {
                        QuestRewardSuccessContent(
                            points = 15 + bonus,
                            bonusPoints = bonus,
                            friendId = friend,
                            accessoryId = accessory,
                            outfitId = null,
                            backgroundId = null,
                            modalScale = 1f,
                            onConfirm = { confirmations++ },
                            avatarScale = { pop },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun everyLookKeepsCapClearanceFloorAndCommonFooter() {
        render()
        assertEquals(
            "Standard text keeps the selected reference size",
            320f,
            bounds("quest-reward-character").width,
            0.01f,
        )
        val looks =
            listOf(
                "friend:mobi" to null,
                "friend:mobi" to "accessory:mobi_headphones",
                "friend:mobi" to "accessory:mobi_goggles",
                "friend:luna" to null,
                "friend:luna" to "accessory:luna_cap",
                "friend:luna" to "accessory:luna_sunglasses",
                "friend:las" to null,
            )
        val floors = mutableListOf<Int>()
        val footer = bounds("quest-modal-btn-confirm")
        looks.forEachIndexed { index, (id, item) ->
            compose.runOnIdle {
                friend = id
                accessory = item
            }
            val area = bounds("quest-reward-artwork-area")
            val badge = bounds("quest-reward-badge")
            val title = bounds("quest-reward-title")
            val artwork = artworkBounds(area, "look-$index")
            assertTrue("Equipment clears badge: $id/$item", artwork.top >= badge.bottom + 8f)
            assertTrue("Body clears title: $id/$item", artwork.bottom <= title.top - 8f)
            floors += artwork.bottom.toInt()
            assertEquals(footer, bounds("quest-modal-btn-confirm"))
        }
        assertTrue("All character bottoms match: $floors", floors.max() - floors.min() <= 2)
        compose.runOnIdle { bonus = 3 }
        assertEquals(footer, bounds("quest-modal-btn-confirm"))
        compose.onAllNodesWithText("날씨 보너스", substring = true, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("quest-modal-btn-confirm").performClick()
        assertEquals(1, confirmations)
    }

    @Test
    fun largeTextAndEntranceOvershootKeepArtworkAndLabelsInside() {
        render()
        for (textScale in listOf(1f, 2f)) {
            for (weatherBonus in listOf(0L, 3L)) {
                compose.runOnIdle {
                    fontScale = textScale
                    bonus = weatherBonus
                    accessory = "accessory:luna_cap"
                    pop = 1.02f
                }
                val modal = bounds("quest-reward-success-modal")
                val badge = bounds("quest-reward-badge")
                val artwork =
                    artworkBounds(bounds("quest-reward-artwork-area"), "cap-font-$textScale-bonus-$weatherBonus")
                val title = bounds("quest-reward-title")
                val subtitle = bounds("quest-reward-subtitle")
                val points = bounds("quest-reward-points")
                val button = bounds("quest-modal-btn-confirm")
                assertTrue("Cap clears badge at font $textScale", artwork.top >= badge.bottom + 8)
                assertTrue(artwork.bottom < title.top)
                assertTrue(title.bottom < subtitle.top)
                assertTrue(subtitle.bottom < points.top)
                assertTrue(points.bottom < button.top)
                assertTrue(button.bottom < modal.bottom)
                if (weatherBonus > 0) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    compose
                        .onNodeWithText("보상 · 18 P (날씨 보너스 +3 P)", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals("Bonus details stay together at font $textScale", 1, layouts.single().lineCount)
                }
                for (tag in listOf(
                    "quest-reward-title",
                    "quest-reward-subtitle",
                    "quest-reward-points",
                    "quest-modal-btn-confirm",
                )) {
                    compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
                }
            }
        }
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun artworkBounds(
        area: Rect,
        name: String,
    ): Rect {
        lateinit var image: Bitmap
        compose.runOnIdle {
            image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
        }
        try {
            // Exclude faint edge residue while recognizing dark soles as well as bright paws.
            val panel = image.getPixel(area.left.toInt(), area.top.toInt())
            val visible = mutableListOf<Pair<Int, Int>>()
            for (y in area.top.toInt() until area.bottom.toInt()) {
                for (x in area.left.toInt() until area.right.toInt()) {
                    val pixel = image.getPixel(x, y)
                    val distance =
                        abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(panel)) +
                            abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(panel)) +
                            abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(panel))
                    if (distance > 64) visible += x to y
                }
            }
            assertTrue("Artwork must be rendered", visible.isNotEmpty())
            val output = File("build/reports/quest-reward-layout/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return Rect(
                visible.minOf { it.first }.toFloat(),
                visible.minOf { it.second }.toFloat(),
                visible.maxOf { it.first } + 1f,
                visible.maxOf { it.second } + 1f,
            )
        } finally {
            image.recycle()
        }
    }
}
