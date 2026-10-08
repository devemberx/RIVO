package com.monsters.mobimon.feature.customization

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClothesFriendSelectionTest {
    @get:Rule val compose = createComposeRule()

    private val catalog =
        listOf(
            CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
            CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
            CosmeticItem("friend:las", CosmeticSlot.FRIEND, 0),
            CosmeticItem("accessory:mobi_headphones", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
            CosmeticItem("accessory:luna_cap", CosmeticSlot.ACCESSORY, 300, "friend:luna"),
            CosmeticItem("outfit:las_test", CosmeticSlot.OUTFIT, 300, "friend:las"),
        )

    @Test fun clothesFollowLoadedAndChangedEquippedFriend() {
        val inventory = mutableStateOf<CosmeticInventory?>(null)
        showStore(inventory)
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.runOnIdle { inventory.value = equipped("friend:luna") }
        assertClothes("루나", "accessory:luna_cap")
        compose.runOnIdle { inventory.value = equipped("friend:las") }
        assertClothes("라스", "outfit:las_test")
    }

    @Test fun changingEquippedFriendReplacesPreviousClothesPreview() {
        val inventory = mutableStateOf<CosmeticInventory?>(equipped("friend:luna"))
        showStore(inventory)
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithText("모비").performClick()
        assertClothes("모비", "accessory:mobi_headphones")
        compose.runOnIdle { inventory.value = equipped("friend:las") }
        assertClothes("라스", "outfit:las_test")
    }

    @Test fun reenteringClothesStartsWithEquippedFriendAfterManualPreview() {
        val inventory = mutableStateOf<CosmeticInventory?>(equipped("friend:luna"))
        showStore(inventory)
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        compose.onNodeWithText("모비").performClick()
        assertClothes("모비", "accessory:mobi_headphones")
        org.junit.Assert.assertEquals("friend:luna", inventory.value?.equippedItemIds?.get(CosmeticSlot.FRIEND))
        compose.onNodeWithTag("store-tab-FRIEND").performClick()
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        assertClothes("루나", "accessory:luna_cap")
    }

    private fun equipped(friendId: String) =
        CosmeticInventory(
            ownedItemIds = catalog.mapTo(mutableSetOf()) { it.id },
            equippedItemIds = mapOf(CosmeticSlot.FRIEND to friendId),
        )

    private fun showStore(inventory: MutableState<CosmeticInventory?>) {
        compose.setContent {
            var selectedId by remember { mutableStateOf<String?>(null) }
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
                    onEquipFriend = { inventory.value = equipped(it) },
                    pointBalance = 300,
                    pointLoadFailed = false,
                )
            }
        }
    }

    private fun assertClothes(
        friendName: String,
        itemId: String,
    ) {
        compose.onNodeWithText(friendName).assertIsSelected()
        compose.onNodeWithTag("store-item-$itemId").assertExists()
        catalog.filter { it.compatibleFriendId != null && it.id != itemId }.forEach {
            compose.onNodeWithTag("store-item-${it.id}").assertDoesNotExist()
        }
    }
}
