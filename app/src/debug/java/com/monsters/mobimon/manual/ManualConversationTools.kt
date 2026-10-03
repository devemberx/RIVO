package com.monsters.mobimon.manual

import com.monsters.mobimon.core.domain.ConversationEvidence
import com.monsters.mobimon.core.domain.ConversationTool
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolDefinition
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.ConversationToolUsage
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ManualRetriever
import com.monsters.mobimon.core.domain.ManualSearchResult
import org.json.JSONArray
import org.json.JSONObject

internal object ManualConversationTools {
    fun create(
        retriever: ManualRetriever,
        onUsage: (ConversationToolUsage) -> Unit = {},
    ): ConversationTools =
        ConversationTools(
            listOf(SearchManual(retriever)),
            "$groundingInstruction\n$legacyReplyInstruction",
            ManualReplyPolicy,
            onUsage,
        )

    val groundingInstruction =
        """
        # Manual evidence
        When search_vehicle_manual is declared, search this turn before answering manual facts, specifications or procedures,
        including follow-ups. Never substitute model memory or prior assistant citations. If the tool is absent, state missing access.
        Search only for the manual portion of the current request, never live vehicle readings, greetings or emotional support.
        The corpus covers the Korean-market 2027 Hyundai IONIQ 5 (NE1), excluding IONIQ 5 N, other years/markets,
        infotainment manuals and image-only information. Do not infer figures or decode icon glyphs.
        Resolve follow-ups into a standalone Korean query naming the feature. For example, after a wiper question,
        '교체는 어떻게 해?' becomes '와이퍼 블레이드 교체 방법'. Clarify genuinely ambiguous references or equipment.
        Use only returned excerpts. Preserve option conditions, units, procedure order and directly relevant safety warnings.
        Distinguish high-voltage traction battery care from 12 V auxiliary battery maintenance.
        Keep storage-only advice and service procedures separate from everyday care. Never assume optional equipment is installed.
        For broad care questions, select relevant supported tips rather than dumping every excerpt.
        Empty search results supply no citations or manual facts; retain useful checked vehicle information in mixed questions.
        In a manual answer, put the exact returned source ID beside each supported claim as [ne1-XXXX],
        and list used IDs in sourceIds once each in first-appearance order. Never copy the app's [1] display markers
        or source list from conversation history; the app renders them after checking the current-turn IDs.
        """.trimIndent()

    // The manual-only diagnostic path still uses its unversioned response policy.
    private val legacyReplyInstruction =
        """
        # Response contract
        Return one JSON object with status, nonempty companion text and sourceIds; no extra fields or Markdown fences.
        {"status":"CONVERSATION","text":"응, 듣고 있어. 편하게 이야기해.","sourceIds":[]}
        CONVERSATION is ordinary chat or discussion of supplied context, without tool execution or manual claims.
        ANSWERED requires current-turn manual evidence. Cite each as [ne1-0000] with the actual returned ID beside its claim.
        List those IDs in sourceIds once each in first-use order, at most four. Use the smallest sufficient set.
        Add no source heading, bibliography or invented source metadata to text; the app supplies them.
        NEEDS_CLARIFICATION asks for necessary question/equipment details. OUT_OF_SCOPE explains unsupported manual coverage/capabilities.
        NO_EVIDENCE means insufficient factual evidence; the app treats it as a failed turn. All non-ANSWERED statuses have empty sourceIds
        and no manual claims. Ordinary chat is in scope. Escape newlines within text as \n; do not invent observations or source metadata.
        """.trimIndent()

    private class SearchManual(
        private val retriever: ManualRetriever,
    ) : ConversationTool {
        override val definition =
            ConversationToolDefinition(
                "search_vehicle_manual",
                "Search the Korean 2027 IONIQ 5 NE1 owner's manual for facts and procedures, including follow-ups. Returns text excerpts with source IDs, or empty sources. Never reads current vehicle state.",
                "query",
                "A standalone Korean manual question; resolve the conversation's references and name the feature.",
            )

        override suspend fun execute(call: ConversationToolCall): ConversationToolResult =
            when (val result = retriever.search(call.argument)) {
                ManualSearchResult.NoEvidence ->
                    ConversationToolResult.Found(
                        JSONObject()
                            .put("status", "NO_EVIDENCE")
                            .put("sources", JSONArray())
                            .put(
                                "message",
                                "No manual evidence found.",
                            ).toString(),
                        manualLookupAttempted = true,
                    )
                ManualSearchResult.Unavailable -> ConversationToolResult.Unavailable
                ManualSearchResult.Limit -> ConversationToolResult.Limit
                is ManualSearchResult.Found -> {
                    val sources = JSONArray()
                    result.evidence.forEach { source ->
                        sources.put(
                            JSONObject()
                                .put("id", source.id)
                                .put("heading", source.heading)
                                .put(
                                    "pdfPages",
                                    JSONArray(source.pdfPages),
                                ).put("printedPages", JSONArray(source.printedPages))
                                .put("text", source.text),
                        )
                    }
                    ConversationToolResult.Found(
                        JSONObject()
                            .put("corpus", "2027 한국형 아이오닉 5 취급설명서")
                            .put("sourceUrl", "https://ownersmanual.hyundai.com/full_pdf/NE1/2027/ko_KR")
                            .put("figures", "그림 및 아이콘의 시각 정보는 해석하지 않음. 추출된 텍스트만 근거로 사용.")
                            .put("sources", sources)
                            .toString(),
                        result.evidence.map { source ->
                            val printed = source.printedPages.mapNotNull(String::toIntOrNull)
                            val label = if (printed.isEmpty()) "" else "책 ${pages(printed)}쪽 · "
                            val heading =
                                source.heading
                                    .split(" > ")
                                    .takeLast(2)
                                    .joinToString(" > ")
                            ConversationEvidence(
                                source.id,
                                "$heading · ${label}PDF ${pages(source.pdfPages)}쪽",
                            )
                        },
                        manualLookupAttempted = true,
                    )
                }
            }
    }

    private fun pages(values: List<Int>): String {
        val sorted = values.distinct().sorted()
        val ranges = mutableListOf<String>()
        var index = 0
        while (index < sorted.size) {
            val first = sorted[index]
            var last = first
            while (index + 1 < sorted.size && sorted[index + 1] == last + 1) last = sorted[++index]
            ranges.add(if (last == first) "$first" else "$first-$last")
            index++
        }
        return ranges.joinToString(", ")
    }
}
