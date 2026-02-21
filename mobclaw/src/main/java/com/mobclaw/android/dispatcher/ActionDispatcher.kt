package com.mobclaw.android.dispatcher

import com.mobclaw.android.model.ChatResponse
import com.mobclaw.android.model.ConversationMessage
import com.mobclaw.android.model.MobAction
import com.mobclaw.android.model.ToolExecutionResult
import com.mobclaw.android.tool.MobTool

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
    fun toProviderMessages(history: List<ConversationMessage>): List<com.mobclaw.android.model.ChatMessage>

    /** Whether this dispatcher sends tool specs via the API (native) or via prompt (xml). */
    fun shouldSendToolSpecs(): Boolean
}
