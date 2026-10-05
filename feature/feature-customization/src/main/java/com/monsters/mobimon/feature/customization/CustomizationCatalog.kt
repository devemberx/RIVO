package com.monsters.mobimon.feature.customization

import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.BackgroundVisual
import com.monsters.mobimon.core.ui.CompanionBackgroundCatalog

internal val NONE_ACCESSORY_ITEM = CosmeticItem("none:accessory", CosmeticSlot.ACCESSORY, 0)
internal val NONE_BACKGROUND_ITEM = CosmeticItem("none:background", CosmeticSlot.BACKGROUND, 0)

internal data class CosmeticPreview(
    val friendId: String,
    val accessoryId: String?,
    val outfitId: String?,
    val backgroundId: String?,
    val backgroundOverlayId: String? = null,
    val backgroundPropId: String? = null,
    val backgroundEffectId: String? = null,
)

internal data class CustomizationCatalog(
    val items: List<CosmeticItem>,
    val selected: CosmeticItem?,
    val selectedOwned: Boolean,
    val selectedEquipped: Boolean,
    val preview: CosmeticPreview,
)

internal fun isPropItem(id: String): Boolean = CompanionBackgroundCatalog.visual(id) is BackgroundVisual.Prop

internal fun isEffectItem(id: String): Boolean = CompanionBackgroundCatalog.visual(id) is BackgroundVisual.Effect

internal fun customizationCatalog(
    inventory: CosmeticInventory?,
    catalog: List<CosmeticItem>,
    tab: CosmeticSlot,
    selectedItemId: String?,
    ownedOnly: Boolean = false,
    clothesFriendId: String? = null,
    category: StoreSpaceCategory = StoreSpaceCategory.BACKGROUNDS,
    selectedBackgroundThemeId: String? = null,
    selectedBackgroundPropId: String? = null,
    selectedBackgroundEffectId: String? = null,
): CustomizationCatalog {
    if (inventory == null) {
        return CustomizationCatalog(
            items = emptyList(),
            selected = null,
            selectedOwned = false,
            selectedEquipped = false,
            preview = CosmeticPreview("friend:mobi", null, null, null, null, null, null),
        )
    }
    val activeFriend = inventory.equippedItemIds[CosmeticSlot.FRIEND] ?: "friend:mobi"
    val committedScene = CompanionBackgroundCatalog.scene(inventory.equippedItemIds[CosmeticSlot.BACKGROUND])
    val defaultSceneEquipped = committedScene == null || committedScene.id == CompanionBackgroundCatalog.defaultScene.id
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
            ?: tabItems.firstOrNull { item ->
                if (tab == CosmeticSlot.BACKGROUND) {
                    when (category) {
                        StoreSpaceCategory.BACKGROUNDS ->
                            if (item.isRemoval) {
                                defaultSceneEquipped
                            } else {
                                inventory.equippedItemIds[CosmeticSlot.BACKGROUND] == item.id
                            }
                        StoreSpaceCategory.PROPS ->
                            if (item.isRemoval) {
                                inventory.backgroundPropId == null
                            } else {
                                inventory.backgroundPropId == item.id
                            }
                        StoreSpaceCategory.EFFECTS ->
                            if (item.isRemoval) {
                                inventory.backgroundEffectId == null
                            } else {
                                inventory.backgroundEffectId == item.id
                            }
                    }
                } else {
                    inventory.isEquippedForFriend(item, friend)
                }
            }
            ?: tabItems.firstOrNull()

    val previewFriend = if (tab == CosmeticSlot.FRIEND) selected?.id ?: friend else friend
    val equipment =
        if (previewFriend == activeFriend) {
            inventory.equippedItemIds
        } else {
            inventory.equippedByFriend[previewFriend].orEmpty()
        }

    val committedTheme =
        inventory.equippedItemIds[CosmeticSlot.BACKGROUND]?.takeIf {
            CompanionBackgroundCatalog.visual(it) is BackgroundVisual.Scene
        }
    val committedProp =
        inventory.backgroundPropId
            ?: inventory.backgroundOverlayId?.takeIf { isPropItem(it) }
            ?: inventory.equippedItemIds[CosmeticSlot.BACKGROUND]?.takeIf { isPropItem(it) }
    val committedEffect =
        inventory.backgroundEffectId
            ?: inventory.backgroundOverlayId?.takeIf { isEffectItem(it) }
            ?: inventory.equippedItemIds[CosmeticSlot.BACKGROUND]?.takeIf { isEffectItem(it) }

    val bgThemeInPreview =
        if (tab == CosmeticSlot.BACKGROUND) {
            if (category == StoreSpaceCategory.BACKGROUNDS) {
                selected?.takeUnless { it.isRemoval }?.id
            } else {
                if (selectedBackgroundThemeId == "none:background") {
                    null
                } else {
                    selectedBackgroundThemeId ?: committedTheme
                }
            }
        } else {
            committedTheme
        }

    val bgPropInPreview =
        if (tab == CosmeticSlot.BACKGROUND) {
            if (category == StoreSpaceCategory.PROPS) {
                selected?.takeUnless { it.isRemoval }?.id
            } else {
                if (selectedBackgroundPropId == "none:background") {
                    null
                } else {
                    selectedBackgroundPropId ?: committedProp
                }
            }
        } else {
            committedProp
        }

    val bgEffectInPreview =
        if (tab == CosmeticSlot.BACKGROUND) {
            if (category == StoreSpaceCategory.EFFECTS) {
                selected?.takeUnless { it.isRemoval }?.id
            } else {
                if (selectedBackgroundEffectId == "none:background") {
                    null
                } else {
                    selectedBackgroundEffectId ?: committedEffect
                }
            }
        } else {
            committedEffect
        }

    val selectedEquipped =
        if (selected == null) {
            false
        } else if (tab == CosmeticSlot.BACKGROUND) {
            when (category) {
                StoreSpaceCategory.BACKGROUNDS ->
                    if (selected.isRemoval) {
                        defaultSceneEquipped
                    } else {
                        inventory.equippedItemIds[CosmeticSlot.BACKGROUND] == selected.id
                    }
                StoreSpaceCategory.PROPS ->
                    if (selected.isRemoval) {
                        inventory.backgroundPropId == null
                    } else {
                        inventory.backgroundPropId == selected.id
                    }
                StoreSpaceCategory.EFFECTS ->
                    if (selected.isRemoval) {
                        inventory.backgroundEffectId == null
                    } else {
                        inventory.backgroundEffectId == selected.id
                    }
            }
        } else {
            selected.let { inventory.isEquippedForFriend(it, friend) }
        }

    return CustomizationCatalog(
        items = visibleItems,
        selected = selected,
        selectedOwned = selected?.let { inventory.isOwned(it) } == true,
        selectedEquipped = selectedEquipped,
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
                    if (tab == CosmeticSlot.BACKGROUND) {
                        bgThemeInPreview
                            ?: if (category != StoreSpaceCategory.BACKGROUNDS) {
                                committedTheme
                            } else if (selected?.isRemoval == true) {
                                null
                            } else {
                                inventory.equippedItemIds[CosmeticSlot.BACKGROUND]
                            }
                    } else {
                        committedTheme ?: inventory.equippedItemIds[CosmeticSlot.BACKGROUND]
                    },
                backgroundOverlayId = bgPropInPreview ?: bgEffectInPreview,
                backgroundPropId = bgPropInPreview,
                backgroundEffectId = bgEffectInPreview,
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
