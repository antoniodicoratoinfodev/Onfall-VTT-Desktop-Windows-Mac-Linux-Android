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

    @Test
    fun `le risorse di classe diventano contatori distinti dagli slot`() {
        val indicators = classResourceIndicators(
            listOf(
                CombatResourceState("${SPELL_SLOT_RESOURCE_PREFIX}1", "Slot 1", 4, 1),
                CombatResourceState("ispirazione", "Ispirazione bardica", 4, 1),
                CombatResourceState("recuperare-energie", "Recuperare energie", 3, 3),
                CombatResourceState("inattiva", "Risorsa inattiva", 0, 0),
            ),
        )

        assertEquals(
            listOf(
                ClassResourceIndicator(
                    id = "ispirazione",
                    name = "Ispirazione bardica",
                    total = 4,
                    remaining = 3,
                ),
                ClassResourceIndicator(
                    id = "recuperare-energie",
                    name = "Recuperare energie",
                    total = 3,
                    remaining = 0,
                ),
            ),
            indicators,
        )
    }

    @Test
    fun `la risorsa tabellare del warlock non duplica gli slot del patto`() {
        val indicators = classResourceIndicators(
            listOf(
                CombatResourceState(
                    "srd521-it:resource:warlock:slot-magia-del-patto",
                    "Slot di Magia del patto",
                    2,
                    0,
                ),
                CombatResourceState("${PACT_SLOT_RESOURCE_PREFIX}3", "Slot del patto 3", 2, 1),
                CombatResourceState("arcanum", "Arcanum mistico (6º)", 1, 0),
            ),
        )

        assertEquals(
            listOf(
                ClassResourceIndicator(
                    id = "arcanum",
                    name = "Arcanum mistico (6º)",
                    total = 1,
                    remaining = 1,
                ),
            ),
            indicators,
        )
    }
}
