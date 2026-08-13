package app.d6d.ui.compendium

import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.DiceExpression
import app.d6d.domain.combat.HealingDefinition
import app.d6d.domain.combat.HealingSlotScaling
import app.d6d.domain.combat.HealingTarget
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.domain.combat.SpellSlotResourceId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompendiumModelsTest {
    @Test
    fun `il roundtrip della bozza conserva la cura senza aggiungere un attacco`() {
        val healing = HealingDefinition.dice(
            HealingTarget.SELF_OR_ALLY,
            DiceExpression(2, 8, 3),
        )
        val original = AbilityDefinition.builder("cura", "Cura")
            .resolutionMethod(ResolutionMethod.AUTOMATIC)
            .automationStatus(AutomationStatus.AUTOMATED)
            .rangeFeet(5)
            .damage(emptyList())
            .healing(healing)
            .build()

        val draft = AbilityDraft.from(original)
        val restored = draft.toDefinition()

        assertEquals(healing, draft.healing)
        assertFalse(draft.dealsDamage)
        assertEquals(healing, restored.healing())
        assertTrue(restored.damage().isEmpty())
        assertEquals(ResolutionMethod.AUTOMATIC, restored.resolutionMethod())
        assertEquals(AutomationStatus.AUTOMATED, restored.automationStatus())
    }

    @Test
    fun `una capacita legacy manuale senza danno resta senza danno`() {
        val original = AbilityDefinition.builder("legacy", "Legacy")
            .resolutionMethod(ResolutionMethod.MANUAL)
            .automationStatus(AutomationStatus.MANUAL_REQUIRED)
            .damage(emptyList())
            .build()

        val restored = AbilityDraft.from(original).toDefinition()

        assertTrue(restored.damage().isEmpty())
        assertEquals(ResolutionMethod.MANUAL, restored.resolutionMethod())
        assertEquals(AutomationStatus.MANUAL_REQUIRED, restored.automationStatus())
    }

    @Test
    fun `il roundtrip conserva scaling e risorsa dello slot di cura`() {
        val slot = SpellSlotResourceId.standard(1).id()
        val healing = HealingDefinition.dice(
            HealingTarget.SELF_OR_ALLY,
            DiceExpression(2, 8, 4),
            HealingSlotScaling(1, 2),
        )
        val original = AbilityDefinition.builder("cura-scalabile", "Cura scalabile")
            .spellOrCantrip(true)
            .resource(slot, 1)
            .healing(healing)
            .build()

        val restored = AbilityDraft.from(original).toDefinition()

        assertEquals(slot, restored.resourceId())
        assertEquals(1, restored.resourceCost())
        assertEquals(healing, restored.healing())
        assertEquals("6d8+4", restored.healing().resolveAtSlotLevel(3).dice().notation())
    }
}
