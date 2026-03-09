package com.mobclaw.android.skill

import com.mobclaw.android.tool.MobTool

/**
 * A skill provides domain-specific knowledge for a particular task domain.
 *
 * Skills are NOT tools — they are **knowledge documents** injected into the system prompt
 * when the user's task matches the skill's trigger keywords. They teach the LLM
 * how to navigate specific apps or accomplish specific types of tasks using the
 * existing tools (click, scroll, input_text, etc.).
 *
 * Example: A "Call" skill teaches the LLM how to navigate the Phone/Dialer app,
 * enter a number, and press the call button.
 */
interface MobSkill {

    /** Unique identifier for this skill. */
    val id: String

    /** Human-readable name shown in logs/debug. */
    val name: String

    /** Keywords that trigger this skill (matched case-insensitively against the user task). */
    val triggerKeywords: List<String>

    /**
     * Detailed instructions appended to the system prompt.
     * Should describe app-specific UI patterns, navigation flows, common pitfalls,
     * and step-by-step guides for the domain.
     */
    fun instructions(): String

    /**
     * Optional: extra tools this skill needs beyond the defaults.
     * For example, a composite "dial_number" tool that wraps open_app + input_text + click.
     */
    fun additionalTools(): List<MobTool> = emptyList()
}
