package com.mobclaw.android.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A single message in an LLM conversation.
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
) {
    companion object {
        fun system(content: String) = ChatMessage("system", content)
        fun user(content: String) = ChatMessage("user", content)
        fun assistant(content: String) = ChatMessage("assistant", content)
        fun tool(content: String) = ChatMessage("tool", content)
    }
}

/**
 * Tool call requested by the LLM.
 */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

/**
 * LLM response containing text and/or tool calls.
 */
data class ChatResponse(
    val text: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
) {
    fun hasToolCalls(): Boolean = toolCalls.isNotEmpty()
    fun textOrEmpty(): String = text.orEmpty()
}

/**
 * Description of a tool for LLM function calling.
 */
@Serializable
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/**
 * Result of a tool execution.
 */
data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
)

/**
 * Parsed tool call from LLM response, ready for execution.
 */
data class MobAction(
    val name: String,
    val arguments: JsonObject,
    val toolCallId: String? = null,
)

/**
 * Result of executing a MobAction.
 */
data class ToolExecutionResult(
    val name: String,
    val output: String,
    val success: Boolean,
    val toolCallId: String? = null,
)

/**
 * A message in a multi-turn conversation including tool interactions.
 */
sealed class ConversationMessage {
    data class Chat(val message: ChatMessage) : ConversationMessage()
    data class AssistantToolCalls(val text: String?, val toolCalls: List<ToolCall>) : ConversationMessage()
    data class ToolResults(val results: List<ToolResultMessage>) : ConversationMessage()
}

@Serializable
data class ToolResultMessage(
    val toolCallId: String,
    val content: String,
)
