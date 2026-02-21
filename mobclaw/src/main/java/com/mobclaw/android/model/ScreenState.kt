package com.mobclaw.android.model

import android.graphics.Rect

/**
 * Represents a UI element on screen, extracted from AccessibilityNodeInfo.
 * Captures all properties useful for LLM decision-making.
 */
data class ScreenNode(
    val id: String,
    val className: String,
    val resourceId: String?,
    val text: String?,
    val contentDescription: String?,
    val hintText: String?,
    val stateDescription: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isLongClickable: Boolean,
    val isScrollable: Boolean,
    val isEditable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isSelected: Boolean,
    val isFocused: Boolean,
    val isEnabled: Boolean,
    val isVisibleToUser: Boolean,
    val depth: Int,
    val childCount: Int,
)

/**
 * A snapshot of the current screen state.
 * Sent to the LLM as context so it can decide which tool to call.
 */
data class ScreenState(
    val packageName: String,
    val activityName: String?,
    val nodes: List<ScreenNode>,
) {
    /**
     * Serialize to a structured text representation for the LLM context window.
     * Uses a compact but readable format to minimize tokens while maximizing info.
     */
    fun toPromptText(): String = buildString {
        appendLine("## Current Screen: $packageName")
        activityName?.let { appendLine("Activity: $it") }
        appendLine("Found ${nodes.size} UI elements:")
        appendLine()

        for (node in nodes) {
            // Skip invisible nodes
            if (!node.isVisibleToUser) continue

            // Primary identification line
            val indent = "  ".repeat(node.depth.coerceAtMost(4))
            append("$indent[${node.id}] ${node.className}")

            // Resource ID is VERY useful — e.g. "com.android.settings:id/wifi_switch"
            node.resourceId?.let { append(" (${it.substringAfter(":")}") }

            appendLine()

            // Text content
            if (!node.text.isNullOrBlank()) {
                appendLine("$indent  text: \"${node.text}\"")
            }
            if (!node.contentDescription.isNullOrBlank()) {
                appendLine("$indent  desc: \"${node.contentDescription}\"")
            }
            if (!node.hintText.isNullOrBlank()) {
                appendLine("$indent  hint: \"${node.hintText}\"")
            }
            if (!node.stateDescription.isNullOrBlank()) {
                appendLine("$indent  state: \"${node.stateDescription}\"")
            }

            // Interactive properties
            val props = mutableListOf<String>()
            if (node.isClickable) props.add("clickable")
            if (node.isLongClickable) props.add("long-clickable")
            if (node.isScrollable) props.add("scrollable")
            if (node.isEditable) props.add("editable")
            if (node.isCheckable) {
                props.add(if (node.isChecked) "☑ checked" else "☐ unchecked")
            }
            if (node.isSelected) props.add("selected")
            if (node.isFocused) props.add("focused")
            if (!node.isEnabled) props.add("DISABLED")

            if (props.isNotEmpty()) {
                appendLine("$indent  [${props.joinToString(", ")}]")
            }

            // Bounds for coordinate reference
            val b = node.bounds
            appendLine("$indent  bounds: (${b.left},${b.top})-(${b.right},${b.bottom})")
        }
    }
}
