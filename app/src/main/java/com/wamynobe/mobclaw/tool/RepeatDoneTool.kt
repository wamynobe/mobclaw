package com.wamynobe.mobclaw.tool

import com.wamynobe.mobclaw.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * End the current loop, either normally or early.
 *
 * Called when all iterations are done or the LLM decides to stop early
 * (e.g., a condition is met, or an error makes continuing pointless).
 */
class RepeatDoneTool(private val loopContext: LoopContext) : MobTool {

    override val name = "repeat_done"

    override val description = "End the current loop. Call this when all iterations are complete, " +
        "or when you want to stop early (e.g., task completed before expected iterations)."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("reason", buildJsonObject {
                put("type", "string")
                put("description", "Optional reason for ending the loop (e.g. 'all contacts messaged' or 'stopped early — contact not found')")
            })
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        if (!loopContext.isActive) {
            return ToolResult(true, "No active loop to end.")
        }

        val reason = (args["reason"] as? JsonPrimitive)?.content
        val completed = loopContext.currentIteration
        val total = loopContext.totalCount
        loopContext.done()

        val output = buildString {
            append("Loop ended: completed $completed/$total iterations of \"${loopContext.description}\"")
            if (reason != null) append(". Reason: $reason")
            append(". Continue with the remaining task or call `finish`.")
        }

        return ToolResult(true, output)
    }
}
