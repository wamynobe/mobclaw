package com.wamynobe.mobclaw.provider

import com.wamynobe.mobclaw.model.ChatMessage
import com.wamynobe.mobclaw.model.ChatResponse
import com.wamynobe.mobclaw.model.ToolCall
import com.wamynobe.mobclaw.model.ToolSpec
import com.mobmock.MobMock

class MobMockProvider(
    private val mobMock: MobMock,
    private val defaultModel: String = "gpt-5"
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
                    "parameters" to it.parameters
                )
            )
        }

        val result = mobMock.chatCompletion(
            model = model ?: defaultModel,
            messages = mappedMessages,
            tools = mappedTools
        )

        if (result.errorMessage != null) {
            throw RuntimeException("MobMock API Error: ${result.errorMessage}")
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
            textBuilder.append("🤔 ${result.reasoningSummary ?: "Thinking"}\\n")
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
}
