package app.d6d.content.srd521it

import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.LevelUpRequest
import app.d6d.rules.character.ResourceDefinition
import app.d6d.rules.character.ResourceFormula
import app.d6d.rules.character.SubclassSelection
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrdLimitedResourcesRegressionTest {
    private val pack = Srd521ItContent.pack
    private val service = GuidedCharacterService(pack)

    @Test
    fun `le nuove risorse dichiarano livello minimo e condizione SRD`() {
        val expected = listOf(
            ExpectedResource(CharacterClassId.BARBARIAN, "ira-persistente", 15),
            ExpectedResource(
                CharacterClassId.BARBARIAN,
                "presenza-intimidatoria",
                14,
                "srd521-it:subclass:cammino-del-berserker",
            ),
            ExpectedResource(CharacterClassId.CLERIC, "intervento-divino", 10),
            ExpectedResource(CharacterClassId.DRUID, "rinascita-selvatica", 5),
            ExpectedResource(
                CharacterClassId.DRUID,
                "recupero-naturale-slot",
                6,
                "srd521-it:subclass:circolo-della-terra",
            ),
            ExpectedResource(
                CharacterClassId.DRUID,
                "recupero-naturale-lancio",
                6,
                "srd521-it:subclass:circolo-della-terra",
            ),
            ExpectedResource(CharacterClassId.DRUID, "mago-della-natura", 20),
            ExpectedResource(CharacterClassId.WIZARD, "incantesimo-personale-1", 20),
            ExpectedResource(CharacterClassId.WIZARD, "incantesimo-personale-2", 20),
            ExpectedResource(CharacterClassId.MONK, "metabolismo-straordinario", 2),
            ExpectedResource(
                CharacterClassId.MONK,
                "integrita-del-corpo",
                6,
                "srd521-it:subclass:guerriero-della-mano-aperta",
                ResourceFormula.ABILITY_MODIFIER,
                Ability.WISDOM,
            ),
            ExpectedResource(CharacterClassId.PALADIN, "punizione-gratuita", 2),
            ExpectedResource(CharacterClassId.PALADIN, "fido-destriero", 5),
            ExpectedResource(
                CharacterClassId.PALADIN,
                "nube-sacra",
                20,
                "srd521-it:subclass:giuramento-di-devozione",
            ),
            ExpectedResource(
                CharacterClassId.RANGER,
                "instancabile",
                10,
                formula = ResourceFormula.ABILITY_MODIFIER,
                ability = Ability.WISDOM,
            ),
            ExpectedResource(
                CharacterClassId.RANGER,
                "velo-della-natura",
                14,
                formula = ResourceFormula.ABILITY_MODIFIER,
                ability = Ability.WISDOM,
            ),
            ExpectedResource(CharacterClassId.SORCERER, "ripristino-stregonesco", 5),
            ExpectedResource(
                CharacterClassId.SORCERER,
                "ali-di-drago",
                14,
                "srd521-it:subclass:stregoneria-draconica",
            ),
            ExpectedResource(
                CharacterClassId.SORCERER,
                "seguace-draconico",
                18,
                "srd521-it:subclass:stregoneria-draconica",
            ),
            ExpectedResource(CharacterClassId.WARLOCK, "scaltrezza-magica", 2),
            ExpectedResource(CharacterClassId.WARLOCK, "contatta-patrono", 9),
            ExpectedResource(
                CharacterClassId.WARLOCK,
                "fortuna-dell-oscuro",
                6,
                "srd521-it:subclass:patrono-immondo",
                ResourceFormula.ABILITY_MODIFIER,
                Ability.CHARISMA,
            ),
            ExpectedResource(
                CharacterClassId.WARLOCK,
                "scagliare-all-inferno",
                14,
                "srd521-it:subclass:patrono-immondo",
            ),
            ExpectedResource(
                CharacterClassId.WARLOCK,
                "dono-degli-abissi",
                5,
                "srd521-it:feature:warlock:dono-degli-abissi",
            ),
            ExpectedResource(
                CharacterClassId.WARLOCK,
                "dono-del-protettore",
                9,
                "srd521-it:feature:warlock:dono-del-protettore",
            ),
        )

        expected.forEach { expectation ->
            val resource = resource(expectation.classId, expectation.slug)
            assertEquals(
                expectation.availableFromClassLevel,
                resource.availableFromClassLevel,
                resource.id,
            )
            assertEquals(expectation.requiredOptionId, resource.requiredOptionId, resource.id)
            assertEquals(expectation.formula, resource.formula, resource.id)
            assertEquals(expectation.ability, resource.ability, resource.id)
        }
    }

    @Test
    fun `una risorsa compare dal proprio livello e non prima`() {
        val levelFour = advance(
            syntheticSheet(CharacterClassId.DRUID, currentClassLevel = 3),
            CharacterClassId.DRUID,
            forcedOptionIds = setOf("srd521-it:feat:origin:aggressore-selvaggio"),
        )
        assertFalse(levelFour.hasResource(CharacterClassId.DRUID, "rinascita-selvatica"))

        val levelFive = advance(
            levelFour.copy(experiencePoints = ExperienceProgression.thresholdForLevel(5)),
            CharacterClassId.DRUID,
        )
        assertTrue(levelFive.hasResource(CharacterClassId.DRUID, "rinascita-selvatica"))
    }

    @Test
    fun `le condizioni di sottoclasse e supplica filtrano le risorse`() {
        val withoutSubclass = advance(
            syntheticSheet(CharacterClassId.WARLOCK, currentClassLevel = 5),
            CharacterClassId.WARLOCK,
        )
        assertFalse(withoutSubclass.hasResource(CharacterClassId.WARLOCK, "fortuna-dell-oscuro"))

        val withSubclass = advance(
            syntheticSheet(
                classId = CharacterClassId.WARLOCK,
                currentClassLevel = 5,
                subclasses = listOf(
                    SubclassSelection(
                        CharacterClassId.WARLOCK,
                        "srd521-it:subclass:patrono-immondo",
                    ),
                ),
            ),
            CharacterClassId.WARLOCK,
        )
        assertTrue(withSubclass.hasResource(CharacterClassId.WARLOCK, "fortuna-dell-oscuro"))

        val withoutInvocation = advance(
            syntheticSheet(CharacterClassId.WARLOCK, currentClassLevel = 4),
            CharacterClassId.WARLOCK,
        )
        assertFalse(withoutInvocation.hasResource(CharacterClassId.WARLOCK, "dono-degli-abissi"))

        val withInvocation = advance(
            syntheticSheet(CharacterClassId.WARLOCK, currentClassLevel = 4),
            CharacterClassId.WARLOCK,
            forcedOptionIds = setOf("srd521-it:feature:warlock:dono-degli-abissi"),
        )
        assertTrue(withInvocation.hasResource(CharacterClassId.WARLOCK, "dono-degli-abissi"))
    }

    @Test
    fun `le risorse a modificatore usano il punteggio attuale e rispettano il minimo`() {
        val highWisdom = advance(
            syntheticSheet(
                classId = CharacterClassId.RANGER,
                currentClassLevel = 9,
                abilityScores = scoresWith(Ability.WISDOM, 18),
            ),
            CharacterClassId.RANGER,
        )
        assertEquals(
            4,
            highWisdom.resource(CharacterClassId.RANGER, "instancabile").maximum,
        )

        val lowWisdom = advance(
            syntheticSheet(
                classId = CharacterClassId.RANGER,
                currentClassLevel = 9,
                abilityScores = scoresWith(Ability.WISDOM, 8),
            ),
            CharacterClassId.RANGER,
        )
        assertEquals(
            1,
            lowWisdom.resource(CharacterClassId.RANGER, "instancabile").maximum,
        )
    }

    @Test
    fun `i due Incantesimi personali hanno utilizzi separati`() {
        val wizardLevelThreeSpells = pack.elements
            .filter { element ->
                element.spell?.level == 3 &&
                    element.classEligibility.any { it.classId == CharacterClassId.WIZARD }
            }
            .take(4)
            .map { it.id }
        assertEquals(4, wizardLevelThreeSpells.size)

        val levelTwenty = advance(
            syntheticSheet(
                classId = CharacterClassId.WIZARD,
                currentClassLevel = 19,
                spellbookSpellIds = wizardLevelThreeSpells,
            ),
            CharacterClassId.WIZARD,
        )
        val signaturePools = levelTwenty.progression.resourcePools.filter {
            it.resourceId.startsWith("srd521-it:resource:mago:incantesimo-personale-")
        }

        assertEquals(
            setOf(
                "srd521-it:resource:mago:incantesimo-personale-1",
                "srd521-it:resource:mago:incantesimo-personale-2",
            ),
            signaturePools.mapTo(mutableSetOf()) { it.resourceId },
        )
        assertTrue(signaturePools.all { it.maximum == 1 && it.remaining == 1 })
    }

    @Test
    fun `una supplica con incantesimo scelta dopo il primo livello lo rende sempre preparato`() {
        val levelTwo = advance(
            syntheticSheet(CharacterClassId.WARLOCK, currentClassLevel = 1),
            CharacterClassId.WARLOCK,
            forcedOptionIds = setOf(
                "srd521-it:feature:warlock:balzo-ultraterreno",
                "srd521-it:feature:warlock:maschera-dei-molti-volti",
            ),
        )

        assertTrue("srd521-it:spell:salto" in levelTwo.progression.alwaysPreparedSpellIds)
        assertTrue("srd521-it:spell:camuffare-se-stesso" in levelTwo.progression.alwaysPreparedSpellIds)
    }

    private fun syntheticSheet(
        classId: CharacterClassId,
        currentClassLevel: Int,
        abilityScores: Map<Ability, Int> = scoresWith(Ability.CONSTITUTION, 10),
        subclasses: List<SubclassSelection> = emptyList(),
        spellbookSpellIds: List<String> = emptyList(),
    ): CharacterSheet = CharacterSheet(
        experiencePoints = ExperienceProgression.thresholdForLevel(currentClassLevel + 1),
        abilityScores = abilityScores,
        progression = CharacterProgression(
            contentPackId = pack.manifest.id,
            contentPackVersion = pack.manifest.version,
            classLevels = listOf(ClassLevelState(classId, currentClassLevel)),
            subclasses = subclasses,
            spellbookSpellIds = spellbookSpellIds,
        ),
    )

    private fun advance(
        sheet: CharacterSheet,
        classId: CharacterClassId,
        forcedOptionIds: Set<String> = emptySet(),
    ): CharacterSheet {
        val classLevel = sheet.progression.levelIn(classId) + 1
        var selected = linkedMapOf<String, List<String>>()
        repeat(8) {
            val provisional = selected.toSelections()
            val requirements = service.requirements(sheet, classId, provisional)
            selected.keys.retainAll(requirements.mapTo(mutableSetOf()) { it.id })
            requirements.forEach { choice ->
                val options = SrdChoiceResolver.options(
                    choice = choice,
                    classId = classId,
                    classLevel = classLevel,
                    sheet = sheet,
                    provisionalSelections = selected.toSelections(),
                ).map { it.id }
                val forced = forcedOptionIds.filter { it in options }
                selected[choice.id] = (forced + options).distinct().take(choice.count)
            }
        }
        val requirements = service.requirements(sheet, classId, selected.toSelections())
        val request = LevelUpRequest(
            classId = classId,
            hitPointIncrease = service.fixedHitPointIncrease(sheet, classId),
            usedFixedHitPoints = true,
            selections = requirements.map { ChoiceSelection(it.id, selected[it.id].orEmpty()) },
        )
        val validation = service.validate(sheet, request)
        assertTrue(
            validation.valid,
            validation.issues.joinToString(" | ") { "${it.code}: ${it.message}" },
        )
        return service.advance(sheet, request)
    }

    private fun resource(classId: CharacterClassId, slug: String): ResourceDefinition =
        pack.classDefinition(classId).resources.single {
            it.id == resourceId(classId, slug)
        }

    private fun CharacterSheet.hasResource(classId: CharacterClassId, slug: String): Boolean =
        progression.resourcePools.any { it.resourceId == resourceId(classId, slug) }

    private fun CharacterSheet.resource(
        classId: CharacterClassId,
        slug: String,
    ) = progression.resourcePools.single { it.resourceId == resourceId(classId, slug) }

    private fun resourceId(classId: CharacterClassId, slug: String): String =
        "srd521-it:resource:${classId.contentId}:$slug"

    private fun scoresWith(ability: Ability, score: Int): Map<Ability, Int> =
        Ability.entries.associateWith { if (it == ability) score else 10 }

    private fun Map<String, List<String>>.toSelections(): List<ChoiceSelection> =
        map { (choiceId, optionIds) -> ChoiceSelection(choiceId, optionIds) }

    private data class ExpectedResource(
        val classId: CharacterClassId,
        val slug: String,
        val availableFromClassLevel: Int,
        val requiredOptionId: String? = null,
        val formula: ResourceFormula = ResourceFormula.FIXED,
        val ability: Ability? = null,
    )
}
