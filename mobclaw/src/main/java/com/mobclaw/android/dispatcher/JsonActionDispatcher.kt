package com.mobclaw.android.dispatcher

import com.mobclaw.android.model.*
import com.mobclaw.android.tool.MobTool
import kotlinx.serialization.json.*

/**
 * Dispatcher that parses JSON tool calls from LLM responses.
 * Supports both native function calling and XML-style fallback.
 */
class JsonActionDispatcher : ActionDispatcher {

    override fun parseResponse(response: ChatResponse): Pair<String, List<MobAction>> {
        val text = response.textOrEmpty()

        // If response has native tool calls, use them directly
        if (response.hasToolCalls()) {
            val actions = response.toolCalls.map { tc ->
                val args = try {
                    Json.parseToJsonElement(tc.arguments).jsonObject
                } catch (e: Exception) {
                    buildJsonObject {}
                }
                MobAction(
                    name = tc.name,
                    arguments = args,
                    toolCallId = tc.id,
                )
            }
            return Pair(text, actions)
        }

        // Fallback: try to parse <tool_call> XML tags from text
        val calls = mutableListOf<MobAction>()
        val textParts = mutableListOf<String>()
        var remaining = text

        while (true) {
            val start = remaining.indexOf("<tool_call>")
            if (start == -1) break

            val before = remaining.substring(0, start).trim()
            if (before.isNotEmpty()) textParts.add(before)

            val end = remaining.indexOf("</tool_call>", start)
            if (end == -1) break

            val inner = remaining.substring(start + 11, end).trim()
            try {
                val parsed = Json.parseToJsonElement(inner).jsonObject
                val name = parsed["name"]?.jsonPrimitive?.content ?: continue
                val arguments = parsed["arguments"]?.jsonObject ?: buildJsonObject {}
                calls.add(MobAction(name = name, arguments = arguments))
            } catch (e: Exception) {
                // Malformed tool call, skip
            }

            remaining = remaining.substring(end + 12)
        }

        val after = remaining.trim()
        if (after.isNotEmpty()) textParts.add(after)

        return Pair(textParts.joinToString("\n"), calls)
    }

    override fun formatResults(results: List<ToolExecutionResult>): ConversationMessage {
        val messages = results.map { result ->
            ToolResultMessage(
                toolCallId = result.toolCallId ?: "unknown",
                content = result.output,
            )
        }
        return ConversationMessage.ToolResults(messages)
    }

    override fun promptInstructions(tools: List<MobTool>): String = buildString {
        appendLine("## Tool Use Protocol")
        appendLine()
        appendLine("To use a tool, wrap a JSON object in <tool_call></tool_call> tags:")
        appendLine()
        appendLine("<tool_call>")
        appendLine("""{"name": "tool_name", "arguments": {"param": "value"}}""")
        appendLine("</tool_call>")
        appendLine()
        appendLine("You may use multiple tool calls in a single response.")
        appendLine("After tool execution, results appear in <tool_result> tags.")
        appendLine("Continue reasoning with the results until the task is complete.")
        appendLine()
        appendLine("### Available Tools")
        appendLine()
        for (tool in tools) {
            appendLine("- **${tool.name}**: ${tool.description}")
            appendLine("  Parameters: `${tool.parametersSchema()}`")
        }
    }

    override fun toProviderMessages(history: List<ConversationMessage>): List<ChatMessage> {
        return history.flatMap { msg ->
            when (msg) {
                is ConversationMessage.Chat -> listOf(msg.message)
                is ConversationMessage.AssistantToolCalls -> {
                    listOf(ChatMessage.assistant(msg.text.orEmpty()))
                }
                is ConversationMessage.ToolResults -> {
                    val content = msg.results.joinToString("\n") { result ->
                        "<tool_result id=\"${result.toolCallId}\">\n${result.content}\n</tool_result>"
                    }
                    listOf(ChatMessage.user("[Tool results]\n$content"))
                }
            }
        }
    }

    override fun shouldSendToolSpecs(): Boolean = false
}
