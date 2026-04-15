package com.wamynobe.mobclaw.dispatcher

import com.wamynobe.mobclaw.model.ChatMessage
import com.wamynobe.mobclaw.model.ChatResponse
import com.wamynobe.mobclaw.model.ConversationMessage
import com.wamynobe.mobclaw.model.ToolCall
import com.wamynobe.mobclaw.model.ToolExecutionResult
import com.wamynobe.mobclaw.model.ToolResultMessage
import com.wamynobe.mobclaw.tool.MobTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class StubTool(
    override val name: String,
    override val description: String,
    private val schema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    },
) : MobTool {
    override fun parametersSchema(): JsonObject = schema

    override suspend fun execute(args: JsonObject): com.wamynobe.mobclaw.model.ToolResult {
        return com.wamynobe.mobclaw.model.ToolResult(true, args.toString())
    }
}

class JsonActionDispatcherTest {

    @Test
    fun parseResponse_withNativeToolCalls_returnsParsedActionsAndText() {
        val dispatcher = JsonActionDispatcher()
        val response = ChatResponse(
            text = "Action detected",
            toolCalls = listOf(
                ToolCall(
                    id = "tool-1",
                    name = "click",
                    arguments = "{\"node_id\":\"n1\",\"index\":5}",
                )
            ),
        )

        val (text, actions) = dispatcher.parseResponse(response)

        assertEquals("Action detected", text)
        assertEquals(1, actions.size)
        assertEquals("click", actions[0].name)
        assertEquals("tool-1", actions[0].toolCallId)
        assertEquals("n1", actions[0].arguments["node_id"]?.jsonPrimitive?.content)
        assertEquals("5", actions[0].arguments["index"]?.jsonPrimitive?.content)
    }

    @Test
    fun parseResponse_withMalformedNativeArguments_returnsEmptyArgumentObject() {
        val dispatcher = JsonActionDispatcher()
        val response = ChatResponse(
            text = "Action detected",
            toolCalls = listOf(
                ToolCall(
                    id = "tool-1",
                    name = "click",
                    arguments = "{invalid-json}",
                )
            ),
        )

        val (_, actions) = dispatcher.parseResponse(response)

        assertEquals(1, actions.size)
        assertEquals(0, actions[0].arguments.size)
    }

    @Test
    fun parseResponse_withXmlFallback_parsesMultipleCallsAndRetainsReasoningText() {
        val dispatcher = JsonActionDispatcher()
        val response = ChatResponse(
            text = "Start<tool_call>{\"name\":\"click\",\"arguments\":{\"node_id\":\"n1\"}}</tool_call>\\n" +
                "Then<tool_call>{\"name\":\"fail\",\"arguments\":{\"reason\":\"bad\"}}</tool_call>End",
        )

        val (text, actions) = dispatcher.parseResponse(response)

        val normalizedLines = text
            .replace("\\n", "\n")
            .lines()
            .filter { it.isNotBlank() }
        assertEquals(listOf("Start", "Then", "End"), normalizedLines)
        assertEquals(2, actions.size)
        assertEquals("click", actions[0].name)
        assertEquals("fail", actions[1].name)
        assertEquals("n1", actions[0].arguments["node_id"]?.jsonPrimitive?.content)
        assertEquals("bad", actions[1].arguments["reason"]?.jsonPrimitive?.content)
    }

    @Test
    fun parseResponse_withMalformedXmlCall_skipsCallAndKeepsSurroundingText() {
        val dispatcher = JsonActionDispatcher()
        val response = ChatResponse(
            text = "Before<tool_call>{invalid-json</tool_call>After",
        )

        val (text, actions) = dispatcher.parseResponse(response)

        assertEquals("Before\nAfter", text)
        assertEquals(0, actions.size)
    }

    @Test
    fun toProviderMessages_mapsConversationTypes() {
        val dispatcher = JsonActionDispatcher()
        val messages = listOf(
            ConversationMessage.Chat(ChatMessage.user("User says hi")),
            ConversationMessage.AssistantToolCalls(
                text = "Used tools",
                toolCalls = listOf(ToolCall("tool-1", "click", "{}")),
            ),
            ConversationMessage.ToolResults(
                results = listOf(
                    ToolResultMessage("tool-1", "Clicked"),
                    ToolResultMessage("tool-2", "Done"),
                ),
            ),
        )

        val flattened = dispatcher.toProviderMessages(messages)

        assertEquals(3, flattened.size)
        assertEquals("user", flattened[0].role)
        assertEquals("User says hi", flattened[0].content)
        assertEquals("assistant", flattened[1].role)
        assertEquals("Used tools", flattened[1].content)
        assertEquals("user", flattened[2].role)
        assertEquals(
            "[Tool results]\n<tool_result id=\"tool-1\">\nClicked\n</tool_result>\n<tool_result id=\"tool-2\">\nDone\n</tool_result>",
            flattened[2].content,
        )
    }

    @Test
    fun formatResults_and_promptInstructions_coverOutput() {
        val dispatcher = JsonActionDispatcher()
        val tool = StubTool(
            name = "click",
            description = "Tap a node",
        )

        val instructions = dispatcher.promptInstructions(listOf(tool))
        assertTrue(instructions.contains("- **click**: Tap a node"))
        assertTrue(instructions.contains("Parameters:"))

        val formatted = dispatcher.formatResults(
            listOf(
                ToolExecutionResult("click", "Clicked", true, "t1"),
                ToolExecutionResult("fail", "Failed", false),
            )
        )

        val results = (formatted as ConversationMessage.ToolResults).results
        assertEquals(2, results.size)
        assertEquals("t1", results[0].toolCallId)
        assertEquals("unknown", results[1].toolCallId)
        assertFalse(dispatcher.shouldSendToolSpecs())
    }
}
