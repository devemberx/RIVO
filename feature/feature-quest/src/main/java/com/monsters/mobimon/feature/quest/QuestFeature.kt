package com.monsters.mobimon.feature.quest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.PointQuestCatalog
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.core.navigation.QuestRoute
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.PointPresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.core.presentation.parkedVerified
import com.monsters.mobimon.core.presentation.parkingBadgeConfirmed
import com.monsters.mobimon.core.ui.rememberParkingInterruption

class QuestFeature(
    private val vehicle: VehiclePresentation,
    private val wallet: PointPresentation,
    private val appearance: CompanionAppearancePresentation,
    private val economy: PointEconomy,
    catalog: PointQuestCatalog,
) : FeatureEntry {
    override val routes = setOf(QuestRoute.QUESTS)
    private val catalog = QuestCatalog(catalog)

    @Composable
    private fun model(): QuestViewModel {
        val factory = remember(this) { viewModelFactory { initializer { QuestViewModel(economy) } } }
        return viewModel(factory = factory)
    }

    @Composable
    override fun Content(
        route: AppRoute,
        navigator: FeatureNavigator,
        modifier: Modifier,
    ) {
        val model = model()
        val walletModel = wallet.model()
        val appearanceModel = appearance.model()
        LaunchedEffect(model, walletModel, appearanceModel) {
            model.retry()
            walletModel.retry()
            appearanceModel.retry()
        }
        val state by model.state.collectAsStateWithLifecycle()
        val pointBalance by walletModel.state.collectAsStateWithLifecycle()
        val equipped by appearanceModel.state.collectAsStateWithLifecycle()
        val reading = vehicle.reading()
        val snapshot = reading.snapshot
        val parkingRequired = rememberParkingInterruption(snapshot.parkedVerified)
        LaunchedEffect(parkingRequired) {
            if (parkingRequired) model.onParkingInterrupted()
        }
        val context = LocalContext.current
        val screenState =
            catalog.present(
                state = state,
                appearance = equipped,
                pointBalance = pointBalance,
                parkedVerified = snapshot.parkedVerified,
                snapshot = snapshot,
                text = context::getString,
            )
        QuestScreen(
            state = screenState,
            onClaimReward = { questId ->
                if (!parkingRequired && screenState.canClaim) model.claimPointQuest(questId, reading.evidence)
            },
            onDismissHiddenQuest = model::dismissHiddenQuest,
            onDismissRewardSuccess = model::dismissRewardSuccess,
            onRetryQuests = model::retry,
            onRetryWallet = walletModel::retry,
            onRetryAppearance = appearanceModel::retry,
            onNavigateRoute = navigator.navigate,
            onBack = navigator.back,
            onHome = navigator.returnHome,
            modifier = modifier,
            parkingBadgeConfirmed = snapshot.parkingBadgeConfirmed,
            parkingRequired = parkingRequired,
        )
    }
}
