package com.monsters.mobimon.di

import android.content.Context
import com.monsters.mobimon.BuildConfig
import com.monsters.mobimon.chat.AndroidUserName
import com.monsters.mobimon.chat.VehicleConversationContext
import com.monsters.mobimon.core.auth.PersistentGitHubAuthentication
import com.monsters.mobimon.core.database.AtomicConversationStore
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.ConversationContextSource
import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationStore
import com.monsters.mobimon.core.domain.CurrentAppUse
import com.monsters.mobimon.core.domain.CurrentVehicleEvidence
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.ProgressionIdentity
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSourceProvider
import com.monsters.mobimon.core.domain.VehicleFreshnessPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthenticationModule {
    @Provides
    @Singleton
    fun authentication(
        @ApplicationContext context: Context,
        appUse: CurrentAppUse,
        vehicle: CurrentVehicleEvidence,
        identity: ProgressionIdentity,
        sourceProvider: SignalSourceProvider,
        clock: Clock,
        freshness: VehicleFreshnessPolicy,
    ): PersistentGitHubAuthentication =
        PersistentGitHubAuthentication.create(context, BuildConfig.GITHUB_CLIENT_ID) {
            val snapshot = freshness.displaySnapshot(vehicle.snapshot(), sourceProvider.source(), clock.nowMillis())
            appUse.state() == AppUseState.ALLOWED &&
                snapshot.quality == SignalQuality.VALID &&
                snapshot.drivingState == DrivingState.PARKED
        }

    @Provides @Singleton
    fun conversationStore(
        @ApplicationContext context: Context,
    ): ConversationStore = AtomicConversationStore(File(context.noBackupFilesDir, "current-conversations"))

    @Provides
    fun githubAuthentication(authentication: PersistentGitHubAuthentication): GitHubAuthentication = authentication

    @Provides
    @Singleton
    fun conversation(
        authentication: PersistentGitHubAuthentication,
        context: ConversationContextSource,
    ): ConversationProvider = authentication.conversationProvider(context)

    @Provides @Singleton
    fun conversationContext(
        @ApplicationContext context: Context,
        vehicle: CurrentVehicleEvidence,
        clock: Clock,
        settings: SettingsRepository,
        freshness: VehicleFreshnessPolicy,
    ): ConversationContextSource =
        VehicleConversationContext(
            AndroidUserName(context)::read,
            vehicle::snapshot,
            clock::nowMillis,
            { settings.settings.first().debugModeEnabled },
            freshness,
        )
}
