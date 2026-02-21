package com.mobclaw.android.observer

import android.util.Log
import kotlin.time.Duration

/**
 * Observer that logs events to Android Logcat.
 */
class LogObserver(private val tag: String = "MobClaw") : MobObserver {

    override fun onAgentStart(task: String) {
        Log.i(tag, "🦀 Agent started: $task")
    }

    override fun onToolCall(toolName: String, duration: Duration, success: Boolean) {
        val status = if (success) "✅" else "❌"
        Log.d(tag, "$status Tool[$toolName] completed in ${duration.inWholeMilliseconds}ms")
    }

    override fun onScreenRead(packageName: String, nodeCount: Int) {
        Log.d(tag, "👁 Screen read: $packageName ($nodeCount nodes)")
    }

    override fun onAgentEnd(task: String, duration: Duration, success: Boolean) {
        val status = if (success) "✅ Success" else "❌ Failed"
        Log.i(tag, "🦀 Agent ended: $task — $status in ${duration.inWholeMilliseconds}ms")
    }

    override fun onError(message: String, throwable: Throwable?) {
        Log.e(tag, "❌ $message", throwable)
    }
}
