package com.monsters.mobimon.feature.pet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monsters.mobimon.core.domain.PetRepository
import com.monsters.mobimon.core.navigation.AiRoute
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

class PetFeature(
    private val pets: PetRepository,
    private val wallet: PointPresentation,
    private val appearance: CompanionAppearancePresentation,
    private val vehicle: VehiclePresentation,
) : FeatureEntry {
    override val routes = setOf(CompanionRoute.HOME)

    @Composable
    override fun Content(
        route: AppRoute,
        navigator: FeatureNavigator,
        modifier: Modifier,
    ) {
        require(route in routes)
        val factory = remember(this) { viewModelFactory { initializer { PetViewModel(pets) } } }
        val model: PetViewModel = viewModel(factory = factory)
        val pet by model.state.collectAsStateWithLifecycle()
        val pointModel = wallet.model()
        val pointBalance by pointModel.state.collectAsStateWithLifecycle()
        val appearanceModel = appearance.model()
        val equipped by appearanceModel.state.collectAsStateWithLifecycle()
        val reading = vehicle.reading()
        val snapshot = reading.snapshot
        val retry = {
            model.retry()
            appearanceModel.retry()
            pointModel.retry()
        }
        val profile = pet.profile
        if (profile == null || pet.isLoading) {
            PetHomeLoadingScreen(
                failed = pet.loadFailed,
                onRetry = retry,
                modifier = modifier,
                parkingBadgeConfirmed = snapshot.parkingBadgeConfirmed,
            )
            return
        }
        PetHomeScreen(
            profile = profile,
            snapshot = snapshot,
            onOpenMenu = navigator.openMenu,
            onPetClick = { bounds ->
                if (snapshot.parkedVerified) navigator.navigateFrom(AiRoute.CONVERSATION, bounds)
            },
            modifier = modifier,
            pointBalance = (pointBalance as? PointBalanceState.Ready)?.balance,
            pointLoadFailed = pointBalance == PointBalanceState.Failed,
            friendId = equipped.friendId,
            accessoryId = equipped.accessoryId,
            outfitId = equipped.outfitId,
            backgroundId = equipped.backgroundId,
            backgroundOverlayId = equipped.backgroundOverlayId,
            backgroundPropId = equipped.backgroundPropId,
            backgroundEffectId = equipped.backgroundEffectId,
            interactionAllowed = snapshot.parkedVerified,
            profileObservationFailed = pet.loadFailed,
            onRetryProfile = retry,
            inventoryLoaded = equipped.inventory != null,
            inventoryLoadFailed = equipped.failed,
            connectionAvailable = true,
            backgroundTimeOfDay = reading.backgroundTimeOfDay,
        )
    }
}
