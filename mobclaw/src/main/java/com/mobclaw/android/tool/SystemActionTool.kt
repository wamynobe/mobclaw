package com.mobclaw.android.tool

import com.mobclaw.android.accessibility.GestureEngine
import com.mobclaw.android.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Perform a global system action: back, home, recents, notifications, etc.
 */
class SystemActionTool : MobTool {

    override val name = "system_action"

    override val description = "Perform a global system action: back, home, recents, notifications, quick_settings, power_dialog."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("action", buildJsonObject {
                put("type", "string")
                put("enum", kotlinx.serialization.json.JsonArray(listOf(
                    JsonPrimitive("back"), JsonPrimitive("home"),
                    JsonPrimitive("recents"), JsonPrimitive("notifications"),
                    JsonPrimitive("quick_settings"), JsonPrimitive("power_dialog")
                )))
                put("description", "The system action to perform")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            JsonPrimitive("action")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val action = (args["action"] as? JsonPrimitive)?.content
            ?: return ToolResult(false, "", "Missing required parameter: action")

        val success = GestureEngine.systemAction(action)
        return if (success) {
            ToolResult(true, "Performed system action: $action")
        } else {
            ToolResult(false, "", "Failed to perform system action: $action")
        }
    }
}
