package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
@Config(sdk = [34], qualifiers = "w2048dp-h512dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HappyArtworkAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun everyHappyLookSharesTheFloorAndLunaCapKeepsTheSameBodyPixels() {
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
        lateinit var view: View
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                MobiMonTheme {
                    Row(Modifier.padding(top = 64.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        looks.forEachIndexed { index, (friend, accessory) ->
                            PetAvatar(
                                Modifier.size(256.dp).testTag("happy-$index"),
                                friendId = friend,
                                accessoryId = accessory,
                                emotion = PetEmotion.HAPPY,
                            )
                        }
                    }
                }
            }
        }
        val bounds = looks.indices.map { compose.onNodeWithTag("happy-$it").fetchSemanticsNode().boundsInRoot }
        lateinit var image: Bitmap
        compose.runOnIdle {
            image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
        }
        try {
            val floors =
                bounds.mapIndexed { index, box ->
                    val last =
                        (box.top.toInt() until box.bottom.toInt()).last { y ->
                            (box.left.toInt() until box.right.toInt()).any { x ->
                                android.graphics.Color.alpha(image.getPixel(x, y)) > 128
                            }
                        } + 1
                    assertTrue("Look $index ground: $last", abs(last - (box.top + 256 * 0.94f)) <= 2)
                    last
                }
            assertTrue("All happy looks share a floor: $floors", floors.max() - floors.min() <= 2)
            val normal = bounds[3]
            val hat = bounds[4]
            var changes = 0
            var compared = 0
            // Compare the shared belly/paws below the facial and equipment edits.
            for (y in 160 until 225) {
                for (x in 84 until 176) {
                    val before = image.getPixel(normal.left.toInt() + x, normal.top.toInt() + y)
                    val after = image.getPixel(hat.left.toInt() + x, hat.top.toInt() + y)
                    if (before != after) changes++
                    compared++
                }
            }
            assertTrue("Cap must not resize the body: $changes/$compared", changes < compared / 100)
            val output = File("build/reports/happy-artwork-alignment/all-looks.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            image.recycle()
        }
    }
}
