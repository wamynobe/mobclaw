package com.wamynobe.mobclaw.tool

/**
 * Shared state for loop/repeat operations.
 *
 * The repeat tools (RepeatTool, RepeatNextTool, RepeatDoneTool) share this context
 * to track the current loop state. The agent injects loop status reminders into
 * the conversation when a loop is active.
 */
class LoopContext {
    var description: String = ""
        private set
    var totalCount: Int = 0
        private set
    var currentIteration: Int = 0
        private set
    var isActive: Boolean = false
        private set

    /** Start a new loop. Resets state. */
    fun start(description: String, count: Int) {
        this.description = description
        this.totalCount = count
        this.currentIteration = 1
        this.isActive = true
    }

    /** Advance to the next iteration. Returns false if already done. */
    fun next(): Boolean {
        if (!isActive) return false
        currentIteration++
        if (currentIteration > totalCount) {
            isActive = false
            return false
        }
        return true
    }

    /** End the loop early or normally. */
    fun done() {
        isActive = false
    }

    /** Get a status string for injection into conversation. */
    fun statusText(): String {
        if (!isActive) return ""
        return "[Loop status: Iteration $currentIteration/$totalCount — \"$description\"]"
    }

    /** Reset everything. */
    fun reset() {
        description = ""
        totalCount = 0
        currentIteration = 0
        isActive = false
    }
}
