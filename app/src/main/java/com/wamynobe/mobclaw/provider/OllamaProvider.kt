package com.wamynobe.mobclaw.provider

/**
 * Ollama provider via OpenAI-compatible endpoint.
 * Works for local and remote Ollama servers.
 */
class OllamaProvider(
    model: String = "llama3.2",
    baseUrl: String = "http://127.0.0.1:11434/v1",
    apiKey: String? = null,
) : OpenAiCompatibleProvider(
    apiKey = apiKey,
    model = model,
    baseUrl = baseUrl,
)
