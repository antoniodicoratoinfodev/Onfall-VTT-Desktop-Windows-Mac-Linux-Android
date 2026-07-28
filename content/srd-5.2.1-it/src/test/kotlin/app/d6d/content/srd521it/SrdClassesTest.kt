package app.d6d.content.srd521it

import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ClassDefinition
import app.d6d.rules.character.ClassLevelDefinition
import app.d6d.rules.character.ResourceMaximum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrdClassesTest {

    @Test
    fun `il catalogo contiene le dodici classi e venti livelli ordinati per ciascuna`() {
        assertEquals(12, srdClasses.size)
        assertEquals(CharacterClassId.entries.toSet(), srdClasses.map { it.id }.toSet())
        assertEquals(12, srdClasses.distinctBy { it.id }.size)

        srdClasses.forEach { definition ->
            assertEquals(
                (1..20).toList(),
                definition.levels.map { it.level },
                "${definition.name}: progressione incompleta o non ordinata",
            )
        }
    }

    @Test
    fun `barbaro guerriero ladro e monaco hanno risorse e tabelle marziali corrette`() {
        val barbarian = classOf(CharacterClassId.BARBARIAN)
        assertNoSpellProgression(barbarian)
        assertResource(barbarian, level = 1, slug = "ira", maximum = 2)
        assertResource(barbarian, level = 12, slug = "ira", maximum = 5)
        assertResource(barbarian, level = 20, slug = "ira", maximum = 6)

        val fighter = classOf(CharacterClassId.FIGHTER)
        assertNoSpellProgression(fighter)
        assertResource(fighter, level = 1, slug = "recuperare-energie", maximum = 2, dieSides = 10)
        assertResource(fighter, level = 4, slug = "recuperare-energie", maximum = 3, dieSides = 10)
        assertResource(fighter, level = 17, slug = "azione-impetuosa", maximum = 2)
        assertResource(fighter, level = 17, slug = "indomabile", maximum = 3)

        val rogue = classOf(CharacterClassId.ROGUE)
        assertNoSpellProgression(rogue)
        assertResource(rogue, level = 19, slug = "colpo-di-fortuna", maximum = 0)
        assertResource(rogue, level = 20, slug = "colpo-di-fortuna", maximum = 1)

        val monk = classOf(CharacterClassId.MONK)
        assertNoSpellProgression(monk)
        assertResource(monk, level = 2, slug = "punti-concentrazione", maximum = 2, dieSides = 6)
        assertResource(monk, level = 11, slug = "punti-concentrazione", maximum = 11, dieSides = 10)
        assertResource(monk, level = 20, slug = "punti-concentrazione", maximum = 20, dieSides = 12)
    }

    @Test
    fun `bardo chierico druido mago e stregone seguono la progressione da incantatore pieno`() {
        val bard = classOf(CharacterClassId.BARD)
        assertSpellRow(bard, 1, cantrips = 2, prepared = 4, slots = listOf(2))
        assertSpellRow(bard, 10, cantrips = 4, prepared = 15, slots = listOf(4, 3, 3, 3, 2))
        assertSpellRow(bard, 20, cantrips = 4, prepared = 22, slots = fullCasterLevel20)
        assertResource(bard, level = 1, slug = "ispirazione-bardica", maximum = 0, dieSides = 6)
        assertResource(bard, level = 15, slug = "ispirazione-bardica", maximum = 0, dieSides = 12)

        val cleric = classOf(CharacterClassId.CLERIC)
        assertSpellRow(cleric, 1, cantrips = 3, prepared = 4, slots = listOf(2))
        assertSpellRow(cleric, 10, cantrips = 5, prepared = 15, slots = listOf(4, 3, 3, 3, 2))
        assertSpellRow(cleric, 20, cantrips = 5, prepared = 22, slots = fullCasterLevel20)
        assertResource(cleric, level = 2, slug = "incanalare-divinita", maximum = 2)
        assertResource(cleric, level = 18, slug = "incanalare-divinita", maximum = 4)

        val druid = classOf(CharacterClassId.DRUID)
        assertSpellRow(druid, 1, cantrips = 2, prepared = 4, slots = listOf(2))
        assertSpellRow(druid, 10, cantrips = 4, prepared = 15, slots = listOf(4, 3, 3, 3, 2))
        assertSpellRow(druid, 20, cantrips = 4, prepared = 22, slots = fullCasterLevel20)
        assertResource(druid, level = 2, slug = "forma-selvatica", maximum = 2)
        assertResource(druid, level = 17, slug = "forma-selvatica", maximum = 4)

        val wizard = classOf(CharacterClassId.WIZARD)
        assertSpellRow(wizard, 1, cantrips = 3, prepared = 4, slots = listOf(2))
        assertEquals(6, wizard.level(1).spellbookAdditions)
        assertSpellRow(wizard, 10, cantrips = 5, prepared = 15, slots = listOf(4, 3, 3, 3, 2))
        assertSpellRow(wizard, 20, cantrips = 5, prepared = 25, slots = fullCasterLevel20)
        assertEquals(2, wizard.level(20).spellbookAdditions)
        assertResource(wizard, level = 1, slug = "recupero-arcano", maximum = 1)

        val sorcerer = classOf(CharacterClassId.SORCERER)
        assertSpellRow(sorcerer, 1, cantrips = 4, prepared = 2, slots = listOf(2))
        assertSpellRow(sorcerer, 10, cantrips = 6, prepared = 15, slots = listOf(4, 3, 3, 3, 2))
        assertSpellRow(sorcerer, 20, cantrips = 6, prepared = 22, slots = fullCasterLevel20)
        assertResource(sorcerer, level = 1, slug = "punti-stregoneria", maximum = 0)
        assertResource(sorcerer, level = 2, slug = "punti-stregoneria", maximum = 2)
        assertResource(sorcerer, level = 20, slug = "punti-stregoneria", maximum = 20)
        assertResource(sorcerer, level = 1, slug = "stregoneria-innata", maximum = 2)
    }

    @Test
    fun `paladino e ranger seguono slot da mezzo incantatore e risorse di classe`() {
        val paladin = classOf(CharacterClassId.PALADIN)
        assertSpellRow(paladin, 1, cantrips = 0, prepared = 2, slots = listOf(2))
        assertSpellRow(paladin, 5, cantrips = 0, prepared = 6, slots = listOf(4, 2))
        assertSpellRow(paladin, 20, cantrips = 0, prepared = 15, slots = halfCasterLevel20)
        assertResource(paladin, level = 1, slug = "imposizione-delle-mani", maximum = 5)
        assertResource(paladin, level = 20, slug = "imposizione-delle-mani", maximum = 100)
        assertResource(paladin, level = 3, slug = "incanalare-divinita", maximum = 2)
        assertResource(paladin, level = 11, slug = "incanalare-divinita", maximum = 3)

        val ranger = classOf(CharacterClassId.RANGER)
        assertSpellRow(ranger, 1, cantrips = 0, prepared = 2, slots = listOf(2))
        assertSpellRow(ranger, 5, cantrips = 0, prepared = 6, slots = listOf(4, 2))
        assertSpellRow(ranger, 20, cantrips = 0, prepared = 15, slots = halfCasterLevel20)
        assertResource(ranger, level = 1, slug = "nemico-prescelto", maximum = 2)
        assertResource(ranger, level = 9, slug = "nemico-prescelto", maximum = 4)
        assertResource(ranger, level = 17, slug = "nemico-prescelto", maximum = 6)
    }

    @Test
    fun `warlock mantiene separati slot del patto slot normali e arcanum`() {
        val warlock = classOf(CharacterClassId.WARLOCK)

        assertPactRow(warlock.level(1), cantrips = 2, prepared = 2, count = 1, slotLevel = 1)
        assertPactRow(warlock.level(5), cantrips = 3, prepared = 6, count = 2, slotLevel = 3)
        assertPactRow(warlock.level(11), cantrips = 4, prepared = 11, count = 3, slotLevel = 5)
        assertPactRow(warlock.level(17), cantrips = 4, prepared = 14, count = 4, slotLevel = 5)
        assertPactRow(warlock.level(20), cantrips = 4, prepared = 15, count = 4, slotLevel = 5)
        assertTrue(warlock.levels.all { it.spellSlots.isEmpty() })

        assertResource(warlock, level = 1, slug = "slot-magia-del-patto", maximum = 1)
        assertResource(warlock, level = 17, slug = "slot-magia-del-patto", maximum = 4)
        assertResource(warlock, level = 10, slug = "arcanum-mistico-6", maximum = 0)
        assertResource(warlock, level = 11, slug = "arcanum-mistico-6", maximum = 1)
        assertResource(warlock, level = 17, slug = "arcanum-mistico-9", maximum = 1)
    }

    @Test
    fun `id di privilegi scelte pool e sottoclassi sono namespaced e coerenti`() {
        val allChoices = srdClasses.flatMap(::allChoices)
        val subclassIds = srdClasses.flatMap { it.subclassIds }

        assertEquals(allChoices.size, allChoices.distinctBy { it.id }.size, "ID scelta duplicato")
        assertEquals(subclassIds.size, subclassIds.distinct().size, "ID sottoclasse duplicato")
        assertTrue(subclassIds.all { it.startsWith("$prefix:subclass:") })

        srdClasses.forEach { definition ->
            val featurePrefix = "$prefix:feature:${definition.id.contentId}:"
            val choicePrefix = "$prefix:choice:${definition.id.contentId}:"
            val featureIds = definition.levels.flatMap { it.featureIds }

            assertTrue(
                featureIds.all { it.startsWith(featurePrefix) },
                "${definition.name}: ID privilegio fuori namespace",
            )
            assertTrue(
                definition.levels.all { it.featureIds.size == it.featureIds.distinct().size },
                "${definition.name}: privilegio duplicato nella stessa riga",
            )
            assertTrue(
                allChoices(definition).all { it.id.startsWith(choicePrefix) },
                "${definition.name}: ID scelta fuori namespace",
            )
        }

        allChoices.forEach { choice ->
            assertTrue(choice.optionIds.all { it.startsWith("$prefix:") }, "${choice.id}: option ID non namespaced")
            assertTrue(choice.poolId == null || choice.poolId!!.startsWith("$prefix:pool:"))
        }

        val ownersByFeature = srdClasses
            .flatMap { definition ->
                definition.levels.flatMap { level -> level.featureIds.map { it to definition.id } }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        assertTrue(ownersByFeature.values.all { owners -> owners.distinct().size == 1 })
    }

    @Test
    fun `prerequisiti multiclasse distinguono OR del guerriero da AND delle classi doppie`() {
        assertEquals(
            listOf(setOf(Ability.STRENGTH, Ability.DEXTERITY)),
            classOf(CharacterClassId.FIGHTER).multiclassPrerequisiteGroups,
        )
        assertEquals(
            listOf(setOf(Ability.DEXTERITY), setOf(Ability.WISDOM)),
            classOf(CharacterClassId.RANGER).multiclassPrerequisiteGroups,
        )
        assertEquals(
            listOf(setOf(Ability.DEXTERITY), setOf(Ability.WISDOM)),
            classOf(CharacterClassId.MONK).multiclassPrerequisiteGroups,
        )
        assertEquals(
            listOf(setOf(Ability.STRENGTH), setOf(Ability.CHARISMA)),
            classOf(CharacterClassId.PALADIN).multiclassPrerequisiteGroups,
        )
    }

    private fun classOf(id: CharacterClassId): ClassDefinition =
        srdClasses.single { it.id == id }

    private fun assertNoSpellProgression(definition: ClassDefinition) {
        assertTrue(definition.levels.all { it.cantripsKnown == 0 })
        assertTrue(definition.levels.all { it.preparedSpellLimit == 0 })
        assertTrue(definition.levels.all { it.spellbookAdditions == 0 })
        assertTrue(definition.levels.all { it.spellSlots.isEmpty() })
        assertTrue(definition.levels.all { it.pactSlotCount == 0 && it.pactSlotLevel == 0 })
    }

    private fun assertSpellRow(
        definition: ClassDefinition,
        level: Int,
        cantrips: Int,
        prepared: Int,
        slots: List<Int>,
    ) {
        val row = definition.level(level)
        assertEquals(cantrips, row.cantripsKnown, "${definition.name} $level: trucchetti")
        assertEquals(prepared, row.preparedSpellLimit, "${definition.name} $level: preparati")
        assertEquals(slots, row.spellSlots, "${definition.name} $level: slot")
        assertEquals(0, row.pactSlotCount, "${definition.name} $level: slot del patto inattesi")
        assertEquals(0, row.pactSlotLevel, "${definition.name} $level: livello del patto inatteso")
    }

    private fun assertPactRow(
        row: ClassLevelDefinition,
        cantrips: Int,
        prepared: Int,
        count: Int,
        slotLevel: Int,
    ) {
        assertEquals(cantrips, row.cantripsKnown, "Warlock ${row.level}: trucchetti")
        assertEquals(prepared, row.preparedSpellLimit, "Warlock ${row.level}: preparati")
        assertEquals(count, row.pactSlotCount, "Warlock ${row.level}: slot del patto")
        assertEquals(slotLevel, row.pactSlotLevel, "Warlock ${row.level}: livello slot")
    }

    private fun assertResource(
        definition: ClassDefinition,
        level: Int,
        slug: String,
        maximum: Int,
        dieSides: Int = 0,
    ) {
        val expectedId = "$prefix:resource:${definition.id.contentId}:$slug"
        val resource = definition.level(level).resourceMaximums.single { it.resourceId == expectedId }
        assertEquals(
            ResourceMaximum(expectedId, maximum, dieSides),
            resource,
            "${definition.name} $level: risorsa $slug",
        )
    }

    private fun allChoices(definition: ClassDefinition): List<ChoiceDefinition> =
        listOfNotNull(
            definition.skillChoice,
            definition.toolChoice,
            definition.multiclassSkillChoice,
            definition.multiclassToolChoice,
        ) + definition.levels.flatMap { it.choices }

    private companion object {
        const val prefix = "srd521-it"
        val fullCasterLevel20 = listOf(4, 3, 3, 3, 3, 2, 2, 1, 1)
        val halfCasterLevel20 = listOf(4, 3, 3, 3, 2)
    }
}
