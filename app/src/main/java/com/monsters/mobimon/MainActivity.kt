package com.monsters.mobimon

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import com.monsters.mobimon.service.FloatingCompanionService
import com.monsters.mobimon.ui.MobiMonApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var launchReady by mutableStateOf(false)

    @Inject lateinit var entries: Set<@JvmSuppressWildcards FeatureEntry>

    @Inject lateinit var appUse: AppUseStateSource

    @Inject lateinit var appearance: CompanionAppearancePresentation

    @Inject lateinit var pets: PetRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var authentication: GitHubAuthentication

    @Inject lateinit var conversation: ConversationProvider

    @Inject lateinit var conversationStore: ConversationStore

    @Inject lateinit var networkStatus: ConversationNetworkStatus

    @Inject lateinit var speechInput: ConversationSpeechInput

    @Inject lateinit var vehicle: VehiclePresentation

    @Inject lateinit var points: PointEconomy

    @Inject lateinit var questCatalog: PointQuestCatalog

    @Inject lateinit var vehicleCards: VehicleCardSelectionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchReady = savedInstanceState != null
        splashScreen.setOnExitAnimationListener { splash ->
            splash.remove()
            launchReady = true
        }
        observeLauncherOverlay()
        setContent {
            MobiMonApp(
                entries,
                appUse,
                appearance,
                settings,
                authentication,
                conversation,
                vehicle,
                pets,
                networkStatus,
                speechInput,
                points,
                questCatalog,
                vehicleCards,
                conversationStore,
                launchReady = launchReady,
            )
        }
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        // Also release launches for which Android does not create a splash view.
        launchReady = true
    }

    override fun onResume() {
        super.onResume()
        syncLauncherOverlay()
    }

    private fun syncLauncherOverlay() {
        lifecycleScope.launch {
            val current = settings.settings.first()
            updateOverlayService(current.launcherCharacterEnabled)
        }
    }

    private fun observeLauncherOverlay() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settings.settings.collect { current ->
                    updateOverlayService(current.launcherCharacterEnabled)
                }
            }
        }
    }

    private fun updateOverlayService(enabled: Boolean) {
        val serviceIntent = Intent(this, FloatingCompanionService::class.java)
        if (enabled && Settings.canDrawOverlays(this)) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            stopService(serviceIntent)
        }
    }
}
