package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w800dp-h300dp-mdpi")
class MobiMonParkingBadgeTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test
    fun restrictedBadgeUsesReferenceSizeAndPauseIcon() {
        compose.setContent {
            MobiMonTheme {
                view = LocalView.current
                MobiMonParkingStatusBadge(confirmed = false, modifier = Modifier.testTag("parking"))
            }
        }

        compose.onNodeWithContentDescription("주차 후 이용").assertWidthIsAtLeast(344.dp)
        val badgeBounds = compose.onNodeWithTag("parking").fetchSemanticsNode().boundsInRoot
        assertEquals(344f, badgeBounds.width, 1f)
        assertEquals(76f, badgeBounds.height, 1f)
        val iconBounds = compose.onNodeWithTag("parking-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val textBounds = compose.onNodeWithText("주차 후 이용", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(badgeBounds.center.x, (iconBounds.left + textBounds.right) / 2f, 1f)
        assertEquals(badgeBounds.center.y, iconBounds.center.y, 1f)
        assertEquals(badgeBounds.center.y, textBounds.center.y, 1f)
        val (background, hasIconPixels) = badgePixels()
        assertEquals(MobiMonColors.panel.toArgb(), background)
        assertTrue(hasIconPixels)
    }

    @Test
    fun confirmedBadgeCentersParkingIconAndLabel() {
        compose.setContent {
            MobiMonTheme {
                view = LocalView.current
                MobiMonParkingStatusBadge(confirmed = true, modifier = Modifier.testTag("parking"))
            }
        }

        compose.onNodeWithContentDescription("주차 확인됨").assertWidthIsAtLeast(344.dp)
        val badgeBounds = compose.onNodeWithTag("parking").fetchSemanticsNode().boundsInRoot
        assertEquals(344f, badgeBounds.width, 1f)
        assertEquals(76f, badgeBounds.height, 1f)
        val textBounds = compose.onNodeWithText("주차 확인됨", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val iconBounds = compose.onNodeWithTag("parking-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(badgeBounds.center.x, (iconBounds.left + textBounds.right) / 2f, 1f)
        assertEquals(badgeBounds.center.y, iconBounds.center.y, 1f)
        assertEquals(badgeBounds.center.y, textBounds.center.y, 1f)
        val (background, hasIconPixels) = badgePixels()
        assertEquals(MobiMonColors.panel.toArgb(), background)
        assertTrue(hasIconPixels)
    }

    private fun badgePixels(): Pair<Int, Boolean> {
        val bounds = compose.onNodeWithTag("parking").fetchSemanticsNode().boundsInRoot
        var sample = 0
        var hasIconPixels = false
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val left = bounds.left.toInt()
            val top = bounds.top.toInt()
            sample = bitmap.getPixel(left + 50, top + 38)
            hasIconPixels =
                ((top + 18) until (top + 58)).any { y ->
                    ((left + 62) until (left + 102)).any { x ->
                        bitmap.getPixel(x, y) != sample
                    }
                }
            bitmap.recycle()
        }
        return sample to hasIconPixels
    }
}
