package com.mobclaw.android.provider

import com.mobclaw.android.model.ChatMessage
import com.mobclaw.android.model.ToolSpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicProviderTest {

    @Test
    fun chat_parsesTextAndToolCallsAndBuildsBody() = runBlocking {
        val server = MockWebServer()
        server.start()

        try {
            val responseBody = """
                {
                  "content": [
                    {"type": "text", "text": "Ready"},
                    {
                      "type": "tool_use",
                      "id": "tc-1",
                      "name": "finish",
                      "input": {"reason": "done"}
                    }
                  ]
                }
            """.trimIndent()

            server.enqueue(MockResponse().setBody(responseBody))

            val provider = AnthropicProvider(
                apiKey = "anthropic-key",
                model = "claude-test",
                baseUrl = server.url("/").toString(),
            )

            val toolSpec = ToolSpec(
                name = "finish",
                description = "Finish task",
                parameters = buildJsonObject {
                    put("type", "object")
                },
            )

            val response = provider.chat(
                messages = listOf(
                    ChatMessage("system", "system directives"),
                    ChatMessage("assistant", "working"),
                    ChatMessage("user", "now finish"),
                ),
                tools = listOf(toolSpec),
                model = null,
                temperature = 0.3,
            )

            val request = server.takeRequest()
            val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

            assertEquals("POST", request.method)
            assertEquals("/messages", request.path)
            assertEquals("claude-test", payload["model"]!!.jsonPrimitive.content)
            assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
            assertEquals("anthropic-key", request.getHeader("x-api-key"))
            assertEquals("2048", payload["max_tokens"]!!.toString())

            val messages = payload["messages"]!!.jsonArray
            assertEquals(2, messages.size)
            assertEquals("assistant", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
            assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)

            val tools = payload["tools"]!!.jsonArray
            assertEquals(1, tools.size)
            assertEquals("finish", tools[0].jsonObject["name"]!!.jsonPrimitive.content)

            assertEquals("Ready", response.text)
            assertEquals(1, response.toolCalls.size)
            assertEquals("tc-1", response.toolCalls[0].id)
            assertEquals("finish", response.toolCalls[0].name)
            assertEquals("{\"reason\":\"done\"}", response.toolCalls[0].arguments)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun parseNoContentReturnsDefaultText() = runBlocking {
        val server = MockWebServer()
        server.start()

        try {
            server.enqueue(MockResponse().setBody("{}"))

            val provider = AnthropicProvider(
                apiKey = "anthropic-key",
                baseUrl = server.url("/").toString(),
            )

            val response = provider.chat(
                messages = listOf(ChatMessage("user", "")),
                tools = emptyList(),
                model = null,
                temperature = 0.5,
            )

            assertEquals("No response generated", response.text)
        } finally {
            server.shutdown()
        }
    }
}
