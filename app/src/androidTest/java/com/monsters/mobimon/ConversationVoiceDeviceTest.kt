package com.monsters.mobimon

import android.Manifest
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.testing.JourneyAuthentication
import com.monsters.mobimon.testing.JourneyConversationProvider
import com.monsters.mobimon.testing.JourneyStorage
import com.monsters.mobimon.testing.JourneyVehicle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject
import com.monsters.mobimon.feature.pet.R as PetR

/** Bundled local STT and app UI; authentication, vehicle and Copilot remain isolated journey fakes. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConversationVoiceDeviceTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var authentication: JourneyAuthentication

    @Inject lateinit var conversations: JourneyConversationProvider

    @Inject lateinit var vehicle: JourneyVehicle

    @Inject lateinit var storage: JourneyStorage

    @Before
    fun setUp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("voiceIntegration") == "true")
        hilt.inject()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("voicePermissionPrompt") != "true") {
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.RECORD_AUDIO,
            )
        }
        authentication.approve()
    }

    @After
    fun tearDown() {
        if (::storage.isInitialized) storage.close()
    }

    private fun normalizedSpeech(
        expected: String,
        partial: Boolean = false,
    ): SemanticsMatcher =
        SemanticsMatcher("Korean transcript matches ignoring spaces and punctuation") { node ->
            val text =
                if (partial) {
                    node.config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
                } else {
                    node.config
                        .getOrNull(SemanticsProperties.EditableText)
                        ?.text
                        .orEmpty()
                }

            fun normalize(value: String) = value.filter { it.isLetterOrDigit() }
            normalize(text) == normalize(expected)
        }

    @Test
    fun offlineKoreanMicrophoneReviewsBeforeExplicitSendAndReleasesOnInterruption() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            compose.waitUntil(30_000) {
                compose
                    .onAllNodes(hasText(context.getString(PetR.string.pet_talk_action)) and isEnabled())
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            compose.onNodeWithText(context.getString(PetR.string.pet_talk_action)).performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("chat-input").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            compose.onNodeWithTag("chat-input").performTextInput("보존할 초안")
            compose.waitUntil(30_000) {
                compose
                    .onAllNodes(
                        hasContentDescription("음성으로 입력") and isEnabled(),
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            compose.onNodeWithContentDescription("음성으로 입력").performClick()
            compose.waitUntil(30_000) {
                compose
                    .onAllNodes(
                        hasContentDescription("녹음 마치고 내용 확인") and isEnabled(),
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            compose.onNodeWithTag("chat-send").assertIsNotEnabled()
            Log.i("MobiMonVoiceDeviceTest", "MICROPHONE_READY_FOR_FIXTURE")
            compose.waitUntil(20_000) {
                compose
                    .onAllNodes(
                        normalizedSpeech("안녕하세요 오늘 날씨가 좋습니다", partial = true),
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            Thread.sleep(6_000)
            compose.onNodeWithContentDescription("녹음 마치고 내용 확인").assertIsEnabled()
            compose.onNodeWithTag("chat-send").assertIsNotEnabled()
            Log.i("MobiMonVoiceDeviceTest", "NEXT_PHRASE_READY_FOR_FIXTURE")
            compose.waitUntil(45_000) {
                compose
                    .onAllNodes(
                        normalizedSpeech("안녕하세요 오늘 날씨가 좋습니다 안녕하세요 오늘 날씨가 좋습니다"),
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            assertEquals(0, conversations.replies)
            val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val output = File(context.filesDir, "test-screenshots").apply { mkdirs() }
            File(output, "conversation-native-recognized-korean.png").outputStream().use {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            image.recycle()
            compose.onNodeWithTag("chat-input").performTextInput(" 수정")
            assertEquals(0, conversations.replies)
            compose.waitUntil(30_000) {
                compose
                    .onAllNodes(
                        hasContentDescription("음성으로 입력") and isEnabled(),
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            compose.onNodeWithContentDescription("음성으로 입력").performClick()
            compose.waitUntil(30_000) {
                compose
                    .onAllNodes(
                        hasContentDescription("녹음 마치고 내용 확인") and isEnabled(),
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            vehicle.publish(DrivingState.MOVING, SignalQuality.VALID)
            compose.waitUntil(10_000) {
                compose
                    .onAllNodesWithTag(
                        "chat-parking-dialog",
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            vehicle.publish(DrivingState.PARKED, SignalQuality.VALID)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("chat-input").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            compose.onNode(normalizedSpeech("안녕하세요 오늘 날씨가 좋습니다 안녕하세요 오늘 날씨가 좋습니다 수정")).assertExists()
            compose.waitUntil(30_000) {
                compose
                    .onAllNodes(
                        hasContentDescription("음성으로 입력") and isEnabled(),
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            compose.onNodeWithContentDescription("음성으로 입력").performClick()
            compose.waitUntil(10_000) {
                compose
                    .onAllNodes(
                        hasContentDescription("녹음 마치고 내용 확인") and isEnabled(),
                    ).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("chat-input").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            compose.onNode(normalizedSpeech("안녕하세요 오늘 날씨가 좋습니다 안녕하세요 오늘 날씨가 좋습니다 수정")).assertExists()
            assertEquals(0, conversations.replies)
            compose.onNodeWithTag("chat-send").performClick()
            compose.waitUntil(10_000) { conversations.replies == 1 }
            assertTrue(conversations.connections > 0)
        }
    }
}
