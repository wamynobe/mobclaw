package com.mobclaw.android.tool

import com.mobclaw.android.accessibility.GestureEngine
import com.mobclaw.android.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Long-click a UI element by node ID.
 * Useful for context menus, drag operations, edit modes.
 */
class LongClickTool : MobTool {

    override val name = "long_click"

    override val description = "Long-click (press and hold) on a UI element. Used for context menus, text selection, drag operations, or edit modes."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("node_id", buildJsonObject {
                put("type", "string")
                put("description", "The node ID (e.g. 'n3') from the screen state")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            JsonPrimitive("node_id")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val nodeId = (args["node_id"] as? JsonPrimitive)?.content
            ?: return ToolResult(false, "", "Missing required parameter: node_id")

        val success = GestureEngine.longClickNode(nodeId)
        return if (success) {
            ToolResult(true, "Long-clicked node $nodeId successfully")
        } else {
            ToolResult(false, "", "Failed to long-click node $nodeId")
        }
    }
}
