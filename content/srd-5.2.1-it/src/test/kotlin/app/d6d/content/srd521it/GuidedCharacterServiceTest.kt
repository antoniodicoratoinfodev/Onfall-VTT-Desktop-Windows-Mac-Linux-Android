package app.d6d.content.srd521it

import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.LevelUpRequest
import app.d6d.sheet.ArmorCategory
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import app.d6d.sheet.Proficiency
import app.d6d.sheet.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidedCharacterServiceTest {
    private val service = GuidedCharacterService(Srd521ItContent.pack)

    @Test
    fun `creazione guidata applica tratti competenze PF e risorse del guerriero`() {
        val draft = CharacterSheet(
            abilityScores = Ability.entries.associateWith { if (it == Ability.CONSTITUTION) 14 else 12 },
        )
        val selections = selectionsFor(draft, CharacterClassId.FIGHTER)
        val hp = service.fixedHitPointIncrease(draft, CharacterClassId.FIGHTER)
        val created = service.advance(
            draft,
            LevelUpRequest(CharacterClassId.FIGHTER, hp, true, selections),
        )

        assertEquals(1, created.progression.totalLevel)
        assertEquals(12, created.maxHitPoints)
        assertEquals(Proficiency.PROFICIENT, created.saveProficiencies[Ability.STRENGTH])
        assertEquals(2, created.skillProficiencies.values.count { it == Proficiency.PROFICIENT })
        assertTrue(created.progression.resourcePools.any { it.name == "Recuperare energie" })
        assertTrue(created.abilityIds.any { it == "srd521-it:action:attacco" })
    }

    @Test
    fun `i PE notificano il livello ma l'avanzamento resta esplicito`() {
        val levelOne = createSimple(CharacterClassId.BARBARIAN)
        val withXp = levelOne.copy(experiencePoints = 300)
        assertTrue(withXp.canLevelUp)
        assertEquals(1, withXp.progression.totalLevel)

        val request = requestFor(withXp, CharacterClassId.BARBARIAN)
        val levelTwo = service.advance(withXp, request)
        assertEquals(2, levelTwo.progression.totalLevel)
        assertFalse(levelTwo.canLevelUp)
    }

    @Test
    fun `gli incantesimi preparati del mago devono provenire dal libro`() {
        val draft = CharacterSheet()
        val requirements = service.requirements(draft, CharacterClassId.WIZARD)
        val spellbook = requirements.first { it.kind == ChoiceKind.SPELLBOOK_SPELL }
        val prepared = requirements.first { it.kind == ChoiceKind.PREPARED_SPELL }
        val base = selectionsFor(draft, CharacterClassId.WIZARD).toMutableList()
        val bookSelection = base.first { it.choiceId == spellbook.id }
        val preparedIndex = base.indexOfFirst { it.choiceId == prepared.id }
        base[preparedIndex] = ChoiceSelection(prepared.id, bookSelection.optionIds.take(prepared.count))
        val request = LevelUpRequest(
            CharacterClassId.WIZARD,
            service.fixedHitPointIncrease(draft, CharacterClassId.WIZARD),
            true,
            base,
        )
        assertTrue(service.validate(draft, request).valid)
    }

    @Test
    fun `iniziato alla magia crea incantesimi e lancio gratuito anche su un non incantatore`() {
        val draft = CharacterSheet()
        val selections = selectionsFor(
            draft,
            CharacterClassId.FIGHTER,
            originFeatId = "srd521-it:feat:origin:iniziato-alla-magia",
        )
        val created = service.advance(
            draft,
            LevelUpRequest(
                CharacterClassId.FIGHTER,
                service.fixedHitPointIncrease(draft, CharacterClassId.FIGHTER),
                true,
                selections,
            ),
        )

        assertEquals(3, created.spellcasting?.spells?.size)
        assertTrue(created.spellcasting?.slots?.all { it.total == 0 } == true)
        assertTrue(
            created.progression.resourcePools.any {
                it.name.startsWith("Iniziato alla magia") && it.maximum == 1
            },
        )
    }

    @Test
    fun `ordini e sottoclassi applicano addestramenti trucchetti e incantesimi sempre preparati`() {
        val cleric = createSimple(CharacterClassId.CLERIC)
        assertTrue(cleric.armorTraining.heavy)
        assertTrue("Armi da guerra" in cleric.weaponProficiencies)

        val druid = createSimple(CharacterClassId.DRUID)
        assertEquals(3, druid.progression.knownCantripIds.size)

        var lifeCleric = cleric
        for (targetLevel in 2..3) {
            lifeCleric = lifeCleric.copy(
                experiencePoints = app.d6d.rules.character.ExperienceProgression
                    .thresholdForLevel(targetLevel),
            )
            lifeCleric = service.advance(
                lifeCleric,
                requestFor(lifeCleric, CharacterClassId.CLERIC),
            )
        }
        assertEquals(4, lifeCleric.progression.alwaysPreparedSpellIds.size)
        assertTrue(
            lifeCleric.spellcasting?.spells?.any {
                it.name == "Benedizione" && it.note == "Sempre preparato"
            } == true,
        )
    }

    @Test
    fun `un aumento di Carisma aggiorna subito gli usi di Ispirazione bardica`() {
        var bard = CharacterSheet(
            abilityScores = Ability.entries.associateWith {
                if (it == Ability.CHARISMA) 16 else 14
            },
        )
        for (targetLevel in 1..3) {
            bard = bard.copy(
                experiencePoints = app.d6d.rules.character.ExperienceProgression
                    .thresholdForLevel(targetLevel),
            )
            bard = service.advance(bard, requestFor(bard, CharacterClassId.BARD))
        }
        bard = bard.copy(experiencePoints = 2_700)
        val selections = selectionsFor(bard, CharacterClassId.BARD)
            .filterNot {
                it.choiceId.contains(":origin:abile:") ||
                    it.choiceId.contains(":magic-initiate:") ||
                    it.choiceId.endsWith(":ability-increase")
            }
            .map { selection ->
                if (selection.choiceId.endsWith(":4:aumento-o-talento")) {
                    ChoiceSelection(
                        selection.choiceId,
                        listOf("srd521-it:feat:general:aumento-punteggi-caratteristica"),
                    )
                } else {
                    selection
                }
            }
        bard = service.advance(
            bard,
            LevelUpRequest(
                CharacterClassId.BARD,
                service.fixedHitPointIncrease(bard, CharacterClassId.BARD),
                true,
                selections,
                abilityScoreIncreases = mapOf(Ability.CHARISMA to 2),
            ),
        )

        assertEquals(18, bard.abilityScores[Ability.CHARISMA])
        assertEquals(
            4,
            bard.progression.resourcePools.single { it.name == "Ispirazione bardica" }.maximum,
        )
    }

    @Test
    fun `barbaro e monaco adottano alla creazione la propria difesa senza armatura`() {
        val barbarianDraft = CharacterSheet(
            abilityScores = Ability.entries.associateWith {
                when (it) {
                    Ability.DEXTERITY -> 16
                    Ability.CONSTITUTION -> 14
                    else -> 10
                }
            },
        )
        val barbarian = service.advance(
            barbarianDraft,
            requestFor(barbarianDraft, CharacterClassId.BARBARIAN),
        )

        assertEquals(ArmorClassMethod.BARBARIAN_UNARMORED, barbarian.armorClassMethod)
        assertEquals(15, barbarian.effectiveArmorClass)

        val monkDraft = CharacterSheet(
            abilityScores = Ability.entries.associateWith {
                when (it) {
                    Ability.DEXTERITY -> 16
                    Ability.WISDOM -> 18
                    else -> 10
                }
            },
        )
        val monk = service.advance(
            monkDraft,
            requestFor(monkDraft, CharacterClassId.MONK),
        )

        assertEquals(ArmorClassMethod.MONK_UNARMORED, monk.armorClassMethod)
        assertEquals(17, monk.effectiveArmorClass)
    }

    @Test
    fun `la creazione non sostituisce una CA manuale che dichiara un armatura`() {
        val draft = CharacterSheet(
            armorClass = 10,
            armorClassMethod = ArmorClassMethod.MANUAL_TOTAL,
            manualArmorCategory = ArmorCategory.LIGHT,
        )

        val barbarian = service.advance(
            draft,
            requestFor(draft, CharacterClassId.BARBARIAN),
        )

        assertEquals(ArmorClassMethod.MANUAL_TOTAL, barbarian.armorClassMethod)
        assertEquals(ArmorCategory.LIGHT, barbarian.wornArmorCategory)
    }

    @Test
    fun `resilienza draconica applica CA e PF retroattivi al terzo e uno ai livelli seguenti`() {
        var sorcerer = CharacterSheet(
            abilityScores = Ability.entries.associateWith {
                when (it) {
                    Ability.DEXTERITY -> 14
                    Ability.CONSTITUTION -> 14
                    Ability.CHARISMA -> 16
                    else -> 10
                }
            },
        )
        for (targetLevel in 1..2) {
            sorcerer = sorcerer.copy(
                experiencePoints = ExperienceProgression.thresholdForLevel(targetLevel),
            )
            sorcerer = service.advance(
                sorcerer,
                requestFor(sorcerer, CharacterClassId.SORCERER),
            )
        }

        val hitPointsBeforeSubclass = sorcerer.maxHitPoints
        sorcerer = sorcerer.copy(
            experiencePoints = ExperienceProgression.thresholdForLevel(3),
        )
        sorcerer = service.advance(
            sorcerer,
            requestFor(sorcerer, CharacterClassId.SORCERER),
        )

        assertEquals(
            service.fixedHitPointIncrease(sorcerer, CharacterClassId.SORCERER) + 3,
            sorcerer.maxHitPoints - hitPointsBeforeSubclass,
        )
        assertEquals(ArmorClassMethod.DRACONIC_RESILIENCE, sorcerer.armorClassMethod)
        assertEquals(15, sorcerer.effectiveArmorClass)

        val hitPointsAtThirdLevel = sorcerer.maxHitPoints
        sorcerer = sorcerer.copy(
            experiencePoints = ExperienceProgression.thresholdForLevel(4),
        )
        val fourthLevelSelections = selectionsFor(sorcerer, CharacterClassId.SORCERER)
            .filterNot { it.choiceId.contains(":origin:abile:") }
            .map { selection ->
                if (selection.choiceId.endsWith(":4:aumento-o-talento")) {
                    ChoiceSelection(
                        selection.choiceId,
                        listOf("srd521-it:feat:general:aumento-punteggi-caratteristica"),
                    )
                } else {
                    selection
                }
            }
        sorcerer = service.advance(
            sorcerer,
            LevelUpRequest(
                classId = CharacterClassId.SORCERER,
                hitPointIncrease = service.fixedHitPointIncrease(
                    sorcerer,
                    CharacterClassId.SORCERER,
                ),
                usedFixedHitPoints = true,
                selections = fourthLevelSelections,
                abilityScoreIncreases = mapOf(Ability.INTELLIGENCE to 2),
            ),
        )

        assertEquals(7, sorcerer.maxHitPoints - hitPointsAtThirdLevel)
        assertEquals(15, sorcerer.effectiveArmorClass)
    }

    @Test
    fun `druido e ladro ricevono le rispettive lingue fisse alla creazione`() {
        val druid = createSimple(CharacterClassId.DRUID)
        val rogue = createSimple(CharacterClassId.ROGUE)

        assertEquals("Druidico", druid.languages)
        assertEquals("Gergo ladresco", rogue.languages)
    }

    @Test
    fun `il ranger sceglie e riceve due lingue al secondo livello`() {
        var ranger = createSimple(CharacterClassId.RANGER)
        ranger = ranger.copy(
            experiencePoints = ExperienceProgression.thresholdForLevel(2),
        )
        ranger = service.advance(ranger, requestFor(ranger, CharacterClassId.RANGER))

        val languageSelection = ranger.progression.selections.single {
            it.choiceId.endsWith(":ranger:2:lingue")
        }
        assertEquals(2, languageSelection.optionIds.distinct().size)
        assertEquals("Comune, Lingua dei segni comune", ranger.languages)
    }

    @Test
    fun `mente sfuggente ed esperto disciplinato aggiungono le competenze nei tiri salvezza`() {
        val rogueAtFourteen = CharacterSheet(
            experiencePoints = ExperienceProgression.thresholdForLevel(15),
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.ROGUE, 14)),
            ),
            saveProficiencies = mapOf(
                Ability.DEXTERITY to Proficiency.PROFICIENT,
                Ability.INTELLIGENCE to Proficiency.PROFICIENT,
            ),
        )
        val rogueAtFifteen = service.advance(
            rogueAtFourteen,
            requestFor(rogueAtFourteen, CharacterClassId.ROGUE),
        )

        assertEquals(Proficiency.PROFICIENT, rogueAtFifteen.saveProficiencies[Ability.WISDOM])
        assertEquals(Proficiency.PROFICIENT, rogueAtFifteen.saveProficiencies[Ability.CHARISMA])

        val monkAtThirteen = CharacterSheet(
            experiencePoints = ExperienceProgression.thresholdForLevel(14),
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.MONK, 13)),
            ),
            saveProficiencies = mapOf(
                Ability.STRENGTH to Proficiency.PROFICIENT,
                Ability.DEXTERITY to Proficiency.PROFICIENT,
            ),
        )
        val monkAtFourteen = service.advance(
            monkAtThirteen,
            requestFor(monkAtThirteen, CharacterClassId.MONK),
        )

        Ability.entries.forEach {
            assertEquals(
                Proficiency.PROFICIENT,
                monkAtFourteen.saveProficiencies[it],
                "Il Monaco di 14º livello deve essere competente nel TS di ${it.italianLabel}.",
            )
        }
    }

    private fun createSimple(classId: CharacterClassId): CharacterSheet {
        val draft = CharacterSheet()
        return service.advance(draft, requestFor(draft, classId))
    }

    private fun requestFor(sheet: CharacterSheet, classId: CharacterClassId): LevelUpRequest {
        return LevelUpRequest(
            classId,
            service.fixedHitPointIncrease(sheet, classId),
            true,
            selectionsFor(sheet, classId),
        )
    }

    private fun selectionsFor(
        sheet: CharacterSheet,
        classId: CharacterClassId,
        originFeatId: String = "srd521-it:feat:origin:aggressore-selvaggio",
    ): List<ChoiceSelection> {
        val classLevel = sheet.progression.levelIn(classId) + 1
        var selected = linkedMapOf<String, List<String>>()
        repeat(4) {
            val provisional = selected.map { ChoiceSelection(it.key, it.value) }
            val requirements = service.requirements(sheet, classId, provisional)
            val currentIds = requirements.mapTo(mutableSetOf()) { it.id }
            selected = selected.filterKeys { it in currentIds }.toMap(LinkedHashMap())
            requirements.forEach { choice ->
                val options = SrdChoiceResolver.options(
                    choice,
                    classId,
                    classLevel,
                    sheet,
                    selected.map { ChoiceSelection(it.key, it.value) },
                )
                val chosen = if (choice.poolId?.endsWith("feats:origin") == true) {
                    listOf(originFeatId)
                } else {
                    options.take(choice.count).map { it.id }
                }
                selected[choice.id] = chosen
            }
        }
        val requirements = service.requirements(
            sheet,
            classId,
            selected.map { ChoiceSelection(it.key, it.value) },
        )
        return requirements.map { ChoiceSelection(it.id, selected[it.id].orEmpty()) }
    }
}
