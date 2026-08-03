package app.d6d.content.srd521it

import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.LevelUpRequest
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import app.d6d.sheet.Proficiency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrdBackgroundsAndEquipmentTest {
    private val pack = Srd521ItContent.pack
    private val service = GuidedCharacterService(pack)

    @Test
    fun `background e pacchetti iniziali coprono tutti i dati distribuiti nello SRD`() {
        assertEquals(4, pack.backgrounds.size)
        assertEquals(33, pack.equipmentPackages.size)
        assertTrue(pack.equipmentPackages.flatMap { it.weaponIds }.all { pack.weapon(it) != null })
        pack.backgrounds.forEach { background ->
            assertEquals(3, background.abilityOptions.size)
            assertEquals(2, background.skillProficiencies.size)
            assertTrue(background.equipmentChoice.optionIds.all { pack.equipmentPackage(it) != null })
        }
        pack.classes.forEach { definition ->
            assertEquals(ChoiceKind.STARTING_EQUIPMENT, definition.startingEquipmentChoice?.kind)
            assertEquals(null, definition.startingWeaponChoice)
            assertTrue(
                definition.startingEquipmentChoice.orEmptyOptions().all {
                    pack.equipmentPackage(it) != null
                },
            )
        }
    }

    @Test
    fun `il background Soldato e le due dotazioni si applicano una sola volta alla scheda`() {
        val draft = CharacterSheet(abilityScores = Ability.entries.associateWith { 10 })
        val selections = selectionsFor(draft, CharacterClassId.FIGHTER)
        val request = LevelUpRequest(
            classId = CharacterClassId.FIGHTER,
            hitPointIncrease = service.fixedHitPointIncrease(draft, CharacterClassId.FIGHTER),
            usedFixedHitPoints = true,
            selections = selections,
            backgroundAbilityScoreIncreases = mapOf(
                Ability.STRENGTH to 2,
                Ability.DEXTERITY to 1,
            ),
        )

        assertTrue(service.validate(draft, request).valid)
        val created = service.advance(draft, request)

        assertEquals("srd521-it:background:soldato", created.progression.backgroundId)
        assertEquals("Soldato", created.background)
        assertEquals(12, created.abilityScores[Ability.STRENGTH])
        assertEquals(11, created.abilityScores[Ability.DEXTERITY])
        assertEquals(Proficiency.PROFICIENT, created.skillProficiencies[app.d6d.rules.character.Skill.ATLETICA])
        assertEquals(Proficiency.PROFICIENT, created.skillProficiencies[app.d6d.rules.character.Skill.INTIMIDIRE])
        assertTrue("Dadi" in created.toolProficiencies)
        assertTrue("srd521-it:feat:origin:aggressore-selvaggio" in created.progression.featIds)
        assertEquals(ArmorClassMethod.CHAIN_MAIL, created.armorClassMethod)
        assertEquals(
            setOf("Lancia", "Arco corto", "Spadone", "Mazzafrusto", "Giavellotto"),
            created.weapons.mapTo(mutableSetOf()) { it.name },
        )
        assertEquals(18, created.money.gold)
        assertTrue(created.equipment.contains("Lancia, arco corto"))
        assertTrue(created.equipment.contains("Cotta di maglia, spadone"))
        assertEquals(
            mapOf(Ability.STRENGTH to 2, Ability.DEXTERITY to 1),
            created.progression.advancementHistory.single().backgroundAbilityScoreIncreases,
        )

        val atSecond = created.copy(experiencePoints = ExperienceProgression.thresholdForLevel(2))
        val advanced = service.advance(
            atSecond,
            LevelUpRequest(
                classId = CharacterClassId.FIGHTER,
                hitPointIncrease = service.fixedHitPointIncrease(atSecond, CharacterClassId.FIGHTER),
                usedFixedHitPoints = true,
                selections = selectionsFor(atSecond, CharacterClassId.FIGHTER),
            ),
        )
        assertEquals(created.abilityScores, advanced.abilityScores)
        assertEquals(created.money, advanced.money)
        assertEquals(created.equipment, advanced.equipment)
    }

    @Test
    fun `gli aumenti del background rifiutano distribuzioni e caratteristiche non ammesse`() {
        val draft = CharacterSheet(abilityScores = Ability.entries.associateWith { 10 })
        val selections = selectionsFor(draft, CharacterClassId.FIGHTER)

        val validation = service.validate(
            draft,
            LevelUpRequest(
                classId = CharacterClassId.FIGHTER,
                hitPointIncrease = 10,
                usedFixedHitPoints = true,
                selections = selections,
                backgroundAbilityScoreIncreases = mapOf(Ability.WISDOM to 2, Ability.DEXTERITY to 1),
            ),
        )

        assertFalse(validation.valid)
        assertTrue(validation.issues.any { it.code == "BACKGROUND_ABILITY_SCORE_INCREASE" })
    }

    @Test
    fun `Accolito fissa Iniziato alla magia sulla lista del chierico`() {
        val draft = CharacterSheet()
        val background = ChoiceSelection(
            "srd521-it:choice:origin:background",
            listOf("srd521-it:background:accolito"),
        )
        val requirements = service.requirements(draft, CharacterClassId.FIGHTER, listOf(background))
        val listChoice = requirements.single { it.id.endsWith(":magic-initiate:list") }

        assertEquals(listOf("srd521-it:spell-list:chierico"), listChoice.optionIds)
        val provisional = listOf(
            background,
            ChoiceSelection(listChoice.id, listChoice.optionIds),
        )
        val cantripChoice = service.requirements(draft, CharacterClassId.FIGHTER, provisional)
            .first { it.id.endsWith(":magic-initiate:cantrips") }
        val options = SrdChoiceResolver.options(
            cantripChoice,
            CharacterClassId.FIGHTER,
            1,
            draft,
            provisional,
        )
        assertTrue(options.isNotEmpty())
        assertTrue(
            options.all { option ->
                pack.element(option.id)?.classEligibility?.any {
                    it.classId == CharacterClassId.CLERIC
                } == true
            },
        )
    }

    @Test
    fun `le competenze negli strumenti usano i nomi SRD ed evitano quella gia concessa dal background`() {
        val draft = CharacterSheet()
        val background = ChoiceSelection(
            "srd521-it:choice:origin:background",
            listOf("srd521-it:background:accolito"),
        )
        val monkToolChoice = service.requirements(draft, CharacterClassId.MONK, listOf(background))
            .single { it.id == "srd521-it:choice:monaco:initial:tool" }
        val options = SrdChoiceResolver.options(
            monkToolChoice,
            CharacterClassId.MONK,
            1,
            draft,
            listOf(background),
        )

        assertTrue(options.any { it.label == "Scorte da alchimista" })
        assertTrue(options.any { it.label == "Strumenti da inventore" })
        assertFalse(options.any { it.label == "Scorte da calligrafo" })
        assertFalse(options.any { it.label == "Strumenti da calligrafo" })
    }

    @Test
    fun `Criminale e Ladro propongono uno strumento sostitutivo invece di duplicare gli arnesi da scasso`() {
        val draft = CharacterSheet()
        val provisional = listOf(
            ChoiceSelection(
                "srd521-it:choice:origin:background",
                listOf("srd521-it:background:criminale"),
            ),
            ChoiceSelection(
                "srd521-it:choice:background:criminale:tool",
                listOf("srd521-it:tool:arnesi-da-scasso"),
            ),
        )
        val rogueToolChoice = service.requirements(draft, CharacterClassId.ROGUE, provisional)
            .single { it.id == "srd521-it:choice:ladro:initial:tool:sostitutiva" }
        val options = SrdChoiceResolver.options(
            rogueToolChoice,
            CharacterClassId.ROGUE,
            1,
            draft,
            provisional,
        )

        assertEquals("srd521-it:pool:tools:any", rogueToolChoice.poolId)
        assertTrue(options.isNotEmpty())
        assertFalse(options.any { it.id == "srd521-it:tool:arnesi-da-scasso" })

        val selected = linkedMapOf<String, List<String>>()
        repeat(8) {
            val current = selected.map { ChoiceSelection(it.key, it.value) }
            val requirements = service.requirements(draft, CharacterClassId.ROGUE, current)
            selected.keys.retainAll(requirements.mapTo(mutableSetOf()) { it.id })
            requirements.forEach { choice ->
                val available = SrdChoiceResolver.options(
                    choice,
                    CharacterClassId.ROGUE,
                    1,
                    draft,
                    selected.map { ChoiceSelection(it.key, it.value) },
                )
                selected[choice.id] = when (choice.id) {
                    "srd521-it:choice:origin:background" ->
                        listOf("srd521-it:background:criminale")
                    "srd521-it:choice:background:criminale:tool" ->
                        listOf("srd521-it:tool:arnesi-da-scasso")
                    "srd521-it:choice:background:criminale:equipment" ->
                        listOf("srd521-it:equipment:background:criminale:b")
                    "srd521-it:choice:class:ladro:equipment" ->
                        listOf("srd521-it:equipment:class:ladro:b")
                    else -> available.take(choice.count).map { it.id }
                }
            }
        }
        val completeSelections = service.requirements(
            draft,
            CharacterClassId.ROGUE,
            selected.map { ChoiceSelection(it.key, it.value) },
        ).map { ChoiceSelection(it.id, selected[it.id].orEmpty()) }
        val request = LevelUpRequest(
            classId = CharacterClassId.ROGUE,
            hitPointIncrease = service.fixedHitPointIncrease(draft, CharacterClassId.ROGUE),
            usedFixedHitPoints = true,
            selections = completeSelections,
            backgroundAbilityScoreIncreases = mapOf(Ability.DEXTERITY to 2, Ability.CONSTITUTION to 1),
        )
        val validation = service.validate(draft, request)

        assertTrue(
            validation.valid,
            validation.issues.joinToString(" | ") { "${it.code}: ${it.message}" },
        )
        val created = service.advance(draft, request)
        assertTrue(created.toolProficiencies.contains("Arnesi da scasso"))
        assertTrue(created.toolProficiencies.split(',').size >= 2)
    }

    private fun selectionsFor(
        sheet: CharacterSheet,
        classId: CharacterClassId,
    ): List<ChoiceSelection> {
        val classLevel = sheet.progression.levelIn(classId) + 1
        val selected = linkedMapOf<String, List<String>>()
        repeat(8) {
            val provisional = selected.map { ChoiceSelection(it.key, it.value) }
            val requirements = service.requirements(sheet, classId, provisional)
            selected.keys.retainAll(requirements.mapTo(mutableSetOf()) { it.id })
            requirements.forEach { choice ->
                val options = SrdChoiceResolver.options(
                    choice,
                    classId,
                    classLevel,
                    sheet,
                    selected.map { ChoiceSelection(it.key, it.value) },
                )
                selected[choice.id] = when (choice.id) {
                    "srd521-it:choice:origin:background" ->
                        listOf("srd521-it:background:soldato")
                    "srd521-it:choice:background:soldato:tool" ->
                        listOf("srd521-it:tool:dadi")
                    "srd521-it:choice:background:soldato:equipment" ->
                        listOf("srd521-it:equipment:background:soldato:a")
                    "srd521-it:choice:class:guerriero:equipment" ->
                        listOf("srd521-it:equipment:class:guerriero:a")
                    else -> options.take(choice.count).map { it.id }
                }
            }
        }
        return service.requirements(
            sheet,
            classId,
            selected.map { ChoiceSelection(it.key, it.value) },
        ).map { ChoiceSelection(it.id, selected[it.id].orEmpty()) }
    }
}

private fun app.d6d.rules.character.ChoiceDefinition?.orEmptyOptions(): List<String> =
    this?.optionIds.orEmpty()
