package com.monsters.mobimon.feature.customization

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.EquipResult
import com.monsters.mobimon.core.domain.PointAwardResult
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.PointWallet
import com.monsters.mobimon.core.domain.ProgressionIdentity
import com.monsters.mobimon.core.domain.PurchaseResult
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.UtcClock
import com.monsters.mobimon.core.domain.VehicleFreshnessPolicy
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.navigation.CompanionRoute
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.PointPresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.core.ui.MobiMonTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
class CustomizationFeatureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun parkedRouteShowsCustomizationPopupAfterParkingLoss() {
        val parked =
            VehicleSnapshot(
                "parked",
                "test",
                1,
                1_000,
                SignalSource.SIMULATED,
                DrivingState.PARKED,
                SignalQuality.VALID,
                speed = 0,
                gear = "P",
            )
        val vehicle =
            object : VehicleRepository {
                override val snapshots = MutableStateFlow(parked)

                override fun start() = error("Feature must not start a provider")

                override fun stop() = error("Feature must not stop a provider")
            }
        val points = TestPoints()
        val feature =
            CustomizationFeature(
                points,
                PointPresentation(points),
                CompanionAppearancePresentation(points),
                VehiclePresentation(
                    vehicle,
                    ProgressionIdentity("test", SignalSource.SIMULATED),
                    Clock { 2_000 },
                    VehicleFreshnessPolicy(15_000),
                    UtcClock { 0L },
                ),
            )
        compose.setContent {
            MobiMonTheme {
                Surface { feature.Content(CompanionRoute.APPEARANCE, FeatureNavigator({}, {}, {}, {}), Modifier) }
            }
        }
        compose.onNodeWithTag("parking-interruption-dialog").assertDoesNotExist()

        compose.runOnIdle {
            vehicle.snapshots.value =
                parked.copy(
                    id = "moving",
                    sequence = 2,
                    drivingState = DrivingState.MOVING,
                    speed = 12,
                    gear = "D",
                )
        }
        compose.onNodeWithText("주차 후 꾸미기를 이어가요").assertIsDisplayed()
        compose.runOnIdle { vehicle.snapshots.value = parked.copy(id = "reparked", sequence = 3) }
        compose.onNodeWithTag("parking-interruption-dialog").assertDoesNotExist()
    }

    private class TestPoints : PointEconomy {
        override val wallet = flowOf(PointWallet(100))
        override val inventory = flowOf(CosmeticInventory(emptySet(), emptyMap()))
        override val catalog = flowOf(emptyList<CosmeticItem>())

        override suspend fun purchase(
            itemId: String,
            expectedPrice: Long,
        ): PurchaseResult = PurchaseResult.ItemUnavailable

        override suspend fun equip(itemId: String): EquipResult = EquipResult.ItemUnavailable

        override suspend fun awardQuest(
            questId: String,
            displayedSnapshot: VehicleSnapshot,
        ): PointAwardResult = PointAwardResult.QuestUnavailable
    }
}
