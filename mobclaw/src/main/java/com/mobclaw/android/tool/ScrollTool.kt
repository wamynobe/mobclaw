package com.mobclaw.android.tool

import com.mobclaw.android.accessibility.GestureEngine
import com.mobclaw.android.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Scroll the screen in a given direction (up, down, left, right).
 */
class ScrollTool : MobTool {

    override val name = "scroll"

    override val description = "Scroll the screen in a direction: up, down, left, or right."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("direction", buildJsonObject {
                put("type", "string")
                put("enum", kotlinx.serialization.json.JsonArray(listOf(
                    JsonPrimitive("up"), JsonPrimitive("down"),
                    JsonPrimitive("left"), JsonPrimitive("right")
                )))
                put("description", "Direction to scroll")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            JsonPrimitive("direction")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val direction = (args["direction"] as? JsonPrimitive)?.content
            ?: return ToolResult(false, "", "Missing required parameter: direction")

        val success = GestureEngine.scroll(direction)
        return if (success) {
            ToolResult(true, "Scrolled $direction successfully")
        } else {
            ToolResult(false, "", "Failed to scroll $direction")
        }
    }
}
