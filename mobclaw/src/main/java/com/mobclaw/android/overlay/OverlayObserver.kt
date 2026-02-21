package com.mobclaw.android.overlay

import com.mobclaw.android.model.ScreenState
import com.mobclaw.android.observer.MobObserver
import kotlin.time.Duration

/**
 * MobObserver implementation that feeds real-time LLM actions to the AgentOverlay.
 * Only shows tool calls from the LLM — not internal events like screen reads.
 * Resolves node IDs to their text from the current screen state.
 */
class OverlayObserver(private val overlay: AgentOverlay) : MobObserver {

    /** Current screen state used to resolve node IDs to text. */
    @Volatile
    var currentScreenState: ScreenState? = null

    /** Internal tools that should NOT be shown in the overlay. */
    private val hiddenTools = setOf("screen_read")

    override fun onAgentStart(task: String) {
        overlay.show()
        overlay.clearActions()
        overlay.updateStatus("▶ $task")
    }

    override fun onToolCall(toolName: String, duration: Duration, success: Boolean) {
        // Only mark completion for visible actions
        if (toolName !in hiddenTools) {
            overlay.markActionComplete(success)
        }
    }

    override fun onScreenRead(packageName: String, nodeCount: Int) {
        // Silent — don't clutter overlay with screen reads
    }

    override fun onAgentEnd(task: String, duration: Duration, success: Boolean) {
        val status = if (success) "✅ Done" else "❌ Failed"
        overlay.updateStatus("$status (${duration.inWholeSeconds}s)")
    }

    override fun onError(message: String, throwable: Throwable?) {
        overlay.updateStatus("❌ $message")
    }

    // --- Extended methods for overlay-specific events ---

    /**
     * Show LLM reasoning text (assistant text before tool calls).
     */
    fun onReasoning(text: String) {
        overlay.showReasoning(text)
    }

    /**
     * Show a pending LLM action, resolving node IDs to their screen text.
     * Filters out internal tools like screen_read.
     */
    fun onActionPending(toolName: String, args: Map<String, String>) {
        // Skip internal tools
        if (toolName in hiddenTools) return

        // Build a human-readable description with resolved node text
        val description = buildActionDescription(toolName, args)
        overlay.showAction(toolName, description, isPending = true)
    }

    /**
     * Build a human-friendly action description, resolving node IDs to their text.
     * e.g. click(n3) → click n3 "Wi-Fi"
     */
    private fun buildActionDescription(toolName: String, args: Map<String, String>): String {
        val nodeId = args["node_id"]
        val resolvedText = nodeId?.let { resolveNodeText(it) }

        return when (toolName) {
            "click", "long_click" -> {
                if (nodeId != null) {
                    val label = resolvedText ?: "?"
                    "$nodeId \"$label\""
                } else "?"
            }
            "input_text" -> {
                val text = args["text"] ?: ""
                val label = resolvedText ?: nodeId ?: "?"
                "$label ← \"$text\""
            }
            "tap" -> {
                val x = args["x"] ?: "?"
                val y = args["y"] ?: "?"
                "($x, $y)"
            }
            "scroll" -> args["direction"] ?: "?"
            "system_action" -> args["action"] ?: "?"
            "open_app" -> args["package_name"]?.substringAfterLast('.') ?: "?"
            "list_apps" -> args["filter"] ?: "all"
            "wait" -> "${args["milliseconds"] ?: "?"}ms"
            "finish" -> args["reason"]?.take(40) ?: "done"
            "fail" -> args["reason"]?.take(40) ?: "failed"
            else -> args.entries.joinToString(", ") { "${it.key}=${it.value}" }.take(50)
        }
    }

    /**
     * Resolve a node ID (e.g. "n3") to its visible text from the current screen state.
     */
    private fun resolveNodeText(nodeId: String): String? {
        val state = currentScreenState ?: return null
        val node = state.nodes.find { it.id == nodeId } ?: return null
        // Priority: text > contentDescription > resourceId
        return node.text?.take(30)
            ?: node.contentDescription?.take(30)
            ?: node.resourceId?.substringAfterLast('/')?.take(30)
    }
}
