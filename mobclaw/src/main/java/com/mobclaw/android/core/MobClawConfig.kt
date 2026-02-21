package com.mobclaw.android.core

/**
 * Configuration for MobClaw agent.
 */
data class MobClawConfig(
    /** Maximum tool call iterations per task before stopping. */
    val maxIterations: Int = 120,

    /** Default temperature for LLM calls. */
    val temperature: Double = 0.7,

    /** Model name for the LLM provider. */
    val model: String? = null,

    /** Delay (ms) between actions to allow UI to settle. */
    val actionDelayMs: Long = 300,

    /** Whether to auto-read the screen after each action. */
    val autoScreenRead: Boolean = true,
)
