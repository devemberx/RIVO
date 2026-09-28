package com.monsters.mobimon.feature.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.Clock
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationProvider
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTurn
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.EquipResult
import com.monsters.mobimon.core.domain.GitHubAccount
import com.monsters.mobimon.core.domain.GitHubAuthentication
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.GitHubSignIn
import com.monsters.mobimon.core.domain.PetAppearance
import com.monsters.mobimon.core.domain.PetProfile
import com.monsters.mobimon.core.domain.PetRepository
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
import com.monsters.mobimon.core.domain.WriteResult
import com.monsters.mobimon.core.navigation.AiRoute
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.core.presentation.VehiclePresentation
import com.monsters.mobimon.core.ui.MobiMonTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
class AiFeatureTest {
    @get:Rule val compose = createComposeRule()

    private val pets = FakePets()
    private val points = FakePoints()
    private val session = MutableStateFlow<GitHubSession>(GitHubSession.SignedOut)
    private var route by mutableStateOf(AiRoute.COPILOT)
    private var homeReturns = 0

    @Test
    fun returningHomeClearsUnsentChatInputBeforeReentry() {
        route = AiRoute.CONVERSATION
        show(parked = true, authenticated = true)
        compose.onNodeWithTag("chat-input").performTextInput("unsent text")
        compose.onNodeWithText("unsent text").assertIsDisplayed()

        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.runOnIdle { assertEquals(AiRoute.COPILOT, route) }
        compose.runOnIdle { route = AiRoute.CONVERSATION }

        compose.onNodeWithText("unsent text").assertDoesNotExist()
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
    }

    @Test
    fun firstConversationFrameKeepsChatWhileCompanionContextLoads() {
        points.initialInventoryGate = CompletableDeferred()
        route = AiRoute.CONVERSATION
        show(parked = true, authenticated = true)
        compose.onNodeWithTag("chat-panel").assertIsDisplayed()
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.onNodeWithText("친구 정보를 불러오는 중이에요.").assertDoesNotExist()
        compose.runOnIdle { points.initialInventoryGate?.complete(Unit) }
        compose.onNodeWithTag("chat-panel").assertIsDisplayed()
    }

    @Test
    fun disconnectedConversationOpensConnectionWhenParked() {
        route = AiRoute.CONVERSATION
        show(parked = true)
        compose.onNodeWithText("연결 안내 열기").assertHeightIsAtLeast(76.dp).performClick()
        compose.onNodeWithText("모비와 이야기해요.").assertIsDisplayed()
    }

    @Test
    fun disconnectedConversationShowsParkingDialogAndReturnsHome() {
        route = AiRoute.CONVERSATION
        show()
        compose.onNodeWithTag("chat-parking-dialog").assertIsDisplayed()
        compose.onNodeWithText("연결 안내 열기").assertDoesNotExist()
        compose.onNodeWithTag("chat-parking-home").performClick()
        compose.runOnIdle { assertEquals(1, homeReturns) }
    }

    @Test
    fun unparkedConversationKeepsParkingDialogWhenCompanionLoadFails() {
        route = AiRoute.CONVERSATION
        pets.initializationFailure = IOException("companion unavailable")
        show()

        compose.onNodeWithTag("chat-parking-dialog").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithTag("chat-parking-home").performClick()
        compose.runOnIdle { assertEquals(1, homeReturns) }
    }

    @Test
    fun authenticatedAccountCanSendAfterModelCheck() {
        route = AiRoute.CONVERSATION
        show(parked = true, authenticated = true)
        compose.onNodeWithTag("chat-input").performTextInput("오늘의 이야기")
        compose.onNodeWithTag("chat-send").assertIsEnabled()
        compose.onNodeWithText("Copilot 연결됨").assertIsDisplayed()
        compose.runOnIdle { route = AiRoute.COPILOT }
        compose.onNodeWithText("GitHub 계정 인증이 완료됐어요.").assertIsDisplayed()
        compose.onNodeWithText("모비와 대화하기").assertIsDisplayed().performClick()
        compose.onNodeWithText("오늘의 이야기").assertIsDisplayed()
        compose.onNodeWithTag("chat-send").assertIsEnabled()
    }

    @Test
    fun signOutInConnectionClearsDraftBeforeSameAccountReconnect() {
        route = AiRoute.CONVERSATION
        show(parked = true, authenticated = true)
        compose.onNodeWithTag("chat-input").performTextInput("이전 계정 세션의 초안")
        compose.runOnIdle { route = AiRoute.COPILOT }
        compose.onNodeWithText("GitHub 계정 인증이 완료됐어요.").assertIsDisplayed()
        compose.runOnIdle { session.value = GitHubSession.SignedOut }
        compose.onNodeWithText("QR로 연결하기").assertExists()
        compose.runOnIdle { session.value = GitHubSession.Authenticated(GitHubAccount(1, "sample")) }
        compose.onNodeWithText("모비와 대화하기").assertIsDisplayed().performClick()
        compose.onNodeWithTag("chat-input").assertExists()
        compose.onNodeWithText("이전 계정 세션의 초안").assertDoesNotExist()
    }

