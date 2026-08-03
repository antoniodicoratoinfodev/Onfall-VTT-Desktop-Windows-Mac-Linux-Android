package app.d6d.ui.battle

import app.d6d.domain.combat.CombatResourceState
import app.d6d.sheet.PACT_SLOT_RESOURCE_PREFIX
import app.d6d.sheet.SPELL_SLOT_RESOURCE_PREFIX
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpellSlotIndicatorsTest {

    @Test
    fun `i quadrati separano slot incantesimo e del patto dello stesso livello`() {
        val indicators = spellSlotIndicators(
            listOf(
                CombatResourceState("${SPELL_SLOT_RESOURCE_PREFIX}1", "Slot 1", 4, 1),
                CombatResourceState("${SPELL_SLOT_RESOURCE_PREFIX}2", "Slot 2", 3, 3),
                CombatResourceState("${PACT_SLOT_RESOURCE_PREFIX}2", "Patto 2", 2, 1),
                CombatResourceState("ira", "Ira", 3, 0),
            ),
        )

        assertEquals(
            listOf(
                SpellSlotIndicator(
                    kind = SpellSlotKind.STANDARD,
                    level = 1,
                    total = 4,
                    remaining = 3,
                ),
                SpellSlotIndicator(
                    kind = SpellSlotKind.STANDARD,
                    level = 2,
                    total = 3,
                    remaining = 0,
                ),
                SpellSlotIndicator(
                    kind = SpellSlotKind.PACT,
                    level = 2,
                    total = 2,
                    remaining = 1,
                ),
            ),
            indicators,
        )
    }

    @Test
    fun `un personaggio senza slot non riceve indicatori`() {
        assertTrue(
            spellSlotIndicators(
                listOf(CombatResourceState("ira", "Ira", 2, 0)),
            ).isEmpty(),
        )
    }
}
