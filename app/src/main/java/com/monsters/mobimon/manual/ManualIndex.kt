package com.monsters.mobimon.manual

import com.monsters.mobimon.core.domain.ManualEvidence
import com.monsters.mobimon.core.domain.ManualSearchResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

internal data class ManualChunk(
    val id: String,
    val heading: List<String>,
    val text: String,
    val firstPage: Int,
    val lastPage: Int,
    val printedPages: List<String>,
    val contexts: List<String>,
    val warnings: List<String>,
)

/** Immutable in-memory BM25 index. Word terms lead; Korean 2/3-grams tolerate spacing and particles. */
internal class ManualIndex(
    private val chunks: List<ManualChunk>,
    private val aliases: List<List<String>>,
) {
    private val byId = chunks.associateBy { it.id }
    private val documents = chunks.map { terms(it.text) }
    private val headings = chunks.map { terms(it.heading.last()) }
    private val lengths = documents.map { it.values.sum().toDouble() }
    private val averageLength = lengths.average().coerceAtLeast(1.0)
    private val frequency =
        mutableMapOf<String, Int>().apply {
            documents.forEach { document -> document.keys.forEach { key -> this[key] = getOrDefault(key, 0) + 1 } }
        }

    suspend fun search(query: String): ManualSearchResult {
        currentCoroutineContext().ensureActive()
        if (query.isBlank() ||
            query.length > 400 ||
            outsideCorpus.containsMatchIn(query)
        ) {
            return ManualSearchResult.NoEvidence
        }
        val normalized = normalize(query)
        val expanded =
            buildString {
                append(normalized)
                aliases.forEach { group ->
                    if (group.any { normalize(it).replace(" ", "") in normalized.replace(" ", "") }) {
                        append(' ')
                        append(group.joinToString(" "))
                    }
                }
            }
        val queryTerms = terms(expanded).keys
        if (queryTerms.isEmpty()) return ManualSearchResult.NoEvidence
        val ranked =
            chunks.indices
                .map { index ->
                    currentCoroutineContext().ensureActive()
                    var score = 0.0
                    for (term in queryTerms) {
                        val count = documents[index][term] ?: continue
                        val df = frequency.getValue(term)
                        val idf = ln(1.0 + (chunks.size - df + 0.5) / (df + 0.5))
                        val tf = count.toDouble()
                        val length = lengths[index] / averageLength
                        val weight =
                            when {
                                term.startsWith("w:") -> 1.0
                                term.startsWith("3:") -> 0.22
                                else -> 0.06
                            }
                        score += weight * idf * tf * 2.2 / (tf + 1.2 * (0.25 + 0.75 * length))
                        if (term in headings[index]) score += weight * idf * 1.8
                    }
                    index to score
                }.filter {
                    it.second >= 2.0 &&
                        normalize(chunks[it.first].text) != normalize(chunks[it.first].heading.last())
                }.sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first })
                .take(8)
        if (ranked.isEmpty()) return ManualSearchResult.NoEvidence
        val evidence = mutableListOf<ManualEvidence>()
        val included = mutableSetOf<String>()
        var characters = 0
        for ((index, _) in ranked) {
            currentCoroutineContext().ensureActive()
            val primary = chunks[index]
            if (primary.id in included) continue
            val required = linkedSetOf<String>()

            fun include(id: String) {
                if (!required.add(id)) return
                val chunk = byId.getValue(id)
                chunk.contexts.forEach(::include)
                chunk.warnings.forEach(::include)
            }
            include(primary.id)
            val bundle =
                required
                    .map(
                        byId::getValue,
                    ).sortedWith(compareBy<ManualChunk> { it.firstPage }.thenBy { it.id })
            val text = bundle.joinToString("\n\n") { "${it.heading.joinToString(" > ")}\n${it.text}" }
            if (characters + text.length > 24_000) {
                if (evidence.isEmpty()) return ManualSearchResult.Limit
                continue
            }
            characters += text.length
            included.addAll(required)
            evidence.add(
                ManualEvidence(
                    primary.id,
                    primary.heading.joinToString(" > "),
                    text,
                    bundle.flatMap { (it.firstPage..it.lastPage).toList() }.distinct().sorted(),
                    bundle.flatMap { it.printedPages }.distinct(),
                    required.toList(),
                ),
            )
            if (evidence.size == 4) break
        }
        return if (evidence.isEmpty()) ManualSearchResult.NoEvidence else ManualSearchResult.Found(evidence)
    }

    private fun terms(text: String): Map<String, Int> {
        val words = normalize(text).split(' ').filter { it.length >= 2 && it !in stopWords }
        val result = mutableMapOf<String, Int>()

        fun add(value: String) {
            result[value] = result.getOrDefault(value, 0) + 1
        }
        words.forEach { word ->
            add("w:$word")
            // Common Korean particles otherwise make "전기차에" miss the exact word "전기차".
            val particle = particles.firstOrNull { word.endsWith(it) && word.length - it.length >= 2 }
            if (particle != null) {
                val stem = word.dropLast(particle.length)
                if (stem !in stopWords) add("w:$stem")
            }
            if (word.any { it in '가'..'힣' }) {
                for (size in 2..3) word.windowed(size).forEach { add("$size:$it") }
            }
        }
        return result
    }

    private fun normalize(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    companion object {
        private val particles =
            listOf("에서는", "으로", "에서", "에게", "부터", "까지", "에는", "은", "는", "을", "를", "이", "가", "에", "의", "도", "로")
        private val stopWords =
            setOf(
                "어떻게",
                "뭐야",
                "알려줘",
                "알려",
                "설명해",
                "설명",
                "해주세요",
                "있어",
                "있나요",
                "차량",
                "아이오닉",
                "2027",
                "ioniq",
                "사용",
                "하는",
                "방법",
                "미해석",
                "시각",
                "그림",
                "정보",
            )
        private val outsideCorpus =
            Regex(
                "20(?:0[0-9]|1[0-9]|2[0-689]|[3-9][0-9])|" +
                    "(?:아이오닉|ioniq)\\s*[69]|(?:아이오닉|ioniq)\\s*5\\s*n(?=$|[^a-z])|" +
                    "\\bev[3-9]\\b|기아|미국|북미|유럽|일본|호주|영국|\\b(?:us|usa|eu)\\b",
                RegexOption.IGNORE_CASE,
            )
    }
}
