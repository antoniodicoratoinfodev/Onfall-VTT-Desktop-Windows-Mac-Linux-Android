package app.d6d.ui.dice

import app.d6d.domain.combat.CombatEvent
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.combat.EventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DiceModelsTest {

    @Test
    fun `il vassoio accetta tutti e soli i dadi esposti dall'interfaccia`() {
        assertEquals(listOf(4, 6, 8, 10, 12, 20, 100), DicePoolSpec.SUPPORTED_DICE)
        DicePoolSpec.SUPPORTED_DICE.forEach { sides -> DicePoolSpec(count = 100, sides = sides) }

        assertThrows(IllegalArgumentException::class.java) { DicePoolSpec(count = 0, sides = 20) }
        assertThrows(IllegalArgumentException::class.java) { DicePoolSpec(count = 101, sides = 20) }
        assertThrows(IllegalArgumentException::class.java) { DicePoolSpec(count = 1, sides = 7) }
    }

    @Test
    fun `un evento d20 conserva entrambi i dadi e il naturale selezionato`() {
        val event = CombatEvent(
            1,
            1,
            EventType.ATTACK_ROLLED,
            1,
            "eroe",
            "goblin",
            mapOf(
                "abilityName" to "Spada lunga",
                "dice" to "[7, 18]",
                "natural" to "18",
                "modifier" to "5",
                "total" to "23",
                "mode" to "ADVANTAGE",
            ),
        )

        val roll = presentedRollsFromEvents(listOf(event)).single()

        assertEquals(DiceRollPurpose.ATTACK, roll.purpose)
        assertEquals(20, roll.sides)
        assertEquals(listOf(7, 18), roll.values)
        assertEquals(18, roll.selectedValue)
        assertEquals(23, roll.total)
        assertEquals(D20Mode.ADVANTAGE, roll.mode)
    }

    @Test
    fun `in parita vantaggio e svantaggio evidenziano un solo dado`() {
        listOf(D20Mode.ADVANTAGE, D20Mode.DISADVANTAGE).forEach { mode ->
            val roll = PresentedDiceRoll(
                purpose = DiceRollPurpose.FREE,
                sides = 20,
                values = listOf(12, 12),
                total = 12,
                mode = mode,
                selectedValue = 12,
            )

            assertEquals(listOf(true, false), roll.values.indices.map(roll::keepsDieAt), mode.name)
        }
    }

    @Test
    fun `un evento di danno ricava le facce dalla formula`() {
        val event = CombatEvent(
            2,
            2,
            EventType.DAMAGE_ROLLED,
            1,
            "eroe",
            "goblin",
            mapOf(
                "formula" to "2d8+3",
                "dice" to "[2, 7]",
                "modifier" to "3",
                "total" to "12",
            ),
        )

        val roll = presentedRollsFromEvents(listOf(event)).single()

        assertEquals(DiceRollPurpose.DAMAGE, roll.purpose)
        assertEquals(8, roll.sides)
        assertEquals("2d8+3", roll.notation)
        assertEquals(12, roll.total)
    }

    @Test
    fun `gli eventi senza dadi non producono falsi lanci`() {
        val event = CombatEvent(3, 3, EventType.ATTACK_HIT, 1, "eroe", "goblin", emptyMap())

        assertEquals(emptyList<PresentedDiceRoll>(), presentedRollsFromEvents(listOf(event)))
    }
}
