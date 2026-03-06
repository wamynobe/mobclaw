package com.mobclaw.android.provider

import com.mobclaw.android.model.ChatMessage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiProviderTest {

    @Test
    fun chat_parsesResponseTextFromCandidates() = runBlocking {
        val server = MockWebServer()
        server.start()

        try {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {"text": "Hello"}
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )

            val provider = GeminiProvider(
                apiKey = "gemini-key",
                model = "gemini-test",
                baseUrl = server.url("/").toString(),
            )

            val response = provider.chat(
                messages = listOf(ChatMessage("user", "Hello")),
                tools = null,
                model = null,
                temperature = 0.5,
            )

            val request = server.takeRequest()
            assertTrue(request.path!!.contains("/models/gemini-test:generateContent"))
            assertEquals("Hello", response.text)
        } finally {
            server.shutdown()
        }
    }
}
