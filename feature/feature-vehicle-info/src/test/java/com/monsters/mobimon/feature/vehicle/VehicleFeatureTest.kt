package com.monsters.mobimon.feature.vehicle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.EquipResult
import com.monsters.mobimon.core.domain.PointAwardResult
import com.monsters.mobimon.core.domain.PointEconomy
import com.monsters.mobimon.core.domain.PointWallet
import com.monsters.mobimon.core.domain.ProgressionIdentity
import com.monsters.mobimon.core.domain.PurchaseResult
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleFreshnessPolicy
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.core.navigation.VehicleRoute
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
class VehicleFeatureTest {
    @get:Rule val compose = createComposeRule()

    private var visible by mutableStateOf(true)
    private lateinit var view: View
    private val vehicleSnapshots =
        MutableStateFlow(
            VehicleSnapshot(
                "parked",
                "test",
                1,
                1_000,
                SignalSource.REAL,
                DrivingState.PARKED,
                SignalQuality.VALID,
                speed = 0,
                gear = "P",
            ),
        )

    @Test
    fun parkedRouteShowsVehiclePopupAfterParkingLoss() {
        vehicleSnapshots.value =
            VehicleSnapshot(
                "parked",
                "test",
                1,
                1_000,
                SignalSource.REAL,
                DrivingState.PARKED,
                SignalQuality.VALID,
                speed = 0,
                gear = "P",
            )
        show(FakePoints())
        compose.onNodeWithText("주차 후 차량 상태를 확인해요").assertDoesNotExist()

        compose.runOnIdle {
            vehicleSnapshots.value =
                vehicleSnapshots.value.copy(
                    id = "moving",
                    sequence = 2,
                    drivingState = DrivingState.MOVING,
                    speed = 8,
                    gear = "D",
                )
        }
        compose.onNodeWithText("주차 후 차량 상태를 확인해요").assertIsDisplayed()
        compose.runOnIdle {
            vehicleSnapshots.value =
                vehicleSnapshots.value.copy(
                    id = "reparked",
                    sequence = 3,
                    drivingState = DrivingState.PARKED,
                    speed = 0,
                    gear = "P",
                )
        }
        compose.onNodeWithText("주차 후 차량 상태를 확인해요").assertDoesNotExist()
    }

    @Test
    fun enteringVehicleRouteWhileUnparkedShowsVehiclePopupImmediately() {
        vehicleSnapshots.value =
            VehicleSnapshot(
                "moving",
                "test",
                1,
                1_000,
                SignalSource.REAL,
                DrivingState.MOVING,
                SignalQuality.VALID,
                speed = 25,
                gear = "D",
            )
        show(FakePoints())
        compose.onNodeWithText("주차 후 차량 상태를 확인해요").assertIsDisplayed()
    }

    @Test
    fun initialAppearanceFailureExposesRetryWithoutHidingVehicleInformation() {
        val points = FakePoints(failInitially = true)
        show(points)

        compose.onNodeWithText("친구 정보를 불러오지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("배터리 정보 없음").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()

        compose.onNodeWithContentDescription("Luna 고양이").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("친구 정보를 불러오지 못했어요.").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, points.activeObservers) }
    }

    @Test
    fun appearanceFailureRetainsCommittedLookAcrossRevisitsAndRetryRecovers() {
        val points = FakePoints()
        show(points)
        compose.onNodeWithContentDescription("Luna 고양이").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { points.fail.trySend(Unit) }

        compose.onNodeWithText("친구 정보를 갱신하지 못했어요.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Luna 고양이").assertExists()
        compose.runOnIdle {
            points.savedInventory.value = equippedFriend("friend:mobi")
            visible = false
        }
        compose.waitForIdle()
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("친구 정보를 갱신하지 못했어요.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Luna 고양이").assertExists()

        compose.onNodeWithText("다시 시도").performClick()

        compose.onNodeWithContentDescription("Mobi 토끼").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("친구 정보를 갱신하지 못했어요.").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, points.subscriptions)
            assertEquals(1, points.activeObservers)
            assertEquals(1, points.maxActiveObservers)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun enlargedTextAppearanceRecoveryProducesReviewImage() {
        show(FakePoints(failInitially = true), fontScale = 1.5f)
        compose.onNodeWithText("친구 정보를 불러오지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertIsDisplayed().assertHeightIsAtLeast(76.dp)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/vehicle-ui").apply { mkdirs() }
            File(directory, "enlarged-text-appearance-retry.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithText("친구 정보를 불러오지 못했어요.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Luna 고양이").performScrollTo().assertIsDisplayed()
    }

    private fun show(
        points: PointEconomy,
        fontScale: Float = 1f,
    ) {
        val vehicle =
            object : VehicleRepository {
                override val snapshots = vehicleSnapshots

                override fun start() = error("Feature must not start a provider")

                override fun stop() = error("Feature must not stop a provider")
            }
        val feature =
            VehicleFeature(
                VehiclePresentation(
                    vehicle,
                    ProgressionIdentity("saved", SignalSource.REAL),
                    Clock { 2_000 },
                    VehicleFreshnessPolicy(15_000),
                ),
                CompanionAppearancePresentation(points),
            )
        val navigator = FeatureNavigator({}, {}, {}, {})
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalMobiMonMotionEnabled provides false,
            ) {
                MobiMonTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        if (visible) feature.Content(VehicleRoute.VEHICLE_INFO, navigator, Modifier)
                    }
                }
            }
        }
    }

    private class FakePoints(
        private val failInitially: Boolean = false,
    ) : PointEconomy {
        val fail = Channel<Unit>(Channel.CONFLATED)
        val savedInventory = MutableStateFlow(equippedFriend("friend:luna"))
        var subscriptions = 0
        var activeObservers = 0
        var maxActiveObservers = 0
        override val inventory =
            flow {
                subscriptions++
                activeObservers++
                maxActiveObservers = maxOf(maxActiveObservers, activeObservers)
                try {
                    if (subscriptions == 1) {
                        if (!failInitially) {
                            emit(savedInventory.value)
                            fail.receive()
                        }
                        throw IOException("temporary inventory read failure")
                    }
                    emitAll(savedInventory)
                } finally {
                    activeObservers--
                }
            }
        override val wallet = flowOf(PointWallet(0))
        override val catalog = flowOf(emptyList<CosmeticItem>())

        override suspend fun purchase(
            itemId: String,
            expectedPrice: Long,
        ) = PurchaseResult.ItemUnavailable

        override suspend fun equip(itemId: String) = EquipResult.ItemUnavailable

        override suspend fun awardQuest(
            questId: String,
            displayedSnapshot: VehicleSnapshot,
        ) = PointAwardResult.QuestUnavailable
    }

    private companion object {
        fun equippedFriend(friend: String) = CosmeticInventory(setOf(friend), mapOf(CosmeticSlot.FRIEND to friend))
    }
}
