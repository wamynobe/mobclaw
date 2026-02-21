package com.mobclaw.android.tool

import com.mobclaw.android.accessibility.GestureEngine
import com.mobclaw.android.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Tap at specific screen coordinates. Fallback when node-based click isn't possible.
 */
class TapTool : MobTool {

    override val name = "tap"

    override val description = "Tap at specific screen coordinates (x, y). Use when you need to interact with elements not identifiable by node ID."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("x", buildJsonObject {
                put("type", "number")
                put("description", "X coordinate in screen pixels")
            })
            put("y", buildJsonObject {
                put("type", "number")
                put("description", "Y coordinate in screen pixels")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            JsonPrimitive("x"), JsonPrimitive("y")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val x = (args["x"] as? JsonPrimitive)?.content?.toFloatOrNull()
            ?: return ToolResult(false, "", "Missing or invalid parameter: x")
        val y = (args["y"] as? JsonPrimitive)?.content?.toFloatOrNull()
            ?: return ToolResult(false, "", "Missing or invalid parameter: y")

        val success = GestureEngine.tap(x, y)
        return if (success) {
            ToolResult(true, "Tapped at ($x, $y) successfully")
        } else {
            ToolResult(false, "", "Failed to tap at ($x, $y)")
        }
    }
}
