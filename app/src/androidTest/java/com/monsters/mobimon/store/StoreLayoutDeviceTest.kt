package com.monsters.mobimon.store

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.feature.customization.CustomizationScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class StoreLayoutDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var calls = 0
    private var selected by mutableStateOf<String?>("friend:luna")
    private var inventory by mutableStateOf(
        CosmeticInventory(
            setOf("friend:mobi", "friend:luna"),
            mapOf(
                CosmeticSlot.FRIEND to "friend:mobi",
            ),
        ),
    )
    private var parking by mutableStateOf(false)
    private val catalog =
        listOf(
            CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
            CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
            CosmeticItem("accessory:mobi_headphones", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
            CosmeticItem("accessory:mobi_goggles", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
            CosmeticItem("accessory:luna_sunglasses", CosmeticSlot.ACCESSORY, 300, "friend:luna"),
            CosmeticItem("background:star", CosmeticSlot.BACKGROUND, 200),
            CosmeticItem("background:snow", CosmeticSlot.BACKGROUND, 200),
            CosmeticItem("background:star_hanger", CosmeticSlot.BACKGROUND, 200),
        )

    private fun render(
        balance: Long = 1200,
        fontScale: Float = 1f,
        motionEnabled: Boolean = false,
    ) {
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(
                LocalMobiMonMotionEnabled provides motionEnabled,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MobiMonTheme {
                    CustomizationScreen(inventory, catalog, selected, false, false, {
                        selected = it
                    }, { _, _ ->
                        calls++
                    }, {}, {}, balance, false, parkingRequired = parking, interactionAllowed = !parking)
                }
            }
        }
    }

    @Test fun mobiUsesSpriteFramesInFriendAndClothesPreviews() {
        selected = "friend:mobi"
        render(motionEnabled = true)
        compose.onNodeWithTag("preview-character").assertContentDescriptionEquals("Mobi 토끼")
        assertEquals(
            2,
            compose.onAllNodesWithTag("mobi-animation-frame-normal", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        capture("friends-mobi-first-frame")
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithText("모비 헤드폰").performClick()
        compose.onNodeWithTag("mobi-animation-frame-headphones", useUnmergedTree = true).assertExists()
        capture("clothes-mobi-headphones-first-frame")
        compose.onNodeWithText("모비 고글").performClick()
        compose.onNodeWithTag("mobi-animation-frame-goggles", useUnmergedTree = true).assertExists()
        capture("clothes-mobi-goggles-first-frame")
    }

    @Test fun lunaIsCatAndAppearsInFriendAndClothesPreviews() {
        render(motionEnabled = true)
        compose.onNodeWithTag("preview-character").assertContentDescriptionEquals("Luna 고양이")
        assertTrue(
            compose
                .onAllNodesWithTag(
                    "luna-animation-frame-normal",
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                .size >=
                2,
        )
        capture("friends-luna-first-frame")
        awaitLunaFrame("normal")
        capture("friends-luna")
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithText("루나").performClick()
        compose.onNodeWithText("루나 선글라스").performClick()
        compose.onNodeWithTag("preview-character").assertContentDescriptionEquals("Luna 고양이")
        compose.onNodeWithText("300 P로 구매하기").assertIsEnabled()
        assertTrue(
            compose
                .onAllNodesWithTag(
                    "luna-animation-frame-sunglasses",
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                .isNotEmpty(),
        )
        capture("clothes-luna-first-frame")
        awaitLunaFrame("sunglasses")
        capture("clothes-luna-sunglasses")
    }

    @Test fun purchaseRequiresConfirmationAndCommittedOwnership() {
        render()
        awaitLunaFrame("normal")
        capture("friends")
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithText("모비 헤드폰").performClick()
        compose.onNodeWithTag("store-action").performClick()
        assertEquals(0, calls)
        capture("purchase-confirmation")
        compose.onNodeWithText("취소").performClick()
        capture("clothes")
        assertEquals(0, calls)
        compose.onNodeWithTag("store-action").performClick()
        compose.onNodeWithText("구매하기").performClick()
        assertEquals(1, calls)
        compose.onNodeWithTag("store-tab-FRIEND").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("store-tab-ACCESSORY").assertIsSelected()
        compose.onNodeWithTag("store-owned-filter").assertIsNotEnabled()
        assertEquals("accessory:mobi_headphones", selected)
        compose.onNodeWithText("구매하기").assertDoesNotExist()
        compose.runOnIdle {
            inventory =
                inventory.copy(ownedItemIds = inventory.ownedItemIds + "accessory:mobi_headphones")
        }
        compose.onNodeWithTag("store-action").assertIsEnabled()
        capture("purchase-complete")
    }

    @Test fun spaceCategoriesAndOwnedFilterKeepPreviewLocal() {
        render()
        compose.onNodeWithTag("store-tab-BACKGROUND").performClick()
        capture("backgrounds")
        compose.onNodeWithText("특수효과").performClick()
        compose.onNodeWithText("반짝이는 별").performClick()
        capture("effects")
        compose.onNodeWithTag("store-owned-filter").performClick()
        compose.onNodeWithText("반짝이는 별").assertDoesNotExist()
        capture("owned-filter")
        compose.onNodeWithTag("store-owned-filter").performClick()
        compose.onNodeWithText("소품").performClick()
        compose.onNodeWithText("별빛 모빌").performClick()
        capture("props")
        assertEquals(0, calls)
    }

    @Test fun shortfallAndParkingNeverSubmitPurchase() {
        render(100)
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithText("모비 헤드폰").performClick()
        compose.onNodeWithText("200 P 부족").assertIsNotEnabled()
        capture("insufficient-points")
        compose.runOnIdle { parking = true }
        compose.onNodeWithTag("parking-interruption-dialog").assertExists()
        capture("parking-required")
        assertEquals(0, calls)
    }

    @Test fun enlargedTextKeepsPurchaseAndCancelReachable() {
        render(fontScale = 2f)
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithTag("store-item-accessory:mobi_headphones").performScrollTo().performClick()
        compose.onNodeWithTag("store-action").performScrollTo().performClick()
        capture("large-text-confirmation")
        compose.onNodeWithText("취소").performScrollTo().performClick()
        assertEquals(0, calls)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        // AAOS exposes cluster displays too; capture the driver display explicitly.
        val displays =
            instrumentation.uiAutomation.executeShellCommand("dumpsys SurfaceFlinger --display-id").use {
                FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
            }
        val displayId = checkNotNull(Regex("Display (\\d+) .*port=0\\b").find(displays)).groupValues[1]
        val bitmap =
            instrumentation.uiAutomation.executeShellCommand("screencap -p -d $displayId").use {
                FileInputStream(it.fileDescriptor).use { stream -> checkNotNull(BitmapFactory.decodeStream(stream)) }
            }
        val dir = File(instrumentation.targetContext.filesDir, "test-screenshots/store").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun awaitLunaFrame(appearance: String) {
        compose.waitUntil(10_000) {
            compose
                .onAllNodesWithTag("luna-animation-frame-$appearance", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
