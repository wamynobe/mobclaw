package com.mobclaw.android.tool

import com.mobclaw.android.accessibility.ScreenReader
import com.mobclaw.android.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Read the current screen state from the Accessibility Service.
 * Returns a text representation of all interactive elements for the LLM.
 */
class ScreenReadTool : MobTool {

    override val name = "screen_read"

    override val description = "Read the current screen state. Returns all visible interactive UI elements with their IDs, text, and properties. Call this after performing an action to see the updated screen."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        // No parameters needed
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val state = ScreenReader.read()
            ?: return ToolResult(false, "", "Accessibility service is not running. Enable MobClaw in Settings → Accessibility.")

        return ToolResult(true, state.toPromptText())
    }
}
