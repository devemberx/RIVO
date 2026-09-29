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
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MobiMonApplication : Application() {
    @Inject lateinit var runtime: CompanionRuntime

    @Inject lateinit var authentication: GitHubAuthentication

    private val _activityInForeground = MutableStateFlow(false)
    val activityInForeground = _activityInForeground.asStateFlow()

    override fun onCreate() {
        super.onCreate()
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
                    owner.lifecycleScope.launch { authentication.restore() }
                }

                override fun onStop(owner: LifecycleOwner) {
                    runtime.stop()
                }
            },
        )
    }
}
