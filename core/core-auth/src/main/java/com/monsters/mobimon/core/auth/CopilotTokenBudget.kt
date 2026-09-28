package com.monsters.mobimon.core.auth

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import com.monsters.mobimon.core.domain.ConversationProblem
import org.json.JSONObject

/** Local estimate includes chat framing; the provider remains authoritative. No history truncation. */
internal object CopilotTokenBudget {
    private val registry by lazy { Encodings.newLazyEncodingRegistry() }

    fun promptTokens(
        model: CopilotModel,
        request: JSONObject,
    ): Long {
        val type =
            when (model.tokenizer) {
                "o200k_base" -> EncodingType.O200K_BASE
                "cl100k_base" -> EncodingType.CL100K_BASE
                else -> throw ConversationException(ConversationProblem.PROVIDER)
            }
        val encoding = registry.getEncoding(type)
        if (request.has("tools")) {
            // Serialized schemas, null content, call IDs, arguments and results plus protocol slack.
            return encoding.countTokensOrdinary(request.toString()).toLong() + 128L
        }
        val messages =
            request.optJSONArray("messages") ?: org.json.JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", request.getString("instructions")))
                val input = request.getJSONArray("input")
                for (index in 0 until input.length()) {
                    val message = input.getJSONObject(index)
                    put(
                        JSONObject()
                            .put("role", message.getString("role"))
                            .put("content", message.getJSONArray("content").getJSONObject(0).getString("text")),
                    )
                }
            }
        var total = 3L // Assistant reply priming.
        for (index in 0 until messages.length()) {
            val message = messages.getJSONObject(index)
            total += 3L + encoding.countTokensOrdinary(message.getString("role")) +
                encoding.countTokensOrdinary(message.getString("content"))
        }
        return total
    }

    fun requireFits(
        model: CopilotModel,
        request: JSONObject,
    ) {
        val maxPrompt =
            model.maxPromptTokens?.takeIf { it > 0 }
                ?: throw ConversationException(ConversationProblem.PROVIDER)
        val tokens = promptTokens(model, request)
        if (tokens > maxPrompt || model.maxContextWindowTokens?.let { tokens + model.maxOutputTokens > it } == true) {
            throw ConversationException(ConversationProblem.LIMIT)
        }
    }
}
