package com.monsters.mobimon.testing

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.robolectric.Shadows.shadowOf

/** Standalone Compose hosts exist only in Robolectric, including Release tests. */
class ComposeTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        shadowOf(packageManager).addActivityIfNotPresent(ComponentName(this, ComponentActivity::class.java))
    }
}
