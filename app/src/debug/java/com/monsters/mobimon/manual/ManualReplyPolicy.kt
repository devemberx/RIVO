package com.monsters.mobimon.manual

import android.util.JsonReader
import android.util.JsonToken
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationReplyPolicy
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolResult
import java.io.StringReader

/** Validates the response envelope and provenance, not semantic truth; source/answer evaluation remains necessary. */
internal object ManualReplyPolicy : ConversationReplyPolicy {
    override fun accept(
        text: String,
        result: ConversationToolResult.Found?,
    ): ConversationResult<String> {
        val reply =
            try {
                parse(text)
            } catch (_: Exception) {
                return failure()
            }
        val markers = Regex("\\[(?:ne1-|[0-9])[^\\]]*]").findAll(reply.text).map { it.value }.toList()
        return when (reply.status) {
            "CONVERSATION" -> {
                // A direct companion reply cannot discard a tool result or claim manual provenance.
                if (result != null || reply.ids.isNotEmpty() || markers.isNotEmpty()) {
                    failure()
                } else {
                    ConversationResult.Success(reply.text)
                }
            }
            "ANSWERED" -> {
                val sources =
                    result?.evidence?.associateBy { it.id }
                        ?: return ConversationResult.Failure(ConversationProblem.NO_EVIDENCE)
                if (reply.ids.isEmpty() ||
                    reply.ids.distinct().size != reply.ids.size ||
                    reply.ids.any { it !in sources } ||
                    markers.distinct() != reply.ids.map { "[$it]" }
                ) {
                    return failure()
                }
                var rendered = reply.text
                reply.ids.forEachIndexed { index, id -> rendered = rendered.replace("[$id]", "[${index + 1}]") }
                val citations = reply.ids.mapIndexed { index, id -> "[${index + 1}] ${sources.getValue(id).citation}" }
                ConversationResult.Success("$rendered\n\n출처: 2027 한국형 아이오닉 5 취급설명서\n${citations.joinToString("\n")}")
            }
            "NEEDS_CLARIFICATION", "OUT_OF_SCOPE" -> {
                if (reply.ids.isNotEmpty() || markers.isNotEmpty() || reply.text.length > 1000) {
                    failure()
                } else {
                    ConversationResult.Success(reply.text)
                }
            }
            "NO_EVIDENCE" -> {
                if (reply.ids.isNotEmpty() || markers.isNotEmpty()) {
                    failure()
                } else {
                    ConversationResult.Failure(ConversationProblem.NO_EVIDENCE)
                }
            }
            else -> failure()
        }
    }

    private class Reply(
        val status: String,
        val text: String,
        val ids: List<String>,
    )

    private fun parse(json: String): Reply {
        require(json.length <= 12_000)
        var status: String? = null
        var text: String? = null
        val ids = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        JsonReader(StringReader(json)).use { reader ->
            reader.isLenient = false
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                require(seen.add(key))
                when (key) {
                    "status" -> {
                        require(reader.peek() == JsonToken.STRING)
                        status = reader.nextString()
                    }
                    "text" -> {
                        require(reader.peek() == JsonToken.STRING)
                        text = reader.nextString()
                    }
                    "sourceIds" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            require(ids.size < 4 && reader.peek() == JsonToken.STRING)
                            ids.add(reader.nextString().also { require(it.matches(Regex("ne1-[0-9]{4}"))) })
                        }
                        reader.endArray()
                    }
                    else -> error("Unexpected response field")
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT && seen == setOf("status", "text", "sourceIds"))
        }
        require(
            !text.isNullOrBlank() &&
                text!!.length <= 8000 &&
                text!!.none { it.isISOControl() && it != '\n' && it != '\t' },
        )
        return Reply(requireNotNull(status), requireNotNull(text), ids)
    }

    private fun failure() = ConversationResult.Failure(ConversationProblem.PROVIDER)
}
