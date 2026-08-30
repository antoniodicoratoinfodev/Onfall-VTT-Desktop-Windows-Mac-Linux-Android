package app.d6d.ui.rules

import app.d6d.i18n.AppLanguage
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageType
import app.d6d.rules.model.CoreRuleIds
import app.d6d.rules.model.RuleAutomationLevel
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RuleValue
import app.d6d.rules.model.RulesetOrigin
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
}
