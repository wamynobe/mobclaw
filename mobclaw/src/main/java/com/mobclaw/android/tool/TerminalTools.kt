package com.mobclaw.android.tool

import com.mobclaw.android.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Terminal tool: signals that the agent has completed the user's task.
 */
class FinishTool : MobTool {

    override val name = "finish"

    override val description = "Signal that ALL parts of the user's task have been completed successfully. " +
        "IMPORTANT: Only call this AFTER you have completed EVERY step the user requested and verified the results. " +
        "If the user asked for multiple things (e.g. 'do X and then Y'), you must have done BOTH X and Y before calling finish."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("reason", buildJsonObject {
                put("type", "string")
                put("description", "Explanation of what was accomplished")
            })
            put("result", buildJsonObject {
                put("type", "string")
                put("description", "Optional result text to return to the user")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            JsonPrimitive("reason")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val reason = (args["reason"] as? JsonPrimitive)?.content ?: "Task completed"
        val result = (args["result"] as? JsonPrimitive)?.content

        val output = buildString {
            append("TASK_COMPLETE: $reason")
            if (result != null) append("\nResult: $result")
        }
        return ToolResult(true, output)
    }
}

/**
 * Terminal tool: signals that the agent cannot complete the task.
 */
class FailTool : MobTool {

    override val name = "fail"

    override val description = "Signal that the task cannot be completed. Provide a reason explaining why."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("reason", buildJsonObject {
                put("type", "string")
                put("description", "Explanation of why the task failed")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            JsonPrimitive("reason")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val reason = (args["reason"] as? JsonPrimitive)?.content ?: "Unknown failure"
        return ToolResult(false, "TASK_FAILED: $reason", reason)
    }
}
