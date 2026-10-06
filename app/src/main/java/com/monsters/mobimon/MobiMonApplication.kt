package com.monsters.mobimon

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.runtime.CompanionRuntime
import com.monsters.mobimon.runtime.DriveEvidenceProducer
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MobiMonApplication : Application() {
    @Inject lateinit var runtime: CompanionRuntime

    @Inject lateinit var driveEvidence: DriveEvidenceProducer

    @Inject lateinit var authentication: Lazy<GitHubAuthentication>

    private val _activityInForeground = MutableStateFlow(false)
    val activityInForeground = _activityInForeground.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        // Verified vehicle evidence only; Debug and fallback snapshots are ignored.
        driveEvidence.start()
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    _activityInForeground.value = true
                }

                override fun onActivityPaused(activity: Activity) {
                    _activityInForeground.value = false
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        // Process lifecycle survives Activity recreation; all routes share one connection.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    runtime.start()
                    // Client/credential construction is not required to draw the loading sky.
                    owner.lifecycleScope.launch(Dispatchers.IO) { authentication.get().restore() }
                }

                override fun onStop(owner: LifecycleOwner) {
                    runtime.stop()
                }
            },
        )
    }
}
