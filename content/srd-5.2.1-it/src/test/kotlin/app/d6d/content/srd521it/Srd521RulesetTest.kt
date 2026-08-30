package app.d6d.content.srd521it

import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RulesetOrigin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class Srd521RulesetTest {
    @Test
    fun catalogoRegoleECompletoBilingueEImmutabile() {
        val revision = Srd521Ruleset.revision

        assertEquals(RulesetOrigin.BUNDLED_STANDARD, revision.origin())
        assertTrue(revision.readOnly())
        assertTrue(revision.entities().any { it.kind() == RuleKind.CLASS })
        assertTrue(revision.entities().any { it.kind() == RuleKind.SPELL })
        assertTrue(revision.entities().any { it.kind() == RuleKind.ITEM })
        assertTrue(revision.entities().size > 300)
        val compiled = revision.compile()
        assertTrue(compiled.stats().size >= 6)
        assertTrue(compiled.skills().size >= 18)
        assertTrue(compiled.randomizers().isNotEmpty())
        assertTrue(compiled.turnStructures().isNotEmpty())
        revision.entities().forEach { entity ->
            assertTrue(entity.name().text("it").isNotBlank())
            assertTrue(entity.name().text("en").isNotBlank())
            assertTrue(entity.description().text("it").isNotBlank())
            assertTrue(entity.description().text("en").isNotBlank())
        }
    }
}
