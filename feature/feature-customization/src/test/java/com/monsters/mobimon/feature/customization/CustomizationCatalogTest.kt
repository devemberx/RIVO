package com.monsters.mobimon.feature.customization

import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomizationCatalogTest {
    @Test fun lasFriendPreviewAndPurchaseCatalogSupport() {
        val lasItem = CosmeticItem("friend:las", CosmeticSlot.FRIEND, 500)
        val inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi"))
        val preview = customizationCatalog(inventory, listOf(lasItem), CosmeticSlot.FRIEND, "friend:las")
        assertEquals("friend:las", preview.preview.friendId)
        assertFalse(preview.selectedOwned)
        assertFalse(preview.selectedEquipped)
    }

    @Test fun starHangerPreviewDoesNotEquipUntilAppliedAndIsAvailableToBothFriends() {
        val item = CosmeticItem("background:star_hanger", CosmeticSlot.BACKGROUND, 200)
        for (friend in listOf("friend:mobi", "friend:luna")) {
            val inventory = CosmeticInventory(setOf(friend), mapOf(CosmeticSlot.FRIEND to friend))
            val preview = customizationCatalog(inventory, listOf(item), CosmeticSlot.BACKGROUND, item.id)
            assertTrue(item in preview.items)
            assertEquals(item.id, preview.preview.backgroundId)
            assertFalse(preview.selectedOwned)
            assertFalse(preview.selectedEquipped)
            assertNull(inventory.equippedItemIds[CosmeticSlot.BACKGROUND])
            val equipped =
                inventory.copy(
                    ownedItemIds = inventory.ownedItemIds + item.id,
                    equippedItemIds = inventory.equippedItemIds + (CosmeticSlot.BACKGROUND to item.id),
                )
            val applied = customizationCatalog(equipped, listOf(item), CosmeticSlot.BACKGROUND, item.id)
            assertTrue(applied.selectedOwned)
            assertTrue(applied.selectedEquipped)
            val removal = customizationCatalog(equipped, listOf(item), CosmeticSlot.BACKGROUND, "none:background")
            assertNull(removal.preview.backgroundId)
        }
    }

    @Test fun yarnBasketPreviewDoesNotEquipUntilAppliedAndIsAvailableToBothFriends() {
        val item = CosmeticItem("background:starlight_yarn_basket", CosmeticSlot.BACKGROUND, 200)
        for (friend in listOf("friend:mobi", "friend:luna")) {
            val inventory = CosmeticInventory(setOf(friend), mapOf(CosmeticSlot.FRIEND to friend))
            val preview = customizationCatalog(inventory, listOf(item), CosmeticSlot.BACKGROUND, item.id)
            assertTrue(item in preview.items)
            assertEquals(item.id, preview.preview.backgroundId)
            assertFalse(preview.selectedOwned)
            assertFalse(preview.selectedEquipped)
            assertNull(inventory.equippedItemIds[CosmeticSlot.BACKGROUND])
            val equipped =
                inventory.copy(
                    ownedItemIds = inventory.ownedItemIds + item.id,
                    equippedItemIds = inventory.equippedItemIds + (CosmeticSlot.BACKGROUND to item.id),
                )
            val applied = customizationCatalog(equipped, listOf(item), CosmeticSlot.BACKGROUND, item.id)
            assertTrue(applied.selectedOwned)
            assertTrue(applied.selectedEquipped)
            val removal = customizationCatalog(equipped, listOf(item), CosmeticSlot.BACKGROUND, "none:background")
            assertNull(removal.preview.backgroundId)
        }
    }

    @Test fun friendPreviewUsesThatFriendsEquipmentWithoutChangingCommittedAppearance() {
        val inventory =
            CosmeticInventory(
                ownedItemIds = setOf("friend:mobi", "friend:luna"),
                equippedItemIds =
                    mapOf(
                        CosmeticSlot.FRIEND to "friend:mobi",
                        CosmeticSlot.ACCESSORY to "accessory:mobi_headphones",
                        CosmeticSlot.BACKGROUND to "background:star",
                    ),
                equippedByFriend =
                    mapOf("friend:luna" to mapOf(CosmeticSlot.ACCESSORY to "accessory:luna_cap")),
            )

        val presentation =
            customizationCatalog(
                inventory,
                listOf(
                    CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                    CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
                ),
                CosmeticSlot.FRIEND,
                "friend:luna",
            )

        assertEquals("friend:luna", presentation.preview.friendId)
        assertEquals("accessory:luna_cap", presentation.preview.accessoryId)
        assertEquals("background:star", presentation.preview.backgroundId)
        assertEquals("friend:mobi", inventory.equippedItemIds[CosmeticSlot.FRIEND])
        assertFalse(presentation.selectedEquipped)
        assertTrue(presentation.selectedOwned)
    }

    @Test fun accessoryCatalogExcludesOtherFriendsAndObsoleteItems() {
        val inventory = CosmeticInventory(emptySet(), mapOf(CosmeticSlot.FRIEND to "friend:luna"))
        val catalog =
            listOf(
                CosmeticItem("accessory:mobi_headphones", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
                CosmeticItem("accessory:luna_cap", CosmeticSlot.ACCESSORY, 300, "friend:luna"),
                CosmeticItem("outfit:luna_jacket", CosmeticSlot.OUTFIT, 300, "friend:luna"),
                CosmeticItem("accessory:legacy", CosmeticSlot.ACCESSORY, 300),
                CosmeticItem("accessory:luna_mint_scarf", CosmeticSlot.ACCESSORY, 300, "friend:luna"),
                CosmeticItem("background:star", CosmeticSlot.BACKGROUND, 200),
            )

        val presentation = customizationCatalog(inventory, catalog, CosmeticSlot.ACCESSORY, "accessory:mobi_headphones")

        assertEquals(
            listOf("none:accessory", "accessory:luna_cap", "outfit:luna_jacket"),
            presentation.items.map { it.id },
        )
        assertEquals("none:accessory", presentation.selected?.id)
        assertNull(presentation.preview.accessoryId)
    }

    @Test fun outfitPreviewPreservesTheCommittedAccessory() {
        val inventory =
            CosmeticInventory(
                emptySet(),
                mapOf(CosmeticSlot.FRIEND to "friend:mobi", CosmeticSlot.ACCESSORY to "accessory:mobi_headphones"),
            )
        val catalog = listOf(CosmeticItem("outfit:mobi_jacket", CosmeticSlot.OUTFIT, 300, "friend:mobi"))

        val presentation = customizationCatalog(inventory, catalog, CosmeticSlot.ACCESSORY, "outfit:mobi_jacket")

        assertEquals("accessory:mobi_headphones", presentation.preview.accessoryId)
        assertEquals("outfit:mobi_jacket", presentation.preview.outfitId)
        assertFalse(presentation.selectedOwned)
        assertFalse(presentation.selectedEquipped)
    }

    @Test fun unequipPreviewKeepsTheCommittedEquipmentUntilApply() {
        val inventory =
            CosmeticInventory(
                setOf("background:star"),
                mapOf(CosmeticSlot.FRIEND to "friend:mobi", CosmeticSlot.BACKGROUND to "background:star"),
                backgroundEffectId = "background:star",
            )

        val presentation =
            customizationCatalog(
                inventory,
                listOf(CosmeticItem("background:star", CosmeticSlot.BACKGROUND, 200)),
                CosmeticSlot.BACKGROUND,
                "none:background",
                category = StoreSpaceCategory.EFFECTS,
            )

        assertNull(presentation.preview.backgroundId)
        assertEquals("background:star", inventory.equippedItemIds[CosmeticSlot.BACKGROUND])
        assertTrue(presentation.selectedOwned)
        assertFalse(presentation.selectedEquipped)
    }

    @Test fun pendingCatalogKeepsTheCommittedBackgroundInPreview() {
        val inventory =
            CosmeticInventory(
                setOf("background:star"),
                mapOf(CosmeticSlot.FRIEND to "friend:mobi", CosmeticSlot.BACKGROUND to "background:star"),
            )

        val presentation = customizationCatalog(inventory, emptyList(), CosmeticSlot.BACKGROUND, null)

        assertTrue(presentation.items.isEmpty())
        assertNull(presentation.selected)
        assertEquals("background:star", presentation.preview.backgroundId)
    }

    @Test fun ownedFilterRetainsPreviewAndShowsTheRemovalChoice() {
        val inventory =
            CosmeticInventory(setOf("accessory:mobi_headphones"), mapOf(CosmeticSlot.FRIEND to "friend:mobi"))
        val catalog =
            listOf(
                CosmeticItem("accessory:mobi_headphones", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
                CosmeticItem("accessory:mobi_goggles", CosmeticSlot.ACCESSORY, 300, "friend:mobi"),
            )

        val presentation =
            customizationCatalog(inventory, catalog, CosmeticSlot.ACCESSORY, "accessory:mobi_goggles", ownedOnly = true)

        assertEquals(listOf("none:accessory", "accessory:mobi_headphones"), presentation.items.map { it.id })
        assertEquals("accessory:mobi_goggles", presentation.selected?.id)
        assertEquals("accessory:mobi_goggles", presentation.preview.accessoryId)
        assertFalse(presentation.selectedOwned)
    }

    @Test fun inactiveFriendClothesPreviewRetainsThatFriendsEquipment() {
        val cap = CosmeticItem("accessory:luna_cap", CosmeticSlot.ACCESSORY, 300, "friend:luna")
        val inventory =
            CosmeticInventory(
                setOf("friend:mobi", "friend:luna", cap.id),
                mapOf(CosmeticSlot.FRIEND to "friend:mobi"),
                mapOf("friend:luna" to mapOf(CosmeticSlot.ACCESSORY to cap.id)),
            )
        val presentation =
            customizationCatalog(inventory, listOf(cap), CosmeticSlot.ACCESSORY, null, clothesFriendId = "friend:luna")
        assertEquals(cap.id, presentation.selected?.id)
        assertEquals(cap.id, presentation.preview.accessoryId)
        assertTrue(presentation.selectedEquipped)
        assertFalse(inventory.isEquippedForFriend(NONE_ACCESSORY_ITEM, "friend:luna"))
    }

    @Test fun cyberpunkBackgroundSelectionPersistsAcrossPropsAndEffectsCategories() {
        val cyberpunk = CosmeticItem("background:cyberpunk_city", CosmeticSlot.BACKGROUND, 400)
        val hanger = CosmeticItem("background:star_hanger", CosmeticSlot.BACKGROUND, 200)
        val star = CosmeticItem("background:star", CosmeticSlot.BACKGROUND, 200)
        val inventory =
            CosmeticInventory(
                setOf("friend:mobi", cyberpunk.id, hanger.id, star.id),
                mapOf(CosmeticSlot.FRIEND to "friend:mobi", CosmeticSlot.BACKGROUND to cyberpunk.id),
                backgroundPropId = hanger.id,
                backgroundEffectId = star.id,
            )

        val propsPresentation =
            customizationCatalog(
                inventory = inventory,
                catalog = listOf(hanger),
                tab = CosmeticSlot.BACKGROUND,
                selectedItemId = hanger.id,
                category = StoreSpaceCategory.PROPS,
                selectedBackgroundThemeId = cyberpunk.id,
                selectedBackgroundEffectId = star.id,
            )

        assertEquals("background:cyberpunk_city", propsPresentation.preview.backgroundId)
        assertEquals("background:star_hanger", propsPresentation.preview.backgroundPropId)
        assertEquals("background:star", propsPresentation.preview.backgroundEffectId)

        val effectsPresentation =
            customizationCatalog(
                inventory = inventory,
                catalog = listOf(star),
                tab = CosmeticSlot.BACKGROUND,
                selectedItemId = star.id,
                category = StoreSpaceCategory.EFFECTS,
                selectedBackgroundThemeId = cyberpunk.id,
                selectedBackgroundPropId = hanger.id,
            )

        assertEquals("background:cyberpunk_city", effectsPresentation.preview.backgroundId)
        assertEquals("background:star_hanger", effectsPresentation.preview.backgroundPropId)
        assertEquals("background:star", effectsPresentation.preview.backgroundEffectId)
    }

    @Test fun equippingThemeResetsStaleSelectionToEquippedThemeInOtherCategories() {
        val cyberpunk = CosmeticItem("background:cyberpunk_city", CosmeticSlot.BACKGROUND, 400)
        val lakePark = CosmeticItem("background:lake_park", CosmeticSlot.BACKGROUND, 200)
        val hanger = CosmeticItem("background:star_hanger", CosmeticSlot.BACKGROUND, 200)
        val inventory =
            CosmeticInventory(
                setOf("friend:mobi", cyberpunk.id, lakePark.id, hanger.id),
                mapOf(CosmeticSlot.FRIEND to "friend:mobi", CosmeticSlot.BACKGROUND to lakePark.id),
            )

        val propsPresentation =
            customizationCatalog(
                inventory = inventory,
                catalog = listOf(hanger),
                tab = CosmeticSlot.BACKGROUND,
                selectedItemId = hanger.id,
                category = StoreSpaceCategory.PROPS,
                selectedBackgroundThemeId = null,
            )

        assertEquals("background:lake_park", propsPresentation.preview.backgroundId)
    }
}
