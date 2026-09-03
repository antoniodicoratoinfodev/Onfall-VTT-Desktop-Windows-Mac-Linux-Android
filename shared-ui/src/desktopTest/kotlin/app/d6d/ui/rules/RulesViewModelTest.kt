package app.d6d.ui.rules

import app.d6d.i18n.AppLanguage
import app.d6d.content.srd521it.Srd521Ruleset
import app.d6d.content.srd521it.SrdRulesetCharacterAdapter
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageType
import app.d6d.rules.authoring.AuthoringMode
import app.d6d.rules.model.CoreRuleIds
import app.d6d.rules.model.GenericRulesetFoundation
import app.d6d.rules.model.LocalizedRuleText
import app.d6d.rules.model.RuleAutomationLevel
import app.d6d.rules.model.RulePatch
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RuleValue
import app.d6d.rules.model.RulesetCompositionIssue
import app.d6d.rules.model.RulesetModule
import app.d6d.rules.model.RulesetModuleRef
import app.d6d.rules.model.RulesetOrigin
import app.d6d.rules.persistence.RulesetLibraryJsonCodec
import app.d6d.rules.persistence.LocalRulesetRepository
import app.d6d.persistence.json.Json
import app.d6d.sheet.SheetStore
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.sheet.SheetViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Files

class RulesViewModelTest {
    @TempDir
    lateinit var directory: Path

    @BeforeEach
    fun italianLocale() {
        AppLocale.use(AppLanguage.ITALIAN)
    }

    @Test
    fun `fork modifica generica pubblicazione e revisione successiva sopravvivono al riavvio`() {
        val rules = RulesViewModel(directory)
        val standard = requireNotNull(rules.selected)

        assertTrue(standard.readOnly)
        rules.forkSelected()
        assertTrue(requireNotNull(rules.selected).isDraft)

        rules.updateRuntime { it.withCriticalHitMinimumNatural(18) }
        rules.addRule(RuleKind.CLASS)
        val classId = requireNotNull(rules.selectedEntityId)
        rules.updateEntity(
            entityId = classId,
            name = "Cronomante",
            description = "Classe homebrew basata sulle riserve temporali.",
            kind = RuleKind.CLASS,
            automation = RuleAutomationLevel.MANUAL,
            enabled = true,
            attributes = mapOf("hitDie" to "d8", "maximumLevel" to "30"),
            tags = listOf("classe", "tempo"),
        )

        val edited = requireNotNull(rules.selectedEntity)
        assertEquals("Cronomante", edited.name().text("it"))
        assertEquals(RuleKind.CLASS, edited.kind())
        assertEquals("30", edited.attributes()["maximumLevel"])

        rules.publishSelected("1.0.0")
        val published = requireNotNull(rules.selected)
        assertFalse(published.isDraft)
        assertEquals(RulesetOrigin.HOMEBREW, published.origin)
        assertEquals(18, published.revision.runtime().criticalHitMinimumNatural())

        val sheets = SheetViewModel(
            SheetStore(directory.resolve("characters.json")),
            loadOnCreate = false,
            rulesetProvider = { rules.publishedRevisions },
        )
        assertTrue(sheets.selectCharacterRuleset(published.key))
        assertTrue(sheets.srdClasses.any { it.name == "Cronomante" && it.maximumLevel == 30 })
        assertTrue(sheets.availableCharacterClasses.any { it.name == "Cronomante" })

        val reloaded = RulesViewModel(directory)
        reloaded.changeOriginFilter(RulesetOriginFilter.HOMEBREW)
        val installed = reloaded.choices.single { !it.isDraft }
        assertNotNull(installed.revision.entity(classId))

        reloaded.selectRuleset(installed.key)
        reloaded.forkSelected()
        val nextDraft = requireNotNull(reloaded.selected)
        assertTrue(nextDraft.isDraft)
        assertEquals(installed.revision.projectId(), nextDraft.revision.projectId())
        assertEquals(installed.revision.canonicalHash(), nextDraft.revision.baseCanonicalHash())
    }

