package com.monsters.mobimon.core.domain

interface ManualRetriever {
    suspend fun search(query: String): ManualSearchResult
}

class ManualEvidence(
    val id: String,
    val heading: String,
    val text: String,
    val pdfPages: List<Int>,
    val printedPages: List<String>,
    val chunkIds: List<String> = listOf(id),
) {
    override fun toString() = "ManualEvidence(REDACTED)"
}

sealed interface ManualSearchResult {
    class Found(
        val evidence: List<ManualEvidence>,
    ) : ManualSearchResult

    data object NoEvidence : ManualSearchResult

    data object Unavailable : ManualSearchResult

    data object Limit : ManualSearchResult
}
