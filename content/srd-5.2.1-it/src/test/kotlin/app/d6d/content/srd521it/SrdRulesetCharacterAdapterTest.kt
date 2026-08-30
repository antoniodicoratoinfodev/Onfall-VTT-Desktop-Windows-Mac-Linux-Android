package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.EffectTarget
import app.d6d.rules.character.LevelUpRequest
import app.d6d.rules.model.LocalizedRuleText
import app.d6d.rules.model.RuleAutomationLevel
import app.d6d.rules.model.RuleEntity
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RulesetOrigin
import app.d6d.rules.model.RulesetRevision
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SrdRulesetCharacterAdapterTest {

    @Test
    fun `la revisione standard conserva integralmente classi ed elementi del pack`() {
        val original = Srd521ItContent.packFor(AppLanguage.ITALIAN)
        val projected = SrdRulesetCharacterAdapter.project(Srd521Ruleset.revision, AppLanguage.ITALIAN)

        assertEquals(original.classes, projected.classes)
        assertEquals(original.elements, projected.elements)
        assertEquals(original.proficiencyProgression, projected.proficiencyProgression)
    }

    @Test
    fun `classe e modificatore homebrew entrano nella creazione e nella progressione reali`() {
        val classEntityId = "local:class:chronomancer"
        val classId = CharacterClassId.of("homebrew:class:chronomancer")
        val customClass = entity(
            id = classEntityId,
            kind = RuleKind.CLASS,
            name = "Cronomante",
            attributes = mapOf(
                "classId" to classId.value,
                "hitDieSides" to "8",
                "fixedHitPointsPerLevel" to "5",
                "primaryAbilities" to "INTELLIGENCE",
                "savingThrowProficiencies" to "INTELLIGENCE,WISDOM",
                "spellcastingKind" to "NONE",
                "maximumLevel" to "3",
                "skillChoiceCount" to "0",
                "subclassIds" to "",
                "levelFeatureIds" to "",
            ),
        )
        val armorModifier = entity(
            id = "local:modifier:temporal-armor",
            kind = RuleKind.MODIFIER,
            name = "Armatura temporale",
            attributes = mapOf(
                "ownerRef" to classEntityId,
                "target" to "ARMOR_CLASS",
                "amount" to "2",
                "condition" to "ALWAYS",
                "minimumLevel" to "1",
                "group" to "temporal-armor",
            ),
        )
        val revision = homebrewRevision(
            additions = listOf(customClass, armorModifier),
            proficiencyBase = 3,
        )

        val pack = SrdRulesetCharacterAdapter.project(revision, AppLanguage.ITALIAN)
        val definition = pack.classDefinition(classId)
        assertEquals("Cronomante", definition.name)
        assertEquals(3, definition.maximumLevel)
        assertEquals(8, definition.hitDieSides)
        assertTrue(
            definition.level(1).effects.any {
                it.target == EffectTarget.ARMOR_CLASS && it.amount == 2 && it.source == "Armatura temporale"
            },
        )

        val service = GuidedCharacterService(pack)
        val draft = CharacterSheet()
        val selections = selectionsFor(service, pack, draft, classId)
        val background = selections
            .first { selection ->
                service.requirements(draft, classId, selections)
                    .firstOrNull { it.id == selection.choiceId }
                    ?.kind == ChoiceKind.BACKGROUND
            }
            .optionIds.single()
            .let(pack::background)
            ?: error("Background di test mancante")
        val backgroundAbilities = background.abilityOptions.toList()
        val request = LevelUpRequest(
            classId = classId,
            hitPointIncrease = service.fixedHitPointIncrease(draft, classId),
            usedFixedHitPoints = true,
            selections = selections,
            backgroundAbilityScoreIncreases = mapOf(
                backgroundAbilities[0] to 2,
                backgroundAbilities[1] to 1,
            ),
        )

        assertTrue(service.validate(draft, request).valid)
        val created = service.advance(draft, request)

        assertEquals(classId, created.progression.classLevels.single().classId)
        assertEquals("Cronomante 1", created.className)
        assertEquals(revision.canonicalHash(), created.progression.rulesetCanonicalHash)
        assertEquals(revision.runtimeHash(), created.progression.rulesetRuntimeHash)
        assertEquals(3, created.proficiencyBonus)
        assertTrue(created.progression.effects.any { it.source == "Armatura temporale" && it.amount == 2 })
    }

    @Test
    fun `disabilitare un modificatore lo rimuove dalla proiezione eseguibile`() {
        val classEntityId = "local:class:chronomancer"
        val classId = CharacterClassId.of("homebrew:class:chronomancer")
        val customClass = entity(
            id = classEntityId,
            kind = RuleKind.CLASS,
            name = "Cronomante",
            attributes = mapOf(
                "classId" to classId.value,
                "maximumLevel" to "3",
                "skillChoiceCount" to "0",
                "spellcastingKind" to "NONE",
            ),
        )
        val disabled = entity(
            id = "local:modifier:disabled",
            kind = RuleKind.MODIFIER,
            name = "Bonus disattivato",
            enabled = false,
            attributes = mapOf(
                "ownerRef" to classEntityId,
                "target" to "ARMOR_CLASS",
                "amount" to "9",
            ),
        )

        val definition = SrdRulesetCharacterAdapter.project(
            homebrewRevision(listOf(customClass, disabled)),
            AppLanguage.ITALIAN,
        ).classDefinition(classId)

        assertFalse(definition.levels.flatMap { it.effects }.any { it.source == "Bonus disattivato" })
    }

    @Test
    fun `il livello minimo non puo essere attribuito a un owner che non e una classe`() {
        val feature = entity(
            id = "local:feature:temporal-step",
            kind = RuleKind.FEATURE,
            name = "Passo temporale",
            attributes = mapOf("elementKind" to "CLASS_FEATURE"),
        )
        val modifier = entity(
            id = "local:modifier:late-step",
            kind = RuleKind.MODIFIER,
            name = "Passo tardivo",
            attributes = mapOf(
                "ownerRef" to feature.id(),
                "target" to "SPEED_FEET",
                "amount" to "5",
                "minimumLevel" to "2",
            ),
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SrdRulesetCharacterAdapter.validateExecutableLinks(
                homebrewRevision(listOf(feature, modifier)),
                AppLanguage.ITALIAN,
            )
        }
        assertTrue(failure.message.orEmpty().contains("only valid for a class owner"))
    }

    @Test
    fun `una eleggibilita di classe non accetta il livello zero`() {
        val feature = entity(
            id = "local:feature:invalid-eligibility",
            kind = RuleKind.FEATURE,
            name = "Privilegio impossibile",
            attributes = mapOf(
                "elementKind" to "CLASS_FEATURE",
                "classEligibility" to "FIGHTER:0",
            ),
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            SrdRulesetCharacterAdapter.project(
                homebrewRevision(listOf(feature)),
                AppLanguage.ITALIAN,
            )
        }
        assertTrue(failure.message.orEmpty().contains("at least 1"))
    }

    @Test
    fun `una classe oltre il venti estende la progressione e usa PE solo finche la tabella li dichiara`() {
        val classId = CharacterClassId.of("homebrew:class:epic")
        val epicClass = entity(
            id = "local:class:epic",
            kind = RuleKind.CLASS,
            name = "Classe epica",
            attributes = mapOf(
                "classId" to classId.value,
                "maximumLevel" to "30",
                "skillChoiceCount" to "0",
                "spellcastingKind" to "NONE",
            ),
        )

        val pack = SrdRulesetCharacterAdapter.project(
            homebrewRevision(listOf(epicClass)),
            AppLanguage.ITALIAN,
        )

        assertEquals(30, pack.maximumCharacterLevel)
        assertEquals(30, pack.classDefinition(classId).maximumLevel)
        assertEquals(20, pack.experienceThresholds.size)
        assertTrue(pack.enforceExperienceThresholds)
    }

    private fun selectionsFor(
        service: GuidedCharacterService,
        pack: app.d6d.rules.character.RulesContentPack,
        sheet: CharacterSheet,
        classId: CharacterClassId,
    ): List<ChoiceSelection> {
        val selected = linkedMapOf<String, List<String>>()
        repeat(8) {
            val provisional = selected.map { ChoiceSelection(it.key, it.value) }
            val requirements = service.requirements(sheet, classId, provisional)
            selected.keys.retainAll(requirements.mapTo(mutableSetOf()) { it.id })
            requirements.forEach { choice ->
                val options = SrdChoiceResolver.options(
                    choice = choice,
                    classId = classId,
                    classLevel = 1,
                    sheet = sheet,
                    provisionalSelections = selected.map { ChoiceSelection(it.key, it.value) },
                    language = AppLanguage.ITALIAN,
                    pack = pack,
                )
                selected[choice.id] = when (choice.kind) {
                    ChoiceKind.BACKGROUND -> listOf("srd521-it:background:soldato")
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

    private fun homebrewRevision(
        additions: List<RuleEntity>,
        proficiencyBase: Int = 2,
    ): RulesetRevision {
        val standard = Srd521Ruleset.revision
        return RulesetRevision.create(
            "test:chronomancer",
            "test:chronomancer:revision:1",
            "1.0.0",
            "Regole del tempo",
            "Regolamento di test con classe eseguibile.",
            RulesetOrigin.HOMEBREW,
            standard.canonicalHash(),
            standard.runtime().withProficiency(proficiencyBase, 4, 7),
            standard.entities() + additions,
            "2026-08-29T00:00:00Z",
        )
    }

    private fun entity(
        id: String,
        kind: RuleKind,
        name: String,
        enabled: Boolean = true,
        attributes: Map<String, String>,
    ): RuleEntity = RuleEntity(
        id,
        kind,
        RulesetOrigin.HOMEBREW,
        LocalizedRuleText.bilingual(name, name),
        LocalizedRuleText.bilingual("Regola di test.", "Test rule."),
        "",
        enabled,
        RuleAutomationLevel.ASSISTED,
        attributes,
        listOf("test"),
        "Test",
        "",
        0,
    )
}
