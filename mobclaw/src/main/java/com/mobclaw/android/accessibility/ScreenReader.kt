package com.mobclaw.android.accessibility

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.mobclaw.android.model.ScreenNode
import com.mobclaw.android.model.ScreenState

/**
 * Traverses the AccessibilityNodeInfo tree and converts it into a rich
 * ScreenState for LLM consumption. Extracts all useful properties from
 * each node to give the LLM maximum context for decision-making.
 */
object ScreenReader {

    /**
     * Read the current screen state from the accessibility service.
     * Returns null if the service is not running.
     */
    fun read(): ScreenState? {
        val service = MobClawAccessibilityService.instance ?: return null
        val root = service.getRootNode() ?: return null

        val packageName = root.packageName?.toString() ?: "unknown"
        val nodes = mutableListOf<ScreenNode>()
        var idCounter = 0

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            // Extract visibility first
            val isVisible = node.isVisibleToUser

            // Include node if it's interactive, has text, or has a resource ID
            val isInteractive = node.isClickable || node.isLongClickable ||
                node.isScrollable || node.isEditable || node.isCheckable ||
                node.isFocusable
            val hasText = !node.text.isNullOrBlank() ||
                !node.contentDescription.isNullOrBlank()
            val hasResourceId = !node.viewIdResourceName.isNullOrBlank()

            if (isVisible && (isInteractive || hasText || hasResourceId)) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                // Skip zero-size elements
                if (bounds.width() > 0 && bounds.height() > 0) {
                    val hintText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        node.hintText?.toString()
                    } else null

                    val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        node.stateDescription?.toString()
                    } else null

                    nodes.add(
                        ScreenNode(
                            id = "n${idCounter++}",
                            className = node.className?.toString()
                                ?.substringAfterLast('.') ?: "View",
                            resourceId = node.viewIdResourceName,
                            text = node.text?.toString(),
                            contentDescription = node.contentDescription?.toString(),
                            hintText = hintText,
                            stateDescription = stateDesc,
                            bounds = bounds,
                            isClickable = node.isClickable,
                            isLongClickable = node.isLongClickable,
                            isScrollable = node.isScrollable,
                            isEditable = node.isEditable,
                            isCheckable = node.isCheckable,
                            isChecked = node.isChecked,
                            isSelected = node.isSelected,
                            isFocused = node.isFocused,
                            isEnabled = node.isEnabled,
                            isVisibleToUser = isVisible,
                            depth = depth,
                            childCount = node.childCount,
                        )
                    )
                }
            }

            // Recurse into children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
                child.recycle()
            }
        }

        try {
            traverse(root, 0)
        } finally {
            root.recycle()
        }

        return ScreenState(
            packageName = packageName,
            activityName = null,
            nodes = nodes,
        )
    }
}
