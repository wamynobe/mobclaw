package com.wamynobe.mobclaw.provider

import com.wamynobe.mobclaw.model.ChatMessage
import com.wamynobe.mobclaw.model.ChatResponse
import com.wamynobe.mobclaw.model.ToolCall
import com.wamynobe.mobclaw.model.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
 * Generic OpenAI-compatible chat provider.
 * Works with endpoints implementing /v1/chat/completions.
 */
open class OpenAiCompatibleProvider(
    private val apiKey: String? = null,
    private val model: String,
    private val baseUrl: String,
    private val extraHeaders: Map<String, String> = emptyMap(),
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
        val effectiveModel = model ?: this@OpenAiCompatibleProvider.model
        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        val body = buildJsonObject {
            put("model", effectiveModel)
            put("temperature", temperature)
            put("messages", buildMessages(messages))

            if (!tools.isNullOrEmpty()) {
                put("tools", buildOpenAiTools(tools))
                put("tool_choice", "auto")
            }
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
        extraHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from OpenAI-compatible API")

        if (!response.isSuccessful) {
            throw Exception("OpenAI-compatible API error ${response.code}: $responseBody")
        }

        parseResponse(responseBody)
    }

    override fun supportsNativeTools(): Boolean = true

    private fun buildMessages(messages: List<ChatMessage>) = buildJsonArray {
        messages.forEach { msg ->
            add(buildJsonObject {
                put("role", normalizeRole(msg.role))
                put("content", msg.content)
            })
        }
    }

    private fun buildOpenAiTools(tools: List<ToolSpec>): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.parameters)
                })
            })
        }
    }

    private fun parseResponse(responseBody: String): ChatResponse {
        val root = Json.parseToJsonElement(responseBody).jsonObject
        val choices = root["choices"]?.jsonArray.orEmpty()
        if (choices.isEmpty()) {
            return ChatResponse(text = "No response generated")
        }

        val message = choices.first().jsonObject["message"]?.jsonObject ?: JsonObject(emptyMap())
        val text = parseMessageText(message)
        val toolCalls = parseToolCalls(message)
        return ChatResponse(text = text, toolCalls = toolCalls)
    }

    private fun parseMessageText(message: JsonObject): String? {
        val content = message["content"] ?: return null
        return when (content) {
            is JsonPrimitive -> content.asStringOrNull()
            is JsonArray -> content.joinToString("\n") { part ->
                part.jsonObject["text"].asStringOrNull().orEmpty()
            }.ifBlank { null }
            else -> null
        }
    }

    private fun parseToolCalls(message: JsonObject): List<ToolCall> {
        val raw = message["tool_calls"]?.jsonArray ?: return emptyList()
        return raw.mapNotNull { item ->
            val obj = item.jsonObject
            val id = obj["id"].asStringOrNull() ?: return@mapNotNull null
            val function = obj["function"]?.jsonObject ?: return@mapNotNull null
            val name = function["name"].asStringOrNull() ?: return@mapNotNull null
            val arguments = when (val args = function["arguments"]) {
                null -> "{}"
                is JsonPrimitive -> args.asStringOrNull() ?: args.toString()
                else -> args.toString()
            }
            ToolCall(id = id, name = name, arguments = arguments)
        }
    }

    private fun JsonElement?.asStringOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return try {
            primitive.content
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeRole(role: String): String = when (role) {
        "system", "user", "assistant" -> role
        "tool" -> "user"
        else -> "user"
    }
}