    @Test
    fun `un regolamento vuoto nasce senza SRD e usa default neutrali`() {
        val rules = RulesViewModel(directory)

        rules.createBlankRuleset()

        val draft = requireNotNull(rules.selected)
        assertTrue(draft.isDraft)
        assertTrue(draft.revision.entities().isEmpty())
        assertFalse(rules.hasLegacyRuntimeControls)

        rules.addRule(RuleKind.STAT)
        val stat = requireNotNull(rules.selectedEntity)
        assertEquals("0", stat.attributes()["defaultFormula"])
        assertEquals("\${score}", stat.attributes()["modifierFormula"])
        assertFalse(stat.attributes().containsKey("minimumFormula"))
        assertFalse(stat.attributes().containsKey("maximumFormula"))

        rules.resetSelectedEntityChange()
        rules.publishSelected("1.0.0")

        val published = requireNotNull(rules.selected)
        assertFalse(published.isDraft)
        assertTrue(published.revision.entities().isEmpty())
        assertFalse(SrdRulesetCharacterAdapter.inheritsSrdContent(published.revision))

        val sheets = SheetViewModel(
            SheetStore(directory.resolve("classless-sheets.json")),
            loadOnCreate = false,
            rulesetProvider = { rules.publishedRevisions },
        )
        assertTrue(sheets.selectCharacterRuleset(published.key))
        assertTrue(sheets.character.modularSheet.configured)
        assertTrue(sheets.srdClasses.isEmpty())
    }

    @Test
    fun `partire dall SRD crea una nuova bozza completa e lascia intatto lo standard`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()
        val standard = Srd521Ruleset.revision
        val baseEntity = standard.entities().first { it.enabled() }
        val originalName = baseEntity.name().text("it")

        // Il comando deve usare lo SRD anche quando la selezione corrente è una bozza diversa.
        rules.createSrdBasedRuleset()

        val draft = requireNotNull(rules.selected)
        assertTrue(draft.isDraft)
        assertEquals(RulesetOrigin.HOMEBREW, draft.origin)
        assertEquals(standard.canonicalHash(), draft.revision.baseCanonicalHash())
        assertEquals(standard.entities().map { it.id() }, draft.revision.entities().map { it.id() })

        rules.selectEntity(baseEntity.id())
        assertTrue(rules.updateEntity(
            entityId = baseEntity.id(),
            name = "Versione personalizzata",
            description = baseEntity.description().text("it"),
            kind = baseEntity.kind(),
            automation = baseEntity.automationLevel(),
            enabled = false,
            attributes = baseEntity.attributes(),
            tags = baseEntity.tags(),
        ))

