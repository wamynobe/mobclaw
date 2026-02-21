package com.mobclaw.android.tool

import com.mobclaw.android.accessibility.GestureEngine
import com.mobclaw.android.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Type text into an editable field identified by node ID.
 */
class InputTextTool : MobTool {

    override val name = "input_text"

    override val description = "Type text into an editable field identified by its node ID."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("node_id", buildJsonObject {
                put("type", "string")
                put("description", "The node ID of the editable field (e.g. 'n5')")
            })
            put("text", buildJsonObject {
                put("type", "string")
                put("description", "The text to type")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            JsonPrimitive("node_id"), JsonPrimitive("text")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val nodeId = (args["node_id"] as? JsonPrimitive)?.content
            ?: return ToolResult(false, "", "Missing required parameter: node_id")
        val text = (args["text"] as? JsonPrimitive)?.content
            ?: return ToolResult(false, "", "Missing required parameter: text")

        val success = GestureEngine.inputText(nodeId, text)
        return if (success) {
            ToolResult(true, "Typed '$text' into node $nodeId")
        } else {
            ToolResult(false, "", "Failed to type into node $nodeId")
        }
    }
}
