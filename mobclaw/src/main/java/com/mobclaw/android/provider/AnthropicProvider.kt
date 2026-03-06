package com.mobclaw.android.provider

import com.mobclaw.android.model.ChatMessage
import com.mobclaw.android.model.ChatResponse
import com.mobclaw.android.model.ToolCall
import com.mobclaw.android.model.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Anthropic provider using the Messages API.
 */
class AnthropicProvider(
    private val apiKey: String,
    private val model: String = "claude-3-5-sonnet-latest",
    private val baseUrl: String = "https://api.anthropic.com/v1",
    private val maxTokens: Int = 2048,
) : LlmProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>?,
        model: String?,
        temperature: Double,
    ): ChatResponse = withContext(Dispatchers.IO) {
        val effectiveModel = model ?: this@AnthropicProvider.model
        val url = "${baseUrl.trimEnd('/')}/messages"
        val systemText = messages
            .filter { it.role == "system" }
            .joinToString("\n") { it.content }

        val body = buildJsonObject {
            put("model", effectiveModel)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            if (systemText.isNotBlank()) put("system", systemText)
            put("messages", buildAnthropicMessages(messages))

            if (!tools.isNullOrEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.parameters)
                        })
                    }
                })
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from Anthropic API")

        if (!response.isSuccessful) {
            throw Exception("Anthropic API error ${response.code}: $responseBody")
        }

        parseAnthropicResponse(responseBody)
    }

    override fun supportsNativeTools(): Boolean = true

    private fun buildAnthropicMessages(messages: List<ChatMessage>) = buildJsonArray {
        messages.forEach { msg ->
            if (msg.role == "system") return@forEach
            val role = when (msg.role) {
                "assistant" -> "assistant"
                else -> "user"
            }
            add(buildJsonObject {
                put("role", role)
                put("content", msg.content)
            })
        }
    }

    private fun parseAnthropicResponse(responseBody: String): ChatResponse {
        val root = Json.parseToJsonElement(responseBody).jsonObject
        val content = root["content"]?.jsonArray.orEmpty()
        if (content.isEmpty()) {
            return ChatResponse(text = "No response generated")
        }

        val textParts = mutableListOf<String>()
        val toolCalls = mutableListOf<ToolCall>()

        content.forEach { block ->
            val obj = block.jsonObject
            when (obj["type"].asStringOrNull()) {
                "text" -> {
                    obj["text"].asStringOrNull()?.let { textParts.add(it) }
                }
                "tool_use" -> {
                    val id = obj["id"].asStringOrNull() ?: return@forEach
                    val name = obj["name"].asStringOrNull() ?: return@forEach
                    val input = obj["input"]?.jsonObject ?: JsonObject(emptyMap())
                    toolCalls.add(
                        ToolCall(
                            id = id,
                            name = name,
                            arguments = input.toString(),
                        )
                    )
                }
            }
        }

        return ChatResponse(
            text = textParts.joinToString("\n").ifBlank { null },
            toolCalls = toolCalls,
        )
    }

    private fun JsonElement?.asStringOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return try {
            primitive.content
        } catch (_: Exception) {
            null
        }
    }
}
