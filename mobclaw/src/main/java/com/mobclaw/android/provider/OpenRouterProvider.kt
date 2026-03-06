package com.mobclaw.android.provider

/**
 * OpenRouter provider via OpenAI-compatible API.
 */
class OpenRouterProvider(
    apiKey: String,
    model: String = "openai/gpt-4o-mini",
    baseUrl: String = "https://openrouter.ai/api/v1",
    appName: String? = null,
    appUrl: String? = null,
) : OpenAiCompatibleProvider(
    apiKey = apiKey,
    model = model,
    baseUrl = baseUrl,
    extraHeaders = buildMap {
        if (!appName.isNullOrBlank()) put("X-Title", appName)
        if (!appUrl.isNullOrBlank()) put("HTTP-Referer", appUrl)
    },
)
