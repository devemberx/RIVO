package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationTurn
import com.monsters.mobimon.core.domain.ManualRetriever
import com.monsters.mobimon.core.domain.ManualSearchResult
import com.monsters.mobimon.core.domain.VehicleChatEvidenceSource
import com.monsters.mobimon.di.ConversationToolsModule
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleToolRoutingTest {
    private val tools =
        ConversationToolsModule.conversationTools(
            object : ManualRetriever {
                override suspend fun search(query: String) = ManualSearchResult.NoEvidence
            },
            VehicleChatEvidenceSource { error("Routing must not capture vehicle data") },
        )
    private val both = setOf("search_vehicle_manual", "get_vehicle_context")

    @Test fun completeCurrentStateRequestsDoNotExposeManualSearch() {
        for (query in listOf(
            "너 왜 아파?",
            "루나 왜 배고파?",
            "타이어 상태확인",
            "타이어 상태",
            "지금 타이어 상태 알려줘",
            "현재 배터리 잔량 확인해줘",
            "차량 상태 보여줘",
            "WHY ARE YOU SICK?",
            "Check my tire pressure.",
            "What is my battery level?",
        )) {
            assertEquals(query, setOf("get_vehicle_context"), selected(query))
        }
    }

    @Test fun manualMixedAndUnrecognizedRequestsKeepSemanticToolChoice() {
        for (query in listOf(
            "타이어 상태 확인 방법",
            "타이어 확인 방법 알려줘",
            "현재 타이어 상태와 권장 공기압",
            "너 왜 아프고 어떻게 조치해?",
            "타이어 상태확인. 그리고 교체 방법",
            "How do I check tire pressure?",
            "Check my tire pressure and explain how to inflate it",
            "Quel est l’état de mes pneus ?",
            "근거",
            "왜?",
            "그건?",
            "타이어 상태 확인하지 마",
            "내가 왜 아파?",
        )) {
            assertEquals(query, both, selected(query))
        }
    }

    @Test fun evidenceFollowupUsesLastSubstantiveUserRequestOnly() {
        val state = listOf(ConversationTurn("너 왜 아파?", true), ConversationTurn("출처 : 취급설명서 [1]", false))
        for (query in listOf("근거 보여줘", "답변의 근거 확인", "답변 근거 확인해줘", "근거 확인해줘")) {
            assertEquals(query, setOf("get_vehicle_context"), selected(query, state))
        }
        val followup = state + ConversationTurn("근거 보여줘", true) + ConversationTurn("현재 관측값", false)
        assertEquals(setOf("get_vehicle_context"), selected("출처는?", followup))
        val manual = listOf(ConversationTurn("타이어 교체 방법", true), ConversationTurn("차량 근거야", false))
        assertEquals(both, selected("근거 보여줘", manual))
        val mixed = listOf(ConversationTurn("현재 타이어 상태와 권장 공기압", true), ConversationTurn("차량 근거", false))
        assertEquals(both, selected("출처는?", mixed))
        assertEquals(both, selected("어떻게 확인해?", state))
        assertEquals(
            both,
            selected(
                "근거 보여줘",
                state + ConversationTurn("설명서대로 관리하는 방법은?", true) + ConversationTurn("안내", false),
            ),
        )
    }

    @Test fun selectionDoesNotRemoveToolsFromLaterTurns() {
        selected("타이어 상태확인")
        assertEquals(both, selected("타이어 권장 공기압 알려줘"))
    }

    private fun selected(
        query: String,
        history: List<ConversationTurn> = emptyList(),
    ) = tools
        .forTurn(history + ConversationTurn(query, true))
        .tools
        .map { it.definition.name }
        .toSet()
}