    @Test
    fun productionIntroductionShowsUnavailableStateBeforeQrAction() {
        show(parked = true)
        compose.onNodeWithText("아직 계정 연결을 이용할 수 없어요.", substring = true).assertExists()
        compose.onNodeWithText("QR로 연결하기").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun failedObservationKeepsCompanionAndExposesRetryAfterRevisiting() {
        show()
        compose.onNodeWithText("모비와 이야기해요.").assertIsDisplayed()
        compose.runOnIdle { points.fail.trySend(Unit) }
        compose.onNodeWithText("친구 정보를 갱신하지 못했어요.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("모비와 이야기해요.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, points.activeObservers)
            points.savedInventory.value = equippedFriend("friend:luna")
            route = AiRoute.CONVERSATION
        }
        compose.waitForIdle()
        compose.runOnIdle { route = AiRoute.COPILOT }
        compose.onNodeWithText("친구 정보를 갱신하지 못했어요.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("모비와 이야기해요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertHeightIsAtLeast(76.dp).performClick()
        compose.onNodeWithText("루나와 이야기해요.").assertIsDisplayed()
        compose.onNodeWithText("친구 정보를 갱신하지 못했어요.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("QR로 연결하기").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle { route = AiRoute.CONVERSATION }
        compose.waitForIdle()
        compose.runOnIdle { route = AiRoute.COPILOT }
        compose.onNodeWithText("루나와 이야기해요.").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, points.subscriptions)
            assertEquals(1, points.activeObservers)
            assertEquals(1, points.maxActiveObservers)
            assertEquals(1, pets.profile.subscriptionCount.value)
        }
    }

    @Test
    fun initialFailureCanRetryWithEnlargedText() {
        pets.initializationFailure = IOException("temporary initialization failure")
        show(fontScale = 2f)
        compose.onNodeWithText("친구 정보를 불러오지 못했어요.").assertIsDisplayed()
        compose.runOnIdle { pets.initializationFailure = null }
        compose
            .onNodeWithText("다시 시도")
            .assertHeightIsAtLeast(76.dp)
            .performClick()
        compose.onNodeWithText("모비와 이야기해요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("친구 정보를 불러오지 못했어요.").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, points.subscriptions) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun retainedContextRetryIsKeyboardReachableWithEnlargedText() {
        show(fontScale = 2f)
        compose.onNodeWithText("모비와 이야기해요.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { points.fail.trySend(Unit) }
        compose.onNodeWithText("친구 정보를 갱신하지 못했어요.", substring = true).assertIsDisplayed()
        compose
            .onNodeWithText("다시 시도")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(76.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("친구 정보를 갱신하지 못했어요.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("모비와 이야기해요.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, points.subscriptions) }
    }

    private fun show(
        fontScale: Float = 1f,
        parked: Boolean = false,
        authenticated: Boolean = false,
    ) {
        session.value =
            if (authenticated) GitHubSession.Authenticated(GitHubAccount(1, "sample")) else GitHubSession.SignedOut
        val vehicle =
            object : VehicleRepository {
                override val snapshots =
                    MutableStateFlow(
                        VehicleSnapshot(
                            "unavailable",
                            "test",
                            0,
                            0,
                            SignalSource.REAL,
                            if (parked) DrivingState.PARKED else DrivingState.UNKNOWN,
                            if (parked) SignalQuality.VALID else SignalQuality.UNAVAILABLE,
                        ),
                    )

                override fun start() = error("Feature must not start a provider")

                override fun stop() = error("Feature must not stop a provider")
            }
        val feature =
            AiFeature(
                pets,
                points,
                VehiclePresentation(
                    vehicle,
                    ProgressionIdentity("saved", SignalSource.REAL),
                    Clock {
                        0
                    },
                    VehicleFreshnessPolicy(15_000),
                    UtcClock { 0L },
                ),
                object : GitHubAuthentication {
                    override val session = this@AiFeatureTest.session
                    override val configured = false

                    override suspend fun restore() = Unit

                    override suspend fun disconnect() = Unit

                    override fun signIn() = flowOf<GitHubSignIn>()
                },
                object : ConversationProvider {
                    override suspend fun connect(accountId: Long) = ConversationResult.Success("gpt-4o")

                    override suspend fun reply(
                        accountId: Long,
                        conversationId: String,
                        friendId: String,
                        messages: List<ConversationTurn>,
                    ) = ConversationResult.Failure(ConversationProblem.ACCESS)
                },
            )
        val navigator =
            FeatureNavigator(
                { route = it as AiRoute },
                { route = AiRoute.COPILOT },
                {
                    homeReturns++
                    route = AiRoute.COPILOT
                },
                {},
            )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MobiMonTheme { feature.Content(route, navigator, Modifier) }
            }
        }
    }

    private class FakePets : PetRepository {
        override val profile = MutableStateFlow(PetProfile("saved"))
        var initializationFailure: Exception? = null

        override suspend fun initialize() {
            initializationFailure?.let { throw it }
        }

        override suspend fun setAppearance(appearance: PetAppearance) = WriteResult.Failure
    }

    private class FakePoints : PointEconomy {
        val fail = Channel<Unit>(Channel.CONFLATED)
        var initialInventoryGate: CompletableDeferred<Unit>? = null
        val savedInventory = MutableStateFlow(equippedFriend("friend:mobi"))
        var subscriptions = 0
        var activeObservers = 0
        var maxActiveObservers = 0
        override val inventory =
            flow {
                subscriptions++
                activeObservers++
                maxActiveObservers = maxOf(maxActiveObservers, activeObservers)
                try {
                    initialInventoryGate?.await()
                    if (subscriptions == 1) {
                        emit(savedInventory.value)
                        fail.receive()
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
