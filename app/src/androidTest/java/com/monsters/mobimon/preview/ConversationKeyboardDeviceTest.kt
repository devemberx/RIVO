package com.monsters.mobimon.preview

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.feature.auth.preview.ConversationPreviewActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ConversationKeyboardDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun voicePreviewHasSeparateActionsAndNativeInsets() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (sample in listOf("voice-listening", "voice-review")) {
            val intent = Intent(context, ConversationPreviewActivity::class.java).putExtra("state", sample)
            ActivityScenario.launch<ConversationPreviewActivity>(intent).use {
                val panel = compose.onNodeWithTag("chat-panel").fetchSemanticsNode().boundsInRoot
                val send = compose.onNodeWithTag("chat-send").fetchSemanticsNode().boundsInRoot
                val left =
                    compose
                        .onNodeWithContentDescription(
                            if (sample == "voice-listening") "녹음 마치고 내용 확인" else "음성으로 입력",
                        ).fetchSemanticsNode()
                        .boundsInRoot
                assertTrue("Adjacent actions must have separate touch bounds", left.right <= send.left + 1f)
                assertTrue("Actions must stay above the system navigation", send.bottom <= panel.bottom)
                if (sample == "voice-listening") compose.onNodeWithTag("chat-send").assertIsNotEnabled()
                val output = File(context.filesDir, "test-screenshots").apply { mkdirs() }
                compose.waitForIdle()
                instrumentation.uiAutomation.waitForIdle(500, 5_000)
                val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(output, "conversation-native-$sample.png").outputStream().use { stream ->
                    assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                image.recycle()
                if (sample == "voice-review") {
                    compose
                        .onNodeWithContentDescription("음성으로 입력")
                        .performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
                    compose.onNodeWithContentDescription("음성으로 입력").assertIsFocused()
                    compose.waitForIdle()
                    val focused = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    File(output, "conversation-native-voice-focus.png").outputStream().use { stream ->
                        assertTrue(focused.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    }
                    focused.recycle()
                }
            }
        }
    }

    @Test
    fun editingFailedPreviewRemovesUnansweredBubble() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, ConversationPreviewActivity::class.java).putExtra("state", "failed")
        ActivityScenario.launch<ConversationPreviewActivity>(intent).use {
            compose.onNodeWithTag("chat-inline-failure").assertIsDisplayed()
            assertEquals(3, compose.onAllNodesWithTag("chat-user-bubble").fetchSemanticsNodes().size)
            compose.onNodeWithText("내용 수정").performClick()
            assertEquals(2, compose.onAllNodesWithTag("chat-user-bubble").fetchSemanticsNodes().size)
            compose.onNodeWithTag("chat-inline-failure").assertDoesNotExist()
        }
    }

    @Test
    fun replyNetworkFailureRechecksInlineWithoutRemovingTheFailedTurn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (sample in listOf("network-failed", "timeout-failed")) {
            val intent = Intent(context, ConversationPreviewActivity::class.java).putExtra("state", sample)
            ActivityScenario.launch<ConversationPreviewActivity>(intent).use {
                compose.onNodeWithTag("chat-network-dialog").assertDoesNotExist()
                compose.onNodeWithTag("chat-inline-failure").assertIsDisplayed()
                compose.onNodeWithContentDescription("음성으로 입력").assertIsDisplayed()
                compose.onNodeWithText("다시 확인").assertIsEnabled().performClick()
                compose.onNodeWithTag("chat-network-dialog").assertDoesNotExist()
                compose.onNodeWithTag("chat-inline-failure").assertIsDisplayed()
                assertEquals(3, compose.onAllNodesWithTag("chat-user-bubble").fetchSemanticsNodes().size)
                compose.onNodeWithText("다시 보내기").assertIsEnabled()
                compose.onNodeWithText("내용 수정").assertIsEnabled().performClick()
                compose.onNodeWithTag("chat-inline-failure").assertDoesNotExist()
                compose.onNodeWithTag("chat-input").assertTextContains("모비는 뭐가 좋아?")
            }
        }
    }

    @Test
    fun shortConversationShowsBothSpeakersAndNewChat() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, ConversationPreviewActivity::class.java).putExtra("state", "messages")
        ActivityScenario.launch<ConversationPreviewActivity>(intent).use {
            compose.onNodeWithText("오늘은 조금 피곤한 하루였어.").assertIsDisplayed()
            compose.onNodeWithText("오늘 하루도 수고했어요.", substring = true).assertIsDisplayed()
            compose.onNodeWithTag("chat-new-action").assertIsDisplayed()
        }
    }

    @Test
    fun realImeResizesPanelsAndBackRetainsDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val setting = "show_ime_with_hard_keyboard"
        val previous = Settings.Secure.getString(context.contentResolver, setting)?.toIntOrNull()

        fun shell(command: String) {
            android.os.ParcelFileDescriptor
                .AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(command),
                ).use { it.readBytes() }
        }
        shell("settings put secure $setting 1")
        try {
            val intent = Intent(context, ConversationPreviewActivity::class.java).putExtra("state", "keyboard")
            ActivityScenario.launch<ConversationPreviewActivity>(intent).use { scenario ->
                compose.onNodeWithTag("chat-input").assertIsDisplayed()
                val full = compose.onNodeWithTag("chat-panel").fetchSemanticsNode().boundsInRoot
                compose.onNodeWithTag("chat-input").performClick()
                compose.onNodeWithTag("chat-input").assertIsFocused()
                scenario.onActivity {
                    WindowInsetsControllerCompat(it.window, it.window.decorView).show(WindowInsetsCompat.Type.ime())
                }

                fun waitForIme(visible: Boolean) {
                    compose.waitUntil(15_000) {
                        var shown = false
                        scenario.onActivity {
                            shown =
                                WindowInsetsCompat
                                    .toWindowInsetsCompat(it.window.decorView.rootWindowInsets)
                                    .isVisible(WindowInsetsCompat.Type.ime())
                        }
                        shown == visible
                    }
                }
                waitForIme(true)
                compose.waitForIdle()
                val resized = compose.onNodeWithTag("chat-panel").fetchSemanticsNode().boundsInRoot
                val composer = compose.onNodeWithTag("chat-composer").fetchSemanticsNode().boundsInRoot
                assertEquals(full.left, resized.left, 1f)
                assertEquals(full.top, resized.top, 1f)
                assertEquals(full.width, resized.width, 1f)
                assertTrue(resized.height < full.height)
                assertTrue(composer.bottom <= resized.bottom)
                val output = File(context.filesDir, "test-screenshots").apply { mkdirs() }
                instrumentation.uiAutomation.waitForIdle(500, 5_000)
                val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(output, "conversation-native-keyboard.png").outputStream().use {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                screenshot.recycle()
                compose.onNodeWithContentDescription("뒤로").performClick()
                waitForIme(false)
                compose.onNodeWithText("오늘 하루가 조금 힘들었어").assertIsDisplayed()
                compose.onNodeWithContentDescription("뒤로").performClick()
                compose.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
            }
        } finally {
            shell(if (previous == null) "settings delete secure $setting" else "settings put secure $setting $previous")
        }
    }
}
