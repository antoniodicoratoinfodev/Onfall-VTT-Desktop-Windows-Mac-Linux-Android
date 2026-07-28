package app.d6d.content.srd521it

import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.LevelUpRequest
import app.d6d.rules.character.ResourceFormula
import app.d6d.rules.character.RuleElementKind
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regressione end-to-end del contratto tra tabelle SRD, resolver delle scelte e
 * applicazione alla scheda. Il chooser usa sempre la prima opzione disponibile,
 * salvo soddisfare prima le dipendenze esplicite (competenze/maestria e
 * libro/preparazione del mago).
 */
class SrdGuidedProgressionMatrixTest {
    private val pack = Srd521ItContent.pack
    private val service = GuidedCharacterService(pack)

    @Test
    fun `tutte le classi avanzano dal primo al ventesimo livello con scelte risolvibili`() {
        CharacterClassId.entries.forEach { classId ->
            var sheet = CharacterSheet(
                abilityScores = Ability.entries.associateWith { 16 },
            )
            val definition = pack.classDefinition(classId)

            for (targetLevel in 1..20) {
                sheet = sheet.copy(
                    experiencePoints = ExperienceProgression.thresholdForLevel(targetLevel),
                )
                if (targetLevel > 1) {
                    assertTrue(
                        sheet.canLevelUp,
                        "${definition.name} $targetLevel: i PE alla soglia non segnalano il livello.",
                    )
                }

                val selections = resolveSelections(sheet, classId, targetLevel)
                val request = LevelUpRequest(
                    classId = classId,
                    hitPointIncrease = service.fixedHitPointIncrease(sheet, classId),
                    usedFixedHitPoints = true,
                    selections = selections,
                    abilityScoreIncreases = abilityScoreIncreases(sheet, classId, selections),
                )
                val validation = service.validate(sheet, request)
                assertTrue(
                    validation.valid,
                    "${definition.name} $targetLevel: " +
                        validation.issues.joinToString(" | ") { "${it.code}: ${it.message}" },
                )

                sheet = service.advance(sheet, request)

                assertEquals(
                    targetLevel,
                    sheet.progression.totalLevel,
                    "${definition.name}: livello totale errato.",
                )
                assertEquals(
                    targetLevel,
                    sheet.progression.levelIn(classId),
                    "${definition.name}: livello di classe errato.",
                )
                assertEquals(
                    targetLevel,
                    sheet.effectiveLevel,
                    "${definition.name}: livello effettivo errato.",
                )
                assertFalse(
                    sheet.canLevelUp,
                    "${definition.name} $targetLevel: il livello appena applicato risulta ancora disponibile.",
                )
                val selectedCantrips = sheet.progression.selections
                    .flatMap { it.optionIds }
                    .filter { pack.element(it)?.spell?.level == 0 }
                    .distinct()
                assertEquals(
                    selectedCantrips.size,
                    sheet.progression.knownCantripIds.size,
                    "${definition.name} $targetLevel: conteggio trucchetti selezionati errato.",
                )
                assertTrue(
                    sheet.progression.knownCantripIds.size >=
                        definition.level(targetLevel).cantripsKnown,
                    "${definition.name} $targetLevel: mancano trucchetti della progressione base.",
                )
                assertResources(definition, targetLevel, sheet)
            }
        }
    }

