package app.d6d.rules.character

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CharacterProgressionEngineTest {

    private val pack = RulesContentPack(
        manifest = ContentPackManifest(
            id = "test-pack",
            version = "1",
            rulesetVersion = "5.2.1",
            locale = "it",
            title = "Test",
            sourceUrl = "https://example.test",
            license = "CC-BY-4.0",
            attribution = "Test",
        ),
        classes = listOf(testClass(CharacterClassId.FIGHTER), testClass(CharacterClassId.WIZARD)),
        elements = listOf(
            RuleElementDefinition(
                id = "spell:mago:luce",
                name = "Luce",
                kind = RuleElementKind.CANTRIP,
                description = "Produce luce.",
                classEligibility = listOf(ClassEligibility(CharacterClassId.WIZARD)),
                spell = SpellDetails(0, "Invocazione", "azione", "contatto", "V, M", "1 ora"),
            ),
        ),
    )
    private val engine = CharacterProgressionEngine(pack)

    @Test
    fun `una definizione risorsa salvata prima dei nuovi vincoli usa default retrocompatibili`() {
        val decoded = Json.decodeFromString<ResourceDefinition>(
            """
            {
              "id": "test:legacy",
              "name": "Risorsa legacy",
              "recovery": "LONG_REST"
            }
            """.trimIndent(),
        )

        assertEquals(ResourceFormula.TABLE, decoded.formula)
        assertEquals(1, decoded.fixedMaximum)
        assertEquals(1, decoded.availableFromClassLevel)
        assertEquals(null, decoded.requiredOptionId)
    }

    @Test
    fun `una scelta salvata prima delle sostituzioni usa default retrocompatibili`() {
        val decoded = Json.decodeFromString<ChoiceDefinition>(
            """
            {
              "id": "test:legacy-choice",
              "title": "Scelta legacy",
              "kind": "CLASS_OPTION",
              "count": 2,
              "optionIds": ["test:a", "test:b"]
            }
            """.trimIndent(),
        )

        assertEquals(2, decoded.minimumCount)
        assertEquals(null, decoded.requiredOptionId)
        assertEquals(ChoiceReplacementWindow.NEVER, decoded.replacementWindow)
        assertEquals(null, decoded.replacesChoiceId)
    }

    @Test
    fun `il riposo breve recupera solo la quantita prevista dalla risorsa`() {
        val partial = ResourcePoolState(
            resourceId = "test:partial",
            name = "Parziale",
            maximum = 4,
            spent = 3,
            recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
            shortRestRecovery = 1,
        )
        assertEquals(2, partial.recoveredAfter(RecoveryPeriod.SHORT_REST).spent)
        assertEquals(0, partial.recoveredAfter(RecoveryPeriod.LONG_REST).spent)

        val full = partial.copy(resourceId = "test:full", shortRestRecovery = 0)
        assertEquals(0, full.recoveredAfter(RecoveryPeriod.SHORT_REST).spent)

        val longOnly = partial.copy(
            resourceId = "test:long",
            recovery = RecoveryPeriod.LONG_REST,
            shortRestRecovery = 0,
        )
        assertEquals(3, longOnly.recoveredAfter(RecoveryPeriod.SHORT_REST).spent)
    }

    @Test
    fun `primo livello richiede competenze e scelte della tabella`() {
        val requirements = engine.requirementsFor(CharacterProgression(), CharacterClassId.FIGHTER)
        assertEquals(listOf("guerriero:skills", "guerriero:1:style"), requirements.map { it.id })

        val invalid = engine.validateLevelUp(
            progression = CharacterProgression(),
            experiencePoints = 0,
            abilityScores = scores(strength = 15, intelligence = 13),
            request = LevelUpRequest(CharacterClassId.FIGHTER, 10, true, emptyList()),
        )
        assertFalse(invalid.valid)
        assertTrue(invalid.issues.any { it.code == "CHOICE_COUNT" })
    }

    @Test
    fun `applicazione del livello registra storia privilegi e risorse`() {
        val request = LevelUpRequest(
            classId = CharacterClassId.FIGHTER,
            hitPointIncrease = 10,
            usedFixedHitPoints = false,
            selections = listOf(
                ChoiceSelection("guerriero:skills", listOf(Skill.ATLETICA.name, Skill.PERCEZIONE.name)),
                ChoiceSelection("guerriero:1:style", listOf("style:difesa")),
            ),
        )
        val result = engine.applyLevelUp(
            CharacterProgression(),
            0,
            scores(strength = 15, intelligence = 13),
            request,
        )

        assertEquals(1, result.totalLevel)
        assertEquals(1, result.levelIn(CharacterClassId.FIGHTER))
        assertEquals(
            listOf("feature:guerriero:recuperare", "style:difesa"),
            result.selectedFeatureIds,
        )
        assertEquals(1, result.advancementHistory.size)
        assertEquals(2, result.resourcePools.single().maximum)
    }

    @Test
    fun `il passaggio al livello successivo richiede la soglia PE`() {
        val first = engine.applyLevelUp(
            CharacterProgression(),
            0,
            scores(strength = 15, intelligence = 13),
            LevelUpRequest(
                CharacterClassId.FIGHTER,
                10,
                true,
                listOf(
                    ChoiceSelection("guerriero:skills", listOf("ATLETICA", "PERCEZIONE")),
                    ChoiceSelection("guerriero:1:style", listOf("style:difesa")),
                ),
            ),
        )
        val request = LevelUpRequest(CharacterClassId.FIGHTER, 6, true, emptyList())

        assertFalse(engine.validateLevelUp(first, 299, scores(15, 13), request).valid)
        assertTrue(engine.validateLevelUp(first, 300, scores(15, 13), request).valid)
    }

    @Test
    fun `una risorsa fissa rispetta livello opzione e conserva gli utilizzi spesi`() {
        fun advance(
            progression: CharacterProgression,
            experiencePoints: Int,
            selections: List<ChoiceSelection> = emptyList(),
        ): CharacterProgression = engine.applyLevelUp(
            progression = progression,
            experiencePoints = experiencePoints,
            abilityScores = scores(strength = 15, intelligence = 13),
            request = LevelUpRequest(
                classId = CharacterClassId.FIGHTER,
                hitPointIncrease = if (progression.configured) 6 else 10,
                usedFixedHitPoints = true,
                selections = selections,
            ),
        )

        val defensiveFirst = advance(
            progression = CharacterProgression(),
            experiencePoints = 0,
            selections = listOf(
                ChoiceSelection("guerriero:skills", listOf("ATLETICA", "PERCEZIONE")),
                ChoiceSelection("guerriero:1:style", listOf("style:difesa")),
            ),
        )
        assertTrue(defensiveFirst.resourcePools.none { it.resourceId == "fighter:defensive-focus" })

        val defensiveSecond = advance(defensiveFirst, experiencePoints = 300)
        val fixedAtSecond = defensiveSecond.resourcePools
            .single { it.resourceId == "fighter:defensive-focus" }
        assertEquals(1, fixedAtSecond.maximum)
        assertEquals(0, fixedAtSecond.spent)

        val withUseSpent = defensiveSecond.copy(
            resourcePools = defensiveSecond.resourcePools.map {
                if (it.resourceId == "fighter:defensive-focus") it.copy(spent = 1) else it
            },
        )
        val defensiveThird = advance(withUseSpent, experiencePoints = 900)
        assertEquals(
            1,
            defensiveThird.resourcePools
                .single { it.resourceId == "fighter:defensive-focus" }
                .spent,
        )

        val duelistFirst = advance(
            progression = CharacterProgression(),
            experiencePoints = 0,
            selections = listOf(
                ChoiceSelection("guerriero:skills", listOf("ATLETICA", "PERCEZIONE")),
                ChoiceSelection("guerriero:1:style", listOf("style:duello")),
            ),
        )
        val duelistSecond = advance(duelistFirst, experiencePoints = 300)
        assertTrue(duelistSecond.resourcePools.none { it.resourceId == "fighter:defensive-focus" })
    }

    @Test
    fun `la multiclasse controlla le caratteristiche primarie attuali e nuove`() {
        val fighter = engine.applyLevelUp(
            CharacterProgression(),
            0,
            scores(strength = 15, intelligence = 12),
            LevelUpRequest(
                CharacterClassId.FIGHTER,
                10,
                true,
                listOf(
                    ChoiceSelection("guerriero:skills", listOf("ATLETICA", "PERCEZIONE")),
                    ChoiceSelection("guerriero:1:style", listOf("style:difesa")),
                ),
            ),
        )
        val wizardRequest = LevelUpRequest(
            CharacterClassId.WIZARD,
            4,
            true,
            listOf(
                ChoiceSelection("mago:skills", listOf("ARCANO", "STORIA")),
                ChoiceSelection("mago:1:cantrips", listOf("spell:mago:luce")),
            ),
        )

        val invalid = engine.validateLevelUp(fighter, 300, scores(15, 12), wizardRequest)
        assertTrue(invalid.issues.any { it.code == "MULTICLASS_PREREQUISITE" })
        assertThrows(IllegalArgumentException::class.java) {
            engine.applyLevelUp(fighter, 300, scores(15, 12), wizardRequest)
        }
    }

    @Test
    fun `una richiesta non puo contenere due righe con lo stesso id scelta`() {
        val validation = engine.validateLevelUp(
            progression = CharacterProgression(),
            experiencePoints = 0,
            abilityScores = scores(strength = 15, intelligence = 13),
            request = LevelUpRequest(
                classId = CharacterClassId.FIGHTER,
                hitPointIncrease = 10,
                usedFixedHitPoints = true,
                selections = listOf(
                    ChoiceSelection("guerriero:skills", listOf("ATLETICA", "PERCEZIONE")),
                    ChoiceSelection("guerriero:1:style", listOf("style:difesa")),
                    ChoiceSelection("guerriero:1:style", listOf("style:duello")),
                ),
            ),
        )

        assertTrue(validation.issues.any { it.code == "DUPLICATE_CHOICE_ID" })
    }

    @Test
    fun `la sottoclasse scelta abilita soltanto privilegi e scelte dipendenti`() {
        val subclassA = "test:subclass:a"
        val subclassB = "test:subclass:b"
        val subclassChoice = ChoiceDefinition(
            id = "test:choice:subclass",
            title = "Sottoclasse",
            kind = ChoiceKind.SUBCLASS,
            count = 1,
            optionIds = listOf(subclassA, subclassB),
        )
        val choiceA = ChoiceDefinition(
            id = "test:choice:a",
            title = "Scelta A",
            kind = ChoiceKind.CLASS_OPTION,
            count = 1,
            optionIds = listOf("test:option:a"),
            requiredOptionId = subclassA,
        )
        val choiceB = ChoiceDefinition(
            id = "test:choice:b",
            title = "Scelta B",
            kind = ChoiceKind.CLASS_OPTION,
            count = 1,
            optionIds = listOf("test:option:b"),
            requiredOptionId = subclassB,
        )
        val conditionalPack = minimalPack(
            levels = listOf(
                ClassLevelDefinition(1),
                ClassLevelDefinition(2),
                ClassLevelDefinition(
                    level = 3,
                    featureIds = listOf("test:feature:base", "test:feature:a", "test:feature:b"),
                    choices = listOf(subclassChoice, choiceA, choiceB),
                ),
            ),
            subclassIds = listOf(subclassA, subclassB),
            elements = listOf(
                testFeature("test:feature:base"),
                testFeature("test:feature:a", requiredOptionId = subclassA),
                testFeature("test:feature:b", requiredOptionId = subclassB),
            ),
        )
        val conditionalEngine = CharacterProgressionEngine(conditionalPack)
        val before = CharacterProgression(
            classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 2)),
        )

        assertEquals(
            listOf(subclassChoice.id),
            conditionalEngine.requirementsFor(before, CharacterClassId.FIGHTER).map { it.id },
        )
        val provisionalSubclass = listOf(ChoiceSelection(subclassChoice.id, listOf(subclassB)))
        assertEquals(
            listOf(subclassChoice.id, choiceB.id),
            conditionalEngine.requirementsFor(
                before,
                CharacterClassId.FIGHTER,
                provisionalSubclass,
            ).map { it.id },
        )

        val request = LevelUpRequest(
            classId = CharacterClassId.FIGHTER,
            hitPointIncrease = 6,
            usedFixedHitPoints = true,
            selections = provisionalSubclass + ChoiceSelection(choiceB.id, listOf("test:option:b")),
        )
        assertTrue(conditionalEngine.validateLevelUp(before, 900, scores(15, 10), request).valid)

        val after = conditionalEngine.applyLevelUp(before, 900, scores(15, 10), request)
        assertEquals(subclassB, after.subclassFor(CharacterClassId.FIGHTER))
        assertTrue("test:feature:base" in after.selectedFeatureIds)
        assertTrue("test:feature:b" in after.selectedFeatureIds)
        assertTrue("test:option:b" in after.selectedFeatureIds)
        assertFalse("test:feature:a" in after.selectedFeatureIds)
        assertFalse("test:option:a" in after.selectedFeatureIds)
    }

    @Test
    fun `al livello successivo una scelta sostituibile aggiorna la fonte senza salvare i controlli`() {
        val oldCantrip = testSpell("test:spell:old", level = 0)
        val newCantrip = testSpell("test:spell:new", level = 0)
        val retainedSpell = testSpell("test:spell:retained", level = 1)
        val acquiredChoice = ChoiceDefinition(
            id = "test:choice:discovery",
            title = "Scoperte magiche",
            kind = ChoiceKind.MAGICAL_DISCOVERY,
            count = 2,
            optionIds = listOf(oldCantrip.id, newCantrip.id, retainedSpell.id),
            replacementWindow = ChoiceReplacementWindow.CLASS_LEVEL_UP,
        )
        val replacementPack = minimalPack(
            levels = listOf(
                ClassLevelDefinition(1, choices = listOf(acquiredChoice)),
                ClassLevelDefinition(2),
            ),
            elements = listOf(oldCantrip, newCantrip, retainedSpell),
        )
        val replacementEngine = CharacterProgressionEngine(replacementPack)
        val before = CharacterProgression(
            classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 1)),
            selections = listOf(
                ChoiceSelection(acquiredChoice.id, listOf(oldCantrip.id, retainedSpell.id)),
            ),
            knownCantripIds = listOf(oldCantrip.id),
            alwaysPreparedSpellIds = listOf(retainedSpell.id),
        )
        val initialRequirements = replacementEngine.requirementsFor(before, CharacterClassId.FIGHTER)
        val target = initialRequirements.single { it.kind == ChoiceKind.REPLACEMENT_TARGET }
        assertEquals(0, target.minimumCount)
        val provisionalTarget = ChoiceSelection(target.id, listOf(oldCantrip.id))
        val replacementChoice = replacementEngine.requirementsFor(
            before,
            CharacterClassId.FIGHTER,
            listOf(provisionalTarget),
        ).single { it.replacesChoiceId == acquiredChoice.id && it.kind == ChoiceKind.MAGICAL_DISCOVERY }
        val request = LevelUpRequest(
            classId = CharacterClassId.FIGHTER,
            hitPointIncrease = 6,
            usedFixedHitPoints = true,
            selections = listOf(
                provisionalTarget,
                ChoiceSelection(replacementChoice.id, listOf(newCantrip.id)),
            ),
        )

        assertTrue(replacementEngine.validateLevelUp(before, 300, scores(15, 10), request).valid)
        val after = replacementEngine.applyLevelUp(before, 300, scores(15, 10), request)

        assertEquals(
            listOf(newCantrip.id, retainedSpell.id),
            after.selections.single { it.choiceId == acquiredChoice.id }.optionIds,
        )
        assertEquals(listOf(newCantrip.id), after.knownCantripIds)
        assertEquals(listOf(retainedSpell.id), after.alwaysPreparedSpellIds)
        assertTrue(after.selections.none { it.choiceId == target.id || it.choiceId == replacementChoice.id })
    }

    private fun testClass(id: CharacterClassId): ClassDefinition {
        val name = id.italianLabel
        val isWizard = id == CharacterClassId.WIZARD
        return ClassDefinition(
            id = id,
            name = name,
            primaryAbilities = setOf(if (isWizard) Ability.INTELLIGENCE else Ability.STRENGTH),
            hitDieSides = if (isWizard) 6 else 10,
            fixedHitPointsPerLevel = if (isWizard) 4 else 6,
            savingThrowProficiencies = if (isWizard) {
                setOf(Ability.INTELLIGENCE, Ability.WISDOM)
            } else {
                setOf(Ability.STRENGTH, Ability.CONSTITUTION)
            },
            skillChoice = ChoiceDefinition(
                id = "${id.contentId}:skills",
                title = "Competenze",
                kind = ChoiceKind.SKILL_PROFICIENCY,
                count = 2,
                optionIds = listOf("ATLETICA", "PERCEZIONE", "ARCANO", "STORIA"),
            ),
            weaponTraining = "Armi semplici",
            subclassIds = listOf("${id.contentId}:subclass"),
            spellcastingAbility = Ability.INTELLIGENCE.takeIf { isWizard },
            spellcastingKind = if (isWizard) SpellcastingKind.SPELLBOOK else SpellcastingKind.NONE,
            levels = (1..20).map { level ->
                ClassLevelDefinition(
                    level = level,
                    featureIds = if (level == 1) listOf("feature:${id.contentId}:recuperare") else emptyList(),
                    choices = if (!isWizard && level == 1) {
                        listOf(
                            ChoiceDefinition(
                                id = "guerriero:1:style",
                                title = "Stile",
                                kind = ChoiceKind.FIGHTING_STYLE,
                                count = 1,
                                optionIds = listOf("style:difesa", "style:duello"),
                            ),
                        )
                    } else {
                        emptyList()
                    },
                    cantripsKnown = if (isWizard) 1 else 0,
                    resourceMaximums = if (!isWizard) {
                        listOf(ResourceMaximum("fighter:second-wind", 2))
                    } else {
                        emptyList()
                    },
                )
            },
            resources = if (!isWizard) {
                listOf(
                    ResourceDefinition(
                        "fighter:second-wind",
                        "Recuperare energie",
                        RecoveryPeriod.LONG_REST,
                    ),
                    ResourceDefinition(
                        id = "fighter:defensive-focus",
                        name = "Concentrazione difensiva",
                        recovery = RecoveryPeriod.LONG_REST,
                        formula = ResourceFormula.FIXED,
                        fixedMaximum = 1,
                        availableFromClassLevel = 2,
                        requiredOptionId = "style:difesa",
                    ),
                )
            } else {
                emptyList()
            },
        )
    }

    private fun minimalPack(
        levels: List<ClassLevelDefinition>,
        subclassIds: List<String> = emptyList(),
        elements: List<RuleElementDefinition> = emptyList(),
    ): RulesContentPack = RulesContentPack(
        manifest = pack.manifest.copy(id = "conditional-test-pack"),
        classes = listOf(
            ClassDefinition(
                id = CharacterClassId.FIGHTER,
                name = "Guerriero di prova",
                primaryAbilities = setOf(Ability.STRENGTH),
                hitDieSides = 10,
                fixedHitPointsPerLevel = 6,
                savingThrowProficiencies = setOf(Ability.STRENGTH, Ability.CONSTITUTION),
                skillChoice = ChoiceDefinition(
                    id = "test:skills",
                    title = "Competenze",
                    kind = ChoiceKind.SKILL_PROFICIENCY,
                    count = 0,
                ),
                weaponTraining = "Nessuna",
                subclassIds = subclassIds,
                subclassLevel = 3,
                levels = levels,
            ),
        ),
        elements = elements,
        maximumCharacterLevel = levels.size,
        experienceThresholds = ExperienceProgression.thresholds.take(levels.size),
    )

    private fun testFeature(
        id: String,
        requiredOptionId: String? = null,
    ): RuleElementDefinition = RuleElementDefinition(
        id = id,
        name = id,
        kind = RuleElementKind.CLASS_FEATURE,
        description = "Privilegio di prova.",
        requiredOptionId = requiredOptionId,
    )

    private fun testSpell(id: String, level: Int): RuleElementDefinition = RuleElementDefinition(
        id = id,
        name = id,
        kind = if (level == 0) RuleElementKind.CANTRIP else RuleElementKind.SPELL,
        description = "Incantesimo di prova.",
        spell = SpellDetails(
            level = level,
            school = "Invocazione",
            castingTime = "azione",
            range = "18 metri",
            components = "V",
            duration = "istantanea",
        ),
    )

    private fun scores(
        strength: Int = 10,
        intelligence: Int = 10,
    ): Map<Ability, Int> = Ability.entries.associateWith {
        when (it) {
            Ability.STRENGTH -> strength
            Ability.INTELLIGENCE -> intelligence
            else -> 13
        }
    }
}
