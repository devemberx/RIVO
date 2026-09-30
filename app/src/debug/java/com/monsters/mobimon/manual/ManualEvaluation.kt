package com.monsters.mobimon.manual

import android.content.res.AssetManager
import com.monsters.mobimon.core.auth.PersistentGitHubAuthentication
import com.monsters.mobimon.core.auth.withToolDiagnosticProvider
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationReplyPolicy
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolUsage
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ConversationTurn
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.ManualRetriever
import com.monsters.mobimon.core.domain.ManualSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Explicit synthetic evaluation only. No real chat input, conversation storage, credentials or provider bodies. */
internal object ManualEvaluation {
    suspend fun run(
        authentication: PersistentGitHubAuthentication,
        assets: AssetManager,
        cacheDirectory: File,
        progress: (String) -> Unit,
        startCase: Int = 1,
        count: Int = 30,
    ): String {
        val account = (authentication.session.value as? GitHubSession.Authenticated)?.account ?: return "ACCOUNT"
        val cases =
            withContext(Dispatchers.IO) {
                assets.open("manuals/evaluation.json").bufferedReader().use { JSONArray(it.readText()) }
            }
        require(startCase in 1..cases.length() && count in 1..cases.length() - startCase + 1)
        val report = JSONArray()
        var recordedQuery: String? = null
        var search: ManualSearchResult? = null
        var searchMillis = 0L
        var usage: ConversationToolUsage? = null
        var status: String? = null
        var envelopeShape: JSONObject? = null
        val local = AssetManualRetriever(assets)
        val recording =
            object : ManualRetriever {
                override suspend fun search(query: String): ManualSearchResult {
                    val started = System.nanoTime()
                    val result = local.search(query)
                    searchMillis = (System.nanoTime() - started) / 1_000_000
                    recordedQuery = query
                    search = result
                    return result
                }
            }
        val base = ManualConversationTools.create(recording) { usage = it }
        val tools =
            ConversationTools(
                base.tools,
                base.instruction,
                ConversationReplyPolicy { text, evidence ->
                    val envelope = runCatching { JSONObject(text) }.getOrNull()
                    val ids = envelope?.optJSONArray("sourceIds")
                    val safeIds =
                        if (ids == null) {
                            emptyList()
                        } else {
                            (0 until ids.length()).mapNotNull {
                                ids.optString(it).takeIf { value -> value.matches(Regex("ne1-[0-9]{4}")) }
                            }
                        }
                    envelopeShape =
                        JSONObject()
                            .put("jsonObject", envelope != null)
                            .put("fieldCount", envelope?.length() ?: 0)
                            .put("sourceIds", JSONArray(safeIds))
                            .put(
                                "inlineIds",
                                JSONArray(Regex("\\[ne1-[0-9]{4}]").findAll(text).map { it.value }.toList()),
                            )
                    base.replyPolicy.accept(text, evidence).also { accepted ->
                        if (accepted is ConversationResult.Success) {
                            status = JSONObject(text).optString("status").ifBlank { "CLARIFIED_EMPTY" }
                        }
                    }
                },
                base.onUsage,
            )
        return authentication.withToolDiagnosticProvider(tools) { provider ->
            for (index in startCase - 1 until startCase - 1 + count) {
                recordedQuery = null
                search = null
                searchMillis = 0
                usage = null
                status = null
                envelopeShape = null
                val case = cases.getJSONObject(index)
                val history = case.getJSONArray("history")
                val messages =
                    (0 until history.length()).map { ConversationTurn(history.getString(it), it % 2 == 0) } +
                        ConversationTurn(case.getString("question"), true)
                val start = System.nanoTime()
                val result =
                    provider.reply(
                        account.id,
                        "manual-evaluation-${case.getString("id")}",
                        "friend:mobi",
                        messages,
                    )
                val elapsed = (System.nanoTime() - start) / 1_000_000
                val sources = (search as? ManualSearchResult.Found)?.evidence.orEmpty()
                val expected = case.getJSONArray("expectedChunkIds")
                val hit =
                    if (expected.length() == 0) {
                        null
                    } else {
                        (0 until expected.length()).all { wanted ->
                            sources.any {
                                expected.getString(wanted) in
                                    it.chunkIds
                            }
                        }
                    }
                val row =
                    JSONObject()
                        .put("id", case.getString("id"))
                        .put("kind", case.getString("kind"))
                        .put("question", case.getString("question"))
                        .put("query", recordedQuery ?: JSONObject.NULL)
                        .put("searchMillis", searchMillis)
                        .put("retrievedIds", JSONArray(sources.map { it.id }))
                        .put("includedChunkIds", JSONArray(sources.flatMap { it.chunkIds }.distinct()))
                        .put("expectedHit", hit ?: JSONObject.NULL)
                        .put("status", status ?: JSONObject.NULL)
                        .put("envelopeShape", envelopeShape ?: JSONObject.NULL)
                        .put("elapsedMillis", elapsed)
                when (result) {
                    is ConversationResult.Success -> row.put("acceptedText", result.value)
                    is ConversationResult.Failure -> row.put("failure", result.problem.name)
                }
                usage?.let {
                    row
                        .put("modelRequests", it.modelRequests)
                        .put("toolExecutions", it.toolExecutions)
                        .put("estimatedPromptTokens", it.estimatedPromptTokens)
                        .put("promptTokens", it.promptTokens ?: JSONObject.NULL)
                        .put("completionTokens", it.completionTokens ?: JSONObject.NULL)
                }
                report.put(row)
                withContext(Dispatchers.IO) {
                    File(cacheDirectory, "manual-evaluation.json").writeText(report.toString(2))
                }
                progress("${case.getString("id")} ${status ?: "REJECTED"} evidence=$hit elapsedMs=$elapsed")
                if (result is ConversationResult.Failure && result.problem in stopProblems) {
                    return@withToolDiagnosticProvider "STOPPED ${case.getString("id")} ${result.problem}"
                }
            }
            "COMPLETED ${report.length()} synthetic cases; report in app cache"
        }
    }

    private val stopProblems =
        setOf(
            ConversationProblem.USAGE,
            ConversationProblem.ACCOUNT,
            ConversationProblem.ACCESS,
            ConversationProblem.RESTRICTED,
            ConversationProblem.NETWORK,
            ConversationProblem.SERVICE,
            ConversationProblem.TIMEOUT,
        )
}
