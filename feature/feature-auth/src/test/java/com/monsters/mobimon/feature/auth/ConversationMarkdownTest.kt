package com.monsters.mobimon.feature.auth

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMarkdownTest {
    @Test fun rendersStrongEmphasisAndEscapedModelDelimiters() {
        val parsed = parseConversationMarkdown("먼저 **완속 충전**을 확인해.\n\\*\\*급속 충전\\*\\*도 살펴봐.")

        assertEquals("먼저 완속 충전을 확인해.\n급속 충전도 살펴봐.", parsed.text)
        val bold = parsed.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(2, bold.size)
        assertEquals("완속 충전", parsed.text.substring(bold[0].start, bold[0].end))
        assertEquals("급속 충전", parsed.text.substring(bold[1].start, bold[1].end))
    }

    @Test fun verifiedSourceHeadingAndEntriesHaveDistinctTypography() {
        val heading = "출처 : 2027 한국형 아이오닉 5 취급설명서"
        val entry = "[1] 전기차 시작하기 > 전기차 관리 시 기타 주의사항  · 책 1–3쪽 · PDF 9–11쪽"
        val parsed = parseConversationMarkdown("**관리 방법**을 확인해 [1].\n\n$heading\n$entry")
        val bold = parsed.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        val compactLines = parsed.spanStyles.filter { it.item.fontSize == 0.8.em }
        val markers = parsed.spanStyles.filter { it.item.fontSize == 0.7.em }

        assertEquals(listOf("관리 방법", heading), bold.map { parsed.text.substring(it.start, it.end) })
        assertEquals(listOf(entry), compactLines.map { parsed.text.substring(it.start, it.end) })
        assertEquals(listOf("[1]", "[1]"), markers.map { parsed.text.substring(it.start, it.end) })
        assertTrue(parseConversationMarkdown("선택지 [1]").spanStyles.isEmpty())
    }

    @Test fun previouslyStoredSourceHeadingStillReceivesSourceStyling() {
        val parsed = parseConversationMarkdown("안내 [1].\n\n출처: 취급설명서\n[1] 충전 관리")
        val bold = parsed.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        val compact = parsed.spanStyles.single { it.item.fontSize == 0.8.em }

        assertEquals("출처: 취급설명서", parsed.text.substring(bold.start, bold.end))
        assertEquals("[1] 충전 관리", parsed.text.substring(compact.start, compact.end))
    }

    @Test fun leavesUnmatchedDelimitersAndSourceReferencesReadable() {
        val parsed = parseConversationMarkdown("**열린 강조와 [1] 출처")
        assertEquals("**열린 강조와 [1] 출처", parsed.text)
        assertTrue(parsed.spanStyles.isEmpty())
    }
}
