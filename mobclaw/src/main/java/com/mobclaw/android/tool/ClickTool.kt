package com.mobclaw.android.tool

import com.mobclaw.android.accessibility.GestureEngine
import com.mobclaw.android.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Click a UI element by its node ID from ScreenState.
 */
class ClickTool : MobTool {

    override val name = "click"

    override val description = "Click on a UI element identified by its node ID from the screen state."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("node_id", buildJsonObject {
                put("type", "string")
                put("description", "The node ID (e.g. 'n3') from the screen state")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonPrimitive("node_id")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val nodeId = args["node_id"]?.let {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
        } ?: return ToolResult(false, "", "Missing required parameter: node_id")

        val success = GestureEngine.clickNode(nodeId)
        return if (success) {
            ToolResult(true, "Clicked node $nodeId successfully")
        } else {
            ToolResult(false, "", "Failed to click node $nodeId")
        }
    }
}
