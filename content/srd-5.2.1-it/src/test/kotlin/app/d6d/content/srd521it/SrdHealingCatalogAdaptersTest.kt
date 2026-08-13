package app.d6d.content.srd521it

import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.HealingTarget
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.rules.character.ResourcePoolState
import app.d6d.sheet.Ability
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.CatalogHealingBonusSource
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.SPELL_SLOT_RESOURCE_PREFIX
import app.d6d.sheet.SpellSlot
import app.d6d.sheet.Spellcasting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrdHealingCatalogAdaptersTest {
    private val catalog = Srd521ItContent.catalog.associateBy { it.id }

    @Test
    fun `cura ferite e parola guaritrice hanno formule gittate e slot espliciti`() {
        assertHealing(
            ability = catalog.getValue("srd521-it:spell:cura-ferite"),
            target = HealingTarget.SELF_OR_ALLY,
            diceCount = 2,
            diceSides = 8,
            rangeFeet = 5,
            resourceId = "${SPELL_SLOT_RESOURCE_PREFIX}1",
            bonusSource = CatalogHealingBonusSource.SPELLCASTING_ABILITY,
            baseSlotLevel = 1,
            additionalDicePerSlotLevel = 2,
        )
        assertHealing(
            ability = catalog.getValue("srd521-it:spell:parola-guaritrice"),
            target = HealingTarget.SELF_OR_ALLY,
            diceCount = 2,
            diceSides = 4,
            rangeFeet = 60,
            resourceId = "${SPELL_SLOT_RESOURCE_PREFIX}1",
            bonusSource = CatalogHealingBonusSource.SPELLCASTING_ABILITY,
            baseSlotLevel = 1,
            additionalDicePerSlotLevel = 2,
        )
    }

    @Test
    fun `recuperare energie cura solo se stessi e consuma la risorsa del guerriero`() {
        assertHealing(
            ability = catalog.getValue("srd521-it:feature:guerriero:recuperare-energie"),
            target = HealingTarget.SELF,
            diceCount = 1,
            diceSides = 10,
            rangeFeet = 0,
            resourceId = "srd521-it:resource:guerriero:recuperare-energie",
            bonusSource = CatalogHealingBonusSource.CLASS_LEVEL,
            bonusClassId = CharacterClassId.FIGHTER,
        )
    }

    @Test
    fun `la fotografia risolve le tre formule con i valori effettivi della scheda`() {
        val cureWoundsId = "srd521-it:spell:cura-ferite"
        val healingWordId = "srd521-it:spell:parola-guaritrice"
        val secondWindId = "srd521-it:feature:guerriero:recuperare-energie"
        val sheet = CharacterSheet(
            abilityScores = mapOf(Ability.WISDOM to 16),
            progression = CharacterProgression(
                classLevels = listOf(
                    ClassLevelState(CharacterClassId.CLERIC, 1),
                    ClassLevelState(CharacterClassId.FIGHTER, 5),
                ),
                resourcePools = listOf(
                    ResourcePoolState(
                        resourceId = "srd521-it:resource:guerriero:recuperare-energie",
                        name = "Recuperare Energie",
                        maximum = 2,
                        recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
                    ),
                ),
            ),
            spellcasting = Spellcasting(
                ability = Ability.WISDOM,
                slots = (1..9).map { level ->
                    SpellSlot(level, total = if (level == 1 || level == 3) 2 else 0)
                },
            ),
            abilityIds = listOf(cureWoundsId, healingWordId, secondWindId),
        )

        val actor = sheet.toActorDefinition(abilityCatalog = Srd521ItContent.catalog)

        assertEquals("2d8+3", actor.ability(cureWoundsId).healing().dice().notation())
        assertEquals(
            "6d8+3",
            actor.ability(cureWoundsId).healing().resolveAtSlotLevel(3).dice().notation(),
        )
        assertEquals("2d4+3", actor.ability(healingWordId).healing().dice().notation())
        assertEquals(
            "6d4+3",
            actor.ability(healingWordId).healing().resolveAtSlotLevel(3).dice().notation(),
        )
        assertEquals("1d10+5", actor.ability(secondWindId).healing().dice().notation())
        assertEquals(3, actor.abilities().size, "gli slot disponibili non duplicano le capacità")
        assertEquals(
            2,
            actor.resources().single {
                it.id() == "srd521-it:resource:guerriero:recuperare-energie"
            }.remaining(),
        )
    }

    @Test
    fun `una voce legacy fuori whitelist resta manuale anche se il testo parla di cura`() {
        val legacy = RuleElementDefinition(
            id = "legacy:feature:unguento",
            name = "Unguento legacy",
            kind = RuleElementKind.CUSTOM,
            description = "Il bersaglio recupera 9d9 punti ferita.",
            activation = "azione",
        ).toCatalogAbility(Srd521ItManifest.value)

        assertNull(legacy.healing)
        assertEquals(ResolutionMethod.MANUAL, legacy.resolutionMethod)
        assertEquals(AutomationStatus.MANUAL_REQUIRED, legacy.automationStatus)
        assertFalse(legacy.dealsDamage)
        assertNull(legacy.resourceId)
        assertEquals(0, legacy.resourceCost)
    }

    private fun assertHealing(
        ability: CatalogAbility,
        target: HealingTarget,
        diceCount: Int,
        diceSides: Int,
        rangeFeet: Int,
        resourceId: String,
        bonusSource: CatalogHealingBonusSource,
        bonusClassId: CharacterClassId? = null,
        baseSlotLevel: Int? = null,
        additionalDicePerSlotLevel: Int? = null,
    ) {
        val healing = requireNotNull(ability.healing)
        val dice = requireNotNull(healing.dice)

        assertEquals(target, healing.target)
        assertEquals(diceCount, dice.count)
        assertEquals(diceSides, dice.sides)
        assertEquals(0, dice.modifier)
        assertNull(healing.fixedAmount)
        assertEquals(bonusSource, healing.bonusSource)
        assertEquals(bonusClassId, healing.bonusClassId)
        assertEquals(baseSlotLevel, healing.slotScaling?.baseSlotLevel)
        assertEquals(additionalDicePerSlotLevel, healing.slotScaling?.additionalDicePerSlotLevel)
        assertEquals(rangeFeet, ability.rangeFeet)
        assertEquals(ResolutionMethod.AUTOMATIC, ability.resolutionMethod)
        assertEquals(AutomationStatus.AUTOMATED, ability.automationStatus)
        assertFalse(ability.dealsDamage)
        assertFalse(ability.passive)
        assertFalse(ability.isArea)
        assertEquals(1, ability.maxTargets)
        assertEquals(resourceId, ability.resourceId)
        assertEquals(1, ability.resourceCost)
        assertTrue(ability.additionalDamage.isEmpty())
    }
}
