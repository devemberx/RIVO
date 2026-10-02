package com.monsters.mobimon.feature.customization

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
class CustomizationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactStoreWaitsForItsOwnInventoryBeforeShowingActions() {
        compose.mainClock.autoAdvance = false
        var storeInventoryReady by mutableStateOf(false)
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
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
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
        }

        compose.onNodeWithTag("preview-character").assertExists()
        compose.onNodeWithText("루나").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(240)
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(2)
        compose.runOnIdle { storeInventoryReady = true }
        compose.mainClock.autoAdvance = true
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(0)
        compose.onNodeWithText("루나와 함께하기").assertIsEnabled()
    }

    @Test fun compactStoreKeepsItsCatalogWhileItemsLoad() {
        compose.mainClock.autoAdvance = false
        var inventory by mutableStateOf<CosmeticInventory?>(null)
        var catalog by mutableStateOf(emptyList<CosmeticItem>())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
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
        }

        compose.onNodeWithTag("shop-items").assertExists()
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(0)
        compose.mainClock.advanceTimeBy(240)
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(2)
        compose.runOnIdle {
            inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi"))
            catalog = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0))
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("shop-items").assertExists()
        compose.onAllNodesWithTag("store-item-placeholder").assertCountEquals(0)
        compose.onNodeWithTag("preview-character").assertExists()
    }

    @Test fun selectingOwnedFriendPreviewsWithoutEquippingUntilApply() {
        var applied: String? = null
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
            )
        val inventory =
            CosmeticInventory(
                ownedItemIds = setOf("friend:mobi", "friend:luna"),
                equippedItemIds = mapOf(CosmeticSlot.FRIEND to "friend:mobi"),
            )

        compose.setContent {
            var selectedId by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = catalog,
                    selectedItemId = selectedId,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = { selectedId = it },
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = { applied = it },
                    pointBalance = 0,
                    pointLoadFailed = false,
                )
            }
        }

        compose.onNodeWithText("친구").performClick()

        compose.onNodeWithTag("shop-items").assertIsDisplayed().performScrollToIndex(1)
        compose
            .onNodeWithText("루나")
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithTag("preview-character").assertIsDisplayed().assertContentDescriptionEquals("Luna 고양이")
        org.junit.Assert.assertNull(applied)
        compose
            .onNodeWithText("루나와 함께하기")
            .assertIsDisplayed()
            .performClick()
        org.junit.Assert.assertEquals("friend:luna", applied)
    }

    @Test fun failedInventoryOffersRetry() {
        var retries = 0
        compose.setContent {
            MobiMonTheme {
                CustomizationScreen(
                    inventory = null,
                    catalog = emptyList(),
                    selectedItemId = null,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = null,
                    pointLoadFailed = false,
                    loadFailed = true,
                    onRetry = { retries++ },
                )
            }
        }

        compose.onNodeWithText("다시 시도").assertIsDisplayed().performClick()
        org.junit.Assert.assertEquals(1, retries)
    }

    @Test fun unverifiedParkingDisablesPurchaseAndDoesNotDispatchTheCommand() {
        var purchases = 0
        compose.setContent {
            MobiMonTheme {
                CustomizationScreen(
                    inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi")),
                    catalog =
                        listOf(
                            CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                            CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 300),
                        ),
                    selectedItemId = "friend:luna",
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> purchases++ },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 1200,
                    pointLoadFailed = false,
                    interactionAllowed = false,
                )
            }
        }

        compose
            .onNodeWithText("300 P로 구매하기")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()
        org.junit.Assert.assertEquals(0, purchases)
    }

    @Test fun accessoryCatalogAndPreviewFollowEquippedFriend() {
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
                CosmeticItem("accessory:mobi_headphones", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
                CosmeticItem("accessory:luna_cap", CosmeticSlot.ACCESSORY, 300, "friend:luna"),
            )
        val inventory =
            mutableStateOf(
                CosmeticInventory(
                    ownedItemIds = catalog.mapTo(mutableSetOf()) { it.id },
                    equippedItemIds = mapOf(CosmeticSlot.FRIEND to "friend:mobi"),
                ),
            )
        compose.setContent {
            var selectedId by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory.value,
                    catalog = catalog,
                    selectedItemId = selectedId,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = { selectedId = it },
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = { id ->
                        inventory.value = inventory.value.copy(equippedItemIds = mapOf(CosmeticSlot.FRIEND to id))
                    },
                    pointBalance = 300,
                    pointLoadFailed = false,
                )
            }
        }

        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithText("모비 헤드폰").assertExists()
        compose.onNodeWithText("루나 모자").assertDoesNotExist()
        compose.runOnIdle {
            inventory.value = inventory.value.copy(equippedItemIds = mapOf(CosmeticSlot.FRIEND to "friend:luna"))
        }
        compose.onNodeWithText("루나 모자").assertExists()
        compose.onNodeWithText("모비 헤드폰").assertDoesNotExist()
        compose.onNodeWithTag("preview-background").assertExists()
        compose.onNodeWithTag("preview-character").assertExists()
    }

    @Test fun unownedAccessoryCanBePurchasedWhilePreviewingAnotherFriend() {
        var purchases = 0
        var friendSwitches = 0
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
                CosmeticItem("accessory:luna_sunglasses", CosmeticSlot.ACCESSORY, 300, "friend:luna"),
            )
        compose.setContent {
            var selectedId by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
            MobiMonTheme {
                CustomizationScreen(
                    inventory =
                        CosmeticInventory(
                            setOf("friend:mobi", "friend:luna"),
                            mapOf(CosmeticSlot.FRIEND to "friend:mobi"),
                        ),
                    catalog = catalog,
                    selectedItemId = selectedId,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = { selectedId = it },
                    onPurchaseItem = { id, price ->
                        org.junit.Assert.assertEquals("accessory:luna_sunglasses", id)
                        org.junit.Assert.assertEquals(300L, price)
                        purchases++
                    },
                    onEquipItem = {},
                    onEquipFriend = { friendSwitches++ },
                    pointBalance = 300,
                    pointLoadFailed = false,
                )
            }
        }

        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithText("루나").performClick()
        compose.onNodeWithText("루나 선글라스").performClick()
        compose.onNodeWithText("300 P로 구매하기").assertIsEnabled().performClick()
        compose.onNodeWithText("구매하기").performClick()
        org.junit.Assert.assertEquals(1, purchases)
        org.junit.Assert.assertEquals(0, friendSwitches)
    }

    @Test fun unownedFriendInClothesTabShowsDisabledBeforePurchaseButtonAndEnablesAfterPurchase() {
        var friendSwitches = 0
        var inventory by mutableStateOf(
            CosmeticInventory(
                ownedItemIds = setOf("friend:mobi"),
                equippedItemIds = mapOf(CosmeticSlot.FRIEND to "friend:mobi"),
            ),
        )
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("friend:las", CosmeticSlot.FRIEND, 500),
            )
        compose.setContent {
            var selectedId by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = catalog,
                    selectedItemId = selectedId,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = { selectedId = it },
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {
                        friendSwitches++
                    },
                    pointBalance = 1000,
                    pointLoadFailed = false,
                )
            }
        }

        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithText("라스").performClick()
        compose.onNodeWithText("구매 전").assertIsNotEnabled()

        inventory =
            inventory.copy(
                ownedItemIds = setOf("friend:mobi", "friend:las"),
            )

        compose.onNodeWithText("라스와 함께하기").assertIsEnabled().performClick()
        org.junit.Assert.assertEquals(1, friendSwitches)
    }

    @Test fun observationFailureOffersRetryWithCommittedInventoryVisible() {
        var retries = 0
        compose.setContent {
            MobiMonTheme {
                CustomizationScreen(
                    inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi")),
                    catalog = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0)),
                    selectedItemId = null,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 0,
                    pointLoadFailed = false,
                    loadFailed = true,
                    onRetry = { retries++ },
                )
            }
        }

        compose.onNodeWithTag("preview-character").assertExists()
        compose
            .onNodeWithText("다시 시도")
            .assertIsDisplayed()
            .performClick()
        org.junit.Assert.assertEquals(1, retries)
    }

    @Test fun walletFailureOffersRetryWithCommittedInventoryVisible() {
        var retries = 0
        compose.setContent {
            MobiMonTheme {
                CustomizationScreen(
                    inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi")),
                    catalog = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0)),
                    selectedItemId = null,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = {},
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = null,
                    pointLoadFailed = true,
                    onRetry = { retries++ },
                )
            }
        }

        compose.onNodeWithTag("preview-character").assertExists()
        compose
            .onNodeWithText("다시 시도")
            .assertIsDisplayed()
            .performClick()
        org.junit.Assert.assertEquals(1, retries)
        compose.onNodeWithTag("preview-character").assertIsDisplayed().assertContentDescriptionEquals("Mobi 토끼")
    }

    @Test fun enlargedTextKeepsRecoveryReachableByScrolling() {
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                MobiMonTheme {
                    CustomizationScreen(
                        inventory =
                            CosmeticInventory(
                                setOf("friend:mobi"),
                                mapOf(CosmeticSlot.FRIEND to "friend:mobi"),
                            ),
                        catalog = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0)),
                        selectedItemId = null,
                        purchasing = false,
                        purchaseFailed = false,
                        onSelectItem = {},
                        onPurchaseItem = { _, _ -> },
                        onEquipItem = {},
                        onEquipFriend = {},
                        pointBalance = null,
                        pointLoadFailed = true,
                        onRetry = { retries++ },
                    )
                }
            }
        }

        compose.onNodeWithTag("store-reference").assertDoesNotExist()
        compose.onNodeWithTag("preview-character").assertExists()
        compose
            .onNodeWithText("다시 시도")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(76.dp)
            .performClick()
        org.junit.Assert.assertEquals(1, retries)
    }

    @Test fun selectingDefaultNoneAccessoryAllowsUnequipping() {
        var unequippedSlot: String? = null
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("accessory:mobi_headphones", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
            )
        val inventory =
            CosmeticInventory(
                ownedItemIds = setOf("friend:mobi", "accessory:mobi_headphones"),
                equippedItemIds =
                    mapOf(
                        CosmeticSlot.FRIEND to "friend:mobi",
                        CosmeticSlot.ACCESSORY to "accessory:mobi_headphones",
                    ),
            )

        compose.setContent {
            var selectedId by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = catalog,
                    selectedItemId = selectedId,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = { selectedId = it },
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = { unequippedSlot = it },
                    onEquipFriend = {},
                    pointBalance = 300,
                    pointLoadFailed = false,
                )
            }
        }

        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithTag("shop-items").assertIsDisplayed().performScrollToIndex(0)
        compose.onNodeWithText("장식 없음").assertIsDisplayed().performClick()
        compose.onNodeWithText("이 모습 적용").assertIsDisplayed().performClick()
        org.junit.Assert.assertEquals("none:accessory", unequippedSlot)
    }

    @Test fun selectingBackgroundPreviewsParticlesAndRendersPreviewText() {
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("background:star", CosmeticSlot.BACKGROUND, 200),
            )
        val inventory =
            CosmeticInventory(
                ownedItemIds = setOf("friend:mobi"),
                equippedItemIds = mapOf(CosmeticSlot.FRIEND to "friend:mobi"),
            )

        compose.setContent {
            var selectedId by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = catalog,
                    selectedItemId = selectedId,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = { selectedId = it },
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 500,
                    pointLoadFailed = false,
                )
            }
        }

        compose.onNodeWithTag("store-tab-BACKGROUND").performClick()
        compose.onNodeWithText("특수효과").performClick()
        compose.onNodeWithTag("shop-items").assertIsDisplayed().performScrollToIndex(0)
        compose.onNodeWithText("반짝이는 별").assertIsDisplayed().performClick()
        compose.onNodeWithText("200 P로 구매하기").assertIsDisplayed()
        compose.onNodeWithTag("store-preview-particles").assertIsDisplayed()
    }

    @Test fun currentlyEquippedFriendDisplaysAccompanyingStatus() {
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
            )
        val inventory =
            CosmeticInventory(
                ownedItemIds = setOf("friend:mobi", "friend:luna"),
                equippedItemIds = mapOf(CosmeticSlot.FRIEND to "friend:mobi"),
            )

        compose.setContent {
            var selectedId by androidx.compose.runtime.remember { mutableStateOf<String?>("friend:mobi") }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = catalog,
                    selectedItemId = selectedId,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = { selectedId = it },
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 0,
                    pointLoadFailed = false,
                )
            }
        }

        compose.onNodeWithText("친구").performClick()
        compose.onAllNodesWithText("동행 중").assertCountEquals(1)
        compose.onNodeWithText("사용 중").assertDoesNotExist()
    }

    @Test fun currentlyEquippedAccessoryDisplaysWearingStatus() {
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("accessory:mobi_headphones", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
            )
        val inventory =
            CosmeticInventory(
                ownedItemIds = setOf("friend:mobi", "accessory:mobi_headphones"),
                equippedItemIds =
                    mapOf(
                        CosmeticSlot.FRIEND to "friend:mobi",
                        CosmeticSlot.ACCESSORY to "accessory:mobi_headphones",
                    ),
            )

        compose.setContent {
            var selectedId by androidx.compose.runtime.remember { mutableStateOf<String?>("accessory:mobi_headphones") }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = catalog,
                    selectedItemId = selectedId,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = { selectedId = it },
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 300,
                    pointLoadFailed = false,
                )
            }
        }

        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onAllNodesWithText("착용 중").assertCountEquals(1)
        compose.onNodeWithText("사용 중").assertDoesNotExist()
    }

    @Test fun currentlyEquippedBackgroundDisplaysInUseStatus() {
        val catalog =
            listOf(
                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                CosmeticItem("background:star", CosmeticSlot.BACKGROUND, 200),
            )
        val inventory =
            CosmeticInventory(
                ownedItemIds = setOf("friend:mobi", "background:star"),
                equippedItemIds =
                    mapOf(
                        CosmeticSlot.FRIEND to "friend:mobi",
                        CosmeticSlot.BACKGROUND to "background:star",
                    ),
            )

        compose.setContent {
            var selectedId by androidx.compose.runtime.remember { mutableStateOf<String?>("background:star") }
            MobiMonTheme {
                CustomizationScreen(
                    inventory = inventory,
                    catalog = catalog,
                    selectedItemId = selectedId,
                    purchasing = false,
                    purchaseFailed = false,
                    onSelectItem = { selectedId = it },
                    onPurchaseItem = { _, _ -> },
                    onEquipItem = {},
                    onEquipFriend = {},
                    pointBalance = 500,
                    pointLoadFailed = false,
                )
            }
        }

        compose.onNodeWithTag("store-tab-BACKGROUND").performClick()
        compose.onNodeWithText("특수효과").performClick()
        compose.onAllNodesWithText("사용 중").assertCountEquals(1)
    }
}
