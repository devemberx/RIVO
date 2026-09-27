package com.monsters.mobimon.feature.vehicle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleWarning
import com.monsters.mobimon.core.domain.WarningSeverity
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
class VehicleReviewTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var view: View

    @Test
    fun enlargedTextKeepsMetricValuesAndBadgesVisible() {
        show({ samples().first().second }, fontScale = 1.5f)

        listOf(
            "정상",
            "확인된 공기압 상태",
            "주의 경고 없음",
            "앞유리 워셔액 잔량",
        ).forEach(::assertTextFullyVisible)
    }

    @Test
    fun enlargedTextKeepsMissingReadingsAndSignalAgesVisible() {
        show({ staleSnapshot() }, fontScale = 1.5f)

        listOf(
            "배터리 정보가 오래되었어요",
            "마지막 확인: 1분 전",
            "마지막 확인: 19초 전",
            "타이어 정보 없음",
            "운전자 보조 정보 없음",
        ).forEach(::assertTextFullyVisible)
    }

    @Test
    fun referenceLayoutKeepsBothSignalAgesVisible() {
        show({ staleSnapshot() }, fontScale = 1f)

        assertTextFullyVisible("마지막 확인: 1분 전")
        assertTextFullyVisible("마지막 확인: 19초 전")
    }

    @Test
    fun referenceMetricCardsUseAvailableVerticalSpace() {
        show({ samples().first().second }, fontScale = 1f)

        val reference = compose.onNodeWithTag("vehicle-reference").getUnclippedBoundsInRoot()
        val finalCardText = compose.onNodeWithText("주의 경고 없음").getUnclippedBoundsInRoot()

        assertTrue(
            "Final metric row should settle near the lower content area",
            finalCardText.bottom > reference.bottom - 260.dp,
        )
    }

    @Test
    fun referenceCardContentMatchesFigmaVerticalPositions() {
        show({ samples().first().second }, fontScale = 1f)

        val card = compose.onNodeWithTag("vehicle-card-slot-1").getUnclippedBoundsInRoot()
        val supporting = compose.onNodeWithText("배터리 82%", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(228f, (supporting.top - card.top).value, 8f)
    }

    @Test
    fun referenceMetricCardsKeepNavigationBarClearance() {
        show({ samples().first().second }, fontScale = 1f)

        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val panelRgb = 0x142A42
            val bottomClearanceStart = (bitmap.height - 16).coerceAtLeast(0)
            var panelPixels = 0
            for (y in bottomClearanceStart until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    if (bitmap.getPixel(x, y) and 0xFFFFFF == panelRgb) {
                        panelPixels += 1
                    }
                }
            }
            bitmap.recycle()
            assertEquals("Metric cards must clear the bottom navigation bar area", 0, panelPixels)
        }
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1125dp-h1200dp-mdpi")
    fun compactWideMetricRowsUseEqualCardHeights() {
        show({ samples().first().second }, fontScale = 1f)

        val topRowCard = compose.onNodeWithTag("vehicle-card-slot-1").getUnclippedBoundsInRoot()
        val bottomRowCard = compose.onNodeWithTag("vehicle-card-slot-4").getUnclippedBoundsInRoot()

        assertEquals(
            "Metric card rows should use matching heights",
            (topRowCard.bottom - topRowCard.top).value,
            (bottomRowCard.bottom - bottomRowCard.top).value,
            1f,
        )
    }

    @Test
    fun referenceStatesProduceReviewImages() {
        renderReviewImages("reference", fontScale = 1f)
    }

    @Test
    fun enlargedTextStatesProduceReviewImages() {
        renderReviewImages("enlarged-text", fontScale = 1.5f)
    }

    @Test
    fun cardSelectorProducesReviewImage() {
        show({ samples().first().second }, fontScale = 1f)
        compose.onNodeWithTag("vehicle-card-slot-1").performTouchInput { longClick() }
        compose.onNodeWithTag("vehicle-card-selector").assertIsDisplayed()
        val directory = File("build/reports/vehicle-ui").apply { mkdirs() }
        capture(view, File(directory, "reference-card-selector.png"))
    }

    private fun renderReviewImages(
        variant: String,
        fontScale: Float,
    ) {
        val samples = samples()
        var snapshot by mutableStateOf(samples.first().second)
        show({ snapshot }, fontScale)
        val directory = File("build/reports/vehicle-ui").apply { mkdirs() }
        samples.forEach { (name, sample) ->
            compose.runOnIdle { snapshot = sample }
            if (variant == "enlarged-text") {
                compose.onNodeWithTag("vehicle-status-banner").performScrollTo()
            }
            compose.onNodeWithText("차량 상태").assertIsDisplayed()
            capture(view, File(directory, "$variant-$name.png"))
            if (variant == "enlarged-text") {
                compose.onNodeWithTag("vehicle-card-slot-6").performScrollTo()
                capture(view, File(directory, "$variant-$name-metrics.png"))
            }
        }
    }

    private fun show(
        snapshot: () -> VehicleSnapshot,
        fontScale: Float,
    ) {
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalMobiMonMotionEnabled provides false,
            ) {
                MobiMonTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        VehicleInfoScreen(snapshot = snapshot(), onBack = {}, onHome = {})
                    }
                }
            }
        }
    }

    private fun assertTextFullyVisible(text: String) {
        val nodes = compose.onAllNodesWithText(text, useUnmergedTree = true)
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("Text exists for $text", count > 0)
        repeat(count) { index ->
            val node =
                nodes[index]
                    .let {
                        runCatching { it.performScrollTo() }.getOrElse { _ -> it }
                    }.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Text layout exists for $text", layouts.isNotEmpty())
            val fullBounds = node.getUnclippedBoundsInRoot()
            val visibleBounds = node.fetchSemanticsNode().boundsInRoot
            layouts.forEach { layout ->
                assertFalse("Clipped text height: $text", layout.didOverflowHeight)
                assertTrue("Text has a rendered line: $text", layout.lineCount > 0)
                assertEquals("Missing characters: $text", text.length, layout.getLineEnd(layout.lineCount - 1, true))
                // Compose 1.6 String Text semantics rebuilds its paragraph at the parent's width.
                // Check actual line extents, not hasVisualOverflow's wider paragraph container.
                repeat(layout.lineCount) { line ->
                    assertFalse("Ellipsized text: $text", layout.isLineEllipsized(line))
                    assertTrue("Clipped line start: $text", layout.getLineLeft(line) >= -0.5f)
                    assertTrue("Clipped line end: $text", layout.getLineRight(line) <= layout.size.width + 0.5f)
                    assertTrue("Clipped line bottom: $text", layout.getLineBottom(line) <= layout.size.height + 0.5f)
                }
            }
            assertEquals(
                "Clipped width: $text",
                (fullBounds.right - fullBounds.left).value,
                visibleBounds.width,
                0.5f,
            )
            assertEquals(
                "Clipped height: $text",
                (fullBounds.bottom - fullBounds.top).value,
                visibleBounds.height,
                0.5f,
            )
        }
    }

    private fun staleSnapshot() = samples().last().second.copy(parkingAgeMillis = 19_000, batteryAgeMillis = 61_000)

    private fun capture(
        view: View,
        output: File,
    ) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }

    private fun samples(): List<Pair<String, VehicleSnapshot>> {
        val checked =
            VehicleSnapshot(
                id = "review",
                epoch = "review",
                sequence = 1,
                receivedAtMillis = 10_000,
                source = SignalSource.SIMULATED,
                drivingState = DrivingState.PARKED,
                quality = SignalQuality.VALID,
                batteryPercent = 82,
                tirePressureStatus = "OK",
                outsideTemperature = 23,
                isRaining = false,
                attentionLevel = 85,
                isEmergencyBraking = false,
                isDrowsy = false,
                isDistracted = false,
                isCharging = false,
                washerFluidLevel = 68,
            )
        val partial =
            checked.copy(
                tirePressureStatus = null,
                outsideTemperature = null,
                isRaining = null,
                attentionLevel = null,
                isEmergencyBraking = null,
                isDrowsy = null,
                isDistracted = null,
            )
        return listOf(
            "checked-items" to checked,
            "charging-required" to checked.copy(batteryPercent = 18),
            "tire-warning" to
                checked.copy(
                    batteryPercent = 65,
                    tirePressureStatus = "NG",
                    warnings =
                        listOf(
                            VehicleWarning(
                                item = "타이어",
                                location = "왼쪽 앞바퀴",
                                severity = WarningSeverity.CAUTION,
                                description = "왼쪽 앞바퀴 공기압을 확인해 주세요.",
                                nextAction = "타이어 공기압을 점검해 주세요.",
                                observedAtMillis = 10_000,
                            ),
                        ),
                ),
            "partial" to partial,
            "unavailable" to
                partial.copy(
                    drivingState = DrivingState.UNKNOWN,
                    quality = SignalQuality.UNAVAILABLE,
                    batteryPercent = null,
                ),
            "stale" to
                checked.copy(
                    quality = SignalQuality.STALE,
                    parkingAgeMillis = 61_000,
                    batteryAgeMillis = 61_000,
                ),
        )
    }
}