    private fun resolveSelections(
        sheet: CharacterSheet,
        classId: CharacterClassId,
        classLevel: Int,
    ): List<ChoiceSelection> {
        val selected = linkedMapOf<String, List<String>>()

        repeat(MAX_RESOLUTION_PASSES) {
            val provisional = selected.toSelections()
            val requirements = service.requirements(sheet, classId, provisional)
            val activeIds = requirements.mapTo(mutableSetOf()) { it.id }
            selected.keys.retainAll(activeIds)
            val unresolved = requirements
                .filter { choice ->
                    !selected.containsKey(choice.id) ||
                        selected[choice.id].orEmpty().size != choice.count
                }
                .sortedWith(
                    compareBy<ChoiceDefinition>(
                        { choicePriority(it) },
                        { requirements.indexOf(it) },
                    ),
                )

            if (unresolved.isEmpty()) {
                return requirements.map { choice ->
                    ChoiceSelection(choice.id, selected.getValue(choice.id))
                }
            }

            unresolved.forEach { choice ->
                val current = selected.toSelections()
                val options = SrdChoiceResolver.options(
                    choice = choice,
                    classId = classId,
                    classLevel = classLevel,
                    sheet = sheet,
                    provisionalSelections = current,
                )
                assertTrue(
                    options.size >= choice.count,
                    buildPoolFailure(sheet, classId, classLevel, choice, options.size, current),
                )

                val chosen = when {
                    isInitialOriginFeat(choice) -> {
                        val skilled = options.firstOrNull {
                            it.id == "srd521-it:feat:origin:abile"
                        }
                        listOf(requireNotNull(skilled) {
                            "${classId.italianLabel} $classLevel: il pool Origini non contiene Abile."
                        }.id)
                    }
                    choice.kind == ChoiceKind.FEAT -> {
                        val grappler = options.firstOrNull {
                            it.id == "srd521-it:feat:general:lottatore"
                        }
                        listOf((grappler ?: options.first()).id)
                    }
                    choice.kind == ChoiceKind.EPIC_BOON -> {
                        val epicBoon = options.firstOrNull {
                            pack.element(it.id)?.kind == RuleElementKind.EPIC_BOON_FEAT
                        }
                        listOf(requireNotNull(epicBoon) {
                            "${classId.italianLabel} $classLevel: il pool non contiene un Dono epico."
                        }.id)
                    }
                    classId == CharacterClassId.WARLOCK &&
                        choice.kind == ChoiceKind.CANTRIP -> {
                        val eldritchBlast = options.firstOrNull {
                            it.id == "srd521-it:spell:deflagrazione-occulta"
                        }
                        (
                            listOfNotNull(eldritchBlast) +
                                options.filterNot { it.id == eldritchBlast?.id }
                            )
                            .take(choice.count)
                            .map { it.id }
                    }
                    isWizardLevelTwentySpellbook(classId, classLevel, choice) -> {
                        val levelThree = options.filter { option ->
                            pack.element(option.id)?.spell?.level == 3
                        }
                        assertTrue(
                            levelThree.size >= choice.count,
                            "${classId.italianLabel} 20: servono ${choice.count} incantesimi " +
                                "di 3º livello tra le nuove aggiunte al libro, disponibili ${levelThree.size}.",
                        )
                        levelThree.take(choice.count).map { it.id }
                    }
                    else -> options.take(choice.count).map { it.id }
                }
                assertEquals(
                    choice.count,
                    chosen.distinct().size,
                    "${classId.italianLabel} $classLevel: «${choice.title}» " +
                        "non produce ${choice.count} scelte distinte.",
                )
                selected[choice.id] = chosen
            }
        }

        val provisional = selected.toSelections()
        val unresolved = service.requirements(sheet, classId, provisional)
            .filter {
                !selected.containsKey(it.id) ||
                    selected[it.id].orEmpty().size != it.count
            }
        throw AssertionError(
            "${classId.italianLabel} $classLevel: risoluzione non stabilizzata dopo " +
                "$MAX_RESOLUTION_PASSES passaggi; restano " +
                unresolved.joinToString { "${it.id} ${selected[it.id].orEmpty().size}/${it.count}" },
        )
    }

