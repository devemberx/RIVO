package com.monsters.mobimon.feature.pet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.PetProfile
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.ui.LocalMobiMonNotificationCount
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
class PetHomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun parkingBadgeUsesSharedTopRightAnchor() {
        render(snapshot = parkedSnapshot())
        val bounds = compose.onNodeWithContentDescription("주차 확인됨").fetchSemanticsNode().boundsInRoot
        assertEquals(36f, bounds.top, 1f)
        assertEquals(2488f, bounds.right, 1f)
    }

    @Test
    fun menuButtonAnnouncesNotificationCount() {
        render(snapshot = parkedSnapshot(), notificationCount = 3)
        compose.onNodeWithContentDescription("메뉴 열기, 알림 3건").assertIsDisplayed()
    }

    @Test
    fun initialLoadingDoesNotShowParkingBadge() {
        compose.setContent { MobiMonTheme { PetHomeLoadingScreen(false, {}) } }
        compose.onNodeWithContentDescription("주차 후 이용").assertDoesNotExist()
        compose.onNodeWithContentDescription("주차 확인됨").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h893dp-mdpi")
    fun conversationActionReportsItsBoundsAfterEnlargedTextScrolling() {
        var origin: Rect? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MobiMonTheme {
                    PetHomeScreen(
                        profile = PetProfile("profile"),
                        snapshot = parkedSnapshot(),
                        onOpenMenu = {},
                        onPetClick = { origin = it },
                        interactionAllowed = true,
                        connectionAvailable = true,
                    )
                }
            }
        }
        val action = compose.onNodeWithTag("home-conversation-action").performScrollTo()
        val bounds = action.fetchSemanticsNode().boundsInRoot
        action.performClick()
        assertEquals(bounds, origin)
    }

    @Test
    fun friendNameIsAccessibleWithoutAnExtraVisibleCaption() {
        render(snapshot = parkedSnapshot())
        compose.onNodeWithContentDescription("Mobi 강아지").assertExists()
        compose.onNodeWithText("Mobi · 강아지").assertDoesNotExist()
    }

    @Test
    fun homeAvatarFollowsEquippedFriendChanges() {
        val friend = mutableStateOf("friend:mobi")
        compose.setContent {
            MobiMonTheme {
                PetHomeScreen(
                    profile = PetProfile("profile"),
                    snapshot = parkedSnapshot(),
                    onOpenMenu = {},
                    onPetClick = {},
                    friendId = friend.value,
                    inventoryLoaded = true,
                )
            }
        }
        compose.onNodeWithContentDescription("Mobi 강아지").assertExists()
        compose.runOnIdle { friend.value = "friend:luna" }
        compose.onNodeWithContentDescription("Luna 고양이").assertExists()
        compose.onNodeWithContentDescription("Mobi 강아지").assertDoesNotExist()
    }

    @Test
    fun referenceContentKeepsActionsReachable() {
        assertLandscapeComposition()
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h893dp-mdpi")
    fun headUnitWindowKeepsActionsReachable() {
        assertLandscapeComposition()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun moderatelyEnlargedParkingTextFitsInsideTheBadge() {
        render(snapshot = parkedSnapshot(), pointBalance = 0, fontScale = 1.2f)
        compose
            .onNodeWithText("주차 확인됨", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                val results = mutableListOf<TextLayoutResult>()
                it(results)
                val result = results.single()
                assertEquals(1, result.lineCount)
                assertTrue(result.multiParagraph.height <= result.layoutInput.constraints.maxHeight)
                assertTrue(result.multiParagraph.width <= result.layoutInput.constraints.maxWidth)
            }
    }

    private fun assertLandscapeComposition() {
        val calls = mutableListOf<String>()
        render(
            snapshot = parkedSnapshot(),
            pointBalance = 0,
            onMenu = { calls += "menu" },
        )
        assertAnimatedLineBelowTitle()
        compose.onNodeWithTag("home-companion-message").assertIsDisplayed()
        compose
            .onNodeWithContentDescription("메뉴 열기")
            .performScrollTo()
            .assertHeightIsAtLeast(76.dp)
            .performClick()
        compose
            .onNodeWithText("대화하기 · 연결 불가")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
        compose.onNodeWithText("AI 연결을 지원하지 않아 대화 기능을 사용할 수 없어요.").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("menu"), calls)
    }

    @Test
    fun greetingKeepsOneAnimatedLineUnderTheTitle() {
        render(snapshot = parkedSnapshot())
        assertAnimatedLineBelowTitle()
    }

    private fun assertAnimatedLineBelowTitle() {
        compose.onNodeWithText("좋은 길엔, 늘 네가 있어.").assertDoesNotExist()
        compose.onNodeWithTag("home-ambient-text-container").assertIsDisplayed()
        val title = compose.onNodeWithText("함께 쉬어 가요.").fetchSemanticsNode().boundsInRoot
        val phrase =
            compose
                .onNodeWithTag("home-ambient-text")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue("Animated phrase is below the title", phrase.top >= title.bottom)
        assertTrue("Animated phrase stays close to the title", phrase.top - title.bottom < title.height)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h893dp-mdpi")
    fun losingAndRecoveringVehicleDataKeepsCommittedCompanionAndTruthfulParking() {
        val snapshot = mutableStateOf(parkedSnapshot())
        render(pointBalance = 0, snapshotSource = { snapshot.value })
        val friend = compose.onNodeWithContentDescription("Mobi 강아지").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            snapshot.value =
                snapshot.value.copy(
                    quality = SignalQuality.UNAVAILABLE,
                    batteryQuality = SignalQuality.UNAVAILABLE,
                    drivingState = DrivingState.UNKNOWN,
                )
        }
        assertEquals(friend, compose.onNodeWithContentDescription("Mobi 강아지").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("주차 후 이용").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("배터리 72%").assertDoesNotExist()
        compose.onNodeWithText("대화하기 · 연결 불가").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { snapshot.value = parkedSnapshot() }
        compose.onNodeWithText("주차 확인됨").assertExists()
        compose.onNodeWithText("배터리 72%").assertDoesNotExist()
    }

    @Test
    fun realUnavailableHomeDoesNotClaimParkedState() {
        render()

        compose.onNodeWithText("주차 후 이용").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("주차 확인됨").assertDoesNotExist()
    }

    @Test
    fun conversationRemainsUnavailableEvenWhenParked() {
        render(snapshot = parkedSnapshot(), interactionAllowed = true)

        compose.onNodeWithText("대화하기 · 연결 불가").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("친구와 대화하기").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h893dp-mdpi")
    fun interactionRestrictedNoticeDisplaysDuringDrivingWithoutShiftingUi() {
        val allowed = mutableStateOf(true)
        compose.setContent {
            MobiMonTheme {
                PetHomeScreen(
                    profile = PetProfile("profile"),
                    snapshot = parkedSnapshot(),
                    onOpenMenu = {},
                    onPetClick = {},
                    interactionAllowed = allowed.value,
                    pointBalance = 0,
                )
            }
        }

        compose.onNodeWithText("주행 중에는 상호작용이 제한돼요.").assertDoesNotExist()
        val friendBoundsParked = compose.onNodeWithContentDescription("Mobi 강아지").fetchSemanticsNode().boundsInRoot
        val buttonBoundsParked = compose.onNodeWithText("대화하기 · 연결 불가").fetchSemanticsNode().boundsInRoot

        compose.runOnIdle { allowed.value = false }

        compose.onNodeWithText("주행 중에는 상호작용이 제한돼요.").assertIsDisplayed()
        assertEquals(
            friendBoundsParked,
            compose.onNodeWithContentDescription("Mobi 강아지").fetchSemanticsNode().boundsInRoot,
        )
        assertEquals(buttonBoundsParked, compose.onNodeWithText("대화하기 · 연결 불가").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun homeActionsForwardToShell() {
        val calls = mutableListOf<String>()
        render(
            onMenu = { calls += "menu" },
        )

        compose.onNodeWithContentDescription("메뉴 열기").performClick()
        assertEquals(listOf("menu"), calls)
    }

    @Test
    fun staleParkingDoesNotClaimParked() {
        render(snapshot = parkedSnapshot().copy(quality = SignalQuality.STALE, batteryQuality = SignalQuality.VALID))

        compose.onNodeWithText("주차 후 이용").assertExists()
        compose.onNodeWithText("배터리 72%").assertDoesNotExist()
        compose.onNodeWithContentDescription("주차 확인됨").assertDoesNotExist()
    }

    @Test
    fun loadingBalanceIsNotRenderedAsSavedZero() {
        render()
        compose.onNodeWithText("포인트 확인 중").assertExists()
        compose.onNodeWithText("0 P", substring = true).assertDoesNotExist()
    }

    @Test
    fun failedBalanceIsNotRenderedAsSavedZero() {
        render(pointLoadFailed = true)
        compose.onNodeWithText("포인트를 확인할 수 없어요").assertExists()
        compose.onNodeWithText("0 P", substring = true).assertDoesNotExist()
    }

    @Test
    fun homeOmitsBatteryAndVehicleHomePreviewControls() {
        render(snapshot = parkedSnapshot())
        compose.onNodeWithText("배터리 72%").assertDoesNotExist()
        compose.onNodeWithText("차량 홈 미리보기").assertDoesNotExist()
        compose.onNodeWithText("친구 홈").assertDoesNotExist()
        compose.onNodeWithContentDescription("차량 정보 자세히 보기").assertDoesNotExist()
        compose.onNodeWithContentDescription("첫 퀘스트 살펴보기").assertDoesNotExist()
    }

    @Test
    fun homeHidesSimulationBadgeAndShowsCommittedZero() {
        render(snapshot = parkedSnapshot(), pointBalance = 0)
        compose.onNodeWithText("시뮬레이션").assertDoesNotExist()
        compose.onNodeWithText("포인트 0 P").assertExists()
    }

    @Test
    fun emptySelectionDoesNotDisplayAnUnownedDefaultFriend() {
        render(friendId = null, inventoryLoaded = true)
        compose.onNodeWithText("선택한 친구가 없어요", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Mobi 강아지").assertDoesNotExist()
    }

    @Test
    fun inventoryLoadingIsDistinctFromEmptySelection() {
        render(friendId = null, inventoryLoaded = false)
        compose.onNodeWithText("소유한 아이템을 확인하고 있어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("선택한 친구가 없어요", substring = true).assertDoesNotExist()
    }

    @Test
    fun failedInventoryOffersRetryAndRetainsLastFriend() {
        var retries = 0
        render(inventoryLoadFailed = true, onRetry = { retries++ })
        compose.onNodeWithText("소유한 아이템을 확인할 수 없어요.").assertExists()
        compose.onNodeWithContentDescription("Mobi 강아지").assertExists()
        compose.onNodeWithText("다시 시도").performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun enlargedTextKeepsPrimaryTargetsReachableAndLargeEnough() {
        render(fontScale = 2f)
        compose
            .onNodeWithContentDescription("메뉴 열기")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(76.dp)
            .assertWidthIsAtLeast(76.dp)
    }

    @Test
    fun restrictionNoticeRemainsWithinTargetContent() {
        render(snapshot = parkedSnapshot())
        val notice = compose.onNodeWithText("주행 중에는 상호작용이 제한돼요.")
        notice.performScrollTo()
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = notice.fetchSemanticsNode().boundsInRoot
        assertTrue("Notice top stays within the window", bounds.top >= viewport.top)
        assertTrue("Entire notice remains reachable", bounds.bottom <= viewport.bottom)
    }

    @Test
    fun menuAcceptsKeyboardFocus() {
        render()
        compose.onNodeWithContentDescription("메뉴 열기").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("메뉴 열기").assertIsFocused()
    }

    @Test
    fun tappingPetTriggersSpeechBubbleInteraction() {
        render(snapshot = parkedSnapshot())
        compose.onNodeWithContentDescription("Mobi 강아지").performClick()
        compose.onNodeWithTag("home-companion-message").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home-companion-message-text", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun initialProfileFailureOffersAnAccessibleRetry() {
        var retries = 0
        compose.setContent { MobiMonTheme { PetHomeLoadingScreen(true, { retries++ }) } }
        compose.onNodeWithText("친구를 불러오지 못했어요", substring = true).assertExists()
        compose.onNodeWithContentDescription("주차 후 이용").assertExists()
        compose.onNodeWithText("다시 시도").assertHeightIsAtLeast(76.dp).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun backgroundDecorationDisplaysFallingParticles() {
        render(backgroundId = "background:star")
        compose.onNodeWithTag("home-background-particles").assertExists()
    }

    @Test
    fun initialLoadingDoesNotOfferRetryOrSavedValues() {
        compose.setContent { MobiMonTheme { PetHomeLoadingScreen(false, {}) } }
        compose.onNodeWithText("친구를 불러오고 있어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("포인트 0 P").assertDoesNotExist()
    }

    private fun parkedSnapshot() =
        VehicleSnapshot(
            "parked",
            "epoch",
            1,
            1_000,
            SignalSource.SIMULATED,
            DrivingState.PARKED,
            SignalQuality.VALID,
            72,
            speed = 0,
            gear = "P",
        )

    private fun render(
        snapshot: VehicleSnapshot =
            parkedSnapshot().copy(
                source = SignalSource.REAL,
                drivingState = DrivingState.UNKNOWN,
                quality = SignalQuality.UNAVAILABLE,
            ),
        interactionAllowed: Boolean = false,
        pointLoadFailed: Boolean = false,
        pointBalance: Long? = null,
        friendId: String? = "friend:mobi",
        backgroundId: String? = null,
        inventoryLoaded: Boolean = true,
        inventoryLoadFailed: Boolean = false,
        fontScale: Float = 1f,
        snapshotSource: (() -> VehicleSnapshot)? = null,
        onRetry: () -> Unit = {},
        onMenu: () -> Unit = {},
        notificationCount: Int = 0,
    ) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalMobiMonNotificationCount provides notificationCount,
            ) {
                MobiMonTheme {
                    PetHomeScreen(
                        profile = PetProfile("profile"),
                        snapshot = snapshotSource?.invoke() ?: snapshot,
                        onOpenMenu = onMenu,
                        onPetClick = {},
                        interactionAllowed = interactionAllowed,
                        pointLoadFailed = pointLoadFailed,
                        pointBalance = pointBalance,
                        friendId = friendId,
                        backgroundId = backgroundId,
                        inventoryLoaded = inventoryLoaded,
                        inventoryLoadFailed = inventoryLoadFailed,
                        onRetryProfile = onRetry,
                    )
                }
            }
        }
    }
}
