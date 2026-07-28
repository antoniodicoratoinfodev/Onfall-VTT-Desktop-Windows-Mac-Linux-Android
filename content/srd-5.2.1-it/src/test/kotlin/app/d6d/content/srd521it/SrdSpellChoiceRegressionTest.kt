package app.d6d.content.srd521it

import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.LevelUpRequest
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrdSpellChoiceRegressionTest {
    private val pack = Srd521ItContent.pack
    private val service = GuidedCharacterService(pack)

    @Test
    fun `Guerriero benedetto offre esattamente i trucchetti da chierico`() {
        assertConditionalCantripPool(
            classId = CharacterClassId.PALADIN,
            optionSuffix = ":guerriero-benedetto",
            spellListClass = CharacterClassId.CLERIC,
        )
    }

    @Test
    fun `Guerriero druidico offre esattamente i trucchetti da druido`() {
        assertConditionalCantripPool(
            classId = CharacterClassId.RANGER,
            optionSuffix = ":guerriero-druidico",
            spellListClass = CharacterClassId.DRUID,
        )
    }

    @Test
    fun `Scoperte magiche al sesto livello distingue trucchetti e incantesimi preparati`() {
        val sheet = sheetBeforeLevel(CharacterClassId.BARD, nextClassLevel = 6)
        val requirements = service.requirements(sheet, CharacterClassId.BARD)
        val discovery = requirements.single { it.kind == ChoiceKind.MAGICAL_DISCOVERY }
        val discoveryOptions = SrdChoiceResolver.options(
            discovery,
            CharacterClassId.BARD,
            6,
            sheet,
        )
        val cantrip = discoveryOptions.first { pack.element(it.id)?.spell?.level == 0 }
        val leveledSpell = discoveryOptions.first { pack.element(it.id)?.spell?.level ?: 0 > 0 }

        assertTrue(discoveryOptions.any { pack.element(it.id)?.spell?.level == 0 })
        assertTrue(discoveryOptions.any { pack.element(it.id)?.spell?.level ?: 0 > 0 })

        val discoverySelection = ChoiceSelection(
            discovery.id,
            listOf(cantrip.id, leveledSpell.id),
        )
        val selections = requirements.map { requirement ->
            if (requirement.id == discovery.id) {
                discoverySelection
            } else {
                val options = SrdChoiceResolver.options(
                    requirement,
                    CharacterClassId.BARD,
                    6,
                    sheet,
                    listOf(discoverySelection),
                )
                ChoiceSelection(
                    requirement.id,
                    options
                        .filterNot { it.id == cantrip.id || it.id == leveledSpell.id }
                        .take(requirement.count)
                        .map { it.id },
                )
            }
        }
        val request = LevelUpRequest(
            classId = CharacterClassId.BARD,
            hitPointIncrease = service.fixedHitPointIncrease(sheet, CharacterClassId.BARD),
            usedFixedHitPoints = true,
            selections = selections,
        )

        assertTrue(service.validate(sheet, request).valid)
        val advanced = service.advance(sheet, request)

        assertTrue(cantrip.id in advanced.progression.knownCantripIds)
        assertFalse(cantrip.id in advanced.progression.alwaysPreparedSpellIds)
        assertTrue(leveledSpell.id in advanced.progression.alwaysPreparedSpellIds)
        assertFalse(leveledSpell.id in advanced.progression.knownCantripIds)
    }

    @Test
    fun `dal decimo livello il nuovo incantesimo del bardo usa Segreti magici`() {
        val sheet = sheetBeforeLevel(CharacterClassId.BARD, nextClassLevel = 10)
        val preparedSpellRequirement = service.requirements(sheet, CharacterClassId.BARD)
            .single {
                it.kind == ChoiceKind.PREPARED_SPELL &&
                    it.id.endsWith(":prepared-spells")
            }

        assertEquals(
            "srd521-it:pool:spells:bardo:magical-secrets",
            preparedSpellRequirement.poolId,
        )
    }

    private fun assertConditionalCantripPool(
        classId: CharacterClassId,
        optionSuffix: String,
        spellListClass: CharacterClassId,
    ) {
        val sheet = sheetBeforeLevel(classId, nextClassLevel = 2)
        val baseRequirements = service.requirements(sheet, classId)
        val fightingStyle = baseRequirements.single { it.kind == ChoiceKind.FIGHTING_STYLE }
        val selectedOption = fightingStyle.optionIds.single { it.endsWith(optionSuffix) }
        val provisional = listOf(
            ChoiceSelection(fightingStyle.id, listOf(selectedOption)),
        )
        val cantripChoice = service.requirements(sheet, classId, provisional)
            .single {
                it.kind == ChoiceKind.CANTRIP &&
                    it.id.startsWith("${fightingStyle.id}:")
            }
        val actualIds = SrdChoiceResolver.options(
            cantripChoice,
            classId,
            2,
            sheet,
            provisional,
        ).mapTo(mutableSetOf()) { it.id }
        val expectedIds = pack.elements
            .filter { element ->
                element.spell?.level == 0 &&
                    element.classEligibility.any {
                        it.classId == spellListClass && it.minimumLevel <= 2
                    }
            }
            .mapTo(mutableSetOf()) { it.id }

        assertTrue(actualIds.isNotEmpty())
        assertEquals(expectedIds, actualIds)
    }

    private fun sheetBeforeLevel(
        classId: CharacterClassId,
        nextClassLevel: Int,
    ): CharacterSheet = CharacterSheet(
        experiencePoints = ExperienceProgression.thresholdForLevel(nextClassLevel),
        progression = CharacterProgression(
            contentPackId = pack.manifest.id,
            contentPackVersion = pack.manifest.version,
            classLevels = listOf(ClassLevelState(classId, nextClassLevel - 1)),
        ),
    )
}