        assertEquals("Versione personalizzata", rules.selectedEntity?.name()?.text("it"))
        assertFalse(requireNotNull(rules.selectedEntity).enabled())
        assertEquals(1, rules.draftChangeSummary?.modified)
        assertEquals(originalName, standard.entity(baseEntity.id()).name().text("it"))
        assertTrue(standard.entity(baseEntity.id()).enabled())
    }

    @Test
    fun `la modalita visuale della regola sopravvive al riavvio insieme alla bozza`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()
        rules.addRule(RuleKind.STAT)
        val stat = requireNotNull(rules.selectedEntity)

        rules.updateEntity(
            stat.id(),
            stat.name().text("it"),
            stat.description().text("it"),
            stat.kind(),
            stat.automationLevel(),
            stat.enabled(),
            stat.attributes(),
            stat.tags(),
            replaceAttributes = true,
            authoringMode = AuthoringMode.VISUAL,
        )

        assertEquals(AuthoringMode.VISUAL, rules.preferredAuthoringMode(stat.id()))

        val reloaded = RulesViewModel(directory)
        val draft = reloaded.choices.single { it.isDraft }
        reloaded.selectRuleset(draft.key)
        reloaded.selectEntity(stat.id())

        assertEquals(AuthoringMode.VISUAL, reloaded.preferredAuthoringMode(stat.id()))
    }

    @Test
    fun `la ricetta tiro crea e collega automaticamente il generatore casuale`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()
        val before = localRepository().drafts().single().saveRevision()

        rules.addGuidedRule(RuleKind.ROLL)

        val roll = requireNotNull(rules.selectedEntity)
        assertEquals(RuleKind.ROLL, roll.kind())
        val randomizerId = requireNotNull(roll.attributes()["randomizerRef"])
        val randomizer = requireNotNull(rules.selected?.revision?.entity(randomizerId))
        assertEquals(RuleKind.RANDOMIZER, randomizer.kind())
        assertEquals("DICE", randomizer.attributes()["mode"])

        val stored = localRepository()
        assertEquals(before + 1, stored.drafts().single().saveRevision())
        val metadata = stored.authoringState().groups(stored.drafts().single().id()).values.single()
        assertEquals(setOf(randomizerId, roll.id()), metadata.generatedEntityIds().toSet())
        assertEquals(setOf(randomizerId, roll.id()), metadata.lastProjectedContentHashes().keys)
        rules.selectEntity(randomizerId)
        assertEquals(AuthoringMode.GUIDED, rules.preferredAuthoringMode(randomizerId))
        rules.selectEntity(roll.id())

        assertTrue(rules.validateSelectedDraft())
        rules.publishSelected("1.0.0")
        assertFalse(requireNotNull(rules.selected).isDraft)
        requireNotNull(rules.selected).revision.compile()
    }

    @Test
    fun `le ricette semplici restano nel percorso guidato`() {
        assertTrue(RuleKind.DAMAGE_TYPE in guidedEditorKinds)
        assertTrue(RuleKind.TEXT_RULE in guidedEditorKinds)
    }

    @Test
    fun `il selettore dei calcoli propone soltanto riferimenti diretti compilabili`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()
        rules.addRule(RuleKind.VALUE)
        val textValue = requireNotNull(rules.selectedEntity)
        rules.addRule(RuleKind.VALUE)
        val numericValue = requireNotNull(rules.selectedEntity)
        assertTrue(rules.updateEntity(
            numericValue.id(), numericValue.name().text("it"), numericValue.description().text("it"),
            numericValue.kind(), numericValue.automationLevel(), numericValue.enabled(),
            numericValue.attributes() + mapOf("valueType" to "NUMBER", "defaultValue" to "0"),
            numericValue.tags(), replaceAttributes = true, authoringMode = AuthoringMode.GUIDED,
        ))
        rules.addRule(RuleKind.RESOURCE)
        val resource = requireNotNull(rules.selectedEntity)
        rules.addRule(RuleKind.STAT)
        val stat = requireNotNull(rules.selectedEntity)

        val candidates = numericRuleCandidates(rules, "not-an-entity").map { it.id() }.toSet()

        assertTrue(numericValue.id() in candidates)
        assertTrue(stat.id() in candidates)
        assertFalse(textValue.id() in candidates)
        assertFalse(resource.id() in candidates)
    }

    @Test
    fun `le state policy non supportate restano visibili come dettagli protetti`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()
        rules.addRule(RuleKind.MODIFIER)
        val modifier = requireNotNull(rules.selectedEntity)

        assertTrue(rules.updateEntity(
            modifier.id(), modifier.name().text("it"), modifier.description().text("it"),
            modifier.kind(), modifier.automationLevel(), modifier.enabled(),
            modifier.attributes() + ("lifetime" to "SCENE"), modifier.tags(),
            replaceAttributes = true, authoringMode = AuthoringMode.VISUAL,
        ))

        assertTrue("lifetime" in rules.protectedAuthoringFields(modifier.id()))
    }

    @Test
    fun `salvare prima di aggiungere un effetto conserva le modifiche del proprietario`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()
        rules.addRule(RuleKind.CONDITION)
        val condition = requireNotNull(rules.selectedEntity)

        assertTrue(rules.updateEntity(
            condition.id(), "Sfasato", "Condizione modificata prima dell'effetto.",
            condition.kind(), condition.automationLevel(), condition.enabled(),
            condition.attributes(), condition.tags(), replaceAttributes = true,
            authoringMode = AuthoringMode.GUIDED,
        ))
        rules.addGenericLinkedModifier(condition.id())

        val revision = requireNotNull(rules.selected).revision
        assertEquals("Sfasato", revision.entity(condition.id()).name().text("it"))
        assertEquals(condition.id(), requireNotNull(rules.selectedEntity).attributes()["ownerRef"])
    }

    @Test
    fun `metadati visuali con hash obsoleto non vengono considerati autorevoli`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()
        rules.addRule(RuleKind.STAT)
        val stat = requireNotNull(rules.selectedEntity)
        assertTrue(rules.updateEntity(
            stat.id(), stat.name().text("it"), stat.description().text("it"), stat.kind(),
            stat.automationLevel(), stat.enabled(), stat.attributes(), stat.tags(),
            replaceAttributes = true, authoringMode = AuthoringMode.VISUAL,
        ))
        assertEquals(AuthoringMode.VISUAL, rules.preferredAuthoringMode(stat.id()))

        val repository = localRepository()
        val draft = repository.drafts().single()
        val changedStat = requireNotNull(draft.additions().firstOrNull { it.id() == stat.id() })
            .withAttributes(stat.attributes() + mapOf(
                "defaultFormula" to "99",
                "plugin.custom" to "keep-me",
            ))
        repository.saveDraft(draft.withContent(
            draft.name(), draft.description(), draft.runtime(), draft.patches(),
            draft.additions().map { if (it.id() == stat.id()) changedStat else it },
            "2026-09-03T12:00:00Z",
        ))

        val reloaded = RulesViewModel(directory)
        val selectedDraft = reloaded.choices.single { it.isDraft }
        reloaded.selectRuleset(selectedDraft.key)
        reloaded.selectEntity(stat.id())
        assertNull(reloaded.preferredAuthoringMode(stat.id()))
        assertEquals("99", reloaded.selectedEntity?.attributes()?.get("defaultFormula"))
        assertEquals(setOf("plugin.custom"), reloaded.protectedAuthoringFields(stat.id()))
    }

    @Test
    fun `il salvataggio visuale segnala formule incorporate e attributi sconosciuti come protetti`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()
        rules.addRule(RuleKind.ACTION)
        val action = requireNotNull(rules.selectedEntity)

        rules.updateEntity(
            action.id(),
            action.name().text("it"),
            action.description().text("it"),
            action.kind(),
            action.automationLevel(),
            action.enabled(),
            action.attributes() + mapOf(
                "costs" to "turn:action=if(\${level} > 3, 2, 1)",
                "plugin.custom" to "keep-me",
            ),
            action.tags(),
            replaceAttributes = true,
            authoringMode = AuthoringMode.VISUAL,
        )

        assertEquals(setOf("costs", "plugin.custom"), rules.protectedAuthoringFields(action.id()))

        val reloaded = RulesViewModel(directory)
        val draft = reloaded.choices.single { it.isDraft }
        reloaded.selectRuleset(draft.key)
        reloaded.selectEntity(action.id())
        assertEquals(setOf("costs", "plugin.custom"), reloaded.protectedAuthoringFields(action.id()))
    }

    @Test
    fun `le primitive modulari nascono con uno schema esplicito e modificabile`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()

        rules.addRule(RuleKind.VALUE)
        val value = requireNotNull(rules.selectedEntity)
        assertEquals("SCALAR", value.attributes()["dimension"])
        assertEquals("PERMANENT", value.attributes()["lifetime"])
        assertEquals("SCOPE", value.attributes()["owner"])
        assertEquals("LOCAL_ONLY", value.attributes()["syncPolicy"])

        rules.addRule(RuleKind.CONDITION)
        val condition = requireNotNull(rules.selectedEntity)
        assertEquals("REPLACE", condition.attributes()["stacking"])
        assertEquals("false", condition.attributes()["sourceScoped"])

        rules.addRule(RuleKind.HEALTH_MODEL)
        val health = requireNotNull(rules.selectedEntity)
        assertEquals("MANUAL", health.attributes()["zeroState"])
        assertTrue(health.attributes().containsKey("primaryResourceRef"))

        rules.addRule(RuleKind.MOVEMENT)
        val movement = requireNotNull(rules.selectedEntity)
        assertEquals("SQUARE", movement.attributes()["topology"])
        assertEquals("1", movement.attributes()["unitsPerCell"])
        assertEquals("unit", movement.attributes()["canonicalUnit"])

        rules.addRule(RuleKind.SHEET_SECTION)
        val section = requireNotNull(rules.selectedEntity)
        assertEquals("LIST", section.attributes()["layout"])
        assertEquals("1", section.attributes()["columns"])

        rules.addRule(RuleKind.SCENE_PROCEDURE)
        val scene = requireNotNull(rules.selectedEntity)
        assertEquals("SCENE", scene.attributes()["phases"])
        assertEquals("false", scene.attributes()["initiativeRequired"])
        assertEquals(RuleAutomationLevel.ASSISTED, scene.automationLevel())
    }

    @Test
    fun `una revisione pubblicata si esporta e si reinstalla in una libreria indipendente`() {
        val first = RulesViewModel(directory.resolve("first"))
        first.forkSelected()
        first.addRule(RuleKind.VALUE)
        val valueId = requireNotNull(first.selectedEntityId)
        first.updateEntity(
            valueId, "Tensione", "Tensione della scena.", RuleKind.VALUE,
            RuleAutomationLevel.ASSISTED, true,
            mapOf("valueType" to "NUMBER", "defaultValue" to "2"),
            listOf("test"),
        )
        first.publishSelected("1.0.0")
        val exportedHash = requireNotNull(first.selected).revision.canonicalHash()
        val portable = directory.resolve("tensione.ruleset.json")

        assertTrue(first.exportSelected(portable.toString()))
        val second = RulesViewModel(directory.resolve("second"))
        assertTrue(second.importRevision(portable.toString()))

        assertEquals(exportedHash, requireNotNull(second.selected).revision.canonicalHash())
        assertNotNull(second.selected?.revision?.entity(valueId))
    }

    @Test
    fun `stat e classe creati dalla fondazione vuota producono un pack guidato autonomo`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()

        rules.addRule(RuleKind.STAT)
        val statEntity = requireNotNull(rules.selectedEntity)
        val statId = statEntity.attributes().getValue("statId")
        rules.addRule(RuleKind.CLASS)
        val classEntity = requireNotNull(rules.selectedEntity)

        assertEquals(statId, classEntity.attributes()["primaryAbilities"])
        assertEquals("1", classEntity.attributes()["maximumLevel"])
        rules.publishSelected("1.0.0")

        val revision = requireNotNull(rules.selected).revision
        val pack = SrdRulesetCharacterAdapter.project(revision, AppLanguage.ITALIAN)
        assertEquals(listOf(statId), pack.stats.map { it.id.value })
        assertEquals(listOf(classEntity.id()), pack.classes.map { it.id.value })
        assertTrue(pack.skills.isEmpty())
        assertTrue(pack.weapons.isEmpty())
        assertTrue(pack.backgrounds.isEmpty())
        assertTrue(pack.equipmentPackages.isEmpty())
    }

    @Test
    fun `replace attributes rimuove le track e i nuovi elementi espongono i default di progressione`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()

        rules.addRule(RuleKind.PROGRESSION)
        val progression = requireNotNull(rules.selectedEntity)
        val progressionDefaults = progression.attributes()
        assertEquals("1", progressionDefaults["maximumLevel"])
        assertEquals("false", progressionDefaults["defaultExperience"])

        rules.updateEntity(
            entityId = progression.id(),
            name = progression.name().text("it"),
            description = progression.description().text("it"),
            kind = progression.kind(),
            automation = progression.automationLevel(),
            enabled = progression.enabled(),
            attributes = mapOf("track.foo" to "table:foo"),
            tags = progression.tags(),
        )

        val withTrack = requireNotNull(rules.selectedEntity)
        assertEquals("table:foo", withTrack.attributes()["track.foo"])
        assertEquals("1", withTrack.attributes()["maximumLevel"])
        assertEquals("false", withTrack.attributes()["defaultExperience"])

        rules.updateEntity(
            entityId = withTrack.id(),
            name = withTrack.name().text("it"),
            description = withTrack.description().text("it"),
            kind = withTrack.kind(),
            automation = withTrack.automationLevel(),
            enabled = withTrack.enabled(),
            attributes = progressionDefaults,
            tags = withTrack.tags(),
            replaceAttributes = true,
        )

        val replaced = requireNotNull(rules.selectedEntity)
        assertFalse(replaced.attributes().containsKey("track.foo"))
        assertEquals("1", replaced.attributes()["maximumLevel"])
        assertEquals("false", replaced.attributes()["defaultExperience"])

        rules.addRule(RuleKind.CLASS)
        val characterClass = requireNotNull(rules.selectedEntity)
        assertEquals("", characterClass.attributes()["progressionEntityRef"])
    }

    @Test
    fun `una modifica ereditata puo essere ripristinata senza toccare la regola base`() {
        val rules = RulesViewModel(directory)
        val base = requireNotNull(rules.selected).revision.entities().first()
        val originalName = base.name().text("it")

        rules.forkSelected()
        rules.selectEntity(base.id())
        rules.updateEntity(
            entityId = base.id(),
            name = "Regola modificata",
            description = base.description().text("it"),
            kind = base.kind(),
            automation = base.automationLevel(),
            enabled = base.enabled(),
            attributes = base.attributes(),
            tags = base.tags(),
        )

        assertEquals(DraftEntityChange.MODIFIED, rules.selectedEntityChange)
        assertEquals(1, rules.draftChangeSummary?.modified)
        assertEquals("Regola modificata", rules.selectedEntity?.name()?.text("it"))

        rules.resetSelectedEntityChange()

        assertEquals(DraftEntityChange.INHERITED, rules.selectedEntityChange)
        assertEquals(0, rules.draftChangeSummary?.modified)
        assertEquals(originalName, rules.selectedEntity?.name()?.text("it"))
    }

    @Test
    fun `modificare direttamente una regola pubblicata apre una bozza sulla stessa regola`() {
        val rules = RulesViewModel(directory)
        val base = requireNotNull(rules.selected).revision.entities().first()

        rules.forkSelected(base.id())

        assertTrue(requireNotNull(rules.selected).isDraft)
        assertEquals(base.id(), rules.selectedEntityId)
        assertEquals(base.id(), rules.selectedEntity?.id())
        assertEquals(DraftEntityChange.INHERITED, rules.selectedEntityChange)
    }

    @Test
    fun `una regola aggiunta puo essere rimossa e filtri e ricerca includono i parametri`() {
        val rules = RulesViewModel(directory)
        rules.forkSelected()
        rules.addRule(RuleKind.CLASS)
        val addedId = requireNotNull(rules.selectedEntityId)
        rules.updateEntity(
            entityId = addedId,
            name = "Cronomante",
            description = "Manipola il tempo.",
            kind = RuleKind.CLASS,
            automation = RuleAutomationLevel.MANUAL,
            enabled = false,
            attributes = mapOf("risorsa" to "frammenti temporali"),
            tags = listOf("tempo"),
        )

        assertEquals(DraftEntityChange.ADDED, rules.selectedEntityChange)
        rules.search = "frammenti temporali"
        rules.kindFilter = RuleKind.CLASS
        rules.automationFilter = RuleAutomationLevel.MANUAL
        rules.enabledFilter = RuleEnabledFilter.DISABLED
        assertEquals(listOf(addedId), rules.visibleEntities.map { it.id() })

        rules.resetSelectedEntityChange()

        assertNull(rules.selectedEntityId)
        assertNull(rules.selected?.revision?.entity(addedId))
        assertEquals(0, rules.draftChangeSummary?.added)
    }

    @Test
    fun `una revisione con collegamenti eseguibili incompleti non puo essere pubblicata`() {
        val rules = RulesViewModel(directory)
        rules.forkSelected()
        rules.addRule(RuleKind.MODIFIER)

        rules.publishSelected("1.0.0")

        assertTrue(requireNotNull(rules.selected).isDraft)
        assertTrue(rules.status.orEmpty().contains("ownerRef"))
    }

    @Test
    fun `modificare gli attributi della competenza aggiorna lo stesso runtime eseguibile`() {
        val rules = RulesViewModel(directory)
        val base = requireNotNull(rules.selected?.revision?.entity(CoreRuleIds.PROFICIENCY))
        rules.forkSelected(base.id())

        rules.updateEntity(
            entityId = base.id(),
            name = base.name().text("it"),
            description = base.description().text("it"),
            kind = base.kind(),
            automation = base.automationLevel(),
            enabled = true,
            attributes = base.attributes() + mapOf(
                "base" to "4",
                "levelsPerIncrease" to "2",
                "maximum" to "9",
            ),
            tags = base.tags(),
        )

        val edited = requireNotNull(rules.selected)
        assertEquals(4, edited.revision.runtime().proficiencyBonusBase())
        assertEquals(2, edited.revision.runtime().proficiencyLevelsPerIncrease())
        assertEquals(9, edited.revision.runtime().proficiencyBonusMaximum())
        assertEquals("4", edited.revision.entity(base.id()).attributes()["base"])
    }

    @Test
    fun `un modificatore creato da una classe nasce gia collegato e pubblicabile`() {
        val rules = RulesViewModel(directory)
        val fighter = requireNotNull(
            rules.selected?.revision?.entities()?.firstOrNull {
                it.kind() == RuleKind.CLASS && it.attributes()["classId"] == "FIGHTER"
            },
        )
        rules.forkSelected(fighter.id())

        rules.addLinkedModifier(fighter.id())

        val modifier = requireNotNull(rules.selectedEntity)
        assertEquals(RuleKind.MODIFIER, modifier.kind())
        assertEquals(fighter.id(), modifier.attributes()["ownerRef"])
        rules.publishSelected("1.0.0")
        assertFalse(requireNotNull(rules.selected).isDraft)
    }

    @Test
    fun `valori non numerici sono dichiarabili collegabili pubblicabili ed eseguibili`() {
        val rules = RulesViewModel(directory)
        rules.forkSelected()
        rules.addRule(RuleKind.VALUE)
        val valueId = requireNotNull(rules.selectedEntityId)
        rules.updateEntity(
            valueId,
            "Assetto della scena",
            "Stato testuale usato da azioni e trigger.",
            RuleKind.VALUE,
            RuleAutomationLevel.FULL,
            true,
            mapOf(
                "valueType" to "TEXT",
                "defaultValue" to "CALMA",
                "allowedValues" to "CALMA,PERICOLO",
                "mutable" to "true",
            ),
            listOf("valore"),
        )
        rules.addRule(RuleKind.MODIFIER)
        val effectId = requireNotNull(rules.selectedEntityId)
        rules.updateEntity(
            effectId,
            "Entra in pericolo",
            "Imposta lo stato tipizzato.",
            RuleKind.MODIFIER,
            RuleAutomationLevel.FULL,
            true,
            mapOf(
                "ownerRef" to valueId,
                "targetRef" to valueId,
                "application" to "SET_VALUE",
                "valueType" to "TEXT",
                "valueLiteral" to "PERICOLO",
                "conditionFormula" to "1",
            ),
            listOf("effetto"),
        )
        rules.addRule(RuleKind.ACTION)
        val actionId = requireNotNull(rules.selectedEntityId)
        rules.updateEntity(
            actionId,
            "Allarme",
            "Azione universale collegata allo stato.",
            RuleKind.ACTION,
            RuleAutomationLevel.FULL,
            true,
            mapOf("costs" to "", "conditionFormula" to "1", "effectRefs" to effectId),
            listOf("azione"),
        )

        rules.publishSelected("1.0.0")

        val compiled = requireNotNull(rules.selected).revision.compile()
        val initial = compiled.initialState(emptyMap(), emptySet())
        assertEquals(RuleValue.text("CALMA"), compiled.ruleValue(valueId, initial))
        val changed = compiled.executeAction(actionId, initial)
        assertEquals(RuleValue.text("PERICOLO"), compiled.ruleValue(valueId, changed.state()))
        assertFalse(requireNotNull(rules.selected).isDraft)
    }

    @Test
    fun `danni e condizioni homebrew entrano nei selettori dei contenuti`() {
        val rules = RulesViewModel(directory)
        rules.forkSelected()
        rules.addRule(RuleKind.DAMAGE_TYPE)
        val damageId = requireNotNull(rules.selectedEntityId)
        rules.updateEntity(
            damageId, "Cronale", "Danno del tempo.", RuleKind.DAMAGE_TYPE,
            RuleAutomationLevel.FULL, true,
            mapOf("damageTypeId" to "homebrew:damage:chronal"), listOf("danno"),
        )
        rules.addRule(RuleKind.CONDITION)
        val conditionId = requireNotNull(rules.selectedEntityId)
        rules.updateEntity(
            conditionId, "Sfasato", "Fuori fase.", RuleKind.CONDITION,
            RuleAutomationLevel.FULL, true,
            mapOf("conditionId" to "homebrew:condition:phased", "maximumStacks" to "3"),
            listOf("condizione"),
        )
        rules.publishSelected("1.0.0")
        val published = requireNotNull(rules.selected)
        assertFalse(published.isDraft)

        val sheets = SheetViewModel(
            SheetStore(directory.resolve("typed-content-characters.json")),
            loadOnCreate = false,
            rulesetProvider = { rules.publishedRevisions },
        )
        assertTrue(sheets.selectCharacterRuleset(published.key))
        assertTrue(DamageType.of("homebrew:damage:chronal") in sheets.damageTypesFor())
        assertTrue(ConditionType.of("homebrew:condition:phased") in sheets.conditionTypesFor())
    }

    @Test
    fun `la composizione moduli mostra dipendenze conflitti e pubblica soltanto dopo un winner`() {
        val rules = RulesViewModel(directory)
        val target = requireNotNull(rules.selected).revision.entities().first()
        val dependency = module("module:dependency", target.id(), "dependency-test", "yes")
        val consumer = module(
            "module:consumer",
            target.id(),
            "consumer-test",
            "yes",
            dependencies = listOf(dependency.reference()),
        )
        val first = module("module:first", target.id(), "conflict-test", "first")
        val second = module("module:second", target.id(), "conflict-test", "second")
        listOf(dependency, consumer, first, second).forEach { candidate ->
            assertTrue(rules.importModule(writeModule(candidate).toString()))
        }

        rules.beginModuleComposition()
        rules.toggleCompositionModule(consumer.canonicalHash())
        assertTrue(rules.compositionIssues.any {
            it.code() == RulesetCompositionIssue.Code.MISSING_DEPENDENCY
        })
        assertFalse(rules.canPublishComposition)

        rules.toggleCompositionModule(dependency.canonicalHash())
        assertTrue(rules.compositionIssues.any {
            it.code() == RulesetCompositionIssue.Code.DEPENDENCY_ORDER
        })
        rules.moveCompositionModule(dependency.canonicalHash(), -1)
        assertTrue(rules.compositionIssues.isEmpty())
        assertNotNull(rules.compositionPreview)
        assertTrue(rules.compositionChanges.any {
            it.path.endsWith("/attributes/consumer-test") && it.before == null && it.after == "yes"
        })

        rules.cancelModuleComposition()
        rules.beginModuleComposition()
        rules.toggleCompositionModule(first.canonicalHash())
        rules.toggleCompositionModule(second.canonicalHash())
        val conflict = rules.compositionIssues.single {
            it.code() == RulesetCompositionIssue.Code.FIELD_CONFLICT
        }
        assertEquals(
            listOf(first.reference(), second.reference()),
            conflict.candidateWinners(),
        )
        assertFalse(rules.publishModuleComposition())

        rules.chooseCompositionWinner(conflict, second.canonicalHash())
        rules.updateCompositionMetadata("Regolamento composto", "Due moduli con winner esplicito", "2.0.0")
        assertTrue(rules.canPublishComposition)
        assertTrue(rules.publishModuleComposition())

        val published = requireNotNull(rules.selected)
        assertEquals("second", published.revision.entity(target.id()).attributes()["conflict-test"])
        val lock = requireNotNull(rules.selectedCompositionLock)
        assertEquals(listOf(first.reference(), second.reference()), lock.modules())
        assertEquals(second.canonicalHash(), lock.resolutions().single().winnerModuleHash())

        val reloaded = RulesViewModel(directory)
        reloaded.selectRuleset(published.key)
        assertEquals(lock, reloaded.selectedCompositionLock)

        val bundle = directory.resolve("composition.onfall-rules-bundle")
        assertTrue(rules.canExportSelectedBundle)
        assertTrue(rules.exportSelectedBundle(bundle.toString()))
        val imported = RulesViewModel(directory.resolve("bundle-import"))
        val explicitDefault = imported.defaultPublishedRevisionHash
        assertTrue(imported.importBundle(bundle.toString()))
        assertEquals(published.revision.canonicalHash(), imported.selected?.revision?.canonicalHash())
        assertEquals(lock, imported.selectedCompositionLock)
        assertTrue(imported.selectedCompositionBaseAvailable)
        assertEquals(explicitDefault, imported.defaultPublishedRevisionHash)
        assertTrue(imported.importBundle(bundle.toString()))
        assertEquals(1, imported.installedModules.count { it.canonicalHash() == first.canonicalHash() })
        assertEquals(1, imported.installedModules.count { it.canonicalHash() == second.canonicalHash() })
    }

    @Test
    fun `moduli incompatibili bloccano la pubblicazione senza creare revisioni`() {
        val rules = RulesViewModel(directory.resolve("incompatible"))
        val target = requireNotNull(rules.selected).revision.entities().first()
        val first = module(
            "module:exclusive:first", target.id(), "exclusive-first", "yes",
            incompatible = setOf("module:exclusive:second"),
        )
        val second = module("module:exclusive:second", target.id(), "exclusive-second", "yes")
        assertTrue(rules.importModule(writeModule(first, "first-exclusive").toString()))
        assertTrue(rules.importModule(writeModule(second, "second-exclusive").toString()))
        val revisionCount = rules.publishedRevisions.size

        rules.beginModuleComposition()
        rules.toggleCompositionModule(first.canonicalHash())
        rules.toggleCompositionModule(second.canonicalHash())

        assertTrue(rules.compositionIssues.any {
            it.code() == RulesetCompositionIssue.Code.INCOMPATIBLE_MODULES
        })
        assertFalse(rules.publishModuleComposition())
        assertEquals(revisionCount, rules.publishedRevisions.size)
    }

    private fun localRepository(): LocalRulesetRepository = LocalRulesetRepository(
        directory.resolve("rulesets"),
        listOf(Srd521Ruleset.revision, GenericRulesetFoundation.revision()),
    )

    private fun writeModule(module: RulesetModule, fileName: String = module.id().replace(':', '-')): Path {
        val path = directory.resolve("$fileName.onfall-rules-module")
        Files.writeString(path, Json.encode(RulesetLibraryJsonCodec.encodePortableModule(module)))
        return path
    }

    private fun module(
        id: String,
        targetId: String,
        key: String,
        value: String,
        dependencies: List<RulesetModuleRef> = emptyList(),
        incompatible: Set<String> = emptySet(),
    ): RulesetModule = RulesetModule.create(
        id,
        "1.0.0",
        LocalizedRuleText.single("it", id),
        LocalizedRuleText.single("it", "Modulo di test"),
        RulesetOrigin.HOMEBREW,
        "1",
        dependencies,
        incompatible,
        listOf(
            RulePatch(
                "patch:$id",
                targetId,
                null,
                null,
                mapOf(key to value),
                emptySet(),
                null,
                null,
                null,
                null,
            ),
        ),
        emptyList(),
    )
}
