package com.wamynobe.mobclaw.dispatcher

import com.wamynobe.mobclaw.model.ChatResponse
import com.wamynobe.mobclaw.model.ConversationMessage
import com.wamynobe.mobclaw.model.MobAction
import com.wamynobe.mobclaw.model.ToolExecutionResult
import com.wamynobe.mobclaw.tool.MobTool

/**
 * Parses LLM responses into executable actions and formats results.
 */
interface ActionDispatcher {

    /** Parse LLM response into text and a list of tool calls. */
    fun parseResponse(response: ChatResponse): Pair<String, List<MobAction>>

    /** Format tool execution results into a conversation message for history. */
    fun formatResults(results: List<ToolExecutionResult>): ConversationMessage

    /** Build prompt instructions describing available tools (for non-native providers). */
    fun promptInstructions(tools: List<MobTool>): String

    /** Convert conversation history to flat ChatMessage list for the provider. */
    fun toProviderMessages(history: List<ConversationMessage>): List<com.wamynobe.mobclaw.model.ChatMessage>

    /** Whether this dispatcher sends tool specs via the API (native) or via prompt (xml). */
    fun shouldSendToolSpecs(): Boolean
}
