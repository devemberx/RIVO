package com.monsters.mobimon.core.auth

import android.util.JsonReader
import android.util.JsonToken
import com.monsters.mobimon.core.domain.ConversationLimits
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationTools
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader

internal sealed interface CopilotToolReply {
    class Final(
        val text: String,
    ) : CopilotToolReply

    class Call(
        val value: ConversationToolCall,
        val assistant: JSONObject,
    ) : CopilotToolReply
}

internal object CopilotToolCodec {
    fun configure(
        request: JSONObject,
        tools: ConversationTools,
    ) {
        val system = request.getJSONArray("messages").getJSONObject(0)
        system.put("content", system.getString("content") + "\n" + tools.instruction)
        val definitions = JSONArray()
        tools.tools.forEach { tool ->
            val definition = tool.definition
            definitions.put(
                JSONObject().put("type", "function").put(
                    "function",
                    JSONObject().put("name", definition.name).put("description", definition.description).put(
                        "parameters",
                        JSONObject()
                            .put("type", "object")
                            .put("additionalProperties", false)
                            .put("required", JSONArray().put(definition.parameter))
                            .put(
                                "properties",
                                JSONObject().put(
                                    definition.parameter,
                                    JSONObject()
                                        .put("type", "string")
                                        .put("minLength", 1)
                                        .put("maxLength", definition.maxArgumentCharacters)
                                        .put("description", definition.parameterDescription),
                                ),
                            ),
                    ),
                ),
            )
        }
        request.put("tools", definitions).put("tool_choice", "auto")
    }

    fun reply(
        json: JSONObject,
        tools: ConversationTools,
    ): CopilotToolReply {
        val choices = json.optJSONArray("choices") ?: fail()
        if (choices.length() != 1) fail()
        val choice = choices.optJSONObject(0) ?: fail()
        val message = choice.optJSONObject("message") ?: fail()
        if (message.optString("role") != "assistant" || !message.isNull("function_call")) fail()
        return when (choice.optString("finish_reason")) {
            "stop" -> {
                if (!message.isNull("tool_calls")) fail()
                val text = message.opt("content") as? String ?: fail()
                if (text.isBlank() || text.length > ConversationLimits.REPLY_CHARACTERS) fail()
                CopilotToolReply.Final(text)
            }
            "tool_calls" -> {
                if (!message.isNull("content") && message.opt("content") !is String) fail()
                if ((message.opt("content") as? String)?.length?.let { it > ConversationLimits.REPLY_CHARACTERS } ==
                    true
                ) {
                    fail()
                }
                val calls = message.optJSONArray("tool_calls") ?: fail()
                if (calls.length() > 1) fail(ConversationProblem.LIMIT)
                val call = calls.optJSONObject(0) ?: fail()
                val id = call.opt("id") as? String ?: fail()
                if (!id.matches(Regex("[A-Za-z0-9_-]{1,128}")) || call.optString("type") != "function") fail()
                val function = call.optJSONObject("function") ?: fail()
                val name = function.opt("name") as? String ?: fail()
                val definition = tools.tools.singleOrNull { it.definition.name == name }?.definition ?: fail()
                val arguments = function.opt("arguments") as? String ?: fail()
                if (arguments.length > 16_384) fail(ConversationProblem.LIMIT)
                val value =
                    try {
                        JsonReader(StringReader(arguments)).use { reader ->
                            reader.isLenient = false
                            reader.beginObject()
                            if (!reader.hasNext() ||
                                reader.nextName() != definition.parameter ||
                                reader.peek() != JsonToken.STRING
                            ) {
                                fail()
                            }
                            val value = reader.nextString()
                            // Reject extra and duplicate keys, nested values and trailing JSON.
                            if (reader.hasNext()) fail()
                            reader.endObject()
                            if (reader.peek() != JsonToken.END_DOCUMENT) fail()
                            value
                        }
                    } catch (error: ConversationException) {
                        throw error
                    } catch (_: Exception) {
                        fail()
                    }
                if (value.isBlank() ||
                    value.length > definition.maxArgumentCharacters ||
                    value.any(Char::isISOControl)
                ) {
                    fail()
                }
                // Rebuild only the fields required for the verified protocol, never forward arbitrary provider fields.
                val assistant =
                    JSONObject().put("role", "assistant").put("content", JSONObject.NULL).put(
                        "tool_calls",
                        JSONArray().put(
                            JSONObject().put("id", id).put("type", "function").put(
                                "function",
                                JSONObject().put("name", name).put("arguments", arguments),
                            ),
                        ),
                    )
                CopilotToolReply.Call(ConversationToolCall(id, name, value), assistant)
            }
            else -> fail()
        }
    }

    private fun fail(problem: ConversationProblem = ConversationProblem.PROVIDER): Nothing =
        throw ConversationException(problem)
}
