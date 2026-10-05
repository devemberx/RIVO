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
import com.monsters.mobimon.service.FloatingCompanionService
import com.monsters.mobimon.ui.MobiMonApp
import com.monsters.mobimon.ui.StartupPreviewView
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var launchReady by mutableStateOf(false)

    @Inject internal lateinit var dependencies: Lazy<MainActivityDependencies>

    private var contentReady = false
    private val nativeSplashReleased = CompletableDeferred<Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchReady = savedInstanceState != null
        splashScreen.setOnExitAnimationListener { splash ->
            splash.remove()
            launchReady = true
            nativeSplashReleased.complete(Unit)
        }
        if (savedInstanceState == null) {
            setContentView(
                StartupPreviewView(this) {
                    lifecycleScope.launch {
                        // Leave the UI thread free to remove the system window before feature setup.
                        // Warm/platform launches may omit the callback; keep their wait bounded.
                        withTimeoutOrNull(500) { nativeSplashReleased.await() }
                        awaitFrame()
                        launchReady = true
                        if (!isFinishing && !isDestroyed) showAppContent()
                    }
                },
            )
        } else {
            showAppContent()
        }
    }

    private fun showAppContent() {
        val services = dependencies.get()
        contentReady = true
        observeLauncherOverlay()
        setContent {
            MobiMonApp(
                services.entries,
                services.appUse,
                services.appearance,
                services.settings,
                services.authentication,
                services.conversation,
                services.vehicle,
                services.pets,
                services.networkStatus,
                services.speechInput,
                services.points,
                services.questCatalog,
                services.vehicleCards,
                services.conversationStore,
                launchReady = launchReady,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (contentReady) syncLauncherOverlay()
    }

    private fun syncLauncherOverlay() {
        lifecycleScope.launch {
            val current =
                dependencies
                    .get()
                    .settings.settings
                    .first()
            updateOverlayService(current.launcherCharacterEnabled)
        }
    }

    private fun observeLauncherOverlay() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dependencies.get().settings.settings.collect { current ->
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
