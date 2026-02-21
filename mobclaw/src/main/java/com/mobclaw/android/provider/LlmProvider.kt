package com.mobclaw.android.provider

import com.mobclaw.android.model.ChatMessage
import com.mobclaw.android.model.ChatResponse
import com.mobclaw.android.model.ToolSpec

/**
 * LLM provider interface.
 *
 * Implementations: GeminiProvider, OpenAiProvider, OllamaProvider, etc.
 * Swap providers via MobAgentBuilder without changing agent logic.
 */
interface LlmProvider {

    /**
     * Send a multi-turn conversation with optional tool definitions.
     * When [tools] is non-null, the provider should use native function calling
     * if supported, or inject tool instructions into the system prompt as fallback.
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>? = null,
        model: String? = null,
        temperature: Double = 0.7,
    ): ChatResponse

    /**
     * Whether this provider supports native tool/function calling via API.
     * If false, tools are injected as text in the system prompt.
     */
    fun supportsNativeTools(): Boolean = false
}
