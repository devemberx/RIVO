package com.monsters.mobimon.di.features

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CompanionSettings
import com.monsters.mobimon.core.domain.IdGenerator
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.core.domain.WeatherCondition
import com.monsters.mobimon.core.domain.WriteResult
import com.monsters.mobimon.core.ui.companionTimePeriod
import com.monsters.mobimon.core.vss.DefaultParkedVssRawVehicleSource
import com.monsters.mobimon.debug.DebugStore
import com.monsters.mobimon.debug.deriveWeatherCondition
import com.monsters.mobimon.vehicle.DemoVehicleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VssBackgroundTimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context
            .getSharedPreferences("debug_vss_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun clearPreferencesAfterTest() {
        clearPreferences()
    }

    @Test
    fun debugVssTimestampAndInterpretationSelectTheScene() =
        runTest {
            val settings = TestSettings()
            val debug = DebugStore(context)
            debug.updateState {
                it.copy(raw = it.raw.copy(currentLocationTimestamp = "2026-10-08T05:00:00+09:00"))
            }
            val vehicle =
                DemoVehicleRepository(
                    Clock { testScheduler.currentTime },
                    IdGenerator { "epoch" },
                    settings,
                    debug,
                    DefaultParkedVssRawVehicleSource(),
                    backgroundScope,
                )
            vehicle.start()
            try {
                runCurrent()
                assertEquals("sunrise", companionTimePeriod(vehicle.snapshots.value.timeOfDay))

                debug.updateState {
                    it.copy(raw = it.raw.copy(currentLocationTimestamp = "2026-10-08T00:00:00+09:00"))
                }
                runCurrent()
                assertEquals("midnight", companionTimePeriod(vehicle.snapshots.value.timeOfDay))
                assertEquals(WeatherCondition.CLOUDY_OR_NIGHT, debug.state.value.deriveWeatherCondition())

                debug.updateState {
                    it.copy(overrides = it.overrides.copy(timeOfDay = "Day"))
                }
                runCurrent()
                assertEquals("day", companionTimePeriod(vehicle.snapshots.value.timeOfDay))
            } finally {
                vehicle.stop()
            }
        }

    private class TestSettings : SettingsRepository {
        override val settings = MutableStateFlow(CompanionSettings(debugModeEnabled = true))

        override suspend fun setReducedMotion(enabled: Boolean) = WriteResult.Success

        override suspend fun setLauncherCharacterEnabled(enabled: Boolean) = WriteResult.Success

        override suspend fun setDebugModeEnabled(enabled: Boolean) = WriteResult.Success
    }
}
