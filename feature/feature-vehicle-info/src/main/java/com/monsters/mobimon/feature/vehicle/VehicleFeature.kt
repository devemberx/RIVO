package com.monsters.mobimon.feature.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.core.navigation.VehicleRoute
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.core.presentation.parkedVerified
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonDimensions
import com.monsters.mobimon.core.ui.MobiMonMessage
import com.monsters.mobimon.core.ui.rememberParkingInterruption

class VehicleFeature(
    private val vehicle: VehiclePresentation,
    private val appearance: CompanionAppearancePresentation,
    private val cardSelectionStore: VehicleCardSelectionStore = InMemoryVehicleCardSelectionStore(),
) : FeatureEntry {
    override val routes = setOf(VehicleRoute.VEHICLE_INFO)

    @Composable
    override fun Content(
        route: AppRoute,
        navigator: FeatureNavigator,
        modifier: Modifier,
    ) {
        val snapshot = vehicle.snapshot()
        val parkingRequired = rememberParkingInterruption(snapshot.parkedVerified)
        val appearanceModel = appearance.model()
        val equipped by appearanceModel.state.collectAsStateWithLifecycle()
        val selectedCards by cardSelectionStore.selectedCards.collectAsStateWithLifecycle()
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(VehicleScreenBackground),
        ) {
            if (equipped.failed && !parkingRequired) {
                Row(
                    Modifier.fillMaxWidth().padding(MobiMonDimensions.contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(MobiMonDimensions.contentGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MobiMonMessage(
                        stringResource(
                            if (equipped.inventory == null) {
                                R.string.vehicle_appearance_load_failed
                            } else {
                                R.string.vehicle_appearance_update_failed
                            },
                        ),
                        Modifier.weight(1f),
                        isError = true,
                    )
                    MobiMonButton(onClick = appearanceModel::retry) { Text(stringResource(R.string.vehicle_retry)) }
                }
            }
            VehicleInfoScreen(
                snapshot = snapshot,
                modifier = Modifier.weight(1f),
                onBack = navigator.back,
                onHome = navigator.returnHome,
                friendId = equipped.friendId,
                accessoryId = equipped.accessoryId,
                backgroundId = equipped.backgroundId,
                outfitId = equipped.outfitId,
                selectedCards = selectedCards,
                onCardSelectionConfirmed = { if (snapshot.parkedVerified) cardSelectionStore.save(it) },
                parkingRequired = parkingRequired,
            )
        }
    }
}
