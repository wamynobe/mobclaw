package com.mobclaw.android.skill

/**
 * Registry that holds all available skills and matches them against user tasks.
 *
 * Usage:
 * ```
 * val registry = SkillRegistry()
 * registry.register(CallSkill())
 * registry.register(FacebookSkill())
 *
 * val matched = registry.findMatchingSkills("call 0123456789")
 * // -> [CallSkill]
 * ```
 */
class SkillRegistry {

    private val skills = mutableListOf<MobSkill>()

    /** Register a skill. Duplicate IDs are replaced. */
    fun register(skill: MobSkill) {
        skills.removeAll { it.id == skill.id }
        skills.add(skill)
    }

    /** Register multiple skills at once. */
    fun registerAll(vararg newSkills: MobSkill) {
        newSkills.forEach { register(it) }
    }

    /** Get all registered skills. */
    fun allSkills(): List<MobSkill> = skills.toList()

    /**
     * Find skills whose trigger keywords match the given task.
     * Matching is case-insensitive and checks if any keyword appears in the task text.
     */
    fun findMatchingSkills(task: String): List<MobSkill> {
        val taskLower = task.lowercase()
        return skills.filter { skill ->
            skill.triggerKeywords.any { keyword ->
                taskLower.contains(keyword.lowercase())
            }
        }
    }

    /**
     * Build the combined skill prompt section for matched skills.
     * Returns empty string if no skills matched.
     */
    fun buildSkillPrompt(matchedSkills: List<MobSkill>): String {
        if (matchedSkills.isEmpty()) return ""

        return buildString {
            appendLine()
            appendLine("## Domain-Specific Knowledge")
            appendLine()
            appendLine("The following guides are loaded based on your task. Follow them closely.")
            for (skill in matchedSkills) {
                appendLine()
                appendLine("### ${skill.name}")
                appendLine(skill.instructions())
            }
        }
    }

    companion object {
        /** Create a registry pre-loaded with all built-in skills. */
        fun withDefaults(): SkillRegistry = SkillRegistry().apply {
            registerAll(
                OpenAppSkill(),
                CallSkill(),
                MessageSkill(),
                FacebookSkill(),
                MessengerSkill(),
            )
        }
    }
}
