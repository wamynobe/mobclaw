package com.mobclaw.android.tool

import com.mobclaw.android.accessibility.MobClawAccessibilityService
import com.mobclaw.android.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * List all launchable apps installed on the device.
 * Returns package names and app labels so the LLM can find the right app.
 */
class ListAppsTool : MobTool {

    override val name = "list_apps"

    override val description =
        "List all installed apps on the device. Returns app names and their package names. Use this to find the correct package name before opening an app."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("filter", buildJsonObject {
                put("type", "string")
                put(
                    "description",
                    "Optional keyword to filter apps by name (case-insensitive). Leave empty to list all apps."
                )
            })
        })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val service = MobClawAccessibilityService.instance
            ?: return ToolResult(false, "", "Accessibility service not running")

        val context = service.applicationContext
        val pm = context.packageManager
        val filter = (args["filter"] as? JsonPrimitive)?.content?.lowercase()

        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(intent, 0)
            .map { resolveInfo ->
                val label = resolveInfo.loadLabel(pm).toString()
                val pkg = resolveInfo.activityInfo.packageName
                label to pkg
            }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
            .let { list ->
                if (filter.isNullOrBlank()) list
                else list.filter {
                    it.first.lowercase().contains(filter) ||
                        it.second.lowercase().contains(filter)
                }
            }

        if (apps.isEmpty()) {
            return ToolResult(true, "No apps found matching filter '$filter'")
        }

        val output = buildString {
            appendLine("Found ${apps.size} apps:")
            for ((label, pkg) in apps) {
                appendLine("  $label → $pkg")
            }
        }
        return ToolResult(true, output)
    }
}
