package com.monsters.mobimon

import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationStore
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.PetRepository
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.PointQuestCatalog
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.feature.auth.ConversationNetworkStatus
import com.monsters.mobimon.feature.auth.ConversationSpeechInput
import com.monsters.mobimon.feature.vehicle.VehicleCardSelectionStore
import com.monsters.mobimon.runtime.AppUseStateSource
import javax.inject.Inject

/** Construct the feature graph only after the launch preview has drawn. */
internal class MainActivityDependencies
    @Inject
    constructor(
        val entries: Set<@JvmSuppressWildcards FeatureEntry>,
        val appUse: AppUseStateSource,
        val appearance: CompanionAppearancePresentation,
        val pets: PetRepository,
        val settings: SettingsRepository,
        val authentication: GitHubAuthentication,
        val conversation: ConversationProvider,
        val conversationStore: ConversationStore,
        val networkStatus: ConversationNetworkStatus,
        val speechInput: ConversationSpeechInput,
        val vehicle: VehiclePresentation,
        val points: PointEconomy,
        val questCatalog: PointQuestCatalog,
        val vehicleCards: VehicleCardSelectionStore,
    )
