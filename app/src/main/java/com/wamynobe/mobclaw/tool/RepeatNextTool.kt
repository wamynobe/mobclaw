package com.wamynobe.mobclaw.tool

import com.wamynobe.mobclaw.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Advance to the next loop iteration.
 *
 * Called after completing one iteration of a repeating sub-task.
 * Reports the new iteration number and how many remain.
 */
class RepeatNextTool(private val loopContext: LoopContext) : MobTool {

    override val name = "repeat_next"

    override val description = "Advance to the next iteration of the current loop. " +
        "Call this after completing one iteration's actions. " +
        "Returns the new iteration number and remaining count."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        if (!loopContext.isActive) {
            return ToolResult(false, "", "No active loop. Call `repeat` first to start a loop.")
        }

        val advanced = loopContext.next()
        return if (advanced) {
            ToolResult(
                true,
                "Advanced to iteration ${loopContext.currentIteration}/${loopContext.totalCount} " +
                    "of \"${loopContext.description}\". " +
                    "Perform the actions for this iteration, then call `repeat_next` or `repeat_done`."
            )
        } else {
            loopContext.done()
            ToolResult(
                true,
                "All ${loopContext.totalCount} iterations of \"${loopContext.description}\" are complete. " +
                    "Loop finished. Continue with the remaining task or call `finish`."
            )
        }
    }
}
