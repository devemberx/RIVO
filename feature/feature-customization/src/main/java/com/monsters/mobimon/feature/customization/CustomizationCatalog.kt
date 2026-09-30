package com.monsters.mobimon.feature.customization

import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot

internal val NONE_ACCESSORY_ITEM = CosmeticItem("none:accessory", CosmeticSlot.ACCESSORY, 0)
internal val NONE_BACKGROUND_ITEM = CosmeticItem("none:background", CosmeticSlot.BACKGROUND, 0)

internal data class CosmeticPreview(
    val friendId: String,
    val accessoryId: String?,
    val outfitId: String?,
    val backgroundId: String?,
)

internal data class CustomizationCatalog(
    val items: List<CosmeticItem>,
    val selected: CosmeticItem?,
    val selectedOwned: Boolean,
    val selectedEquipped: Boolean,
    val preview: CosmeticPreview,
)

internal fun customizationCatalog(
    inventory: CosmeticInventory?,
    catalog: List<CosmeticItem>,
    tab: CosmeticSlot,
    selectedItemId: String?,
    ownedOnly: Boolean = false,
    clothesFriendId: String? = null,
): CustomizationCatalog {
    if (inventory == null) {
        return CustomizationCatalog(
            items = emptyList(),
            selected = null,
            selectedOwned = false,
            selectedEquipped = false,
            preview = CosmeticPreview("friend:mobi", null, null, null),
        )
    }
    val activeFriend = inventory.equippedItemIds[CosmeticSlot.FRIEND] ?: "friend:mobi"
    val friend = if (tab == CosmeticSlot.ACCESSORY) clothesFriendId ?: activeFriend else activeFriend
    val available = catalog.filterNot { it.id.contains("necklace") || it.id.contains("mint_scarf") }
    val tabItems =
        if (catalog.isEmpty()) {
            emptyList()
        } else {
            when (tab) {
                CosmeticSlot.ACCESSORY ->
                    listOf(NONE_ACCESSORY_ITEM) +
                        available.filter {
                            (it.slot == CosmeticSlot.ACCESSORY || it.slot == CosmeticSlot.OUTFIT) &&
                                (
                                    it.compatibleFriendId == friend ||
                                        (friend == "friend:mobi" && it.compatibleFriendId == null)
                                )
                        }
                CosmeticSlot.BACKGROUND -> listOf(NONE_BACKGROUND_ITEM) + available.filter { it.slot == tab }
                else -> available.filter { it.slot == tab }
            }
        }
    val visibleItems = if (ownedOnly) tabItems.filter { inventory.isOwned(it) } else tabItems
    val selected =
        tabItems.firstOrNull { it.id == selectedItemId }
            ?: tabItems.firstOrNull { inventory.isEquippedForFriend(it, friend) }
            ?: tabItems.firstOrNull()
    val previewFriend = if (tab == CosmeticSlot.FRIEND) selected?.id ?: friend else friend
    val equipment =
        if (previewFriend ==
            activeFriend
        ) {
            inventory.equippedItemIds
        } else {
            inventory.equippedByFriend[previewFriend].orEmpty()
        }
    return CustomizationCatalog(
        items = visibleItems,
        selected = selected,
        selectedOwned = selected?.let { inventory.isOwned(it) } == true,
        selectedEquipped = selected?.let { inventory.isEquippedForFriend(it, friend) } == true,
        preview =
            CosmeticPreview(
                friendId = previewFriend,
                accessoryId =
                    if (selected?.slot == CosmeticSlot.ACCESSORY) {
                        selected.takeUnless { it.isRemoval }?.id
                    } else {
                        equipment[CosmeticSlot.ACCESSORY]
                    },
                outfitId = selected?.takeIf { it.slot == CosmeticSlot.OUTFIT }?.id ?: equipment[CosmeticSlot.OUTFIT],
                backgroundId =
                    if (tab == CosmeticSlot.BACKGROUND && selected != null) {
                        selected.takeUnless { it.isRemoval }?.id
                    } else {
                        inventory.equippedItemIds[CosmeticSlot.BACKGROUND]
                    },
            ),
    )
}

internal val CosmeticItem.isRemoval: Boolean
    get() = id == NONE_ACCESSORY_ITEM.id || id == NONE_BACKGROUND_ITEM.id

internal fun CosmeticInventory.isOwned(item: CosmeticItem): Boolean = item.isRemoval || item.id in ownedItemIds

internal fun CosmeticInventory.isEquipped(item: CosmeticItem): Boolean =
    if (item.isRemoval) {
        equippedItemIds[item.slot] == null
    } else {
        equippedItemIds[item.slot] == item.id
    }

internal fun CosmeticInventory.isEquippedForFriend(
    item: CosmeticItem,
    friendId: String,
): Boolean {
    if (item.slot != CosmeticSlot.ACCESSORY && item.slot != CosmeticSlot.OUTFIT) return isEquipped(item)
    val activeFriend = equippedItemIds[CosmeticSlot.FRIEND] ?: "friend:mobi"
    val equipment = if (friendId == activeFriend) equippedItemIds else equippedByFriend[friendId].orEmpty()
    return if (item.isRemoval) equipment[item.slot] == null else equipment[item.slot] == item.id
}
