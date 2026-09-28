package com.monsters.mobimon.core.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monsters.mobimon.core.domain.ConversationTurn
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CopilotTokenBudgetDeviceTest {
    @Test fun bundledTokenizerCountsKoreanOnAndroidWithoutNetwork() {
        val model =
            CopilotModel(
                "gpt-4o",
                CopilotChatApi.CHAT_COMPLETIONS,
                maxPromptTokens = 64000,
                tokenizer = "o200k_base",
            )
        val request = CopilotMessageCodec.request(model, "friend:mobi", listOf(ConversationTurn("안녕 모비", true)))
        assertTrue(CopilotTokenBudget.promptTokens(model, request) > 100)
        CopilotTokenBudget.requireFits(model, request)
    }
}
