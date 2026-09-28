package com.monsters.mobimon.di.features

import android.content.Context
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.ProgressionIdentity
import com.monsters.mobimon.core.domain.SignalSourceProvider
import com.monsters.mobimon.core.domain.VehicleFreshnessPolicy
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.feature.vehicle.VehicleCardSelectionStore
import com.monsters.mobimon.feature.vehicle.VehicleFeature
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VehicleFeatureModule {
    @Provides @Singleton
    fun cardSelectionStore(
        @ApplicationContext context: Context,
    ): VehicleCardSelectionStore =
        VehicleCardSelectionPreferences(context.getSharedPreferences("vehicle-status-cards", Context.MODE_PRIVATE))

    @Provides @Singleton
    fun presentation(
        source: VehicleRepository,
        identity: ProgressionIdentity,
        clock: Clock,
        freshness: VehicleFreshnessPolicy,
        sourceProvider: SignalSourceProvider,
    ): VehiclePresentation = VehiclePresentation(source, identity, clock, freshness, sourceProvider)

    @Provides @IntoSet @Singleton
    fun entry(
        vehicle: VehiclePresentation,
        appearance: CompanionAppearancePresentation,
        cards: VehicleCardSelectionStore,
    ): FeatureEntry = VehicleFeature(vehicle, appearance, cards)
}
