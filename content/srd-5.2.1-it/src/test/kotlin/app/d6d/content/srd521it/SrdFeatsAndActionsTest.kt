package app.d6d.content.srd521it

import app.d6d.rules.character.RuleElementKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrdFeatsAndActionsTest {
    @Test
    fun `categorie talenti e azioni corrispondono allo SRD`() {
        assertEquals(17, SrdFeatsAndActions.feats.size)
        assertEquals(12, SrdFeatsAndActions.actions.size)
        assertEquals(4, count(RuleElementKind.ORIGIN_FEAT))
        assertEquals(2, count(RuleElementKind.GENERAL_FEAT))
        assertEquals(4, count(RuleElementKind.FIGHTING_STYLE_FEAT))
        assertEquals(7, count(RuleElementKind.EPIC_BOON_FEAT))
        assertTrue(SrdFeatsAndActions.all.all { it.description.isNotBlank() && it.sourcePage > 0 })
    }

    private fun count(kind: RuleElementKind): Int =
        SrdFeatsAndActions.feats.count { it.kind == kind }
}
