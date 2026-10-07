package com.monsters.mobimon.feature.quest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.DefaultPointQuestCatalog
import com.monsters.mobimon.core.domain.DrivingQuestIds
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.VehicleRoute
import com.monsters.mobimon.core.presentation.CompanionAppearanceState
import com.monsters.mobimon.core.presentation.PointBalanceState
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class QuestScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = QuestCatalog(DefaultPointQuestCatalog())

    @Test
    fun parkingLossShowsQuestPopupInsteadOfRewardSuccess() {
        var parkingRequired by mutableStateOf(false)
        var home = false
        val state = presentation(QuestUiState(isLoading = false, rewardSuccess = QuestRewardSuccess("quest", 20)))
        compose.setContent {
            MobiMonTheme {
                QuestScreen(
                    state,
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    parkingRequired = parkingRequired,
                    onHome = { home = true },
                )
            }
        }

        compose.runOnIdle { parkingRequired = true }
        compose.onNodeWithText("주차 후 퀘스트를 이어가요").assertIsDisplayed()
        compose.onNodeWithTag("quest-reward-success-modal").assertDoesNotExist()
        compose.onNodeWithTag("parking-interruption-home").performClick()
        assertTrue(home)
        compose.runOnIdle { parkingRequired = false }
        compose.onNodeWithTag("parking-interruption-dialog").assertDoesNotExist()
    }

    @Test
    fun unavailableParkingShowsSharedRestrictedBadge() {
        render(presentation().copy(parkedVerified = false))
        compose.onNodeWithText("주차 후 이용").assertIsDisplayed()
        val bounds = compose.onNodeWithContentDescription("주차 후 이용").fetchSemanticsNode().boundsInRoot
        assertEquals(36f, bounds.top, 1f)
        assertEquals(2488f, bounds.right, 1f)
    }

    @Test
    fun loadingNoticeDoesNotMoveParkingBadge() {
        render(presentation(QuestUiState(isLoading = true)).copy(parkedVerified = false))
        val bounds = compose.onNodeWithContentDescription("주차 후 이용").fetchSemanticsNode().boundsInRoot
        assertEquals(36f, bounds.top, 1f)
        assertEquals(2488f, bounds.right, 1f)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w2560dp-h1332dp-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun panelsFollowAvailableContentHeight() = checkPanelHeights(listOf(1184.dp, 1268.dp, 1184.dp), 1f)

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h952dp-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun compatibilityPanelsFollowAvailableContentHeight() = checkPanelHeights(listOf(829.dp, 888.dp, 829.dp), 0.7f)

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h893dp-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun enlargedTextKeepsCompactQuestActionsReachable() {
        lateinit var view: View
        val state = presentation()
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MobiMonTheme { QuestScreen(state, {}, {}, {}, {}, {}, {}, {}) }
            }
        }
        compose.onNodeWithTag("quest-reference").assertDoesNotExist()
        capture({ view }, "quest-list-enlarged")
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.BATTERY_CARE}").performScrollTo().performClick()
        compose.onNodeWithTag("quest-btn-detail-execute").performScrollTo().assertIsDisplayed()
        capture({ view }, "quest-detail-enlarged")
    }

    private fun checkPanelHeights(
        heights: List<Dp>,
        scale: Float,
    ) {
        var height by mutableStateOf(heights.first())
        lateinit var view: View
        val state = presentation()
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                Box(Modifier.height(height)) {
                    QuestScreen(state, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }

        fun checkPanels(detail: Boolean) {
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val y = (height.value - 40 * scale).toInt()
                assertEquals(
                    "Companion panel must reach the bottom gutter",
                    MobiMonColors.panel.toArgb(),
                    bitmap.getPixel(
                        (
                            400 *
                                scale
                        ).toInt(),
                        y,
                    ),
                )
                if (detail) {
                    assertEquals(
                        "Detail panel must reach the bottom gutter",
                        MobiMonColors.panel.toArgb(),
                        bitmap.getPixel(
                            (
                                2000 *
                                    scale
                            ).toInt(),
                            y,
                        ),
                    )
                }
                bitmap.recycle()
            }
        }
        for (contentHeight in heights) {
            compose.runOnIdle { height = contentHeight }
            capture({ view }, "quest-list-height-${contentHeight.value.toInt()}")
            checkPanels(detail = false)
            compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.BATTERY_CARE}").performScrollTo().performClick()
            capture({ view }, "quest-detail-height-${contentHeight.value.toInt()}")
            checkPanels(detail = true)
            compose.onNodeWithTag("quest-header-back-button").performClick()
        }
    }

    @Test
    fun claimDoesNotInventCompletionOrSuccessBeforeCommittedResult() {
        var claimedId: String? = null
        render(presentation(friend = "friend:luna"), onClaim = { claimedId = it })
        compose.onNodeWithTag("quest-hidden-btn-claim").performClick()
        assertEquals(DrivingQuestIds.HIDDEN_NEW_FRIEND, claimedId)
        compose.onNodeWithTag("quest-reward-success-modal").assertDoesNotExist()
        compose.onNodeWithTag("quest-hidden-claim-modal").assertIsDisplayed()
    }

    @Test
    fun pendingDisablesHiddenClaim() {
        render(
            presentation(
                QuestUiState(isLoading = false, pendingQuestId = DrivingQuestIds.HIDDEN_NEW_FRIEND),
                friend = "friend:luna",
            ),
        )
        compose.onNodeWithTag("quest-hidden-btn-claim").assertIsNotEnabled()
        compose.onNodeWithText("보상 저장 중…").assertIsDisplayed()
        compose.onNodeWithText("진행 상황을 저장하고 있어요.").assertDoesNotExist()
        compose.onNodeWithTag("quest-hidden-btn-dismiss").assertDoesNotExist()
        compose.onNodeWithTag("quest-reward-success-modal").assertDoesNotExist()
    }

    @Test
    fun legacyQ01IsNotOfferedAsAPointQuest() {
        render(presentation())
        compose.onNodeWithTag("quest-card-q01").assertDoesNotExist()
        compose.onNodeWithText("놓지마 생명줄!").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("네 바퀴의 균형").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pendingDisablesDrivingClaim() {
        val state =
            QuestUiState(
                isLoading = false,
                satisfiedDrivingQuestIds = setOf(DrivingQuestIds.SEATBELT),
                pendingQuestId = DrivingQuestIds.SEATBELT,
            )
        render(presentation(state))
        compose.onNodeWithTag("quest-btn-claim-${DrivingQuestIds.SEATBELT}").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("보상 저장 중…").assertIsDisplayed()
    }

    @Test
    fun detailClaimKeepsInlineProgressUntilRewardConfirmation() {
        val questId = DrivingQuestIds.SEATBELT
        var questState by mutableStateOf(
            QuestUiState(isLoading = false, satisfiedDrivingQuestIds = setOf(questId)),
        )
        compose.setContent {
            MobiMonTheme {
                QuestScreen(
                    presentation(questState),
                    { questState = questState.copy(pendingQuestId = it) },
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithTag("quest-card-$questId").performScrollTo().performClick()
        compose.onNodeWithTag("quest-btn-detail-claim").performClick()
        compose.onNodeWithTag("quest-btn-detail-claim").assertIsNotEnabled()
        compose.onNodeWithText("보상 저장 중…").assertIsDisplayed()
        compose.runOnIdle {
            questState = questState.copy(completedPointQuestIds = setOf(questId))
        }
        compose.onNodeWithTag("quest-btn-detail-claim").assertIsNotEnabled()
        compose.onNodeWithText("보상 저장 중…").assertIsDisplayed()
        compose.onNodeWithTag("quest-header-back-button").performClick()
        compose.onNodeWithTag("quest-btn-claim-$questId").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("보상 저장 중…").assertIsDisplayed()
    }

    @Test
    fun filterSurvivesDetailBackAndSavedStateRestoration() {
        val state =
            presentation(QuestUiState(isLoading = false, completedPointQuestIds = setOf(DrivingQuestIds.SEATBELT)))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MobiMonTheme { QuestScreen(state, {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("quest-tab-completed").assertIsDisplayed().performClick()
        compose.onNodeWithTag("quest-scroll-indicator").assertDoesNotExist()
        compose.onNodeWithTag("quest-card-${DrivingQuestIds.SEATBELT}").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("quest-header-back-button").assertIsDisplayed().performClick()
        compose.onNodeWithTag("quest-tab-completed").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("quest-card-${DrivingQuestIds.SAFE_DRIVE}").assertDoesNotExist()
        compose.onNodeWithText("2026.09.14", substring = true).assertDoesNotExist()
    }

    @Test
    fun detailMovesCompanionAndKeepsHeadingWithoutFalseScrollIndicator() {
        render(presentation())
        val listBounds = compose.onNodeWithTag("quest-companion-panel").getUnclippedBoundsInRoot()
        val listWidth = (listBounds.right - listBounds.left).value
        compose.onNodeWithText("작은 도전, 큰 여정").assertIsDisplayed()
        compose.onNodeWithTag("quest-scroll-indicator").assertIsDisplayed()

        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.BATTERY_CARE}").performScrollTo().performClick()
        compose.waitForIdle()
        val detailBounds = compose.onNodeWithTag("quest-companion-panel").getUnclippedBoundsInRoot()
        val detailWidth = (detailBounds.right - detailBounds.left).value
        assertTrue("Companion panel grows on detail", detailWidth > listWidth)
        compose.onNodeWithText("작은 도전, 큰 여정").assertIsDisplayed()
        compose.onNodeWithTag("quest-detail-card").assertIsDisplayed()
        compose.onNodeWithTag("quest-scroll-indicator").assertDoesNotExist()

        compose.onNodeWithTag("quest-header-back-button").performClick()
        compose.waitForIdle()
        val returnedBounds = compose.onNodeWithTag("quest-companion-panel").getUnclippedBoundsInRoot()
        assertEquals(listWidth, (returnedBounds.right - returnedBounds.left).value, 1f)
        compose.onNodeWithText("작은 도전, 큰 여정").assertIsDisplayed()
        compose.onNodeWithTag("quest-scroll-indicator").assertIsDisplayed()
    }

    @Test
    fun returningFromDetailRestoresScrolledListPosition() {
        render(presentation())
        val card = compose.onNodeWithTag("quest-card-${DrivingQuestIds.BATTERY_CARE}")
        val initialTop = card.getUnclippedBoundsInRoot().top.value
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.BATTERY_CARE}").performScrollTo()
        val scrolledTop = card.getUnclippedBoundsInRoot().top.value
        assertTrue("The selected quest is below the initial viewport", scrolledTop < initialTop)

        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.BATTERY_CARE}").performClick()
        compose.onNodeWithTag("quest-header-back-button").performClick()
        compose.waitForIdle()

        assertEquals("Return to the previous list position", scrolledTop, card.getUnclippedBoundsInRoot().top.value, 1f)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1280dp-h800dp-mdpi")
    fun compactListPositionSurvivesDetail() {
        render(presentation())
        val card = compose.onNodeWithTag("quest-card-${DrivingQuestIds.BATTERY_CARE}")
        val initialTop = card.getUnclippedBoundsInRoot().top.value
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.BATTERY_CARE}").performScrollTo()
        val scrolledTop = card.getUnclippedBoundsInRoot().top.value
        assertTrue("The compact list scrolls to the selected quest", scrolledTop < initialTop)

        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.BATTERY_CARE}").performClick()
        compose.onNodeWithTag("quest-header-back-button").performClick()
        compose.waitForIdle()

        assertEquals("Return to the compact list position", scrolledTop, card.getUnclippedBoundsInRoot().top.value, 1f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun notificationEntryStartsOnQuestDetailWithoutListFrame() {
        val questId = DrivingQuestIds.BATTERY_CARE
        var state by mutableStateOf(presentation(QuestUiState(isLoading = false, requestedQuestId = questId)))
        lateinit var view: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                QuestScreen(
                    state = state,
                    onClaimReward = {},
                    onDismissHiddenQuest = {},
                    onDismissRewardSuccess = {},
                    onRetryQuests = {},
                    onRetryWallet = {},
                    onRetryAppearance = {},
                    onNavigateRoute = {},
                    onClearRequestedQuest = { state = state.copy(requestedQuestId = null) },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val firstFrame = snapshot(view)
        compose.mainClock.advanceTimeBy(500)
        val settledFrame = snapshot(view)

        try {
            assertEquals(
                "Notification entry must not animate the list into detail",
                0L,
                visualDistance(firstFrame, settledFrame),
            )
        } finally {
            firstFrame.recycle()
            settledFrame.recycle()
        }
        compose.onNodeWithTag("quest-detail-card").assertIsDisplayed()
        compose.onNodeWithTag("quest-header-back-button").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("quest-tab-all").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun notificationWhileQuestListIsOpenSkipsListToDetailAnimation() {
        val questId = DrivingQuestIds.BATTERY_CARE
        var state by mutableStateOf(presentation())
        lateinit var view: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                QuestScreen(
                    state = state,
                    onClaimReward = {},
                    onDismissHiddenQuest = {},
                    onDismissRewardSuccess = {},
                    onRetryQuests = {},
                    onRetryWallet = {},
                    onRetryAppearance = {},
                    onNavigateRoute = {},
                    onClearRequestedQuest = { state = state.copy(requestedQuestId = null) },
                )
            }
        }
        val listFrame = snapshot(view)
        compose.runOnIdle { state = state.copy(requestedQuestId = questId) }
        compose.mainClock.advanceTimeByFrame()
        val firstFrame = snapshot(view)
        compose.mainClock.advanceTimeBy(500)
        val settledFrame = snapshot(view)

        try {
            assertTrue(
                "Notification must show detail on its first frame",
                visualDistance(firstFrame, settledFrame) * 2 < visualDistance(firstFrame, listFrame),
            )
        } finally {
            listFrame.recycle()
            firstFrame.recycle()
            settledFrame.recycle()
        }
        compose.onNodeWithTag("quest-detail-card").assertIsDisplayed()
    }

    @Test
    fun reducedMotionOpensQuestDetailWithoutTransition() {
        val state = presentation()
        compose.setContent {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                MobiMonTheme { QuestScreen(state, {}, {}, {}, {}, {}, {}, {}) }
            }
        }
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.BATTERY_CARE}").performScrollTo().performClick()
        compose.onNodeWithTag("quest-detail-card").assertIsDisplayed()
        compose.onNodeWithText("작은 도전, 큰 여정").assertIsDisplayed()
        compose.onNodeWithTag("quest-scroll-indicator").assertDoesNotExist()
        compose.onNodeWithTag("quest-header-back-button").performClick()
        compose.onNodeWithText("작은 도전, 큰 여정").assertIsDisplayed()
    }

    @Test
    fun completedDetailShowsOnlyAnObservedCompletionDate() {
        val completedAt =
            java.time.Instant
                .parse("2026-09-14T12:00:00Z")
                .toEpochMilli()
        render(
            presentation(
                QuestUiState(
                    isLoading = false,
                    completedPointQuestIds = setOf(DrivingQuestIds.SEATBELT),
                    completedPointQuestDates = mapOf(DrivingQuestIds.SEATBELT to completedAt),
                ),
            ),
        )
        compose.onNodeWithTag("quest-card-${DrivingQuestIds.SEATBELT}").performScrollTo().performClick()
        compose.onNodeWithText("보상 수령 완료 (2026.09.14)").assertIsDisplayed()
    }

    @Test
    fun drivingDetailExecutesRealNavigationCallback() {
        var route: AppRoute? = null
        render(presentation(), onNavigate = { route = it })
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.BATTERY_CARE}").performScrollTo().performClick()
        compose.onNodeWithTag("quest-btn-detail-execute").assertIsDisplayed().performClick()
        assertEquals(VehicleRoute.VEHICLE_INFO, route)
    }

    @Test
    fun seatbeltDetailShowsDistanceMetricsAndNoExecuteButtonOrVehicleStep() {
        render(presentation())
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.SEATBELT}").performScrollTo().performClick()
        compose.onNodeWithTag("quest-btn-detail-execute").assertDoesNotExist()
        compose.onNodeWithText("차량 정보 확인 하기").assertDoesNotExist()
        compose.onNodeWithText("현재 주행 거리").assertIsDisplayed()
        compose.onNodeWithText("남은 거리").assertIsDisplayed()
    }

    @Test
    fun firstDriveDetailShowsGuideMessageAndNoExecuteButton() {
        render(presentation())
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.FIRST_DRIVE}").performScrollTo().performClick()
        compose.onNodeWithTag("quest-btn-detail-execute").assertDoesNotExist()
        compose.onNodeWithText("차량 정보 확인 하기").assertDoesNotExist()
        compose.onNodeWithText("오늘의 첫 주행을 시작해보세요!").assertIsDisplayed()
    }

    @Test
    fun batteryCareDetailShowsChargeAndVehicleStep() {
        render(presentation())
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.BATTERY_CARE}").performScrollTo().performClick()
        compose.onNodeWithText("차량 정보 확인 하기").assertIsDisplayed()
        compose.onNodeWithTag("quest-btn-detail-execute").assertIsDisplayed()
        compose.onNodeWithText("배터리 충전량").assertIsDisplayed()
        val step = compose.onNodeWithText("차량 정보 확인 하기").getUnclippedBoundsInRoot()
        val reward = compose.onNodeWithText("보상 ·", substring = true).getUnclippedBoundsInRoot()
        assertTrue("Progress stays above the reward", step.bottom <= reward.top)
    }

    @Test
    fun multiRowProgressStaysAboveReward() {
        render(presentation())
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.CLEAN_DRIVE}").performScrollTo().performClick()
        val lastMetric = compose.onNodeWithText("남은 거리").getUnclippedBoundsInRoot()
        val reward = compose.onNodeWithText("보상 ·", substring = true).getUnclippedBoundsInRoot()
        assertTrue("Two metric rows stay above the reward", lastMetric.bottom <= reward.top)
    }

    @Test
    fun progressToRewardSpacingIsConsistentAcrossQuestTypes() {
        render(presentation())
        for (questId in listOf(DrivingQuestIds.SEATBELT, DrivingQuestIds.CLEAN_DRIVE)) {
            compose.onNodeWithTag("quest-btn-detail-$questId").performScrollTo().performClick()
            val progress = compose.onNodeWithTag("quest-detail-progress").getUnclippedBoundsInRoot()
            val reward = compose.onNodeWithTag("quest-detail-reward").getUnclippedBoundsInRoot()
            assertEquals("Progress to reward gap for $questId", 40f, (reward.top - progress.bottom).value, 1f)
            compose.onNodeWithTag("quest-header-back-button").performClick()
        }
    }

    @Test
    fun focusDriveDetailShowsMetricsAndCustomDescription() {
        render(presentation())
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.FOCUS_DRIVE}").performScrollTo().performClick()
        compose.onNodeWithTag("quest-btn-detail-execute").assertDoesNotExist()
        compose.onNodeWithText("차량 정보 확인 하기").assertDoesNotExist()
        compose.onNodeWithText("전방 주시와 주의력(부주의 레벨 70% 이상)유지하며").assertIsDisplayed()
        compose.onNodeWithText("현재 주행 거리").assertIsDisplayed()
        compose.onNodeWithText("남은 거리").assertIsDisplayed()
        compose.onNodeWithText("부주의 레벨").assertIsDisplayed()
    }

    @Test
    fun longTripRestDetailShowsMetricsAndNoExecuteButton() {
        render(presentation())
        compose.onNodeWithTag("quest-btn-detail-${DrivingQuestIds.LONG_TRIP_REST}").performScrollTo().performClick()
        compose.onNodeWithTag("quest-btn-detail-execute").assertDoesNotExist()
        compose.onNodeWithText("차량 정보 확인 하기").assertDoesNotExist()
        compose.onNodeWithText("현재 주행 거리").assertIsDisplayed()
        compose.onNodeWithText("남은 거리").assertIsDisplayed()
        compose.onNodeWithText("주행 시간").assertIsDisplayed()
    }

    @Test
    fun failedClaimRemainsVisibleAndCanBeRetried() {
        var claims = 0
        val state =
            presentation(
                QuestUiState(isLoading = false, message = QuestMessage.STORAGE_FAILURE),
                friend = "friend:luna",
            )
        render(state, onClaim = { claims++ })
        compose.onNodeWithTag("quest-hidden-claim-modal").assertIsDisplayed()
        compose.onNodeWithTag("quest-reward-success-modal").assertDoesNotExist()
        compose.onNodeWithTag("quest-hidden-btn-claim").performClick()
        assertEquals(1, claims)
    }

    @Test
    fun committedSuccessDisplaysActualPointsAndDismissesThroughOwner() {
        var state by mutableStateOf(
            presentation(
                QuestUiState(isLoading = false, rewardSuccess = QuestRewardSuccess(DrivingQuestIds.SEATBELT, 17)),
            ),
        )
        compose.setContent {
            MobiMonTheme {
                QuestScreen(state, {}, {}, { state = state.copy(rewardSuccess = null) }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("17 P를 받았어요!").assertIsDisplayed()
        compose.onNodeWithText("보상 · 17 P").assertIsDisplayed()
        compose.onNodeWithTag("quest-reward-success-modal").performClick()
        compose.onNodeWithTag("quest-reward-success-modal").assertIsDisplayed()
        compose.onNodeWithTag("quest-modal-btn-confirm").performClick()
        compose.onNodeWithTag("quest-reward-success-modal").assertDoesNotExist()
    }

    @Test
    fun committedSuccessWithWeatherBonusDisplaysBonusBadgeNotificationAndChip() {
        val state =
            presentation(
                QuestUiState(
                    isLoading = false,
                    rewardSuccess =
                        QuestRewardSuccess(
                            questId = DrivingQuestIds.SEATBELT,
                            points = 8,
                            basePoints = 5,
                            weatherMultiplier = 1.5f,
                        ),
                ),
            )
        compose.setContent {
            MobiMonTheme {
                QuestScreen(state, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("8 P를 받았어요!").assertIsDisplayed()
        compose.onNodeWithText("퀘스트 완료").assertIsDisplayed()
        compose.onNodeWithText("날씨 보너스 +3 P").assertDoesNotExist()
        compose.onNodeWithText("보상 · 8 P (날씨 보너스 +3 P)").assertIsDisplayed()
    }

    @Test
    fun hiddenClaimModalDoesNotOfferDismissButton() {
        render(presentation(friend = "friend:luna"))
        compose.onNodeWithTag("quest-hidden-claim-modal").assertIsDisplayed()
        compose.onNodeWithTag("quest-hidden-btn-claim").assertIsDisplayed()
        compose.onNodeWithTag("quest-hidden-btn-dismiss").assertDoesNotExist()
        compose.onNodeWithText("닫기").assertDoesNotExist()
    }

    @Test
    fun observationAndWalletFailuresExposeTheirOwnRetries() {
        var questRetries = 0
        var walletRetries = 0
        val state =
            presentation(
                QuestUiState(isLoading = false, observationFailed = true),
            ).copy(pointBalance = PointBalanceState.Failed)
        compose.setContent {
            MobiMonTheme { QuestScreen(state, {}, {}, {}, { questRetries++ }, { walletRetries++ }, {}, {}) }
        }
        compose.onNodeWithText("퀘스트 기록 다시 확인").performClick()
        compose.onNodeWithText("포인트 다시 확인").performClick()
        assertEquals(1, questRetries)
        assertEquals(1, walletRetries)
        compose.onNodeWithText("포인트를 확인할 수 없어요").assertIsDisplayed()
    }

    @Test
    fun walletLoadingAndZeroRemainDistinct() {
        var state by mutableStateOf(presentation().copy(pointBalance = PointBalanceState.Loading))
        compose.setContent { MobiMonTheme { QuestScreen(state, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("포인트 확인 중").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(pointBalance = PointBalanceState.Ready(0)) }
        compose.onNodeWithText("포인트 0 P").assertIsDisplayed()
        compose.onNodeWithText("0P를 받았어요").assertDoesNotExist()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderClaimPendingAndFailureForVisualReview() {
        var state by mutableStateOf(presentation())
        lateinit var view: View
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme { QuestScreen(state, {}, {}, {}, {}, {}, {}, {}) }
        }
        capture(viewProvider = { view }, name = "quest-list")
        compose.runOnIdle {
            state = presentation(QuestUiState(isLoading = false, pendingQuestId = DrivingQuestIds.SEATBELT))
        }
        capture(viewProvider = { view }, name = "quest-claim-pending")
        compose.runOnIdle {
            state = presentation(QuestUiState(isLoading = false, message = QuestMessage.STORAGE_FAILURE))
        }
        capture(viewProvider = { view }, name = "quest-claim-failure")
    }

    @Test
    fun headerBackButtonCallsOnBackWhenAtRootList() {
        var backCalled = false
        val state = presentation()
        compose.setContent {
            MobiMonTheme {
                QuestScreen(
                    state = state,
                    onClaimReward = {},
                    onDismissHiddenQuest = {},
                    onDismissRewardSuccess = {},
                    onRetryQuests = {},
                    onRetryWallet = {},
                    onRetryAppearance = {},
                    onNavigateRoute = {},
                    onBack = { backCalled = true },
                )
            }
        }
        compose.onNodeWithTag("quest-header-back-button").performClick()
        assertTrue(backCalled)
    }

    @Test
    fun headerHomeButtonDoesNotExist() {
        val state = presentation()
        compose.setContent {
            MobiMonTheme {
                QuestScreen(
                    state = state,
                    onClaimReward = {},
                    onDismissHiddenQuest = {},
                    onDismissRewardSuccess = {},
                    onRetryQuests = {},
                    onRetryWallet = {},
                    onRetryAppearance = {},
                    onNavigateRoute = {},
                )
            }
        }
        compose.onNodeWithTag("quest-header-home-button").assertDoesNotExist()
    }

    @Test
    fun headerBackButtonPopsDetailToListWhenInDetail() {
        var backCalled = false
        val state = presentation()
        compose.setContent {
            MobiMonTheme {
                QuestScreen(
                    state = state,
                    onClaimReward = {},
                    onDismissHiddenQuest = {},
                    onDismissRewardSuccess = {},
                    onRetryQuests = {},
                    onRetryWallet = {},
                    onRetryAppearance = {},
                    onNavigateRoute = {},
                    onBack = { backCalled = true },
                )
            }
        }
        compose.onNodeWithTag("quest-card-${DrivingQuestIds.SEATBELT}").performScrollTo().performClick()
        compose.onNodeWithTag("quest-detail-back-button").assertDoesNotExist()
        compose.onNodeWithTag("quest-header-back-button").performClick()
        compose.onNodeWithTag("quest-card-${DrivingQuestIds.SEATBELT}").assertIsDisplayed()
        assertFalse(backCalled)
    }

    @Test
    fun claimableQuestAppearsAtTopInList() {
        val state =
            presentation(
                QuestUiState(
                    isLoading = false,
                    satisfiedDrivingQuestIds = setOf(DrivingQuestIds.TIRE_CHECK),
                ),
            )
        render(state)
        assertEquals(DrivingQuestIds.TIRE_CHECK, state.quests.first().id)
        assertEquals(QuestItemStatus.CLAIMABLE, state.quests.first().status)
        compose.onNodeWithTag("quest-card-${DrivingQuestIds.TIRE_CHECK}").assertIsDisplayed()
        compose.onNodeWithTag("quest-btn-claim-${DrivingQuestIds.TIRE_CHECK}").assertIsDisplayed()
    }

    @Test
    fun referenceListAlignsHeaderBalanceAndTabs() {
        render(presentation())
        val backButton = compose.onNodeWithTag("quest-header-back-button").getUnclippedBoundsInRoot()
        val parkingBadge = compose.onNodeWithContentDescription("주차 확인됨").getUnclippedBoundsInRoot()
        val balance = compose.onNodeWithText("포인트 120 P").getUnclippedBoundsInRoot()
        val firstTab = compose.onNodeWithTag("quest-tab-all").getUnclippedBoundsInRoot()
        val secondTab = compose.onNodeWithTag("quest-tab-ongoing").getUnclippedBoundsInRoot()

        assertEquals(
            "Parking badge starts at the reference header top",
            backButton.top.value,
            parkingBadge.top.value,
            2f,
        )
        assertEquals("Balance follows Home's 48 px badge gap", 48f, (parkingBadge.left - balance.right).value, 2f)
        val balanceCenterOffset = ((balance.top + balance.bottom) - (parkingBadge.top + parkingBadge.bottom)).value / 2f
        assertEquals("Balance follows Home's 7 px vertical offset", 7f, balanceCenterOffset, 2f)
        assertEquals(808f, firstTab.left.value, 2f)
        assertEquals(314f, firstTab.top.value, 2f)
        assertEquals(336f, (firstTab.right - firstTab.left).value, 2f)
        assertEquals(88f, (firstTab.bottom - firstTab.top).value, 2f)
        assertEquals(1168f, secondTab.left.value, 2f)
    }

    @Test
    fun bothEmptyFiltersOfferAWorkingAllQuestsAction() {
        render(presentation().copy(quests = emptyList()))
        for (tab in listOf("quest-tab-ongoing", "quest-tab-completed")) {
            compose.onNodeWithTag(tab).performClick()
            compose.onNodeWithTag("quest-empty-state").assertIsDisplayed()
            compose.onNodeWithTag("quest-empty-show-all").assertIsDisplayed().performClick()
            compose.onNodeWithTag("quest-tab-all").assertIsSelected()
        }
    }

    @Test
    @Config(qualifiers = "ko-rKR-w2560dp-h1332dp-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun captureReferenceQuestStatesForVisualReview() {
        val catalogState = presentation()
        val tire = catalogState.quests.first { it.id == DrivingQuestIds.TIRE_CHECK }
        val first = tire.copy(status = QuestItemStatus.CLAIMABLE, actionType = QuestActionType.CLAIM_REWARD)
        val second =
            catalogState.quests
                .first { it.id == DrivingQuestIds.BATTERY_CARE }
                .copy(status = QuestItemStatus.IN_PROGRESS, actionType = QuestActionType.VIEW_DETAIL)
        val third =
            catalogState.quests
                .first { it.id == DrivingQuestIds.SEATBELT }
                .copy(status = QuestItemStatus.COMPLETED, actionType = QuestActionType.ALREADY_CLAIMED)
        var state by mutableStateOf(catalogState.copy(quests = listOf(first, second, third)))
        lateinit var view: View
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                Box(Modifier.height(1184.dp)) { QuestScreen(state, {}, {}, {}, {}, {}, {}, {}) }
            }
        }

        capture({ view }, "quest-reference-list-all")
        compose.onNodeWithTag("quest-tab-ongoing").performClick()
        capture({ view }, "quest-reference-list-in-progress")
        compose.onNodeWithTag("quest-tab-completed").performClick()
        capture({ view }, "quest-reference-list-completed")
        compose.runOnIdle { state = state.copy(quests = listOf(first, second)) }
        capture({ view }, "quest-reference-empty-completed")
        compose.runOnIdle { state = state.copy(quests = listOf(third)) }
        compose.onNodeWithTag("quest-tab-ongoing").performClick()
        capture({ view }, "quest-reference-empty-in-progress")
        compose.onNodeWithTag("quest-tab-all").performClick()
        compose.runOnIdle {
            state =
                state.copy(
                    quests =
                        listOf(
                            first.copy(status = QuestItemStatus.IN_PROGRESS, actionType = QuestActionType.VIEW_DETAIL),
                        ),
                )
        }
        compose.onNodeWithTag("quest-card-${DrivingQuestIds.TIRE_CHECK}").performClick()
        capture({ view }, "quest-reference-detail-actionable")
        compose.runOnIdle { state = state.copy(quests = listOf(first)) }
        capture({ view }, "quest-reference-detail-claimable")
        compose.runOnIdle { state = state.copy(quests = listOf(first.copy(status = QuestItemStatus.COMPLETED))) }
        capture({ view }, "quest-reference-detail-completed")
        compose.onNodeWithTag("quest-header-back-button").performClick()
        compose.runOnIdle {
            state = state.copy(quests = listOf(first), rewardSuccess = QuestRewardSuccess(first.id, first.rewardPoints))
        }
        compose.onNodeWithTag("quest-reward-success-modal").assertIsDisplayed()
        capture(
            {
                org.robolectric.shadows.ShadowDialog
                    .getLatestDialog()
                    .window!!
                    .decorView
            },
            "quest-reference-reward-success",
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun lasCharacterDisplaysLasNameInModal() {
        val reward = QuestRewardSuccess(DrivingQuestIds.SEATBELT, 5)
        render(presentation(friend = "friend:las").copy(rewardSuccess = reward))
        compose.onNodeWithText("작은 도전으로 라스와 함께 성장했어요.").assertIsDisplayed()
    }

    private fun render(
        state: QuestScreenState,
        onClaim: (String) -> Unit = {},
        onNavigate: (AppRoute) -> Unit = {},
    ) {
        compose.setContent { MobiMonTheme { QuestScreen(state, onClaim, {}, {}, {}, {}, {}, onNavigate) } }
    }

    private fun presentation(
        state: QuestUiState = QuestUiState(isLoading = false),
        friend: String = "friend:mobi",
    ): QuestScreenState =
        catalog.present(
            state,
            CompanionAppearanceState(CosmeticInventory(emptySet(), mapOf(CosmeticSlot.FRIEND to friend))),
            PointBalanceState.Ready(120),
            parkedVerified = true,
            context::getString,
        )

    private fun snapshot(view: View): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    private fun visualDistance(
        first: Bitmap,
        second: Bitmap,
    ): Long {
        var distance = 0L
        for (x in 960 until 2480 step 8) {
            for (y in 200 until 1100 step 8) {
                val a = first.getPixel(x, y)
                val b = second.getPixel(x, y)
                distance += kotlin.math.abs((a shr 16 and 255) - (b shr 16 and 255))
                distance += kotlin.math.abs((a shr 8 and 255) - (b shr 8 and 255))
                distance += kotlin.math.abs((a and 255) - (b and 255))
            }
        }
        return distance
    }

    private fun capture(
        viewProvider: () -> View,
        name: String,
    ) {
        compose.runOnIdle {
            val view = viewProvider()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/quest-ui").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }
}
