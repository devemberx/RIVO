package com.monsters.mobimon.feature.customization

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class StoreReferenceScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun parkingLossShowsCustomizationPopupWithHomeAction() {
        var parkingRequired by mutableStateOf(false)
        var home = false
        compose.setContent {
            MobiMonTheme {
                CustomizationScreen(
                    inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi")),
                    catalog = emptyList(),
                    selectedItemId = null,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 1200,
                    pointLoadFailed = false,
                    parkingRequired = parkingRequired,
                    onHome = { home = true },
                )
            }
        }

        compose.runOnIdle { parkingRequired = true }
        compose.onNodeWithText("주차 후 꾸미기를 이어가요").assertIsDisplayed()
        compose.onNodeWithTag("parking-interruption-home").performClick()
        assertTrue(home)
        compose.runOnIdle { parkingRequired = false }
        compose.onNodeWithTag("parking-interruption-dialog").assertDoesNotExist()
    }

    @Test fun sharedAppearanceDoesNotEnableActionsBeforeStoreInventoryLoads() {
        compose.mainClock.autoAdvance = false
        var storeInventoryReady by mutableStateOf(false)
        lateinit var view: View
        val inventory =
            CosmeticInventory(
                setOf("friend:mobi", "friend:luna"),
                mapOf(CosmeticSlot.FRIEND to "friend:mobi"),
            )
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
            )
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = catalog,
                    selectedItemId = "friend:luna",
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 1200,
                    pointLoadFailed = false,
                    storeInventoryReady = storeInventoryReady,
                )
            }
        }

        compose.onNodeWithTag("preview-character").assertExists()
        compose.onNodeWithText("아이템을 골라 주세요").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(240)
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(3)
        capture(view, "shared-appearance-loading")
        compose.runOnIdle { storeInventoryReady = true }
        compose.mainClock.autoAdvance = true
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(0)
        compose.onNodeWithText("루나와 함께하기").assertIsEnabled()
        capture(view, "shared-appearance-loaded")
    }

    @Test fun equippedBackgroundRemainsVisibleWhileCatalogLoads() {
        val inventory =
            CosmeticInventory(
                setOf("friend:mobi", "background:star"),
                mapOf(CosmeticSlot.FRIEND to "friend:mobi", CosmeticSlot.BACKGROUND to "background:star"),
            )
        compose.setContent {
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = listOf(CosmeticItem("background:star", CosmeticSlot.BACKGROUND, 200)),
                    selectedItemId = null,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 1200,
                    pointLoadFailed = false,
                    storeInventoryReady = false,
                )
            }
        }

        compose.onNodeWithTag("store-tab-BACKGROUND").performClick()
        compose.onNodeWithTag("store-preview-particles").assertExists()
    }

    @Test fun fastCatalogReadFillsExistingStoreWithoutPlaceholders() {
        compose.mainClock.autoAdvance = false
        val inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi"))
        var catalog by mutableStateOf(emptyList<CosmeticItem>())
        compose.setContent {
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = catalog,
                    selectedItemId = null,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 1200,
                    pointLoadFailed = false,
                )
            }
        }

        compose.onNodeWithTag("store-reference").assertExists()
        val initialGrid = compose.onNodeWithTag("shop-items").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("preview-character").assertExists()
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(0)
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle {
            catalog = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0))
        }
        compose.mainClock.autoAdvance = true
        assertEquals(initialGrid, compose.onNodeWithTag("shop-items").getUnclippedBoundsInRoot())
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(0)
        compose.onAllNodesWithText("모비").assertCountEquals(2)
    }

    @Test fun firstInventoryLoadKeepsReferenceFrameThroughFailureAndRecovery() {
        compose.mainClock.autoAdvance = false
        var inventory by mutableStateOf<CosmeticInventory?>(null)
        var failed by mutableStateOf(false)
        var retries = 0
        var backs = 0
        lateinit var view: View
        val catalog = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0))
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = catalog,
                    selectedItemId = null,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 1200,
                    pointLoadFailed = false,
                    loadFailed = failed,
                    onRetry = {
                        retries++
                        failed = false
                    },
                    onBack = { backs++ },
                )
            }
        }

        val initialFrame = compose.onNodeWithTag("store-reference").getUnclippedBoundsInRoot()
        val initialHeader = compose.onNodeWithText("꾸미기").getUnclippedBoundsInRoot()
        val initialPreview = compose.onNodeWithTag("store-preview-panel").getUnclippedBoundsInRoot()
        val initialGrid = compose.onNodeWithTag("shop-items").getUnclippedBoundsInRoot()
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(0)
        val loadingBadge = compose.onNodeWithContentDescription("주차 확인됨").getUnclippedBoundsInRoot()
        val pointSummary = compose.onNodeWithText("포인트 1,200 P").getUnclippedBoundsInRoot()
        assertEquals(36f, loadingBadge.top.value, 1f)
        assertEquals(2488f, loadingBadge.right.value, 1f)
        assertEquals(48f, (loadingBadge.left - pointSummary.right).value, 2f)
        assertEquals(
            7f,
            ((pointSummary.top + pointSummary.bottom) - (loadingBadge.top + loadingBadge.bottom)).value / 2f,
            2f,
        )
        compose.onNodeWithContentDescription("뒤로").performClick()
        assertEquals(1, backs)
        compose.mainClock.advanceTimeBy(96)
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(0)
        compose.mainClock.advanceTimeBy(128)
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(3)
        capture(view, "inventory-loading")
        compose.mainClock.autoAdvance = true

        compose.runOnIdle { failed = true }
        compose.onNodeWithText("소유한 아이템을 확인할 수 없어요.").assertIsDisplayed()
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(0)
        compose.onNodeWithText("다시 시도").assertIsDisplayed().performClick()
        assertEquals(1, retries)
        assertEquals(initialPreview, compose.onNodeWithTag("store-preview-panel").getUnclippedBoundsInRoot())

        compose.runOnIdle {
            inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi"))
        }
        assertEquals(initialFrame, compose.onNodeWithTag("store-reference").getUnclippedBoundsInRoot())
        assertEquals(initialHeader, compose.onNodeWithText("꾸미기").getUnclippedBoundsInRoot())
        assertEquals(initialPreview, compose.onNodeWithTag("store-preview-panel").getUnclippedBoundsInRoot())
        assertEquals(initialGrid, compose.onNodeWithTag("shop-items").getUnclippedBoundsInRoot())
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(0)
    }

    @Test fun categoriesSelectIndependentlyAndPreviewOnlyAppliesOnConfirmation() {
        var applied: String? = null
        lateinit var view: View
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
                CosmeticItem("accessory:mobi_headphones", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
            )
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            var selected by remember { mutableStateOf<String?>("friend:luna") }
            MobiMonTheme {
                CustomizationScreen(
                    CosmeticInventory(
                        catalog.mapTo(mutableSetOf()) { it.id },
                        mapOf(
                            CosmeticSlot.FRIEND to "friend:mobi",
                        ),
                    ),
                    catalog,
                    selected,
                    false,
                    false,
                    { selected = it },
                    { _, _ -> },
                    {},
                    { applied = it },
                    1200,
                    false,
                )
            }
        }
        compose.onNodeWithTag("store-tab-FRIEND").assertIsSelected()
        compose.onNodeWithText("주차 확인됨").assertIsDisplayed()
        val badge = compose.onNodeWithContentDescription("주차 확인됨").fetchSemanticsNode().boundsInRoot
        assertEquals(36f, badge.top, 1f)
        assertEquals(2488f, badge.right, 1f)
        compose.onNodeWithText("꾸미기").assertExists()
        assertNull(applied)
        capture(view, "P20-friend")
        compose.onNodeWithText("루나와 함께하기").performClick()
        assertEquals("friend:luna", applied)
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick().assertIsSelected()
        compose.onNodeWithTag("store-tab-FRIEND").assertIsNotSelected()
        capture(view, "P22-accessories")
        compose.onNodeWithTag("store-tab-BACKGROUND").performClick().assertIsSelected()
        compose.onNodeWithTag("store-tab-ACCESSORY").assertIsNotSelected()
        capture(view, "P22-backgrounds")
    }

    @Test fun runtimeHeightChangesReflowActionsWithoutShrinkingTheReferenceWidth() {
        val height = mutableStateOf(1184.dp)
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("accessory:mobi_headphones", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
            )
        compose.setContent {
            MobiMonTheme {
                Box(Modifier.height(height.value)) {
                    CustomizationScreen(
                        CosmeticInventory(setOf("friend:mobi"), emptyMap()),
                        catalog,
                        "friend:mobi",
                        false,
                        false,
                        {},
                        { _, _ -> },
                        {},
                        {},
                        1200,
                        false,
                    )
                }
            }
        }

        fun check(bottom: Float) {
            val reference = compose.onNodeWithTag("store-reference").getUnclippedBoundsInRoot()
            val action = compose.onNodeWithText("모비와 함께하기").getUnclippedBoundsInRoot()
            assertEquals(2560f, (reference.right - reference.left).value, 1f)
            assertEquals(bottom - 88f, action.bottom.value, 1f)
            assertEquals(104f, (action.bottom - action.top).value, 1f)
        }
        check(1184f)
        compose.runOnIdle { height.value = 1144.dp }
        check(1144f)
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        val catalogBounds = compose.onNodeWithTag("shop-items").getUnclippedBoundsInRoot()
        val hint = compose.onNodeWithText("아이템을 선택하면 친구에게 먼저 입혀 볼 수 있어요.").getUnclippedBoundsInRoot()
        assertTrue("Catalog stays below its description after height changes", catalogBounds.top >= hint.bottom)
        compose.runOnIdle { height.value = 540.dp }
        compose.onNodeWithTag("store-reference").assertDoesNotExist()
    }

    @Test fun previewDescriptionMatchesItemTypes() {
        val descriptions = mutableMapOf<String, String>()
        compose.setContent {
            MobiMonTheme {
                descriptions["friend:mobi"] =
                    storePreviewDescription(CosmeticSlot.FRIEND, null, "friend:mobi", true)
                descriptions["friend:luna"] =
                    storePreviewDescription(CosmeticSlot.FRIEND, null, "friend:luna", false)
                descriptions["none:accessory"] =
                    storePreviewDescription(CosmeticSlot.ACCESSORY, "none:accessory", "friend:mobi", true)
                descriptions["accessory:luna_cap"] =
                    storePreviewDescription(CosmeticSlot.ACCESSORY, "accessory:luna_cap", "friend:luna", false)
                descriptions["accessory:luna_sunglasses"] =
                    storePreviewDescription(CosmeticSlot.ACCESSORY, "accessory:luna_sunglasses", "friend:luna", false)
                descriptions["accessory:mobi_headphones"] =
                    storePreviewDescription(CosmeticSlot.ACCESSORY, "accessory:mobi_headphones", "friend:mobi", false)
                descriptions["accessory:mobi_goggles"] =
                    storePreviewDescription(CosmeticSlot.ACCESSORY, "accessory:mobi_goggles", "friend:mobi", false)
                descriptions["none:background"] =
                    storePreviewDescription(CosmeticSlot.BACKGROUND, "none:background", "friend:mobi", true)
                descriptions["background:star"] =
                    storePreviewDescription(CosmeticSlot.BACKGROUND, "background:star", "friend:mobi", false)
                descriptions["background:snow"] =
                    storePreviewDescription(CosmeticSlot.BACKGROUND, "background:snow", "friend:mobi", false)
                descriptions["background:petal"] =
                    storePreviewDescription(CosmeticSlot.BACKGROUND, "background:petal", "friend:mobi", false)
            }
        }
        assertEquals("우리들의 작은 친구 모비에요", descriptions["friend:mobi"])
        assertEquals("귀여운 애교쟁이 루나랍니다냥", descriptions["friend:luna"])
        assertEquals("내추럴한 모습이에요", descriptions["none:accessory"])
        assertEquals("함께 항해를 떠나볼까요?", descriptions["accessory:luna_cap"])
        assertEquals("눈부실 때는 선글라스만한게 없죠~", descriptions["accessory:luna_sunglasses"])
        assertEquals("노이즈캔슬링으로 운전에 집중!", descriptions["accessory:mobi_headphones"])
        assertEquals("빈티지 느낌에는 고글만한게 없죠~", descriptions["accessory:mobi_goggles"])
        assertEquals("깔끔한 배경화면이에요", descriptions["none:background"])
        assertEquals("반짝반짝 별이 내려요~", descriptions["background:star"])
        assertEquals("소복소복 눈이 내려요~", descriptions["background:snow"])
        assertEquals("살랑살랑 꽃이 내려요~", descriptions["background:petal"])
    }

    @Test fun catalogFailureRetainsPreviewAndOffersRetryBeforeApplying() {
        var retries = 0
        lateinit var view: View
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
            )
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            var failed by remember { mutableStateOf(true) }
            MobiMonTheme {
                CustomizationScreen(
                    inventory =
                        CosmeticInventory(
                            catalog.mapTo(mutableSetOf()) { it.id },
                            mapOf(
                                CosmeticSlot.FRIEND to "friend:mobi",
                            ),
                        ),
                    catalog = catalog,
                    selectedItemId = "friend:luna",
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 1200,
                    pointLoadFailed = false,
                    catalogLoadFailed = failed,
                    onRetry = {
                        retries++
                        failed = false
                    },
                )
            }
        }

        compose.onNodeWithTag("store-reference").assertExists()
        compose.onNodeWithText("아이템 목록을 불러오지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("루나와 함께하기").assertIsNotEnabled()
        assertFriendCardFitsCatalogViewport()
        capture(view, "catalog-recovery")
        compose.onNodeWithText("다시 시도").assertIsDisplayed().performClick()
        assertEquals(1, retries)
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("루나와 함께하기").assertIsEnabled()
    }

    @Test fun walletFailureRetainsPreviewAndOffersRetry() {
        var retries = 0
        lateinit var view: View
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
            )
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            var failed by remember { mutableStateOf(true) }
            MobiMonTheme {
                CustomizationScreen(
                    inventory =
                        CosmeticInventory(
                            catalog.mapTo(mutableSetOf()) { it.id },
                            mapOf(CosmeticSlot.FRIEND to "friend:mobi"),
                        ),
                    catalog = catalog,
                    selectedItemId = "friend:luna",
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = if (failed) null else 1200,
                    pointLoadFailed = failed,
                    onRetry = {
                        retries++
                        failed = false
                    },
                )
            }
        }

        compose.onNodeWithTag("store-reference").assertExists()
        compose.onNodeWithTag("preview-character").assertExists()
        compose.onNodeWithText("다시 시도").assertIsDisplayed()
        assertFriendCardFitsCatalogViewport()
        capture(view, "wallet-recovery")
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retries)
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("루나와 함께하기").assertIsEnabled()
    }

    @Test fun unverifiedParkingDisablesApplyAndDoesNotDispatchTheCommand() {
        var applied: String? = null
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
            )
        compose.setContent {
            MobiMonTheme {
                CustomizationScreen(
                    inventory =
                        CosmeticInventory(
                            catalog.mapTo(mutableSetOf()) { it.id },
                            mapOf(
                                CosmeticSlot.FRIEND to "friend:mobi",
                            ),
                        ),
                    catalog = catalog,
                    selectedItemId = "friend:luna",
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = { applied = it },
                    pointBalance = 1200,
                    pointLoadFailed = false,
                    interactionAllowed = false,
                )
            }
        }

        compose.onNodeWithText("루나와 함께하기").assertIsNotEnabled().performClick()
        assertNull(applied)
    }

    @Test fun previewBackgroundUpdatesThroughEveryPeriodWithoutRecreatingContent() {
        val period = mutableStateOf("Morning")
        lateinit var view: View
        val catalog = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0))
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi")),
                    catalog = catalog,
                    selectedItemId = "friend:mobi",
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
        val colors = mutableSetOf<Int>()
        listOf("Sunrise", "Morning", "Day", "Afternoon", "Sunset", "Night", "Midnight").forEach { value ->
            compose.runOnIdle { period.value = value }
            capture(view, "background-${value.lowercase()}")
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                colors += bitmap.getPixel(view.width / 4, view.height / 3)
                bitmap.recycle()
            }
        }
        assertEquals("Each period must render in the preview", 7, colors.size)
    }

    private fun assertFriendCardFitsCatalogViewport() {
        val viewport = compose.onNodeWithTag("shop-items").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithText("모비").getUnclippedBoundsInRoot()
        assertTrue("Friend card status must fit above the recovery controls", card.bottom <= viewport.bottom)
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
