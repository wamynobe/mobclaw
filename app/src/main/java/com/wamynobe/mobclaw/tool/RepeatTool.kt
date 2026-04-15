package com.wamynobe.mobclaw.tool

import com.wamynobe.mobclaw.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Declare a repeating sub-task.
 *
 * When the LLM needs to do the same set of actions multiple times (e.g., "send this message to 5 contacts"),
 * it calls this tool to declare the loop. After that, it performs one iteration of the task,
 * then calls `repeat_next` to advance, and `repeat_done` when finished.
 */
class RepeatTool(private val loopContext: LoopContext) : MobTool {

    override val name = "repeat"

    override val description = """Declare a repeating sub-task. Use when you need to perform the same set of actions multiple times (e.g., send a message to 5 contacts, like 3 posts). After calling this, perform the actions for iteration 1, then call `repeat_next` to advance to iteration 2, and so on. Call `repeat_done` when all iterations are complete or you want to stop early."""

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("count", buildJsonObject {
                put("type", "integer")
                put("description", "Number of times to repeat the sub-task")
            })
            put("description", buildJsonObject {
                put("type", "string")
                put("description", "Brief description of what you are repeating (e.g. 'sending message to each contact')")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            JsonPrimitive("count"), JsonPrimitive("description")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val count = (args["count"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: return ToolResult(false, "", "Missing or invalid parameter: count")
        val description = (args["description"] as? JsonPrimitive)?.content
            ?: return ToolResult(false, "", "Missing required parameter: description")

        if (count < 1) {
            return ToolResult(false, "", "Count must be at least 1")
        }
        if (count > 100) {
            return ToolResult(false, "", "Count must be at most 100")
        }

        loopContext.start(description, count)

        return ToolResult(
            true,
            "Loop started: \"$description\" — $count iterations. " +
                "You are now on iteration 1/$count. " +
                "Perform the actions for this iteration, then call `repeat_next` to advance."
        )
    }
}
