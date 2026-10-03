package com.monsters.mobimon.feature.auth.testing

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.robolectric.Shadows.shadowOf

/** A test-only Compose host, including when the production variant excludes preview activities. */
class ComposeTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        shadowOf(packageManager).addActivityIfNotPresent(ComponentName(this, ComponentActivity::class.java))
    }
}
