package com.monsters.mobimon.feature.quest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.DefaultPointQuestCatalog
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
import com.monsters.mobimon.core.navigation.QuestRoute
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.PointPresentation
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import kotlinx.coroutines.flow.MutableStateFlow
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
class QuestFeatureTest {
    @get:Rule val compose = createComposeRule()
    private val vehicle = TestVehicle()
    private val points = StrictEvidenceEconomy(vehicle)
    private lateinit var contentView: View

    @Test
    fun parkedRouteShowsInterruptionWhenVehicleBecomesMovingAndClearsWhenParkReturns() {
        show()
        compose.onNodeWithTag("parking-interruption-dialog").assertDoesNotExist()

        compose.runOnIdle {
            vehicle.snapshots.value =
                vehicle.initial.copy(
                    id = "moving",
                    sequence = 2,
                    drivingState = DrivingState.MOVING,
                    speed = 12,
                    gear = "D",
                )
        }
        compose.onNodeWithText("주차 후 퀘스트를 이어가요").assertIsDisplayed()
        compose.runOnIdle { vehicle.snapshots.value = vehicle.initial.copy(id = "reparked", sequence = 3) }
        compose.onNodeWithTag("parking-interruption-dialog").assertDoesNotExist()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun productionRouteProducesReviewImage() {
        show()
        compose.onNodeWithTag("quest-hidden-btn-claim").performClick()
        compose.onNodeWithTag("quest-hidden-claim-modal").assertDoesNotExist()
        compose.onNodeWithTag("quest-modal-btn-confirm").performClick()
        compose.onNodeWithTag("quest-reward-success-modal").assertDoesNotExist()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(contentView.width, contentView.height, Bitmap.Config.ARGB_8888)
            contentView.draw(Canvas(bitmap))
            val directory = File("build/reports/quest-ui").apply { mkdirs() }
            File(directory, "quest-route.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }

    @Test
    fun displayedFreshnessDoesNotReplaceRawRewardEvidence() {
        show()
        compose.onNodeWithTag("quest-hidden-btn-claim").performClick()
        compose.onNodeWithTag("quest-reward-success-modal").assertIsDisplayed()
        compose.onNodeWithText("30 P를 받았어요!").assertIsDisplayed()
        assertEquals(vehicle.initial, points.submittedEvidence)
        assertEquals(1, points.awards)
    }

    @Test
    fun repositoryRejectsEvidenceChangedAfterDisplayedClaim() {
        points.changeEvidenceDuringAward = true
        show()
        compose.onNodeWithTag("quest-hidden-btn-claim").performClick()
        compose.onNodeWithTag("quest-reward-success-modal").assertDoesNotExist()
        compose.onNodeWithTag("quest-hidden-claim-modal").assertIsDisplayed()
        assertEquals(vehicle.initial, points.submittedEvidence)
        assertEquals(0, points.awards)
    }

    private fun show() {
        val feature =
            QuestFeature(
                VehiclePresentation(
                    vehicle,
                    ProgressionIdentity("profile", SignalSource.SIMULATED),
                    Clock {
                        2_000
                    },
                    VehicleFreshnessPolicy(15_000),
                ),
                PointPresentation(points),
                CompanionAppearancePresentation(points),
                points,
                DefaultPointQuestCatalog(),
            )
        compose.setContent {
            val view = LocalView.current
            SideEffect { contentView = view }
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                MobiMonTheme {
                    Surface {
                        feature.Content(QuestRoute.QUESTS, FeatureNavigator({}, {}, {}, {}), Modifier)
                    }
                }
            }
        }
    }

    private class TestVehicle : VehicleRepository {
        val initial =
            VehicleSnapshot(
                "raw-evidence",
                "epoch",
                1,
                1_000,
                SignalSource.SIMULATED,
                DrivingState.PARKED,
                SignalQuality.VALID,
                67,
            )
        override val snapshots = MutableStateFlow(initial)

        override fun start() = error("Feature cannot start a provider")

        override fun stop() = error("Feature cannot stop a provider")
    }

    private class StrictEvidenceEconomy(
        private val vehicle: TestVehicle,
    ) : PointEconomy {
        var changeEvidenceDuringAward = false
        var submittedEvidence: VehicleSnapshot? = null
        var awards = 0
        override val wallet = flowOf(PointWallet(120))
        override val catalog = flowOf(emptyList<CosmeticItem>())
        override val completedQuestIds = MutableStateFlow<Set<String>>(emptySet())
        override val inventory =
            flowOf(
                CosmeticInventory(
                    setOf("friend:luna"),
                    mapOf(
                        CosmeticSlot.FRIEND to "friend:luna",
                    ),
                ),
            )

        override suspend fun purchase(
            itemId: String,
            expectedPrice: Long,
        ): PurchaseResult = PurchaseResult.ItemUnavailable

        override suspend fun equip(itemId: String): EquipResult = EquipResult.ItemUnavailable

        override suspend fun awardQuest(
            questId: String,
            displayedSnapshot: VehicleSnapshot,
        ): PointAwardResult {
            submittedEvidence = displayedSnapshot
            if (changeEvidenceDuringAward) {
                vehicle.snapshots.value = vehicle.initial.copy(id = "new-evidence", sequence = 2)
            }
            if (displayedSnapshot != vehicle.snapshots.value) return PointAwardResult.EvidenceChanged
            awards++
            completedQuestIds.value += questId
            return PointAwardResult.Awarded(30, 150, "once")
        }
    }
}
