package app.d6d.rules.character

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class OpenRuleIdsTest {

    @Test
    fun `un id namespaced non collassa su una caratteristica standard con lo stesso suffisso`() {
        val custom = Ability.of("homebrew:stat:strength")

        assertEquals("homebrew:stat:strength", custom.value)
        assertNotEquals(Ability.STRENGTH, custom)
        assertEquals(Ability.STRENGTH, Ability.of("strength"))
    }

    @Test
    fun `un id namespaced non collassa su una skill standard con lo stesso suffisso`() {
        val custom = Skill.of("homebrew:skill:percezione")

        assertEquals("homebrew:skill:percezione", custom.value)
        assertNotEquals(Skill.PERCEZIONE, custom)
        assertEquals(Skill.PERCEZIONE, Skill.of("percezione"))
    }
}
