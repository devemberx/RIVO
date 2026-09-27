package com.monsters.mobimon.feature.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.CompanionRoute
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.PointBalanceState
import com.monsters.mobimon.core.presentation.PointPresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.core.presentation.parkedVerified
import com.monsters.mobimon.core.presentation.parkingBadgeConfirmed
import com.monsters.mobimon.core.ui.rememberParkingInterruption

class CustomizationFeature(
    private val points: PointEconomy,
    private val wallet: PointPresentation,
    private val appearance: CompanionAppearancePresentation,
    private val vehicle: VehiclePresentation,
) : FeatureEntry {
    override val routes = setOf(CompanionRoute.APPEARANCE)

    @Composable
    override fun Content(
        route: AppRoute,
        navigator: FeatureNavigator,
        modifier: Modifier,
    ) {
        require(route == CompanionRoute.APPEARANCE) { "Unsupported customization route: $route" }
        val factory =
            remember(this) {
                viewModelFactory { initializer { CosmeticInventoryViewModel(points) } }
            }
        val inventoryModel: CosmeticInventoryViewModel = viewModel(factory = factory)
        val inventoryState by inventoryModel.state.collectAsStateWithLifecycle()
        val appearanceModel = appearance.model()
        val appearanceState by appearanceModel.state.collectAsStateWithLifecycle()
        val pointModel = wallet.model()
        val pointBalance by pointModel.state.collectAsStateWithLifecycle()
        val vehicleReading = vehicle.reading()
        val vehicleSnapshot = vehicleReading.snapshot
        val interactionAllowed = vehicleSnapshot.parkedVerified
        val parkingRequired = rememberParkingInterruption(interactionAllowed)
        LaunchedEffect(parkingRequired) {
            if (parkingRequired) inventoryModel.onParkingInterrupted()
        }

        CustomizationScreen(
            inventory = inventoryState.inventory ?: appearanceState.inventory,
            storeInventoryReady = inventoryState.inventory != null,
            catalog = inventoryState.catalog,
            selectedItemId = inventoryState.selectedItemId,
            purchasing = inventoryState.purchasing,
            purchaseFailed = inventoryState.purchaseFailed,
            onSelectItem = { if (!parkingRequired) inventoryModel.selectItem(it) },
            onPurchaseItem = { itemId, price ->
                if (interactionAllowed && !parkingRequired && inventoryModel.state.value.inventory != null) {
                    inventoryModel.purchaseItem(itemId, price)
                }
            },
            onEquipItem = { itemId ->
                if (interactionAllowed && !parkingRequired && inventoryModel.state.value.inventory != null) {
                    inventoryModel.equipItem(itemId)
                }
            },
            onEquipFriend = { itemId ->
                if (interactionAllowed &&
                    !parkingRequired &&
                    inventoryModel.state.value.inventory != null
                ) {
                    inventoryModel.equipFriend(itemId)
                }
            },
            pointBalance = (pointBalance as? PointBalanceState.Ready)?.balance,
            pointLoadFailed = pointBalance == PointBalanceState.Failed,
            modifier = modifier,
            saving = inventoryState.saving,
            loadFailed = inventoryState.inventoryLoadFailed,
            catalogLoadFailed = inventoryState.catalogLoadFailed,
            saveFailed = inventoryState.saveFailed,
            onRetry = {
                inventoryModel.retry()
                pointModel.retry()
            },
            onBack = navigator.back,
            timeOfDay = vehicleReading.backgroundTimeOfDay,
            interactionAllowed = interactionAllowed,
            parkingBadgeConfirmed = vehicleSnapshot.parkingBadgeConfirmed,
            parkingRequired = parkingRequired,
            onHome = navigator.returnHome,
        )
    }
}
