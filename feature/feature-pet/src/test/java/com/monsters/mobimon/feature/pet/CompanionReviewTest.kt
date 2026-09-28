package com.monsters.mobimon.feature.pet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.monsters.mobimon.core.domain.CompanionSettings
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.PetProfile
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.core.ui.companionBackgroundRes
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
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanionReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingContentHeightKeepsTheBackgroundHorizonAnchored() {
        val source = IntSize(2560, 1440)
        val original = HomeBackgroundAlignment.align(source, IntSize(2560, 1268), LayoutDirection.Ltr)
        val resized = HomeBackgroundAlignment.align(source, IntSize(2560, 1184), LayoutDirection.Ltr)
        assertEquals(IntOffset(0, -106), original)
        assertEquals(original, resized)
        assertEquals(
            IntOffset(0, -74),
            HomeBackgroundAlignment.align(
                IntSize(1792, 1008),
                IntSize(1792, 829),
                LayoutDirection.Ltr,
            ),
        )
    }

    @Test fun homeReferenceRender() {
        homeRender("Night")
        val avatar = compose.onNodeWithContentDescription("Mobi 강아지").fetchSemanticsNode().boundsInRoot
        assertEquals(980f, avatar.left, 1f)
        assertEquals(372f, avatar.top, 1f)
        assertEquals(600f, avatar.width, 1f)
        assertEquals(avatar.width, avatar.height, 1f)
        val menu = compose.onNodeWithContentDescription("메뉴 열기").fetchSemanticsNode().boundsInRoot
        assertEquals(72f, menu.left, 1f)
        assertEquals(36f, menu.top, 1f)
        assertEquals(104f, menu.width, 1f)
        val bubble = compose.onNodeWithTag("home-companion-message").fetchSemanticsNode().boundsInRoot
        val phrase = compose.onNodeWithTag("home-ambient-text").fetchSemanticsNode().boundsInRoot
        assertEquals(1280f, (phrase.left + phrase.right) / 2f, 1f)
        assertEquals(272f, phrase.top, 1f)
        assertEquals(1576f, bubble.left, 1f)
        assertEquals(500f, bubble.top, 1f)
        assertTrue("Bubble width adapts to its message", bubble.width in 260f..560f)
        assertTrue("Bubble keeps its minimum height", bubble.height >= 130f)
        assertSpeechBubbleTextAndProportions()
        val action = compose.onNodeWithTag("home-conversation-action").fetchSemanticsNode().boundsInRoot
        assertEquals(1013.6f, action.left, 1f)
        assertEquals(1048f, action.top, 1f)
        assertEquals(532.8f, action.width, 1f)
        assertEquals(100.8f, action.height, 1f)
    }

    @Test
    fun sharedVehicleWarningChangesHomeArtworkWithoutMovingItsSlot() {
        val warning = mutableStateOf(false)
        val view = render("home-warning-before") { ReviewHome("Night", warning.value) }
        val bounds = compose.onNodeWithContentDescription("Mobi 강아지").fetchSemanticsNode().boundsInRoot

        fun avatarPixels(): Int {
            var hash = 0
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                for (y in 480 until 960 step 24) {
                    for (x in 1040 until 1520 step 24) {
                        hash = hash * 31 + bitmap.getPixel(x, y)
                    }
                }
                bitmap.recycle()
            }
            return hash
        }
        val normal = avatarPixels()
        compose.runOnIdle { warning.value = true }
        compose.waitUntil(5000) { avatarPixels() != normal }
        assertEquals(bounds, compose.onNodeWithContentDescription("Mobi 강아지").fetchSemanticsNode().boundsInRoot)
        capture(view, "home-warning-collapsed")
        compose.runOnIdle { warning.value = false }
        compose.waitUntil(5000) { avatarPixels() == normal }
        assertEquals(bounds, compose.onNodeWithContentDescription("Mobi 강아지").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun sunriseHomeReferenceRender() = homeRender("Sunrise")

    @Test fun morningHomeReferenceRender() = homeRender("Morning")

    @Test fun dayHomeReferenceRender() = homeRender("Day")

    @Test fun afternoonHomeReferenceRender() = homeRender("Afternoon")

    @Test fun sunsetHomeReferenceRender() = homeRender("Sunset")

    @Test fun midnightHomeReferenceRender() = homeRender("Midnight")

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h893dp-mdpi")
    fun aaosContentKeepsSpeechBubbleTextAndProportions() {
        val view = render("home-aaos-content") { ReviewHome("Night") }
        compose.runOnIdle {
            assertEquals(1792, view.width)
            assertEquals(829, view.height)
        }
        compose.onNodeWithTag("home-companion-message").assertIsDisplayed()
        assertSpeechBubbleTextAndProportions()
    }

    @Test
    fun enlargedTextHomeUpdatesAllBackgroundsWithoutRecreatingContent() {
        val time = mutableStateOf("Morning")
        lateinit var view: View
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(
                LocalMobiMonMotionEnabled provides false,
                LocalDensity provides Density(1f, 1.5f),
            ) {
                MobiMonTheme { ReviewHome(time.value) }
            }
        }
        val skyColors = mutableSetOf<Int>()
        listOf("Sunrise", "Morning", "Day", "Afternoon", "Sunset", "Night", "Midnight").forEach { period ->
            compose.runOnIdle { time.value = period }
            assertLightlyTintedCrop(view, period)
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                skyColors += bitmap.getPixel(12, 12)
                val directory = File("build/reports/companion-ui").apply { mkdirs() }
                File(directory, "home-enlarged-text-${period.lowercase()}.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
            }
        }
        assertEquals("Each period must render its own background", 7, skyColors.size)
        compose.onNodeWithTag("home-companion-message").performScrollTo().assertIsDisplayed()
        assertSpeechBubbleTextAndProportions()
        capture(view, "home-enlarged-text-bubble")
        compose.onNodeWithTag("home-conversation-action").performScrollTo().assertIsDisplayed()
        capture(view, "home-enlarged-text-action")
    }

    private fun homeRender(period: String) {
        val view = render(if (period == "Night") "home" else "home-${period.lowercase()}") { ReviewHome(period) }
        assertLightlyTintedCrop(view, period)
    }

    private fun assertSpeechBubbleTextAndProportions() {
        compose
            .onNodeWithTag("home-companion-message-text", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                val results = mutableListOf<TextLayoutResult>()
                it(results)
                val layout = results.single()
                assertTrue("Message uses at least one natural line", layout.lineCount >= 1)
                repeat(layout.lineCount) { line ->
                    assertTrue("No message line is truncated", !layout.isLineEllipsized(line))
                    assertTrue("Each line fits horizontally", layout.getLineRight(line) <= layout.size.width + 1f)
                    assertTrue("Each line fits vertically", layout.getLineBottom(line) <= layout.size.height + 1f)
                }
            }
        val bubble = compose.onNodeWithTag("home-companion-message").fetchSemanticsNode().boundsInRoot
        assertTrue("Bubble keeps measurable adaptive bounds", bubble.width > 0f && bubble.height > 0f)
    }

    @Composable
    private fun ReviewHome(
        period: String,
        warning: Boolean = false,
    ) {
        PetHomeScreen(
            profile = PetProfile("review"),
            snapshot =
                VehicleSnapshot(
                    "review",
                    "epoch",
                    1,
                    1000,
                    SignalSource.SIMULATED,
                    DrivingState.PARKED,
                    SignalQuality.VALID,
                    72,
                    timeOfDay = "Night",
                    isDrowsy = warning,
                ),
            onOpenMenu = {},
            onPetClick = {},
            pointBalance = 1200,
            interactionAllowed = true,
            connectionAvailable = true,
            backgroundTimeOfDay = period,
        )
    }

    @Test fun settingsReferenceRender() {
        render("settings") {
            SettingsScreen(
                CompanionSettings(),
                {},
                debugModeAvailable = true,
                parkedVerified = true,
                onOpenCopilot = {},
                onLauncherCharacterChange = {},
            )
        }
        compose.onNodeWithText("앱 밖의 화면 위에 캐릭터를 표시해요.").assertIsDisplayed()
        compose.onNodeWithText("차량 홈 캐릭터가 자동으로 돌아다니지 않게 해요.").assertIsDisplayed()
        val back = compose.onNodeWithTag("settings-back").fetchSemanticsNode().boundsInRoot
        assertEquals(72f, back.left, 1f)
        assertEquals(36f, back.top, 1f)
        assertEquals(104f, back.width, 1f)
        val title = compose.onNodeWithText("설정").fetchSemanticsNode().boundsInRoot
        assertEquals(208f, title.left, 1f)
        val done = compose.onNodeWithTag("settings-done").fetchSemanticsNode().boundsInRoot
        assertEquals(800f, done.left, 1f)
        assertEquals(996f, done.top, 1f)
        assertEquals(960f, done.width, 1f)
        assertEquals(100f, done.height, 1f)
        compose.onNodeWithTag("settings-done").assertIsDisplayed()
    }

    @Test
    fun settingsKeepsDoneReachableAtEnlargedText() {
        val view =
            render("settings-enlarged-text") {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                    SettingsScreen(CompanionSettings(reducedMotion = true), {
                    }, debugModeAvailable = true, parkedVerified = true, onOpenCopilot = {})
                }
            }
        compose.onNodeWithText("움직임 줄이기").performScrollTo().assertIsDisplayed()
        capture(view, "settings-enlarged-text-motion")
        compose.onNodeWithText("Debugger").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings-done").assertIsDisplayed()
        capture(view, "settings-enlarged-text-debugger")
    }

    private fun render(
        name: String,
        content: @Composable () -> Unit,
    ): View {
        lateinit var view: View
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) { MobiMonTheme(content = content) }
        }
        capture(view, name)
        return view
    }

    private fun assertLightlyTintedCrop(
        view: View,
        period: String,
    ) {
        compose.runOnIdle {
            val source = BitmapFactory.decodeResource(view.resources, companionBackgroundRes(period))
            val expected = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val actual = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val scale = maxOf(view.width.toFloat() / source.width, view.height.toFloat() / source.height)
            val width = (source.width * scale).roundToInt()
            val height = (source.height * scale).roundToInt()
            val left = ((view.width - width) / 2f).roundToInt().toFloat()
            val top = (-106f * view.width / 2560f).roundToInt().toFloat()
            Canvas(expected).drawBitmap(
                source,
                null,
                RectF(left, top, left + width, top + height),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            view.draw(Canvas(actual))
            val tint = Color.rgb(9, 21, 37)
            var visibleTint = false
            // Bound the effect against the cropped source, without coupling to exact gradient stops.
            for (x in listOf(12, view.width - 12)) {
                for (y in listOf(12, view.height / 3, view.height * 2 / 3, view.height - 12)) {
                    val wanted = expected.getPixel(x, y)
                    val rendered = actual.getPixel(x, y)
                    listOf(Color::red, Color::green, Color::blue).forEach { channel ->
                        val original = channel(wanted).toFloat()
                        val strongestTint = original + (channel(tint) - original) * 0.13f
                        assertTrue(
                            "$period keeps its crop and light tint at ($x, $y)",
                            channel(rendered).toFloat() in
                                (minOf(original, strongestTint) - 3f)..(maxOf(original, strongestTint) + 3f),
                        )
                        visibleTint = visibleTint || abs(channel(rendered) - original) > 3f
                    }
                }
            }
            if (period == "Day") assertTrue("Daylight receives a visible but subtle window tint", visibleTint)
            source.recycle()
            expected.recycle()
            actual.recycle()
        }
    }

    private fun capture(
        view: View,
        name: String,
    ) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/companion-ui").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }
}
