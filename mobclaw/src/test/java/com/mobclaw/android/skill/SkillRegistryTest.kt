package com.mobclaw.android.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRegistryTest {

    @Test
    fun findMatchingSkills_matchesSingleKeyword() {
        val registry = SkillRegistry()
        registry.register(CallSkill())
        registry.register(MessageSkill())

        val matched = registry.findMatchingSkills("call 0123456789")

        assertEquals(1, matched.size)
        assertEquals("call", matched[0].id)
    }

    @Test
    fun findMatchingSkills_matchesMultipleSkills() {
        val registry = SkillRegistry()
        registry.register(CallSkill())
        registry.register(OpenAppSkill())

        // "open the phone app and call someone" should match both
        val matched = registry.findMatchingSkills("open the phone app and call someone")

        assertEquals(2, matched.size)
        val ids = matched.map { it.id }.toSet()
        assertTrue(ids.contains("call"))
        assertTrue(ids.contains("open_app"))
    }

    @Test
    fun findMatchingSkills_caseInsensitive() {
        val registry = SkillRegistry()
        registry.register(FacebookSkill())

        val matched = registry.findMatchingSkills("Post on FACEBOOK please")

        assertEquals(1, matched.size)
        assertEquals("facebook", matched[0].id)
    }

    @Test
    fun findMatchingSkills_noMatch() {
        val registry = SkillRegistry()
        registry.register(CallSkill())
        registry.register(MessageSkill())

        val matched = registry.findMatchingSkills("turn on wifi")

        assertTrue(matched.isEmpty())
    }

    @Test
    fun register_replacesDuplicate() {
        val registry = SkillRegistry()
        val skill1 = CallSkill()
        val skill2 = CallSkill()
        registry.register(skill1)
        registry.register(skill2)

        assertEquals(1, registry.allSkills().size)
    }

    @Test
    fun withDefaults_registersAllBuiltInSkills() {
        val registry = SkillRegistry.withDefaults()

        val ids = registry.allSkills().map { it.id }.toSet()
        assertTrue(ids.contains("open_app"))
        assertTrue(ids.contains("call"))
        assertTrue(ids.contains("message"))
        assertTrue(ids.contains("facebook"))
        assertTrue(ids.contains("messenger"))
        assertEquals(5, ids.size)
    }

    @Test
    fun buildSkillPrompt_returnsEmptyForNoSkills() {
        val registry = SkillRegistry()
        val prompt = registry.buildSkillPrompt(emptyList())
        assertEquals("", prompt)
    }

    @Test
    fun buildSkillPrompt_includesSkillName() {
        val registry = SkillRegistry()
        val skill = CallSkill()
        val prompt = registry.buildSkillPrompt(listOf(skill))

        assertTrue(prompt.contains("Phone Calls"))
        assertTrue(prompt.contains("Domain-Specific Knowledge"))
    }

    @Test
    fun findMatchingSkills_matchesMessengerKeyword() {
        val registry = SkillRegistry.withDefaults()

        val matched = registry.findMatchingSkills("send a message on messenger to John")

        val ids = matched.map { it.id }.toSet()
        assertTrue(ids.contains("messenger"))
    }
}
