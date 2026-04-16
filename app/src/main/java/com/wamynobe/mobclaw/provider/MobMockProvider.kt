package com.wamynobe.mobclaw.provider

import android.util.Log
import com.wamynobe.mobclaw.model.ChatMessage
import com.wamynobe.mobclaw.model.ChatResponse
import com.wamynobe.mobclaw.model.ToolCall
import com.wamynobe.mobclaw.model.ToolSpec
import com.mobmock.MobMock
import kotlinx.serialization.json.*

class MobMockProvider(
    private val mobMock: MobMock,
    private val defaultModel: String = "gpt-5.4"
) : LlmProvider {

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>?,
        model: String?,
        temperature: Double
    ): ChatResponse {
        val mappedMessages = messages.map {
            mapOf("role" to it.role, "content" to it.content)
        }

        val mappedTools = tools?.map {
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to it.name,
                    "description" to it.description,
                    "parameters" to convertJsonElementToAny(it.parameters)
                )
            )
        }

        val result = mobMock.chatCompletion(
            model = model ?: defaultModel,
            messages = mappedMessages,
            tools = mappedTools
        )

        if (result.errorMessage != null) {
            Log.e("MobMockProvider", "MobMock API Error: ${result.errorMessage}")
            throw RuntimeException("MobMock API Error: ${result.errorMessage}")
            //log
        }

        val toolCalls = result.toolCalls.map {
            ToolCall(
                id = it.id,
                name = it.name,
                arguments = it.arguments
            )
        }

        // Construct the full text from summary and content if available
        val textBuilder = StringBuilder()
        if (result.reasoningText != null) {
            textBuilder.append("[Reasoning] ${result.reasoningSummary ?: "Thinking"}\\n")
            textBuilder.appendLine(result.reasoningText)
            textBuilder.appendLine("---")
        }
        if (result.content != null) {
            textBuilder.append(result.content)
        }
        
        return ChatResponse(
            text = if (textBuilder.isEmpty()) null else textBuilder.toString(),
            toolCalls = toolCalls
        )
    }

    override fun supportsNativeTools(): Boolean = true

    private fun convertJsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> {
                if (element.isString) element.content
                else element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.content
            }
            is JsonArray -> element.map { convertJsonElementToAny(it) }
            is JsonObject -> element.mapValues { convertJsonElementToAny(it.value) }
        }
    }
}
