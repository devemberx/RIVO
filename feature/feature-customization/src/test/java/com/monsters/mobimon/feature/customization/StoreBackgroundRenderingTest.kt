package com.monsters.mobimon.feature.customization

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.core.ui.companionBackgroundRes
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class StoreBackgroundRenderingTest {
    // Queue IO completions on the test scheduler instead of resuming effects on worker threads.
    @get:Rule val compose = createComposeRule(effectContext = StandardTestDispatcher())

    @Test
    fun cityPreviewAndCatalogArtworkFollowTheSameVssPeriods() {
        val cityId = "background:cyberpunk_city"
        val period = mutableStateOf("Morning")
        lateinit var view: View
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            MobiMonTheme {
                CustomizationScreen(
                    inventory =
                        CosmeticInventory(
                            setOf("friend:mobi", cityId),
                            mapOf(
                                CosmeticSlot.FRIEND to "friend:mobi",
                                CosmeticSlot.BACKGROUND to cityId,
                            ),
                        ),
                    catalog =
                        listOf(
                            CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                            CosmeticItem(cityId, CosmeticSlot.BACKGROUND, 400),
                        ),
                    selectedItemId = cityId,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 1200,
                    pointLoadFailed = false,
                    timeOfDay = period.value,
                )
            }
        }
        compose.onNodeWithTag("store-tab-BACKGROUND").performClick()
        compose.onAllNodesWithText("사이버펑크 시티")[0].assertIsDisplayed()
        listOf("Sunrise", "Morning", "Day", "Afternoon", "Sunset", "Night", "Midnight").forEach { value ->
            compose.runOnIdle { period.value = value }
            compose.onNodeWithTag("store-preview-particles").assertDoesNotExist()
            awaitBackground(companionBackgroundRes(value, cityId), count = 2)
            capture(view, "cyberpunk-${value.lowercase()}")
            listOf("preview-background", "store-artwork-$cityId").forEach { tag ->
                val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                compose.runOnIdle {
                    val actual = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(actual))
                    val source = BitmapFactory.decodeResource(view.resources, companionBackgroundRes(value, cityId))
                    val scale = maxOf(bounds.width / source.width, bounds.height / source.height)
                    val left = bounds.left + (bounds.width - source.width * scale) / 2
                    val top = bounds.top + (bounds.height - source.height * scale) / 2
                    val expected = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    Canvas(expected).drawBitmap(
                        source,
                        null,
                        RectF(left, top, left + source.width * scale, top + source.height * scale),
                        Paint(Paint.FILTER_BITMAP_FLAG),
                    )
                    listOf(0.25f to 0.25f, 0.5f to 0.5f, 0.75f to 0.65f).forEach { (x, y) ->
                        val px = (bounds.left + x * bounds.width).toInt()
                        val py = (bounds.top + y * bounds.height).toInt()
                        val wanted = expected.getPixel(px, py)
                        val rendered = actual.getPixel(px, py)
                        listOf(Color::red, Color::green, Color::blue).forEach { channel ->
                            assertTrue(
                                "$tag renders $value at ($px, $py): expected $wanted, actual $rendered",
                                kotlin.math.abs(channel(wanted) - channel(rendered)) <= 25,
                            )
                        }
                    }
                    source.recycle()
                    expected.recycle()
                    actual.recycle()
                }
            }
        }
    }

    private fun awaitBackground(
        id: Int,
        count: Int = 1,
    ) {
        compose.waitUntil(10_000) {
            compose
                .onAllNodesWithTag("store-background-ready-$id", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size == count
        }
    }

    private fun capture(
        view: View,
        name: String,
    ) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/store-ui").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
