package com.mobclaw.android.tool

import com.mobclaw.android.model.ToolResult
import com.mobclaw.android.model.ToolSpec
import kotlinx.serialization.json.JsonObject

/**
 * Core tool interface — implement for any agent capability.
 *
 * Each Android action (click, scroll, type, etc.) is a separate MobTool implementation.
 * The LLM sees the tool specs and decides which to call at each turn.
 */
interface MobTool {

    /** Tool name used in LLM function calling. */
    val name: String

    /** Human-readable description for the LLM. */
    val description: String

    /** JSON schema for the tool's parameters. */
    fun parametersSchema(): JsonObject

    /** Execute the tool with the given arguments. */
    suspend fun execute(args: JsonObject): ToolResult

    /** Get the full spec for LLM registration. */
    fun spec(): ToolSpec = ToolSpec(name, description, parametersSchema())
}
