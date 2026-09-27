package com.monsters.mobimon.feature.customization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.EquipResult
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.PurchaseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CosmeticInventoryUiState(
    val inventory: CosmeticInventory? = null,
    val catalog: List<CosmeticItem> = emptyList(),
    val inventoryLoadFailed: Boolean = false,
    val catalogLoadFailed: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val selectedItemId: String? = null,
    val purchasing: Boolean = false,
    val purchaseFailed: Boolean = false,
) {
    val loadFailed: Boolean get() = inventoryLoadFailed || catalogLoadFailed
    val equippedFriendId: String? get() = inventory?.equippedItemIds?.get(CosmeticSlot.FRIEND)
    val equippedAccessoryId: String? get() = inventory?.equippedItemIds?.get(CosmeticSlot.ACCESSORY)
}

class CosmeticInventoryViewModel(
    private val points: PointEconomy,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CosmeticInventoryUiState())
    val state = mutableState.asStateFlow()
    private var observer: Job? = null
    private var catalogObserver: Job? = null
    private var purchaseJob: Job? = null
    private var equipJob: Job? = null
    private var operationGeneration = 0

    init {
        retry()
    }

    fun retry() {
        observeInventory()
        observeCatalog()
    }

    private fun observeInventory() {
        if (observer?.isActive == true) return
        observer =
            viewModelScope.launch {
                try {
                    points.inventory.collect { inventory ->
                        mutableState.update { it.copy(inventory = inventory, inventoryLoadFailed = false) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.update { it.copy(inventoryLoadFailed = true) }
                }
            }
    }

    private fun observeCatalog() {
        if (catalogObserver?.isActive == true) return
        catalogObserver =
            viewModelScope.launch {
                try {
                    points.catalog.collect { catalog ->
                        mutableState.update { it.copy(catalog = catalog, catalogLoadFailed = false) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.update { it.copy(catalogLoadFailed = true) }
                }
            }
    }

    fun selectItem(itemId: String?) {
        mutableState.update { it.copy(selectedItemId = itemId, purchaseFailed = false, saveFailed = false) }
    }

    fun onParkingInterrupted() {
        operationGeneration++
        purchaseJob?.cancel()
        equipJob?.cancel()
        mutableState.update {
            it.copy(
                selectedItemId = null,
                purchasing = false,
                purchaseFailed = false,
                saving = false,
                saveFailed = false,
            )
        }
    }

    fun purchaseItem(
        itemId: String,
        price: Long,
    ) {
        if (state.value.purchasing) return
        mutableState.update { it.copy(purchasing = true, purchaseFailed = false) }
        val generation = operationGeneration
        purchaseJob =
            viewModelScope.launch {
                try {
                    val result = points.purchase(itemId, price)
                    if (generation == operationGeneration) {
                        mutableState.update { it.copy(purchaseFailed = result !is PurchaseResult.Purchased) }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == operationGeneration) mutableState.update { it.copy(purchaseFailed = true) }
                } finally {
                    if (generation == operationGeneration) mutableState.update { it.copy(purchasing = false) }
                }
            }
    }

    fun equipItem(itemId: String) {
        val isNone = itemId.startsWith("none")
        if (state.value.saving) return
        if (!isNone &&
            state.value.inventory
                ?.ownedItemIds
                ?.contains(itemId) != true
        ) {
            return
        }
        mutableState.update { it.copy(saving = true, saveFailed = false) }
        val generation = operationGeneration
        equipJob =
            viewModelScope.launch {
                try {
                    val result = points.equip(itemId)
                    if (generation == operationGeneration) {
                        mutableState.update {
                            it.copy(
                                saveFailed =
                                    result != EquipResult.Applied && result != EquipResult.AlreadyApplied,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == operationGeneration) mutableState.update { it.copy(saveFailed = true) }
                } finally {
                    if (generation == operationGeneration) mutableState.update { it.copy(saving = false) }
                }
            }
    }

    fun equipFriend(itemId: String) {
        equipItem(itemId)
    }
}
