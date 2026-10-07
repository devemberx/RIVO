package com.monsters.mobimon.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.PetProfile
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.core.ui.companionBackgroundRes
import com.monsters.mobimon.feature.customization.CustomizationScreen
import com.monsters.mobimon.feature.pet.PetHomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native rendering with simulated VSS labels; does not verify a live vehicle adapter. */
@RunWith(AndroidJUnit4::class)
class CompanionBackgroundDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val cityId = "background:cyberpunk_city"
    private val periods = listOf("Midnight", "Sunrise", "Morning", "Day", "Afternoon", "Sunset", "Night")

    @Test
    fun cityHomeRendersAllSevenPeriodsWithoutParticles() {
        val time = mutableStateOf("Night")
        compose.setContent {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                MobiMonTheme {
                    PetHomeScreen(
                        profile = PetProfile("background-device-fixture"),
                        snapshot =
                            VehicleSnapshot(
                                "fixture",
                                "epoch",
                                1,
                                1000,
                                SignalSource.SIMULATED,
                                DrivingState.PARKED,
                                SignalQuality.VALID,
                                72,
                            ),
                        onOpenMenu = {},
                        onPetClick = {},
                        backgroundId = cityId,
                        backgroundTimeOfDay = time.value,
                    )
                }
            }
        }
        val rendered =
            periods.map { period ->
                compose.runOnIdle { time.value = period }
                compose.onNodeWithTag("home-background-particles").assertDoesNotExist()
                fingerprint(compose.onNodeWithTag("home-background").captureToImage())
            }
        assertEquals(7, rendered.distinct().size)
    }

    @Test
    fun cityStorePreviewAndThumbnailRenderEveryPeriod() {
        val time = mutableStateOf("Night")
        compose.setContent {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                MobiMonTheme {
                    CustomizationScreen(
                        inventory =
                            CosmeticInventory(
                                setOf("friend:mobi", cityId),
                                mapOf(
                                    CosmeticSlot.FRIEND to "friend:mobi",
                                    CosmeticSlot.BACKGROUND to cityId,
                                ),
                            ),
                        catalog =
                            listOf(
                                CosmeticItem("friend:mobi", CosmeticSlot.FRIEND, 0),
                                CosmeticItem(cityId, CosmeticSlot.BACKGROUND, 400),
                            ),
                        selectedItemId = cityId,
                        purchasing = false,
                        purchaseFailed = false,
                        onSelectItem = {},
                        onPurchaseItem = { _, _ -> },
                        onEquipItem = {},
                        onEquipFriend = {},
                        pointBalance = 1200,
                        pointLoadFailed = false,
                        timeOfDay = time.value,
                    )
                }
            }
        }
        compose.onNodeWithTag("store-tab-BACKGROUND").performClick()
        val previews = mutableSetOf<Int>()
        val thumbnails = mutableSetOf<Int>()
        periods.forEach { period ->
            compose.runOnIdle { time.value = period }
            compose.onNodeWithTag("store-preview-particles").assertDoesNotExist()
            val backgroundId = companionBackgroundRes(period, cityId)
            // Compose idleness does not wait for background decoding on the IO dispatcher.
            compose.waitUntil(10_000) {
                compose
                    .onAllNodesWithTag("store-background-ready-$backgroundId", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .size == 2
            }
            previews += fingerprint(compose.onNodeWithTag("preview-background").captureToImage())
            thumbnails +=
                fingerprint(compose.onNodeWithTag("store-artwork-$cityId", useUnmergedTree = true).captureToImage())
        }
        assertEquals(7, previews.size)
        assertEquals(7, thumbnails.size)
    }

    private fun fingerprint(image: ImageBitmap): Int {
        val pixels = image.toPixelMap()
        return listOf(0.03f to 0.03f, 0.9f to 0.1f, 0.95f to 0.8f).fold(1) { hash, (x, y) ->
            hash * 31 + pixels[(image.width * x).toInt(), (image.height * y).toInt()].hashCode()
        }
    }
}
