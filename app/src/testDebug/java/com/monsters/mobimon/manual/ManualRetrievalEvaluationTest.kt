package com.monsters.mobimon.manual

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.domain.ManualSearchResult
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ManualRetrievalEvaluationTest {
    @Test fun frozenQuestionsMeasureEvidenceRecall() =
        runTest {
            val assets = ApplicationProvider.getApplicationContext<Application>().assets
            val cases = assets.open("manuals/evaluation.json").bufferedReader().use { JSONArray(it.readText()) }
            assertEquals(30, cases.length())
            val retriever = AssetManualRetriever(assets)
            val rows = JSONArray()
            var evidenceCases = 0
            var hits = 0
            var correctAbstentions = 0
            for (index in 0 until cases.length()) {
                val case = cases.getJSONObject(index)
                val start = System.nanoTime()
                val result = retriever.search(case.getString("query"))
                val found = (result as? ManualSearchResult.Found)?.evidence.orEmpty()
                val expected = case.getJSONArray("expectedChunkIds")
                val hit =
                    if (expected.length() == 0) {
                        (result == ManualSearchResult.NoEvidence).also { if (it) correctAbstentions++ }
                    } else {
                        evidenceCases++
                        (0 until expected.length())
                            .all { wanted ->
                                found.any { expected.getString(wanted) in it.chunkIds }
                            }.also { if (it) hits++ }
                    }
                rows.put(
                    JSONObject()
                        .put("id", case.getString("id"))
                        .put("kind", case.getString("kind"))
                        .put("query", case.getString("query"))
                        .put("hit", hit)
                        .put("ids", JSONArray(found.map { it.id }))
                        .put("includedChunkIds", JSONArray(found.flatMap { it.chunkIds }.distinct()))
                        .put("millis", (System.nanoTime() - start) / 1_000_000),
                )
            }
            val report =
                JSONObject()
                    .put("evidenceCases", evidenceCases)
                    .put("hits", hits)
                    .put("correctAbstentions", correctAbstentions)
                    .put("rows", rows)
            File("build/reports/manual").mkdirs()
            File("build/reports/manual/retrieval-evaluation.json").writeText(report.toString(2))
            // Lexical weather matches do not classify live-world intent; the live AI evaluation checks that boundary.
            assertTrue(report.toString(), hits.toDouble() / evidenceCases >= 0.9)
        }
}
