package com.monsters.mobimon.di.features

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VehicleCardSelectionPreferencesTest {
    @Test
    fun confirmedSelectionSurvivesStoreRecreation() {
        val preferences =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("vehicle-card-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val first = VehicleCardSelectionPreferences(preferences)
        first.save(listOf("battery-health", "charging", "tire", "washer", "low-beam", "fatigue"))

        assertEquals("battery-health", VehicleCardSelectionPreferences(preferences).selectedCards.value.first())
    }

    @Test
    fun malformedOrUnknownSlotsFallBackToCurrentDefaults() {
        val preferences =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("vehicle-card-invalid-test", Context.MODE_PRIVATE)
        preferences.edit().putString("slots", "battery,invalid,tire,washer,environment,assist").commit()

        assertEquals(
            listOf("battery", "tire", "washer", "low-beam", "fatigue", "service-distance"),
            VehicleCardSelectionPreferences(preferences).selectedCards.value,
        )
    }

    @Test
    fun retiredGroupedCardsAreReplacedWithoutResettingOtherSelections() {
        val preferences =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("vehicle-card-retired-test", Context.MODE_PRIVATE)
        preferences.edit().putString("slots", "battery-health,charging,tire,washer,environment,assist").commit()

        assertEquals(
            listOf("battery-health", "charging", "tire", "washer", "low-beam", "fatigue"),
            VehicleCardSelectionPreferences(preferences).selectedCards.value,
        )
    }

    @Test
    fun savedOldDefaultsBecomeNewDefaults() {
        val preferences =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("vehicle-card-old-defaults-test", Context.MODE_PRIVATE)
        preferences.edit().putString("slots", "battery,charging,tire,washer,environment,assist").commit()

        assertEquals(
            listOf("battery", "tire", "washer", "low-beam", "fatigue", "service-distance"),
            VehicleCardSelectionPreferences(preferences).selectedCards.value,
        )
    }

    @Test
    fun retiredCardReplacementAvoidsAnAlreadySelectedCard() {
        val preferences =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("vehicle-card-retired-collision-test", Context.MODE_PRIVATE)
        preferences.edit().putString("slots", "low-beam,battery-health,tire,washer,environment,assist").commit()

        assertEquals(
            listOf("low-beam", "battery-health", "tire", "washer", "battery", "fatigue"),
            VehicleCardSelectionPreferences(preferences).selectedCards.value,
        )
    }
}
