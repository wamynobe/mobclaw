package com.mobclaw.android.observer

import kotlin.time.Duration

/**
 * Observer interface for recording agent events.
 */
interface MobObserver {

    fun onAgentStart(task: String)

    fun onToolCall(toolName: String, duration: Duration, success: Boolean)

    fun onScreenRead(packageName: String, nodeCount: Int)

    fun onAgentEnd(task: String, duration: Duration, success: Boolean)

    fun onError(message: String, throwable: Throwable? = null)
}
