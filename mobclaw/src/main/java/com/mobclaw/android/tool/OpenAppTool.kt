package com.mobclaw.android.tool

import com.mobclaw.android.accessibility.MobClawAccessibilityService
import com.mobclaw.android.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Open an app by its package name using the system launcher intent.
 * Much more reliable than navigating the home screen to find and click an app icon.
 */
class OpenAppTool : MobTool {

    override val name = "open_app"

    override val description =
        "Open an app by its package name (e.g. 'com.android.settings'). Use `list_apps` first to find the package name if you don't know it."

    override fun parametersSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("package_name", buildJsonObject {
                put("type", "string")
                put("description", "The package name of the app (e.g. 'com.android.settings', 'com.android.chrome')")
            })
        })
        put("required", kotlinx.serialization.json.JsonArray(listOf(
            JsonPrimitive("package_name")
        )))
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val packageName = (args["package_name"] as? JsonPrimitive)?.content
            ?: return ToolResult(false, "", "Missing required parameter: package_name")

        val service = MobClawAccessibilityService.instance
            ?: return ToolResult(false, "", "Accessibility service not running")

        val context = service.applicationContext
        val pm = context.packageManager

        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return ToolResult(false, "", "App not found or not launchable: $packageName")

        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(launchIntent)
            ToolResult(true, "Opened app: $packageName. Wait for it to load, then read the screen.")
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to open $packageName: ${e.message}")
        }
    }
}
