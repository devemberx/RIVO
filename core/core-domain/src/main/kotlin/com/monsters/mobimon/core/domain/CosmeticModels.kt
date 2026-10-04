package com.monsters.mobimon.core.domain

enum class CosmeticSlot { FRIEND, OUTFIT, ACCESSORY, BACKGROUND }

data class CosmeticItem(
    val id: String,
    val slot: CosmeticSlot,
    val price: Long,
    val compatibleFriendId: String? = null,
)

data class CosmeticInventory(
    val ownedItemIds: Set<String>,
    val equippedItemIds: Map<CosmeticSlot, String>,
    val equippedByFriend: Map<String, Map<CosmeticSlot, String>> = emptyMap(),
    val backgroundOverlayId: String? = null,
    val backgroundPropId: String? = null,
    val backgroundEffectId: String? = null,
)

sealed interface PurchaseResult {
    data class Purchased(
        val resultingBalance: Long,
    ) : PurchaseResult

    data object AlreadyOwned : PurchaseResult

    data object ItemUnavailable : PurchaseResult

    data class PriceChanged(
        val currentPrice: Long,
    ) : PurchaseResult

    data class InsufficientPoints(
        val shortfall: Long,
    ) : PurchaseResult

    data object Incompatible : PurchaseResult

    data object InteractionRestricted : PurchaseResult

    data object StorageFailure : PurchaseResult
}

sealed interface EquipResult {
    data object Applied : EquipResult

    data object AlreadyApplied : EquipResult

    data object NotOwned : EquipResult

    data object ItemUnavailable : EquipResult

    data object Incompatible : EquipResult

    data object InteractionRestricted : EquipResult

    data object StorageFailure : EquipResult
}
