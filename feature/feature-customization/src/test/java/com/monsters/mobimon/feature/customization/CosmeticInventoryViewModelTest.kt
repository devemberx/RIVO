package com.monsters.mobimon.feature.customization

import androidx.lifecycle.ViewModelStore
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.EquipResult
import com.monsters.mobimon.core.domain.PointAwardResult
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.PointWallet
import com.monsters.mobimon.core.domain.PurchaseResult
import com.monsters.mobimon.core.domain.VehicleSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CosmeticInventoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun retryResubscribesAfterInventoryObservationFails() =
        runTest(dispatcher) {
            val expected = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi"))
            var subscriptions = 0
            val inventory: Flow<CosmeticInventory> =
                flow {
                    subscriptions++
                    if (subscriptions == 1) throw IOException("temporary Room failure")
                    emit(expected)
                }
            val model = CosmeticInventoryViewModel(FakePoints(inventory)).also { store.put("inventory", it) }

            runCurrent()
            assertEquals(true, model.state.value.loadFailed)
            assertEquals(null, model.state.value.inventory)

            model.retry()
            runCurrent()
            assertEquals(2, subscriptions)
            assertEquals(false, model.state.value.loadFailed)
            assertEquals(expected, model.state.value.inventory)
        }

    @Test fun equipItemNonePassesThroughWithoutOwnedCheck() =
        runTest(dispatcher) {
            var equippedId: String? = null
            val points =
                object : FakePoints(flowOf(CosmeticInventory(emptySet(), emptyMap()))) {
                    override suspend fun equip(itemId: String): EquipResult {
                        equippedId = itemId
                        return EquipResult.Applied
                    }
                }
            val model = CosmeticInventoryViewModel(points).also { store.put("inventory", it) }
            runCurrent()

            model.equipItem("none:accessory")
            runCurrent()

            assertEquals("none:accessory", equippedId)
        }

    @Test fun catalogFailureRemainsVisibleAfterInventoryUpdates() =
        runTest(dispatcher) {
            val inventory = MutableStateFlow(CosmeticInventory(setOf("friend:mobi"), emptyMap()))
            val catalog = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0))
            val points =
                FakePoints(
                    inventory,
                    flow {
                        emit(catalog)
                        throw IOException("temporary catalog failure")
                    },
                )
            val model = CosmeticInventoryViewModel(points).also { store.put("inventory", it) }

            runCurrent()
            inventory.value = inventory.value.copy(equippedItemIds = mapOf(CosmeticSlot.FRIEND to "friend:mobi"))
            runCurrent()

            assertEquals(true, model.state.value.loadFailed)
            assertEquals(true, model.state.value.catalogLoadFailed)
            assertEquals(false, model.state.value.inventoryLoadFailed)
            assertEquals(catalog, model.state.value.catalog)
            assertEquals("friend:mobi", model.state.value.equippedFriendId)
        }

    @Test fun retryRestartsFailedCatalogWhileInventoryRemainsActive() =
        runTest(dispatcher) {
            val inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi"))
            val catalog = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0))
            var inventorySubscriptions = 0
            var catalogSubscriptions = 0
            val points =
                FakePoints(
                    flow {
                        inventorySubscriptions++
                        emit(inventory)
                        awaitCancellation()
                    },
                    flow {
                        catalogSubscriptions++
                        if (catalogSubscriptions == 1) throw IOException("temporary catalog failure")
                        emit(catalog)
                        awaitCancellation()
                    },
                )
            val model = CosmeticInventoryViewModel(points).also { store.put("inventory", it) }
            runCurrent()
            model.selectItem("friend:luna")

            model.retry()
            runCurrent()

            assertEquals(catalog, model.state.value.catalog)
            assertEquals(false, model.state.value.loadFailed)
            assertEquals(inventory, model.state.value.inventory)
            assertEquals("friend:luna", model.state.value.selectedItemId)
            assertEquals(1, inventorySubscriptions)
            assertEquals(2, catalogSubscriptions)
        }

    @Test fun retryRestartsInventoryWithoutInterruptingHealthyCatalog() =
        runTest(dispatcher) {
            val inventory = CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi"))
            val catalog = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0))
            var inventorySubscriptions = 0
            var catalogSubscriptions = 0
            val points =
                FakePoints(
                    flow {
                        inventorySubscriptions++
                        emit(inventory)
                        if (inventorySubscriptions == 1) throw IOException("temporary inventory failure")
                        awaitCancellation()
                    },
                    flow {
                        catalogSubscriptions++
                        emit(catalog)
                        awaitCancellation()
                    },
                )
            val model = CosmeticInventoryViewModel(points).also { store.put("inventory", it) }
            runCurrent()
            model.selectItem("friend:luna")
            assertEquals(true, model.state.value.loadFailed)
            assertEquals(inventory, model.state.value.inventory)

            model.retry()
            runCurrent()

            assertEquals(false, model.state.value.loadFailed)
            assertEquals(inventory, model.state.value.inventory)
            assertEquals(catalog, model.state.value.catalog)
            assertEquals("friend:luna", model.state.value.selectedItemId)
            assertEquals(2, inventorySubscriptions)
            assertEquals(1, catalogSubscriptions)
        }

    @Test fun clearingTheViewModelCancelsBothObserversWithoutReportingFailure() =
        runTest(dispatcher) {
            var inventoryCancelled = false
            var catalogCancelled = false
            val points =
                FakePoints(
                    flow {
                        try {
                            emit(CosmeticInventory(emptySet(), emptyMap()))
                            awaitCancellation()
                        } finally {
                            inventoryCancelled = true
                        }
                    },
                    flow {
                        try {
                            emit(emptyList())
                            awaitCancellation()
                        } finally {
                            catalogCancelled = true
                        }
                    },
                )
            val model = CosmeticInventoryViewModel(points).also { store.put("inventory", it) }
            runCurrent()

            store.clear()
            runCurrent()

            assertEquals(true, inventoryCancelled)
            assertEquals(true, catalogCancelled)
            assertEquals(false, model.state.value.loadFailed)
        }

    @Test fun retryKeepsCatalogFailureUntilFreshDataArrives() =
        runTest(dispatcher) {
            val committed = listOf(CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0))
            val refreshed =
                listOf(
                    CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                    CosmeticItem("friend:luna", CosmeticSlot.FRIEND, 0),
                )
            val recovery = CompletableDeferred<List<CosmeticItem>>()
            var subscriptions = 0
            val points =
                FakePoints(
                    MutableStateFlow(
                        CosmeticInventory(setOf("friend:mobi"), mapOf(CosmeticSlot.FRIEND to "friend:mobi")),
                    ),
                    flow {
                        subscriptions++
                        if (subscriptions == 1) {
                            emit(committed)
                            throw IOException("temporary catalog failure")
                        }
                        emit(recovery.await())
                        awaitCancellation()
                    },
                )
            val model = CosmeticInventoryViewModel(points).also { store.put("inventory", it) }
            runCurrent()

            model.retry()
            runCurrent()

            assertEquals(true, model.state.value.catalogLoadFailed)
            assertEquals(committed, model.state.value.catalog)
            recovery.complete(refreshed)
            runCurrent()
            assertEquals(false, model.state.value.catalogLoadFailed)
            assertEquals(refreshed, model.state.value.catalog)
        }

    @Test fun parkingInterruptionClearsSelectionAndCancelsPendingPurchase() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<PurchaseResult>()
            var cancelled = false
            val points =
                object : FakePoints(flowOf(CosmeticInventory(emptySet(), emptyMap()))) {
                    override suspend fun purchase(
                        itemId: String,
                        expectedPrice: Long,
                    ): PurchaseResult =
                        try {
                            gate.await()
                        } catch (error: CancellationException) {
                            cancelled = true
                            throw error
                        }
                }
            val model = CosmeticInventoryViewModel(points).also { store.put("inventory", it) }
            runCurrent()
            model.selectItem("friend:luna")
            model.purchaseItem("friend:luna", 100)
            runCurrent()
            assertEquals(true, model.state.value.purchasing)

            model.onParkingInterrupted()
            runCurrent()

            assertEquals(true, cancelled)
            assertEquals(null, model.state.value.selectedItemId)
            assertEquals(false, model.state.value.purchasing)
            assertEquals(false, model.state.value.purchaseFailed)
        }

    private open class FakePoints(
        override val inventory: Flow<CosmeticInventory>,
        override val catalog: Flow<List<CosmeticItem>> = flowOf(emptyList()),
    ) : PointEconomy {
        override val wallet = flowOf(PointWallet(0))

        override suspend fun purchase(
            itemId: String,
            expectedPrice: Long,
        ): PurchaseResult = PurchaseResult.ItemUnavailable

        override suspend fun equip(itemId: String): EquipResult = EquipResult.ItemUnavailable

        override suspend fun awardQuest(
            questId: String,
            displayedSnapshot: VehicleSnapshot,
        ): PointAwardResult = PointAwardResult.QuestUnavailable
    }
}
