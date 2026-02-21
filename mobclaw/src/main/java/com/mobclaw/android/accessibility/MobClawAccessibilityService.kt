package com.mobclaw.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Central AccessibilityService for MobClaw.
 *
 * Must be declared in the app's AndroidManifest.xml and enabled by the user
 * in Settings → Accessibility. Provides global UI tree access and gesture dispatch.
 */
class MobClawAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are consumed on-demand via ScreenReader, not reactively.
    }

    override fun onInterrupt() {
        // No-op
    }

    /**
     * Get the root AccessibilityNodeInfo for the current active window.
     * Returns null if not available.
     */
    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow

    companion object {
        /**
         * Singleton reference to the running service instance.
         * Set when the service connects, cleared when it disconnects.
         */
        @Volatile
        var instance: MobClawAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