    private fun assertResources(
        definition: app.d6d.rules.character.ClassDefinition,
        classLevel: Int,
        sheet: CharacterSheet,
    ) {
        val poolsById = sheet.progression.resourcePools.associateBy { it.resourceId }
        val selectedOptionIds = sheet.progression.selections
            .flatMapTo(mutableSetOf()) { it.optionIds }
            .apply {
                addAll(sheet.progression.subclasses.map { it.subclassId })
            }
        val activeResources = definition.resources.filter {
            classLevel >= it.availableFromClassLevel &&
                (it.requiredOptionId == null || it.requiredOptionId in selectedOptionIds)
        }
        assertEquals(
            activeResources.mapTo(linkedSetOf()) { it.id },
            poolsById.keys,
            "${definition.name} $classLevel: insieme delle risorse errato.",
        )

        activeResources.forEach { resource ->
            val pool = requireNotNull(poolsById[resource.id]) {
                "${definition.name} $classLevel: risorsa assente ${resource.id}."
            }
            val tableMaximum = definition.level(classLevel).resourceMaximums
                .firstOrNull { it.resourceId == resource.id }
            val rawMaximum = when (resource.formula) {
                ResourceFormula.TABLE -> tableMaximum?.maximum ?: 0
                ResourceFormula.FIXED -> resource.fixedMaximum
                ResourceFormula.CLASS_LEVEL -> classLevel
                ResourceFormula.CLASS_LEVEL_TIMES_MULTIPLIER ->
                    classLevel * resource.multiplier
                ResourceFormula.ABILITY_MODIFIER -> {
                    val score = sheet.abilityScores[resource.ability] ?: 10
                    Math.floorDiv(score - 10, 2)
                }
                ResourceFormula.PROFICIENCY_BONUS ->
                    ExperienceProgression.proficiencyBonus(sheet.effectiveLevel)
            }
            assertEquals(
                rawMaximum.coerceAtLeast(resource.minimum),
                pool.maximum,
                "${definition.name} $classLevel: massimo errato per ${resource.name}.",
            )
            assertEquals(
                tableMaximum?.dieSides ?: 0,
                pool.dieSides,
                "${definition.name} $classLevel: dado errato per ${resource.name}.",
            )
            val expectedRecovery = if (
                resource.fullShortRestRecoveryFromLevel > 0 &&
                classLevel >= resource.fullShortRestRecoveryFromLevel
            ) {
                app.d6d.rules.character.RecoveryPeriod.SHORT_OR_LONG_REST
            } else {
                resource.recovery
            }
            assertEquals(
                expectedRecovery,
                pool.recovery,
                "${definition.name} $classLevel: recupero errato per ${resource.name}.",
            )
            assertEquals(
                resource.shortRestRecovery,
                pool.shortRestRecovery,
                "${definition.name} $classLevel: recupero breve parziale errato per ${resource.name}.",
            )
        }
    }

    private fun abilityScoreIncreases(
        sheet: CharacterSheet,
        classId: CharacterClassId,
        selections: List<ChoiceSelection>,
    ): Map<Ability, Int> {
        val requirements = service.requirements(sheet, classId, selections)
            .associateBy { it.id }
        return selections
            .filter { requirements[it.choiceId]?.kind == ChoiceKind.ABILITY_SCORE_INCREASE }
            .flatMap { it.optionIds }
            .mapNotNull { optionId ->
                val slug = optionId.substringAfterLast(':')
                Ability.entries.firstOrNull { it.name.lowercase() == slug }
            }
            .groupingBy { it }
            .eachCount()
    }

    private fun choicePriority(choice: ChoiceDefinition): Int = when {
        isInitialOriginFeat(choice) -> 0
        choice.kind == ChoiceKind.SKILL_PROFICIENCY -> 10
        choice.kind == ChoiceKind.TOOL_PROFICIENCY -> 10
        choice.kind == ChoiceKind.SPELLBOOK_SPELL -> 10
        choice.kind == ChoiceKind.CANTRIP -> 20
        choice.kind == ChoiceKind.SKILL_OR_TOOL_PROFICIENCY -> 30
        choice.kind == ChoiceKind.EXPERTISE -> 40
        choice.id.contains(":maestria-incantesimo-") -> 50
        choice.id.endsWith(":incantesimi-personali") -> 50
        choice.kind == ChoiceKind.PREPARED_SPELL -> 40
        else -> 20
    }

    private fun isInitialOriginFeat(choice: ChoiceDefinition): Boolean =
        choice.poolId?.endsWith("feats:origin") == true

    private fun isWizardLevelTwentySpellbook(
        classId: CharacterClassId,
        classLevel: Int,
        choice: ChoiceDefinition,
    ): Boolean =
        classId == CharacterClassId.WIZARD &&
            classLevel == 20 &&
            choice.kind == ChoiceKind.SPELLBOOK_SPELL &&
            choice.id.endsWith(":spellbook")

    private fun buildPoolFailure(
        sheet: CharacterSheet,
        classId: CharacterClassId,
        classLevel: Int,
        choice: ChoiceDefinition,
        available: Int,
        provisional: List<ChoiceSelection>,
    ): String = buildString {
        append("${classId.italianLabel} $classLevel: pool insufficiente per «${choice.title}» ")
        append("(${choice.id}), richieste ${choice.count}, disponibili $available.")
        append(" Livello corrente ${sheet.progression.totalLevel}; scelte provvisorie: ")
        append(
            provisional.joinToString {
                "${it.choiceId}=[${it.optionIds.joinToString()}]"
            },
        )
    }

    private fun Map<String, List<String>>.toSelections(): List<ChoiceSelection> =
        map { (choiceId, optionIds) -> ChoiceSelection(choiceId, optionIds) }

    private companion object {
        const val MAX_RESOLUTION_PASSES = 12
    }
}
