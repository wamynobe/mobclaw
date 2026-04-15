package com.wamynobe.mobclaw.provider

/**
 * OpenAI native provider via Chat Completions API.
 */
class OpenAiProvider(
    apiKey: String,
    model: String = "gpt-4o-mini",
    baseUrl: String = "https://api.openai.com/v1",
) : OpenAiCompatibleProvider(
    apiKey = apiKey,
    model = model,
    baseUrl = baseUrl,
)
