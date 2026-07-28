package app.d6d.content.srd521it

import app.d6d.rules.character.RuleElementKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrdSpellsTest {
    @Test
    fun `il catalogo contiene tutti gli incantesimi e i trucchetti SRD`() {
        assertEquals(339, SrdSpells.all.size)
        assertEquals(27, SrdSpells.all.count { it.kind == RuleElementKind.CANTRIP })
        assertEquals(
            mapOf(0 to 27, 1 to 57, 2 to 57, 3 to 42, 4 to 34, 5 to 38, 6 to 31, 7 to 20, 8 to 17, 9 to 16),
            SrdSpells.all.groupingBy { it.spell!!.level }.eachCount().toSortedMap(),
        )
    }

    @Test
    fun `metadati e id sono completi e univoci`() {
        assertEquals(SrdSpells.all.size, SrdSpells.all.distinctBy { it.id }.size)
        assertTrue(SrdSpells.all.all { it.description.isNotBlank() })
        assertTrue(SrdSpells.all.all { it.classEligibility.isNotEmpty() })
        assertTrue(SrdSpells.all.all { it.sourcePage in 121..201 })
        assertTrue(SrdSpells.all.all { it.spell!!.castingTime.isNotBlank() })
        assertTrue(SrdSpells.all.all { it.spell!!.range.isNotBlank() })
        assertTrue(SrdSpells.all.all { it.spell!!.components.isNotBlank() })
        assertTrue(SrdSpells.all.all { it.spell!!.duration.isNotBlank() })
    }
}
