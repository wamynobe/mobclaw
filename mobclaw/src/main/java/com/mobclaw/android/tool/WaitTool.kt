package com.mobclaw.android.tool

import com.mobclaw.android.model.ToolResult
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Wait for a specified duration to allow UI transitions to complete.
 */
class WaitTool : MobTool {

    override val name = "wait"

    override val description = "Wait for a specified number of milliseconds to allow UI transitions, loading, or animations to complete before reading the screen again."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("milliseconds", buildJsonObject {
                put("type", "integer")
                put("description", "Duration to wait in milliseconds (100-5000)")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            JsonPrimitive("milliseconds")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val ms = (args["milliseconds"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: return ToolResult(false, "", "Missing or invalid parameter: milliseconds")

        val clamped = ms.coerceIn(100, 5000)
        delay(clamped)
        return ToolResult(true, "Waited ${clamped}ms")
    }
}
