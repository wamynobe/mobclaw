package com.mobclaw.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Translates logical agent actions into Android gestures using
 * AccessibilityService.dispatchGesture().
 */
object GestureEngine {

    private val service: MobClawAccessibilityService?
        get() = MobClawAccessibilityService.instance

    /**
     * Last known screen state — used as fallback for bounds-based clicking
     * when the accessibility tree has changed since the last read.
     */
    @Volatile
    var lastScreenState: com.mobclaw.android.model.ScreenState? = null

    /**
     * Click on a node identified by its ScreenNode id.
     * Strategy: tree lookup → native click → bounds tap → saved bounds tap.
     */
    suspend fun clickNode(nodeId: String): Boolean {
        val service = service ?: return false
        val root = service.getRootNode() ?: return false

        var idCounter = 0
        val target = findNodeById(root, nodeId, { idCounter++ }) 
        if (target != null) {
            if (target.isClickable) {
                val result = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                target.recycle()
                root.recycle()
                return result
            }
            val bounds = Rect()
            target.getBoundsInScreen(bounds)
            target.recycle()
            root.recycle()
            return tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }

        root.recycle()

        // Fallback: use saved bounds from last ScreenState
        return tapSavedBounds(nodeId)
    }

    /**
     * Long-click on a node identified by its ScreenNode id.
     * Falls back to saved bounds if tree has changed.
     */
    suspend fun longClickNode(nodeId: String): Boolean {
        val service = service ?: return false
        val root = service.getRootNode() ?: return false

        var idCounter = 0
        val target = findNodeById(root, nodeId, { idCounter++ })
        if (target != null) {
            if (target.isLongClickable) {
                val result = target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                target.recycle()
                root.recycle()
                return result
            }
            val bounds = Rect()
            target.getBoundsInScreen(bounds)
            target.recycle()
            root.recycle()
            return longPress(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }

        root.recycle()

        // Fallback: use saved bounds from last ScreenState
        return longPressSavedBounds(nodeId)
    }

    /**
     * Fallback: tap center of saved bounds from last screen read.
     */
    private suspend fun tapSavedBounds(nodeId: String): Boolean {
        val node = lastScreenState?.nodes?.find { it.id == nodeId } ?: return false
        val b = node.bounds
        return tap(b.centerX().toFloat(), b.centerY().toFloat())
    }

    /**
     * Fallback: long-press center of saved bounds from last screen read.
     */
    private suspend fun longPressSavedBounds(nodeId: String): Boolean {
        val node = lastScreenState?.nodes?.find { it.id == nodeId } ?: return false
        val b = node.bounds
        return longPress(b.centerX().toFloat(), b.centerY().toFloat())
    }

    /**
     * Long-press at specific screen coordinates (500ms hold).
     */
    suspend fun longPress(x: Float, y: Float): Boolean {
        val service = service ?: return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()
        return dispatchGesture(service, gesture)
    }

    /**
     * Tap at specific screen coordinates.
     */
    suspend fun tap(x: Float, y: Float): Boolean {
        val service = service ?: return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(service, gesture)
    }

    /**
     * Type text into the currently focused editable field.
     */
    fun inputText(nodeId: String, text: String): Boolean {
        val service = service ?: return false
        val root = service.getRootNode() ?: return false

        var idCounter = 0
        val target = findNodeById(root, nodeId, { idCounter++ })
        if (target != null) {
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val result = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            target.recycle()
            root.recycle()
            return result
        }

        root.recycle()
        return false
    }

    /**
     * Scroll the screen in a given direction.
     */
    suspend fun scroll(direction: String): Boolean {
        val service = service ?: return false
        // Get display metrics for scroll gesture coordinates
        val dm = service.resources.displayMetrics
        val centerX = dm.widthPixels / 2f
        val centerY = dm.heightPixels / 2f
        val distance = dm.heightPixels / 3f

        val path = Path()
        when (direction.lowercase()) {
            "up" -> {
                path.moveTo(centerX, centerY)
                path.lineTo(centerX, centerY + distance)
            }
            "down" -> {
                path.moveTo(centerX, centerY)
                path.lineTo(centerX, centerY - distance)
            }
            "left" -> {
                path.moveTo(centerX, centerY)
                path.lineTo(centerX + distance, centerY)
            }
            "right" -> {
                path.moveTo(centerX, centerY)
                path.lineTo(centerX - distance, centerY)
            }
            else -> return false
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(service, gesture)
    }

    /**
     * Perform a global system action (Home, Back, Recents, Notifications).
     */
    fun systemAction(action: String): Boolean {
        val service = service ?: return false
        val actionId = when (action.lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "power_dialog" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            else -> return false
        }
        return service.performGlobalAction(actionId)
    }

    // --- Private helpers ---

    private fun findNodeById(
        root: AccessibilityNodeInfo,
        targetId: String,
        counter: () -> Int,
    ): AccessibilityNodeInfo? {
        // Check inclusion criteria FIRST — must mirror ScreenReader exactly
        val isVisible = root.isVisibleToUser
        val isInteractive = root.isClickable || root.isLongClickable ||
            root.isScrollable || root.isEditable || root.isCheckable ||
            root.isFocusable
        val hasText = !root.text.isNullOrBlank() ||
            !root.contentDescription.isNullOrBlank()
        val hasResourceId = !root.viewIdResourceName.isNullOrBlank()

        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        val hasSize = bounds.width() > 0 && bounds.height() > 0

        val meetsInclusion = isVisible && hasSize && (isInteractive || hasText || hasResourceId)

        // Only assign an ID (increment counter) if node meets inclusion criteria
        if (meetsInclusion) {
            val currentId = "n${counter()}"
            if (currentId == targetId) {
                return root
            }
        }

        // Recurse into children
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeById(child, targetId, counter)
            if (found != null) return found
            child.recycle()
        }

        return null
    }

    private suspend fun dispatchGesture(
        service: AccessibilityService,
        gesture: GestureDescription,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(false)
            }
        }
        service.dispatchGesture(gesture, callback, null)
    }
}
