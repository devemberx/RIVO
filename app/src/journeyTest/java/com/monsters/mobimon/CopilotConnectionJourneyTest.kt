package com.monsters.mobimon

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.GitHubAccount
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.testing.JourneyAuthentication
import com.monsters.mobimon.testing.JourneyConversationProvider
import com.monsters.mobimon.testing.JourneyNetworkStatus
import com.monsters.mobimon.testing.JourneyStorage
import com.monsters.mobimon.testing.JourneyVehicle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import com.monsters.mobimon.feature.auth.R as AuthR
import com.monsters.mobimon.feature.pet.R as PetR

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CopilotConnectionJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var vehicle: JourneyVehicle

    @Inject lateinit var storage: JourneyStorage

    @Inject lateinit var authentication: JourneyAuthentication

    @Inject lateinit var conversations: JourneyConversationProvider

    @Inject lateinit var networkStatus: JourneyNetworkStatus

    @Before
    fun setUp() = hilt.inject()

    @After
    fun tearDown() {
        if (::storage.isInitialized) storage.close()
    }

    @Test
    fun settingsConnectionSurvivesRecreationAndBackReturnsToSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(hasContentDescription(text(PetR.string.pet_open_menu)))
            compose.onNodeWithContentDescription(text(PetR.string.pet_open_menu)).ensureDisplayed().performClick()
            compose.onNodeWithText(text(R.string.drawer_settings)).ensureDisplayed().performClick()
            waitFor(hasText(text(PetR.string.pet_settings_ai_title)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_settings_ai_title)).ensureDisplayed().performClick()
            waitFor(hasText(text(AuthR.string.copilot_connect)))
            compose.waitForIdle()
            compose.onNodeWithText(text(AuthR.string.copilot_connect)).ensureDisplayed().assertIsNotEnabled()
            compose.onNodeWithText("아직 계정 연결을 이용할 수 없어요.", substring = true).assertExists()
            scenario.recreate()
            waitFor(hasText("아직 계정 연결을 이용할 수 없어요.", substring = true))
            compose.onNodeWithText(text(AuthR.string.copilot_connected_heading)).assertDoesNotExist()
            compose.onNodeWithContentDescription(text(AuthR.string.copilot_back)).ensureDisplayed().performClick()
            compose.onNodeWithTag("settings-done").assertExists()
        }
    }

    @Test
    fun settingsStartChatOnFirstBootOpensConversation() {
        authentication.approve()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasContentDescription(text(PetR.string.pet_open_menu)))
            compose.onNodeWithContentDescription(text(PetR.string.pet_open_menu)).ensureDisplayed().performClick()
            compose.onNodeWithText(text(R.string.drawer_settings)).ensureDisplayed().performClick()
            waitFor(hasText(text(PetR.string.pet_settings_ai_title)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_settings_ai_title)).ensureDisplayed().performClick()
            val chat =
                InstrumentationRegistry.getInstrumentation().targetContext.getString(
                    AuthR.string.copilot_chat,
                    "모비",
                )
            waitFor(hasText(chat) and isEnabled())
            compose.onNodeWithText(chat).ensureDisplayed().performClick()
            waitFor(hasTestTag("chat-input"))
            compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        }
    }

    @Test
    fun lossOfParkingShowsDialogAndHomeReturnsHome() {
        authentication.approve()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText(text(PetR.string.pet_talk_action)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_talk_action)).ensureDisplayed().performClick()
            waitFor(hasTestTag("chat-input"))
            compose.onNodeWithTag("chat-input").assertIsEnabled()
            vehicle.publish(DrivingState.UNKNOWN, SignalQuality.UNAVAILABLE)
            waitFor(hasTestTag("chat-parking-dialog"))
            compose.onNodeWithTag("chat-input").assertDoesNotExist()
            compose.onNodeWithTag("chat-parking-home").ensureDisplayed().performClick()
            waitFor(hasContentDescription(text(PetR.string.pet_open_menu)))
        }
    }

    @Test
    fun startupNetworkFailureStaysOnHomeAndConversationCanRecheckWithoutSending() {
        authentication.approve()
        conversations.connectionResult = ConversationResult.Failure(ConversationProblem.NETWORK)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText(text(PetR.string.pet_talk_action)) and isEnabled())
            compose.waitUntil(timeoutMillis = 10_000) { conversations.connections >= 1 }
            compose.onNodeWithTag("chat-network-dialog").assertDoesNotExist()
            compose.onNodeWithText(text(PetR.string.pet_talk_action)).ensureDisplayed().performClick()
            waitFor(hasTestTag("chat-network-dialog"))
            compose.onNodeWithTag("chat-inline-failure").assertDoesNotExist()
            compose.onNodeWithTag("chat-send").assertDoesNotExist()
            compose.onNodeWithContentDescription(text(AuthR.string.chat_voice_start)).assertDoesNotExist()
            conversations.connectionResult = ConversationResult.Success("gpt-4o")
            compose.onNodeWithText(text(AuthR.string.chat_network_recheck)).ensureDisplayed().performClick()
            waitFor(hasText(text(AuthR.string.chat_ready)))
            compose.onNodeWithTag("chat-network-dialog").assertDoesNotExist()
            assertEquals(0, conversations.replies)
        }
    }

    @Test
    fun networkLossShowsPopupKeepsDraftAndOfflineRecheckDoesNotCallCopilot() {
        authentication.approve()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText(text(PetR.string.pet_talk_action)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_talk_action)).ensureDisplayed().performClick()
            waitFor(hasTestTag("chat-input"))
            compose.onNodeWithTag("chat-input").performTextInput("연결 확인")
            waitFor(hasTestTag("chat-send") and isEnabled())
            val checksBeforeDisconnect = conversations.connections
            networkStatus.online.value = false

            waitFor(hasTestTag("chat-network-dialog"))
            compose.onNodeWithTag("chat-inline-failure").assertDoesNotExist()
            compose.onNodeWithTag("chat-send").assertDoesNotExist()
            compose.onNodeWithContentDescription(text(AuthR.string.chat_voice_start)).assertDoesNotExist()
            assertEquals(0, conversations.replies)
            compose.onNodeWithText(text(AuthR.string.chat_network_recheck)).ensureDisplayed().performClick()
            compose.onNodeWithText(text(AuthR.string.chat_network_body), substring = true).assertExists()
            assertEquals(checksBeforeDisconnect, conversations.connections)
            networkStatus.online.value = true
            compose.onNodeWithTag("chat-network-retry").ensureDisplayed().performClick()
            waitFor(hasText(text(AuthR.string.chat_ready)))
            compose.onNodeWithTag("chat-input").assertTextContains("연결 확인")
            assertEquals(0, conversations.replies)
        }
    }

    @Test
    fun authenticationNetworkFailureRechecksCredentialsThenModelFromConversation() {
        authentication.failNetwork()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText(text(PetR.string.pet_talk_action)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_talk_action)).ensureDisplayed().performClick()
            waitFor(hasTestTag("chat-network-dialog"))
            assertEquals(0, conversations.connections)
            authentication.restoreSession = GitHubSession.Authenticated(GitHubAccount(1, "journey-sample"))
            compose.onNodeWithTag("chat-network-retry").ensureDisplayed().performClick()
            waitFor(hasText(text(AuthR.string.chat_ready)))
            compose.onNodeWithTag("chat-network-dialog").assertDoesNotExist()
            assertEquals(0, conversations.replies)
        }
    }

    @Test
    fun conversationSendsDirectlyAndKeepsRepliesAcrossRecreationUntilNewConversation() {
        authentication.approve()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(hasText(text(PetR.string.pet_talk_action)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_talk_action)).ensureDisplayed().performClick()
            waitFor(hasTestTag("chat-input"))
            waitFor(hasText(text(AuthR.string.chat_ready)))
            compose.onNodeWithTag("chat-input").performTextInput("오늘도 반가워")
            waitFor(hasTestTag("chat-send") and isEnabled())
            compose.onNodeWithTag("chat-send").performClick()
            waitFor(hasText("이야기를 들려줘서 고마워요."))
            compose.onNodeWithText("오늘도 반가워").assertExists()
            scenario.recreate()
            waitFor(hasText("이야기를 들려줘서 고마워요."))
            var keyboardVisible = false
            scenario.onActivity { activity ->
                keyboardVisible = ViewCompat
                    .getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            if (keyboardVisible) {
                compose.onNodeWithContentDescription(text(AuthR.string.copilot_back)).ensureDisplayed().performClick()
            }
            waitFor(hasText(text(AuthR.string.chat_new)))
            compose.onNodeWithText(text(AuthR.string.chat_new)).ensureDisplayed().performClick()
            compose.onNodeWithText("이야기를 들려줘서 고마워요.").assertDoesNotExist()
            compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        }
    }

    @Test
    fun returningFromHomeClearsVisibleMessagesButKeepsProviderHistory() {
        authentication.approve()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(hasText(text(PetR.string.pet_talk_action)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_talk_action)).ensureDisplayed().performClick()
            waitFor(hasText(text(AuthR.string.chat_ready)))
            compose.onNodeWithTag("chat-input").performTextInput("first exchange")
            compose.onNodeWithTag("chat-send").performClick()
            waitFor(hasText("이야기를 들려줘서 고마워요."))
            var keyboardVisible = false
            scenario.onActivity { activity ->
                keyboardVisible = ViewCompat
                    .getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            if (keyboardVisible) {
                compose.onNodeWithContentDescription(text(AuthR.string.copilot_back)).ensureDisplayed().performClick()
            }
            compose.onNodeWithContentDescription(text(AuthR.string.copilot_back)).ensureDisplayed().performClick()
            waitFor(hasText(text(PetR.string.pet_talk_action)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_talk_action)).ensureDisplayed().performClick()
            waitFor(hasText(text(AuthR.string.chat_ready)))
            compose.onNodeWithText("first exchange").assertDoesNotExist()
            compose.onNodeWithText("이야기를 들려줘서 고마워요.").assertDoesNotExist()
            compose.onNodeWithTag("chat-new-action").assertExists()
            compose.onNodeWithTag("chat-input").performTextInput("second exchange")
            compose.onNodeWithTag("chat-send").performClick()
            waitFor(hasText("이야기를 들려줘서 고마워요."))
            compose.onNodeWithText("first exchange").assertDoesNotExist()
            compose.onNodeWithText("second exchange").assertExists()
            assertEquals(
                listOf("first exchange", "이야기를 들려줘서 고마워요.", "second exchange"),
                conversations.requests.last().map { it.text },
            )
            assertEquals(1, conversations.conversationIds.distinct().size)
            scenario.onActivity { activity ->
                keyboardVisible = ViewCompat
                    .getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            if (keyboardVisible) {
                compose.onNodeWithContentDescription(text(AuthR.string.copilot_back)).ensureDisplayed().performClick()
            }
            compose.onNodeWithContentDescription(text(AuthR.string.copilot_back)).ensureDisplayed().performClick()
            waitFor(hasText(text(PetR.string.pet_talk_action)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_talk_action)).ensureDisplayed().performClick()
            waitFor(hasText(text(AuthR.string.chat_ready)))
            compose.onNodeWithText("second exchange").assertDoesNotExist()
            compose.onNodeWithTag("chat-new-action").ensureDisplayed().performClick()
            compose.onNodeWithTag("chat-new-action").assertDoesNotExist()
            compose.onNodeWithTag("chat-input").performTextInput("fresh exchange")
            compose.onNodeWithTag("chat-send").performClick()
            waitFor(hasText("이야기를 들려줘서 고마워요."))
            assertEquals(listOf("fresh exchange"), conversations.requests.last().map { it.text })
            assertEquals(2, conversations.conversationIds.distinct().size)
        }
    }

    @Test
    fun signedOutHomeAndMenuChatOpenConnectionDirectly() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText(text(PetR.string.pet_talk_action)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_talk_action)).ensureDisplayed().performClick()
            waitFor(hasText(text(AuthR.string.copilot_connect)))
            compose.onNodeWithTag("chat-input").assertDoesNotExist()
            compose.onNodeWithContentDescription(text(AuthR.string.copilot_back)).ensureDisplayed().performClick()
            waitFor(hasContentDescription(text(PetR.string.pet_open_menu)))
            compose.onNodeWithContentDescription(text(PetR.string.pet_open_menu)).performClick()
            compose
                .onNode(hasText(text(R.string.drawer_menu_chat)) and hasAnyAncestor(hasTestTag("companion-menu")))
                .ensureDisplayed()
                .performClick()
            waitFor(hasText(text(AuthR.string.copilot_connect)))
            compose.onNodeWithTag("chat-input").assertDoesNotExist()
        }
    }

    @Test
    fun authenticationSuccessOffersConversationBeforeSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasText(text(PetR.string.pet_talk_action)) and isEnabled())
            compose.onNodeWithText(text(PetR.string.pet_talk_action)).ensureDisplayed().performClick()
            waitFor(hasText(text(AuthR.string.copilot_connect)))
            authentication.approve()
            val chat =
                InstrumentationRegistry.getInstrumentation().targetContext.getString(
                    AuthR.string.copilot_chat,
                    "모비",
                )
            waitFor(hasText(chat) and isEnabled())
            compose.onNodeWithText(text(AuthR.string.copilot_settings)).ensureDisplayed()
            compose.onNodeWithText(chat).ensureDisplayed().performClick()
            waitFor(hasTestTag("chat-input"))
            compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        }
    }

    @Test
    fun authenticatedMenuChatOpensComposer() {
        authentication.approve()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(hasContentDescription(text(PetR.string.pet_open_menu)))
            compose.onNodeWithContentDescription(text(PetR.string.pet_open_menu)).performClick()
            compose
                .onNode(hasText(text(R.string.drawer_menu_chat)) and hasAnyAncestor(hasTestTag("companion-menu")))
                .ensureDisplayed()
                .performClick()
            waitFor(hasTestTag("chat-input"))
            compose.onNodeWithTag("chat-input").assertIsEnabled()
            compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        }
    }

    private fun SemanticsNodeInteraction.ensureDisplayed(): SemanticsNodeInteraction {
        if (!isDisplayed()) performScrollTo()
        return assertIsDisplayed()
    }

    private fun text(resource: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }
    }
}
