package com.monsters.mobimon.di.features

import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationStore
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.PetRepository
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.feature.auth.AiFeature
import com.monsters.mobimon.feature.auth.ConversationNetworkStatus
import com.monsters.mobimon.feature.auth.ConversationSpeechInput
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiFeatureModule {
    @Provides @IntoSet @Singleton
    fun entry(
        pets: PetRepository,
        vehicle: VehiclePresentation,
        appearance: CompanionAppearancePresentation,
        authentication: GitHubAuthentication,
        conversation: ConversationProvider,
        networkStatus: ConversationNetworkStatus,
        speechInput: ConversationSpeechInput,
        conversationStore: ConversationStore,
    ): FeatureEntry =
        AiFeature(
            pets,
            vehicle,
            appearance,
            authentication,
            conversation,
            networkStatus,
            speechInput,
            conversationStore,
        )
}
