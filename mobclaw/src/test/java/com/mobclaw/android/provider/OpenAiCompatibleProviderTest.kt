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

class OpenAiCompatibleProviderTest {

    @Test
    fun chat_buildsRequestAndParsesTextAndToolCalls() = runBlocking {
        val server = MockWebServer()
        server.start()

        try {
            val responseBody = """
            {
              "choices": [
                {
                  "message": {
                    "content": "Done",
                    "tool_calls": [
                      {
                        "id": "tc-1",
                        "function": {
                          "name": "click",
                          "arguments": "{\\\"node_id\\\":\\\"n1\\\"}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()

            server.enqueue(MockResponse().setBody(responseBody))

            val provider = OpenAiCompatibleProvider(
                apiKey = "sk-test",
                model = "test-model",
                baseUrl = server.url("/").toString(),
            )

            val response = provider.chat(
                messages = listOf(
                    ChatMessage("system", "System directive"),
                    ChatMessage("assistant", "Working"),
                    ChatMessage("tool", "tool result"),
                    ChatMessage("user", "Now do action"),
                ),
                tools = listOf(
                    ToolSpec(
                        name = "click",
                        description = "Tap element",
                        parameters = buildJsonObject {
                            put("type", "object")
                        },
                    ),
                ),
                model = "custom-model",
                temperature = 0.2,
            )

            val request = server.takeRequest()
            val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            val sentMessages = payload["messages"]!!.jsonArray

            assertEquals("POST", request.method)
            assertEquals("/chat/completions", request.path)
            assertEquals("custom-model", payload["model"]!!.jsonPrimitive.content)
            assertEquals(0.2, payload["temperature"]!!.jsonPrimitive.content.toDouble(), 0.0)
            assertEquals("Bearer sk-test", request.getHeader("Authorization"))

            assertEquals("system", sentMessages[0].jsonObject["role"]!!.jsonPrimitive.content)
            assertEquals("assistant", sentMessages[1].jsonObject["role"]!!.jsonPrimitive.content)
            assertEquals("user", sentMessages[2].jsonObject["role"]!!.jsonPrimitive.content)
            assertEquals("user", sentMessages[3].jsonObject["role"]!!.jsonPrimitive.content)

            val sentTools = payload["tools"]!!.jsonArray
            assertEquals(1, sentTools.size)
            val tool = sentTools[0].jsonObject["function"]!!.jsonObject
            assertEquals("function", sentTools[0].jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("click", tool["name"]!!.jsonPrimitive.content)

            assertEquals("Done", response.text)
            assertEquals(1, response.toolCalls.size)
            assertEquals("tc-1", response.toolCalls[0].id)
            assertEquals("click", response.toolCalls[0].name)
            assertTrue(response.toolCalls[0].arguments.contains("node_id"))
            assertTrue(response.toolCalls[0].arguments.contains("n1"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun chat_parsesArrayMessageContentAsJoinedText() = runBlocking {
        val server = MockWebServer()
        server.start()

        try {
            val responseBody = """
            {
              "choices": [
                {
                  "message": {
                    "content": [
                      {"text": "first"},
                      {"text": "second"}
                    ],
                    "tool_calls": []
                  }
                }
              ]
            }
            """.trimIndent()

            server.enqueue(MockResponse().setBody(responseBody))

            val provider = OpenAiCompatibleProvider(
                apiKey = null,
                model = "test-model",
                baseUrl = server.url("/").toString(),
            )

            val response = provider.chat(
                messages = listOf(ChatMessage("user", "Hello")),
                tools = null,
                model = null,
                temperature = 0.7,
            )

            assertEquals("first\nsecond", response.text)
            assertTrue(response.toolCalls.isEmpty())
        } finally {
            server.shutdown()
        }
    }
}
