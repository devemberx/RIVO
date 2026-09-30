package com.monsters.mobimon.manual

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.domain.ConversationEvidence
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.ManualEvidence
import com.monsters.mobimon.core.domain.ManualSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ManualCoreTest {
    private val assets get() = ApplicationProvider.getApplicationContext<Application>().assets
    private val evidence =
        ConversationToolResult.Found(
            "private source",
            listOf(ConversationEvidence("ne1-0020", "완속 충전 · PDF 18-20쪽")),
        )

    @Test fun acceptsOnlyCurrentEvidenceAndRendersAppOwnedCitations() {
        val result = ManualReplyPolicy.accept(reply("ANSWERED", "설명 [ne1-0020]", listOf("ne1-0020")), evidence)
        assertTrue(result is ConversationResult.Success)
        val rendered = (result as ConversationResult.Success).value
        assertTrue(rendered.startsWith("설명 [1]"))
        assertTrue(rendered.contains("2027 한국형 아이오닉 5"))
        assertTrue(rendered.contains("[1] 완속 충전 · PDF 18-20쪽"))
        assertFalse(rendered.contains("ne1-0020"))
        assertEquals(
            ConversationResult.Failure(ConversationProblem.NO_EVIDENCE),
            ManualReplyPolicy.accept(reply("ANSWERED", "설명 [ne1-0020]", listOf("ne1-0020")), null),
        )
    }

    @Test fun oneCurrentSourceWithoutInlineMarkerReceivesAppOwnedCitation() {
        assertEquals(
            ConversationResult.Success(
                "완속 충전 방법을 확인해 봐. [1]\n\n출처 : 2027 한국형 아이오닉 5 취급설명서\n" +
                    "[1] 완속 충전 · PDF 18-20쪽",
            ),
            ManualReplyPolicy.accept(reply("ANSWERED", "완속 충전 방법을 확인해 봐.", listOf("ne1-0020")), evidence),
        )
    }

    @Test fun multipleCurrentSourcesStillRequireInlineMarkers() {
        val twoSources =
            ConversationToolResult.Found(
                "private source",
                evidence.evidence + ConversationEvidence("ne1-0021", "급속 충전 · PDF 21쪽"),
            )
        assertEquals(
            ConversationResult.Failure(ConversationProblem.PROVIDER),
            ManualReplyPolicy.accept(
                reply("ANSWERED", "충전 방법을 확인해 봐.", listOf("ne1-0020", "ne1-0021")),
                twoSources,
            ),
        )
    }

    @Test fun ordinaryConversationNeedsNoManualEvidenceAndCannotMasqueradeAsACitedReply() {
        for (text in listOf("오늘 많이 피곤했구나. 잠깐 나랑 쉬어 가자.", "지금 배터리 잔량은 내가 직접 확인할 수 없어.")) {
            assertEquals(
                ConversationResult.Success(text),
                ManualReplyPolicy.accept(reply("CONVERSATION", text), null),
            )
        }
        for ((json, sources) in listOf(
            reply("CONVERSATION", "설명 [ne1-0020]", listOf("ne1-0020")) to null,
            reply("CONVERSATION", "설명 [1]") to null,
            reply("CONVERSATION", "검색 결과를 무시한 답변") to evidence,
        )) {
            assertEquals(
                ConversationResult.Failure(ConversationProblem.PROVIDER),
                ManualReplyPolicy.accept(json, sources),
            )
        }
    }

    @Test fun ordinaryConversationCanOmitUnusedSourcesButCannotDiscardEvidence() {
        val text = "응, 듣고 있어. 편하게 이야기해."
        val withoutSources = JSONObject().put("status", "CONVERSATION").put("text", text).toString()
        assertEquals(ConversationResult.Success(text), ManualReplyPolicy.accept(withoutSources, null))
        assertEquals(
            ConversationResult.Failure(ConversationProblem.PROVIDER),
            ManualReplyPolicy.accept(withoutSources, evidence),
        )
        for (marker in listOf("[1]", "[ne1-0020]")) {
            val forged = JSONObject(withoutSources).put("text", "$text $marker").toString()
            assertEquals(
                ConversationResult.Failure(ConversationProblem.PROVIDER),
                ManualReplyPolicy.accept(forged, null),
            )
        }
    }

    @Test fun omittedConversationSourcesDoNotRelaxOtherEnvelopeChecks() {
        val withoutSources = JSONObject().put("status", "CONVERSATION").put("text", "오늘 이야기를 들려줘.")
        val invalid =
            listOf(
                JSONObject(withoutSources.toString()).put("sourceIds", JSONObject.NULL).toString(),
                JSONObject(withoutSources.toString()).put("sourceIds", "").toString(),
                JSONObject(withoutSources.toString()).put("extra", true).toString(),
            ) +
                listOf("ANSWERED", "NEEDS_CLARIFICATION", "OUT_OF_SCOPE", "NO_EVIDENCE", "UNKNOWN").map {
                    JSONObject(withoutSources.toString()).put("status", it).toString()
                }
        for (json in invalid) {
            assertEquals(
                ConversationResult.Failure(ConversationProblem.PROVIDER),
                ManualReplyPolicy.accept(json, null),
            )
        }
    }

    @Test fun rejectsUnknownMissingDuplicateAndNumericCitations() {
        val variants =
            listOf(
                reply("ANSWERED", "설명 [ne1-9999]", listOf("ne1-9999")),
                reply("ANSWERED", "설명 [ne1-0020]", emptyList()),
                reply("ANSWERED", "설명 [1]", listOf("ne1-0020")),
                reply("ANSWERED", "설명 [ne1-0020]", listOf("ne1-0020", "ne1-0020")),
                reply("ANSWERED", "설명 [ne1-0020] [ne1-9999]", listOf("ne1-0020")),
            )
        variants.forEach {
            assertEquals(
                ConversationResult.Failure(ConversationProblem.PROVIDER),
                ManualReplyPolicy.accept(it, evidence),
            )
        }
    }

    @Test fun emptyObjectWithoutEvidenceAsksForClarificationButNeverTurnsEvidenceIntoAReply() {
        assertEquals(
            ConversationResult.Success("무슨 뜻인지 잘 모르겠어. 다시 말해 줄래?"),
            ManualReplyPolicy.accept("{}", null),
        )
        assertEquals(
            ConversationResult.Failure(ConversationProblem.PROVIDER),
            ManualReplyPolicy.accept("{}", evidence),
        )
    }

    @Test fun rejectsBrokenEnvelopesWithoutPlainTextFallback() {
        val variants =
            listOf(
                "plain answer",
                "```json\n${reply("OUT_OF_SCOPE", "범위 안내")}\n```",
                "{status:'OUT_OF_SCOPE',text:'안내',sourceIds:[]}",
                "{\"status\":\"ANSWERED\",\"status\":\"OUT_OF_SCOPE\",\"text\":\"안내\",\"sourceIds\":[]}",
                "{\"status\":\"OUT_OF_SCOPE\",\"text\":3,\"sourceIds\":[]}",
                "{\"status\":\"OUT_OF_SCOPE\",\"text\":\"안내\",\"sourceIds\":[],\"extra\":true}",
                reply("OUT_OF_SCOPE", "안내") + " trailing",
                reply("OTHER", "안내"),
            )
        variants.forEach {
            assertEquals(
                ConversationResult.Failure(ConversationProblem.PROVIDER),
                ManualReplyPolicy.accept(it, evidence),
            )
        }
    }

    @Test fun allowsClarificationAndScopeButRejectsUnsupportedAnswers() {
        for (status in listOf("NEEDS_CLARIFICATION", "OUT_OF_SCOPE")) {
            assertEquals(
                ConversationResult.Success("어떤 기능이 궁금해?"),
                ManualReplyPolicy.accept(reply(status, "어떤 기능이 궁금해?"), null),
            )
        }
        assertEquals(
            ConversationResult.Failure(ConversationProblem.NO_EVIDENCE),
            ManualReplyPolicy.accept(reply("NO_EVIDENCE", "근거가 부족해."), evidence),
        )
    }

    @Test fun missingAssetsStayUnavailableAndCancellationIsNotCached() =
        runTest {
            var attempts = 0
            val unavailable =
                AssetManualRetriever {
                    attempts++
                    throw FileNotFoundException()
                }
            repeat(2) { assertEquals(ManualSearchResult.Unavailable, unavailable.search("완속 충전")) }
            assertEquals(1, attempts)
            val cancelled = AssetManualRetriever { throw CancellationException("cancel") }
            try {
                cancelled.search("완속 충전")
                error("Cancellation was swallowed")
            } catch (_: CancellationException) {
            }
        }

    @Test fun validatesTheBundledCorpusAndDetectsTampering() =
        runTest {
            val files =
                listOf("manifest.json", "chunks.json", "aliases.json").map { name ->
                    assets.open("manuals/ioniq5_2027_ko/$name").use { it.readBytes() }
                }
            AssetManualRetriever.parse(files[0], files[1], files[2])
            for (bad in listOf(files[1] + 32.toByte(), "[]".toByteArray())) {
                assertTrue(
                    runCatching {
                        AssetManualRetriever.parse(files[0], bad, files[2])
                    }.exceptionOrNull() is IllegalStateException,
                )
            }
            val wrongYear =
                JSONObject(
                    files[0].toString(Charsets.UTF_8),
                ).put("modelYear", 2026).toString().toByteArray()
            assertTrue(
                runCatching {
                    AssetManualRetriever.parse(wrongYear, files[1], files[2])
                }.exceptionOrNull() is IllegalStateException,
            )
        }

    @Test fun retrievalPreservesChargingWarningsAndRejectsOtherYears() =
        runTest {
            val retriever = AssetManualRetriever(assets)
            val found = retriever.search("완속 충전기 사용 방법") as ManualSearchResult.Found
            assertTrue(found.evidence.size in 1..4)
            val source = found.evidence.first { "ne1-0020" in it.chunkIds }
            assertTrue(source.text.contains("전기차 충전 시 안전을 위한 주의사항"))
            assertTrue(source.text.contains("연장 케이블을 사용하지 마십시오"))
            assertTrue(source.text.contains("충전 도어를 눌러 완전히 닫으십시오"))
            assertTrue(source.pdfPages.containsAll(listOf(11, 18, 19, 20)))
            assertTrue(source.printedPages.containsAll(listOf("3", "10", "11", "12")))
            assertFalse("ne1-0072" in source.chunkIds)
            for (query in listOf("2024 아이오닉 5 충전", "아이오닉 5 N의 충전", "EV6 V2L", "2027 미국형 충전", "IONIQ 6 충전", "그거", "")) {
                assertEquals(query, ManualSearchResult.NoEvidence, retriever.search(query))
            }
        }

    @Test fun citedListsPreserveParagraphsEmphasisAndRepeatedSourceMarkers() {
        val text = "핵심부터 확인해 보자.\n\n- **첫 항목**: 설명 [ne1-0020]\n- **둘째 항목**: 주의 [ne1-0020]"
        val result = ManualReplyPolicy.accept(reply("ANSWERED", text, listOf("ne1-0020")), evidence)
        assertEquals(
            ConversationResult.Success(
                text.replace("[ne1-0020]", "[1]") +
                    "\n\n출처 : 2027 한국형 아이오닉 5 취급설명서\n[1] 완속 충전 · PDF 18-20쪽",
            ),
            result,
        )
    }

    @Test fun toolKeepsSourcesStructuredAndUsesVerifiedPageMetadata() =
        runTest {
            val source = ManualEvidence("ne1-0001", "절 제목", "원문", listOf(9, 10, 12), listOf("1", "2", "4"))
            val tool =
                ManualConversationTools
                    .create(
                        object : com.monsters.mobimon.core.domain.ManualRetriever {
                            override suspend fun search(query: String) = ManualSearchResult.Found(listOf(source))
                        },
                    ).tools
                    .single()
            assertEquals("search_vehicle_manual", tool.definition.name)
            val result =
                tool.execute(
                    ConversationToolCall("call", tool.definition.name, "질문"),
                ) as ConversationToolResult.Found
            assertEquals(
                "ne1-0001",
                JSONObject(result.content).getJSONArray("sources").getJSONObject(0).getString("id"),
            )
            assertEquals("절 제목 · 책 1-2, 4쪽 · PDF 9-10, 12쪽", result.evidence.single().citation)
        }

    @Test fun tuningQueriesUseSeparateFixturesFromTheHeldOutEvaluation() =
        runTest {
            val file =
                listOf(File("../scripts/manual/tuning_cases.json"), File("scripts/manual/tuning_cases.json")).first {
                    it.exists()
                }
            val cases = JSONArray(file.readText())
            val retriever = AssetManualRetriever(assets)
            val rows = JSONArray()
            var hits = 0
            for (index in 0 until cases.length()) {
                val case = cases.getJSONObject(index)
                val start = System.nanoTime()
                val result = retriever.search(case.getString("question"))
                val found = (result as? ManualSearchResult.Found)?.evidence.orEmpty()
                val hit = found.any { case.getString("expectedChunkId") in it.chunkIds }
                if (hit) hits++
                rows.put(
                    JSONObject()
                        .put("query", case.getString("question"))
                        .put("hit", hit)
                        .put("ids", JSONArray(found.map { it.id }))
                        .put("millis", (System.nanoTime() - start) / 1_000_000),
                )
            }
            File("build/reports/manual").mkdirs()
            File("build/reports/manual/tuning.json").writeText(rows.toString(2))
            assertEquals(rows.toString(), cases.length(), hits)
        }

    @Test fun curatedCorpusPreservesOperationalSectionsAndReviewedTableCells() {
        val json = assets.open("manuals/ioniq5_2027_ko/chunks.json").bufferedReader().use { JSONArray(it.readText()) }
        val chunks = (0 until json.length()).associate { json.getJSONObject(it).let { c -> c.getString("id") to c } }
        val excluded =
            (1..9).map { "ne1-%04d".format(it) } +
                (50..57).map { "ne1-%04d".format(it) } + "ne1-0071"
        assertTrue(excluded.none(chunks::containsKey))
        assertTrue(
            listOf("ne1-0018", "ne1-0020", "ne1-0060", "ne1-0065", "ne1-0067", "ne1-0069").all(chunks::containsKey),
        )
        val autoHold = chunks.getValue("ne1-0363").getString("text")
        assertFalse(autoHold.contains("N 표시등"))
        assertTrue(autoHold.contains("[그림]"))
        assertEquals(
            listOf("329", "330", "331"),
            chunks.getValue("ne1-0363").getJSONArray("printedPages").let {
                (0 until it.length()).map(it::getString)
            },
        )
        val towing = chunks.getValue("ne1-0498").getString("text")
        assertTrue(towing.contains("N"))
        val fluids = chunks.getValue("ne1-0065").getString("text").replace(Regex("[ \t]+"), " ")
        assertTrue(fluids.contains("4WD | 전륜 | 전륜 | HK ATF 6S SP4M-1 | 약 3.2-3.3 ℓ"))
        assertTrue(fluids.contains("기본형 | 2WD | 히트펌프 미적용 | 당사 직영 하이테크센터 또는 블루핸즈 문의 | 약 9.8 ℓ"))
        assertTrue(fluids.contains("향속형 | 4WD | 히트펌프 적용 | 당사 직영 하이테크센터 또는 블루핸즈 문의 | 약 12.1 ℓ"))
        val warnings = chunks.getValue("ne1-0161").getString("text").replace(Regex("\\s+"), "")
        assertTrue(warnings.contains("브레이크액부족시브레이크가정상적으로작동하지않을수있습니다"))
        assertTrue(warnings.contains("주차브레이크를천천히조작하십시오"))
        assertTrue(warnings.contains("뒷바퀴만제동되며차량이옆으로미끄러질수있습니다"))
    }

    @Test fun oversizedRequiredWarningsAreNotTruncated() =
        runTest {
            val warning =
                ManualChunk(
                    "ne1-0001",
                    listOf("경고"),
                    "충전 " + "경고".repeat(13000),
                    1,
                    1,
                    listOf("1"),
                    emptyList(),
                    emptyList(),
                )
            val procedure =
                ManualChunk(
                    "ne1-0002",
                    listOf("충전 절차"),
                    "충전 커넥터 연결",
                    2,
                    2,
                    listOf("2"),
                    emptyList(),
                    listOf(warning.id),
                )
            assertEquals(
                ManualSearchResult.Limit,
                ManualIndex(listOf(warning, procedure), emptyList()).search("충전 커넥터 연결"),
            )
        }

    private fun reply(
        status: String,
        text: String,
        ids: List<String> = emptyList(),
    ): String =
        JSONObject()
            .put("status", status)
            .put("text", text)
            .put("sourceIds", JSONArray(ids))
            .toString()
}
