package com.monsters.mobimon.manual

import android.content.res.AssetManager
import com.monsters.mobimon.core.domain.ManualRetriever
import com.monsters.mobimon.core.domain.ManualSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest

internal class AssetManualRetriever(
    private val openAsset: (String) -> InputStream,
) : ManualRetriever {
    constructor(assets: AssetManager) : this({ name -> assets.open(name) })

    private val mutex = Mutex()
    private var attempted = false
    private var index: ManualIndex? = null

    override suspend fun search(query: String): ManualSearchResult {
        val loaded =
            mutex.withLock {
                if (!attempted) {
                    try {
                        val files =
                            withContext(Dispatchers.IO) {
                                listOf("manifest.json", "chunks.json", "aliases.json").map { name ->
                                    openAsset("manuals/ioniq5_2027_ko/$name").use { input ->
                                        val bytes = input.readNBytes(4_000_001)
                                        check(bytes.size <= 4_000_000)
                                        bytes
                                    }
                                }
                            }
                        index = withContext(Dispatchers.Default) { parse(files[0], files[1], files[2]) }
                        attempted = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        attempted = true
                    }
                }
                index
            } ?: return ManualSearchResult.Unavailable
        return withContext(Dispatchers.Default) { loaded.search(query) }
    }

    companion object {
        internal suspend fun parse(
            manifestBytes: ByteArray,
            chunksBytes: ByteArray,
            aliasesBytes: ByteArray,
        ): ManualIndex {
            fun sha(bytes: ByteArray) =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
            val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
            check(manifest.getInt("schemaVersion") == 1 && manifest.getString("corpusId") == "ioniq5_2027_ko")
            check(manifest.getInt("modelYear") == 2027 && manifest.getString("market") == "A99")
            check(manifest.getString("language") == "ko_KR" && manifest.getString("projectCode") == "NE1")
            check(manifest.getInt("pdfPageCount") == 565)
            check(
                manifest.getString(
                    "sourceSha256",
                ) == "4525f59eb85737ca87c3e0e0b43de9f36cb48ad2d5456f708f5861a0d6de9b1c",
            )
            check(
                sha(chunksBytes) == manifest.getString("chunksSha256") &&
                    sha(aliasesBytes) == manifest.getString("aliasesSha256"),
            )
            val json = JSONArray(chunksBytes.toString(Charsets.UTF_8))
            check(json.length() == manifest.getInt("chunkCount") && json.length() in 1..2000)

            fun strings(array: JSONArray) = (0 until array.length()).map { array.getString(it) }
            val chunks =
                (0 until json.length()).map { index ->
                    currentCoroutineContext().ensureActive()
                    val item = json.getJSONObject(index)
                    ManualChunk(
                        item.getString("id"),
                        strings(item.getJSONArray("headingPath")),
                        item.getString("text"),
                        item.getInt("pdfPageStart"),
                        item.getInt("pdfPageEnd"),
                        strings(item.getJSONArray("printedPages")),
                        strings(item.getJSONArray("contextIds")),
                        strings(item.getJSONArray("linkedWarningIds")),
                    )
                }
            val ids = chunks.map { it.id }.toSet()
            check(ids.size == chunks.size)
            chunks.forEach {
                check(it.id.matches(Regex("ne1-[0-9]{4}")) && it.text.isNotBlank() && it.heading.isNotEmpty())
                check(it.firstPage in 1..565 && it.lastPage in it.firstPage..565)
                check((it.contexts + it.warnings).all(ids::contains))
            }
            val aliases = JSONArray(aliasesBytes.toString(Charsets.UTF_8))
            return ManualIndex(chunks, (0 until aliases.length()).map { strings(aliases.getJSONArray(it)) })
        }
    }
}
