package app.d6d.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.CombatStatus
import app.d6d.i18n.label
import app.d6d.rules.character.EffectCondition
import app.d6d.rules.character.EffectTarget
import app.d6d.rules.authoring.AuthoringMode
import app.d6d.rules.authoring.FormulaDraft
import app.d6d.rules.authoring.ProjectionStatus
import app.d6d.rules.model.RuleAutomationLevel
import app.d6d.rules.model.RuleEntity
import app.d6d.rules.model.RuleFormula
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RuleScope
import app.d6d.rules.model.RuleValue
import app.d6d.rules.model.RulesetCompiler
import app.d6d.rules.model.RulesetCompositionIssue
import app.d6d.rules.model.RulesetRuntimeConfig
import app.d6d.ui.battle.GameButton
import app.d6d.ui.battle.LocalDenseGameButtonTouchTargets
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.i18n.strings
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.OnfallTheme
import app.d6d.ui.theme.Palette
import java.math.BigDecimal

private val exposedKinds = RuleKind.entries.toList()
private val modifierOwnerKinds = setOf(
    RuleKind.CLASS,
    RuleKind.FEATURE,
    RuleKind.SUBCLASS,
    RuleKind.FEAT,
    RuleKind.SPELL,
    RuleKind.ACTION,
)
private val modularRuntimeKinds = setOf(
    RuleKind.VALUE,
    RuleKind.CONDITION,
    RuleKind.HEALTH_MODEL,
    RuleKind.MOVEMENT,
    RuleKind.SHEET_SECTION,
    RuleKind.SCENE_PROCEDURE,
)
internal val guidedEditorKinds = setOf(
    RuleKind.STAT,
    RuleKind.SKILL,
    RuleKind.SAVE,
    RuleKind.DEFENSE,
    RuleKind.VALUE,
    RuleKind.MODIFIER,
    RuleKind.CONDITION,
    RuleKind.RESOURCE,
    RuleKind.TRACK,
    RuleKind.RANDOMIZER,
    RuleKind.ROLL,
    RuleKind.ACTION_ECONOMY,
    RuleKind.ACTION,
    RuleKind.TRIGGER,
    RuleKind.TABLE,
    RuleKind.PROGRESSION,
    RuleKind.HEALTH_MODEL,
    RuleKind.MOVEMENT,
    RuleKind.SHEET_SECTION,
    RuleKind.SCENE_PROCEDURE,
    RuleKind.DAMAGE_TYPE,
    RuleKind.TEXT_RULE,
)
private val rollResolutionAttributes = setOf(
    "randomizerRef",
    "totalFormula",
    "targetFormula",
    "comparison",
    "naturalSuccessMinimum",
    "naturalFailureMaximum",
    "threatMinimumNatural",
    "confirmationRequired",
    "criticalMultiplier",
    "outcomeTableRef",
    "opposedRollRef",
)

private val LocalGuidedFormulaEditing = staticCompositionLocalOf { false }

internal enum class RulesWorkspaceArea { OVERVIEW, RULES, BUILDER, TEST, PACKAGES }

internal class RuleEditorNavigationGate {
    var entityId: String? = null
        private set
    var dirty by mutableStateOf(false)
        private set
    var valid by mutableStateOf(true)
        private set
    var showValidationErrors by mutableStateOf(false)
        private set
    var validationMessage by mutableStateOf<String?>(null)
        private set
    private var saveAction: () -> Boolean = { true }
    private var resetAction: () -> Unit = {}

    fun bind(
        entityId: String,
        dirty: Boolean,
        valid: Boolean,
        validationMessage: String?,
        save: () -> Boolean,
        reset: () -> Unit,
    ) {
        if (this.entityId != entityId) showValidationErrors = false
        this.entityId = entityId
        this.dirty = dirty
        this.valid = valid
        this.validationMessage = validationMessage
        saveAction = save
        resetAction = reset
    }

    fun clear(entityId: String) {
        if (this.entityId != entityId) return
        this.entityId = null
        dirty = false
        valid = true
        showValidationErrors = false
        validationMessage = null
        saveAction = { true }
        resetAction = {}
    }

    fun save(): Boolean {
        if (!dirty) return true
        if (!valid) {
            showValidationErrors = true
            return false
        }
        return saveAction().also { saved -> if (saved) dirty = false }
    }

    fun reset() {
        resetAction()
        dirty = false
        showValidationErrors = false
    }

    fun navigate(action: () -> Unit): Boolean {
        if (!save()) return false
        action()
        return true
    }
}

/** Catalogo affiancato al Compendio: standard read-only, fork e revisioni homebrew. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(
    viewModel: RulesViewModel,
    compact: Boolean,
    activeBattle: BattleViewModel?,
    modifier: Modifier = Modifier,
) {
    val words = strings.rules
    val italian = strings.language.tag == "it"
    var localNotice by remember { mutableStateOf<String?>(null) }
    var area by remember { mutableStateOf(RulesWorkspaceArea.OVERVIEW) }
    val navigationGate = remember { RuleEditorNavigationGate() }
    fun navigate(action: () -> Unit) {
        if (!navigationGate.navigate(action)) {
            localNotice = if (italian) {
                "Completa i campi obbligatori prima di cambiare schermata."
            } else {
                "Complete the required fields before leaving this screen."
            }
        }
    }
    CompositionLocalProvider(LocalDenseGameButtonTouchTargets provides compact) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(words.title, color = Palette.Text, style = MaterialTheme.typography.titleLarge)
            Text(words.subtitle, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            activeBattle?.state?.rulesetBinding()?.let { binding ->
                Text(
                    "${words.currentSessionUses}: ${binding.displayName()}",
                    color = Palette.Heal,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        GoldenRule()

        RulesWorkspaceTabs(
            selected = area,
            compact = compact,
            onSelect = { selected -> navigate { area = selected } },
        )

        val notice = localNotice ?: viewModel.status
        notice?.let { message ->
            Text(
                message,
                color = Palette.GoldBright,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().background(Palette.Gold.copy(alpha = .10f))
                    .clickable {
                        localNotice = null
                        viewModel.dismissStatus()
                    }.padding(horizontal = 18.dp, vertical = 7.dp),
            )
        }

        when (area) {
            RulesWorkspaceArea.OVERVIEW -> RulesOverview(
                viewModel = viewModel,
                activeBattle = activeBattle,
                compact = compact,
                onOpenArea = { selected -> navigate { area = selected } },
                onSelectRuleset = { key -> navigate { viewModel.selectRuleset(key) } },
                onNotice = { localNotice = it },
                modifier = Modifier.fillMaxSize(),
            )
            RulesWorkspaceArea.RULES -> RulesCatalog(
                viewModel = viewModel,
                compact = compact,
                onSelectRuleset = { key -> navigate { viewModel.selectRuleset(key) } },
                onSelectEntity = { id -> navigate { viewModel.selectEntity(id) } },
                onEdit = { navigate { area = RulesWorkspaceArea.BUILDER } },
                modifier = Modifier.fillMaxSize(),
            )
            RulesWorkspaceArea.BUILDER -> RulesBuilder(
                viewModel = viewModel,
                activeBattle = activeBattle,
                compact = compact,
                navigationGate = navigationGate,
                onSelectEntity = { id -> navigate { viewModel.selectEntity(id) } },
                onNotice = { localNotice = it },
                modifier = Modifier.fillMaxSize(),
            )
            RulesWorkspaceArea.TEST -> RulesTestLab(
                viewModel = viewModel,
                navigationGate = navigationGate,
                modifier = Modifier.fillMaxSize(),
            )
            RulesWorkspaceArea.PACKAGES -> RulesPackages(
                viewModel = viewModel,
                activeBattle = activeBattle,
                compact = compact,
                onSelectRuleset = { key -> navigate { viewModel.selectRuleset(key) } },
                onNotice = { localNotice = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    }
}

@Composable
private fun RulesWorkspaceTabs(
    selected: RulesWorkspaceArea,
    compact: Boolean,
    onSelect: (RulesWorkspaceArea) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val choices = listOf(
        RulesWorkspaceArea.OVERVIEW to if (italian) "Panoramica" else "Overview",
        RulesWorkspaceArea.RULES to if (italian) "Regole" else "Rules",
        RulesWorkspaceArea.BUILDER to if (italian) "Costruttore" else "Builder",
        RulesWorkspaceArea.TEST to if (italian) "Prova" else "Test",
        RulesWorkspaceArea.PACKAGES to if (italian) "Gestione" else "Manage",
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(
            horizontal = if (compact) 8.dp else 14.dp,
            vertical = 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        choices.forEach { (area, label) ->
            GameButton(label, dense = !compact, selected = selected == area, onClick = { onSelect(area) })
        }
    }
    GoldenRule()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesOverview(
    viewModel: RulesViewModel,
    activeBattle: BattleViewModel?,
    compact: Boolean,
    onOpenArea: (RulesWorkspaceArea) -> Unit,
    onSelectRuleset: (String) -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val italian = strings.language.tag == "it"
    val words = strings.rules
    val choice = viewModel.selected
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(if (compact) 12.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (italian) "Cosa vuoi fare?" else "What do you want to do?",
            color = Palette.Text,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (italian) {
                "Scegli un’attività: i dettagli tecnici restano disponibili in Gestione."
            } else {
                "Choose a task; technical details remain available under Manage."
            },
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameButton(
                if (italian) "Cambiare una regola" else "Change a rule",
                subtitle = if (italian) "Cerca ciò che esiste e aprilo nel costruttore" else
                    "Find an existing rule and open it in the builder",
                accent = Palette.Party,
                onClick = { onOpenArea(RulesWorkspaceArea.RULES) },
            )
            GameButton(
                if (italian) "Creare una regola" else "Create a rule",
                subtitle = if (italian) "Parti dall’effetto che vuoi ottenere" else
                    "Start from the outcome you want",
                accent = Palette.Heal,
                onClick = {
                    when {
                        choice == null -> viewModel.createBlankRuleset()
                        choice.isDraft -> viewModel.clearEntitySelection()
                        else -> viewModel.forkSelected()
                    }
                    onOpenArea(RulesWorkspaceArea.BUILDER)
                },
            )
            GameButton(
                if (italian) "Provare le modifiche" else "Test changes",
                subtitle = if (italian) "Controlla errori e risultato prima di pubblicare" else
                    "Check errors and outcome before publishing",
                onClick = { onOpenArea(RulesWorkspaceArea.TEST) },
            )
            GameButton(
                if (italian) "Gestire e condividere" else "Manage and share",
                subtitle = if (italian) "Versioni, moduli, importazione ed esportazione" else
                    "Versions, modules, import, and export",
                accent = Palette.TextMuted,
                onClick = { onOpenArea(RulesWorkspaceArea.PACKAGES) },
            )
        }
        HorizontalDivider(color = Palette.Line)
        Eyebrow(if (italian) "Crea un nuovo regolamento" else "Create a new ruleset")
        Text(
            if (italian) {
                "Scegli se usare tutte le regole SRD come punto di partenza oppure cominciare da zero."
            } else {
                "Choose whether to use the complete SRD as your starting point or begin from scratch."
            },
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameButton(
                words.newFromSrdRuleset,
                subtitle = words.newFromSrdHint,
                accent = Palette.Heal,
                onClick = {
                    viewModel.createSrdBasedRuleset()
                    onOpenArea(RulesWorkspaceArea.BUILDER)
                },
            )
            GameButton(
                words.newBlankRuleset,
                subtitle = words.newBlankRulesetHint,
                onClick = {
                    viewModel.createBlankRuleset()
                    onOpenArea(RulesWorkspaceArea.BUILDER)
                },
            )
        }
        HorizontalDivider(color = Palette.Line)
        Eyebrow(if (italian) "Regolamento selezionato" else "Selected ruleset")
        if (choice == null) {
            Text(words.noResults, color = Palette.TextMuted)
        } else {
            RulesetSummaryCard(choice)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (!choice.isDraft) {
                    GameButton(
                        if (choice.readOnly) words.fork else words.newRevisionDraft,
                        accent = Palette.Heal,
                        onClick = { viewModel.forkSelected(); onOpenArea(RulesWorkspaceArea.BUILDER) },
                    )
                } else {
                    GameButton(
                        if (italian) "Continua la bozza" else "Continue draft",
                        accent = Palette.Heal,
                        onClick = { onOpenArea(RulesWorkspaceArea.BUILDER) },
                    )
                }
                if (!choice.isDraft) {
                    val resolved = activeBattle?.state?.status() == CombatStatus.RESOLVED
                    GameButton(
                        words.applyToSession,
                        enabled = activeBattle != null && !resolved &&
                            activeBattle.state.rulesetBinding().canonicalHash() != choice.revision.canonicalHash(),
                        onClick = {
                            val cpuWasEnabled = activeBattle?.enemyCpuEnabled == true
                            if (activeBattle?.applyRuleset(choice.revision) == true) {
                                onNotice(
                                    if (cpuWasEnabled && !activeBattle.enemyCpuEnabled) {
                                        words.appliedWithManualCpu(choice.name)
                                    } else words.applied(choice.name),
                                )
                            }
                        },
                    )
                }
            }
        }
        Eyebrow(if (italian) "Cambia regolamento" else "Switch ruleset")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            viewModel.choices.forEach { candidate ->
                GameButton(
                    candidate.name,
                    dense = true,
                    selected = candidate.key == viewModel.selectedKey,
                    subtitle = if (candidate.isDraft) words.editableDraft else words.revision(candidate.version),
                    onClick = { onSelectRuleset(candidate.key) },
                )
            }
        }
    }
}

@Composable
private fun RulesetSummaryCard(choice: RulesetChoice) {
    val words = strings.rules
    Column(
        Modifier.fillMaxWidth().background(Palette.SurfaceHigh, RoundedCornerShape(9.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(9.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(choice.name, color = Palette.Text, style = MaterialTheme.typography.titleMedium)
        Text(choice.revision.description(), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(if (choice.isDraft) words.editableDraft else words.revision(choice.version), Palette.Heal)
            Chip(words.entities(choice.revision.entities().size), Palette.Party)
        }
    }
}

@Composable
private fun RulesCatalog(
    viewModel: RulesViewModel,
    compact: Boolean,
    onSelectRuleset: (String) -> Unit,
    onSelectEntity: (String) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OriginFilters(
            viewModel,
            onChange = { viewModel.changeOriginFilter(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
        )
        RulesetPickerBar(viewModel, onSelectRuleset)
        GoldenRule()
        if (compact && viewModel.selectedEntity != null) {
            Column(Modifier.fillMaxSize()) {
                GameButton(
                    if (strings.language.tag == "it") "‹ Torna alle regole" else "‹ Back to rules",
                    modifier = Modifier.padding(10.dp),
                    accent = Palette.TextMuted,
                    onClick = { viewModel.clearEntitySelection() },
                )
                CatalogEntityDetail(viewModel, onEdit, Modifier.fillMaxSize())
            }
        } else if (compact) {
            EntityBrowser(viewModel, Modifier.fillMaxSize(), compact = true, onSelect = onSelectEntity)
        } else {
            Row(Modifier.fillMaxSize()) {
                EntityBrowser(viewModel, Modifier.width(360.dp).fillMaxSize(), onSelect = onSelectEntity)
                GoldenVerticalRule()
                CatalogEntityDetail(viewModel, onEdit, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun RulesetPickerBar(viewModel: RulesViewModel, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        viewModel.choices.forEach { choice ->
            GameButton(
                choice.name,
                dense = true,
                selected = choice.key == viewModel.selectedKey,
                subtitle = if (choice.isDraft) strings.rules.editableDraft else choice.version,
                onClick = { onSelect(choice.key) },
            )
        }
    }
}

@Composable
private fun CatalogEntityDetail(
    viewModel: RulesViewModel,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val italian = strings.language.tag == "it"
    val entity = viewModel.selectedEntity
    Column(
        modifier.background(Palette.Surface.copy(alpha = .92f)).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (entity == null) {
            Text(
                if (italian) "Scegli una regola per leggerne il riepilogo." else
                    "Choose a rule to read its summary.",
                color = Palette.TextMuted,
            )
            return@Column
        }
        Text(entity.name().text(strings.language.tag), color = Palette.Text, style = MaterialTheme.typography.titleLarge)
        Text(entity.description().text(strings.language.tag), color = Palette.TextMuted,
            style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(kindLabel(entity.kind()), Palette.Party)
            Chip(originLabel(entity.origin()), originColor(entity.origin()))
            Chip(automationLabel(entity.automationLevel()), Palette.TextMuted)
        }
        Text(rulePlainSummary(entity, emptyMap(), strings.language.tag), color = Palette.Text,
            style = OnfallTheme.typography.bodyEmphasis)
        val groupMembers = viewModel.authoringGroupMembers(entity.id()).filter { it.id() != entity.id() }
        if (groupMembers.isNotEmpty()) {
            Eyebrow(if (italian) "Componenti gestiti automaticamente" else "Automatically managed components")
            groupMembers.forEach { member ->
                Text(
                    "${member.name().text(strings.language.tag)} · ${kindLabel(member.kind())}",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (viewModel.selected?.isDraft == true) {
            GameButton(
                if (italian) "Modifica nel costruttore" else "Edit in builder",
                accent = Palette.Heal,
                onClick = onEdit,
            )
        } else {
            Text(
                if (italian) "Crea una variante del regolamento per modificarla." else
                    "Create a ruleset variant to edit it.",
                color = Palette.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RulesBuilder(
    viewModel: RulesViewModel,
    activeBattle: BattleViewModel?,
    compact: Boolean,
    navigationGate: RuleEditorNavigationGate,
    onSelectEntity: (String) -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedEntity = viewModel.selectedEntity
    if (compact && selectedEntity != null) {
        Column(modifier) {
            GameButton(
                if (strings.language.tag == "it") "‹ Elenco e nuova regola" else "‹ List and new rule",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                accent = Palette.TextMuted,
                onClick = { if (navigationGate.navigate(viewModel::clearEntitySelection)) Unit },
            )
            RuleDetail(
                viewModel, activeBattle, onNotice, RuleDetailPurpose.BUILDER,
                navigationGate, Modifier.fillMaxSize(),
            )
        }
    } else if (compact) {
        Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuilderCreationPanel(viewModel, compact = true) { request ->
                navigationGate.navigate {
                    viewModel.addGuidedRule(request.kind, request.name, request.description, request.attributes)
                }
            }
            HorizontalDivider(color = Palette.Line)
            EntityBrowser(viewModel, Modifier.fillMaxWidth().heightIn(min = 300.dp), compact = true,
                onSelect = onSelectEntity)
        }
    } else {
        Row(modifier) {
            Column(Modifier.width(330.dp).fillMaxSize()) {
                BuilderCreationPanel(viewModel, compact = false) { request ->
                    navigationGate.navigate {
                        viewModel.addGuidedRule(request.kind, request.name, request.description, request.attributes)
                    }
                }
                GoldenRule()
                EntityBrowser(viewModel, Modifier.fillMaxSize(), onSelect = onSelectEntity)
            }
            GoldenVerticalRule()
            RuleDetail(
                viewModel, activeBattle, onNotice, RuleDetailPurpose.BUILDER,
                navigationGate, Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RulesTestLab(
    viewModel: RulesViewModel,
    navigationGate: RuleEditorNavigationGate,
    modifier: Modifier = Modifier,
) {
    val italian = strings.language.tag == "it"
    val entity = viewModel.selectedEntity
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (italian) "Laboratorio delle regole" else "Rules lab", color = Palette.Text,
            style = MaterialTheme.typography.titleLarge)
        Text(
            if (italian) "Salva la modifica corrente e usa lo stesso controllo della pubblicazione." else
                "Save the current edit and run the same checks used for publishing.",
            color = Palette.TextMuted,
        )
        entity?.let { selectedRule ->
            Eyebrow(if (italian) "Regola osservata" else "Observed rule")
            Text(selectedRule.name().text(strings.language.tag), color = Palette.Text,
                style = MaterialTheme.typography.titleMedium)
            Text(rulePlainSummary(selectedRule, emptyMap(), strings.language.tag), color = Palette.TextMuted)
            val formulaEntry = selectedRule.attributes().entries.firstOrNull { (key, value) ->
                value.isNotBlank() && (key.endsWith("Formula") || key in setOf("formula", "default")) &&
                    runCatching { RuleFormula.compile(value) }.isSuccess
            }
            formulaEntry?.let { (key, source) ->
                Eyebrow(if (italian) "Scenario modificabile" else "Editable scenario")
                Text(
                    formulaFieldLabel(key, italian),
                    color = Palette.Text,
                    style = OnfallTheme.typography.bodyEmphasis,
                )
                FormulaExample(source, booleanResult = key.contains("condition", ignoreCase = true),
                    numericRules = numericRuleCandidates(viewModel, selectedRule.id()))
            }
        }
        GameButton(
            if (italian) "Controlla tutta la bozza" else "Validate the whole draft",
            accent = Palette.Party,
            enabled = viewModel.selected?.isDraft == true,
            onClick = { if (navigationGate.save()) viewModel.validateSelectedDraft() },
        )
        Text(
            if (italian) {
                "Il controllo verifica formule, collegamenti, contenuti eseguibili e compatibilità con il motore senza modificare partite o pubblicare la bozza."
            } else {
                "Validation checks formulas, links, executable content, and engine compatibility without changing games or publishing the draft."
            },
            color = Palette.TextFaint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RulesPackages(
    viewModel: RulesViewModel,
    activeBattle: BattleViewModel?,
    compact: Boolean,
    onSelectRuleset: (String) -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gate = remember { RuleEditorNavigationGate() }
    Column(modifier) {
        OriginFilters(viewModel, onChange = { viewModel.changeOriginFilter(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp))
        if (compact) {
            RulesetList(viewModel, Modifier.fillMaxWidth().heightIn(max = 260.dp), onSelectRuleset)
            GoldenRule()
            RuleDetail(viewModel, activeBattle, onNotice, RuleDetailPurpose.PACKAGES, gate, Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize()) {
                RulesetList(viewModel, Modifier.width(320.dp).fillMaxSize(), onSelectRuleset)
                GoldenVerticalRule()
                RuleDetail(viewModel, activeBattle, onNotice, RuleDetailPurpose.PACKAGES, gate,
                    Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun OriginFilters(
    viewModel: RulesViewModel,
    onChange: (RulesetOriginFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = strings.rules
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(
            RulesetOriginFilter.ALL to words.allRulesets,
            RulesetOriginFilter.STANDARD to words.standard,
            RulesetOriginFilter.HOMEBREW to words.homebrew,
        ).forEach { (filter, label) ->
            GameButton(
                label = label,
                selected = viewModel.originFilter == filter,
                dense = true,
                onClick = { onChange(filter) },
            )
        }
    }
}

@Composable
private fun RulesetList(
    viewModel: RulesViewModel,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit = viewModel::selectRuleset,
) {
    val words = strings.rules
    var portablePath by remember { mutableStateOf("") }
    LazyColumn(
        modifier.background(Palette.Surface.copy(alpha = .88f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item { Eyebrow(words.ruleset) }
        item {
            GameButton(
                words.newFromSrdRuleset,
                subtitle = words.newFromSrdHint,
                dense = true,
                accent = Palette.Heal,
                onClick = viewModel::createSrdBasedRuleset,
            )
        }
        item {
            GameButton(
                words.newBlankRuleset,
                subtitle = words.newBlankRulesetHint,
                dense = true,
                onClick = viewModel::createBlankRuleset,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RuleTextField(
                    portablePath,
                    { portablePath = it },
                    if (strings.language.tag == "it") {
                        "Percorso revisione o bundle portabile"
                    } else {
                        "Portable revision or bundle path"
                    },
                    Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    GameButton(
                        if (strings.language.tag == "it") "Importa" else "Import",
                        dense = true,
                        enabled = portablePath.isNotBlank(),
                        onClick = { viewModel.importRevision(portablePath) },
                    )
                    GameButton(
                        if (strings.language.tag == "it") "Esporta selezionato" else "Export selected",
                        dense = true,
                        enabled = portablePath.isNotBlank() && viewModel.selected?.let {
                            !it.isDraft && !it.readOnly
                        } == true,
                        onClick = { viewModel.exportSelected(portablePath) },
                    )
                    GameButton(
                        if (strings.language.tag == "it") "Importa bundle" else "Import bundle",
                        dense = true,
                        enabled = portablePath.isNotBlank(),
                        onClick = { viewModel.importBundle(portablePath) },
                    )
                    GameButton(
                        if (strings.language.tag == "it") "Esporta bundle" else "Export bundle",
                        dense = true,
                        enabled = portablePath.isNotBlank() && viewModel.canExportSelectedBundle,
                        onClick = { viewModel.exportSelectedBundle(portablePath) },
                    )
                }
                Text(
                    if (strings.language.tag == "it") {
                        "Il bundle include snapshot, lock e moduli esatti, non la base: resta giocabile offline; futuri confronti/rebase richiedono la base installata."
                    } else {
                        "The bundle includes the snapshot, lock, and exact modules, not its base: it remains playable offline; future comparisons/rebase require the base to be installed."
                    },
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(viewModel.choices, key = { it.key }) { choice ->
            RulesetCard(choice, choice.key == viewModel.selectedKey) { onSelect(choice.key) }
        }
    }
}

@Composable
private fun RulesetCard(choice: RulesetChoice, selected: Boolean, onClick: () -> Unit) {
    val words = strings.rules
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(if (selected) Palette.Gold.copy(alpha = .15f) else Palette.SurfaceHigh, shape)
            .border(1.dp, if (selected) Palette.Gold else Palette.Line, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(choice.name, color = Palette.Text, style = OnfallTheme.typography.itemTitle,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Chip(originLabel(choice.origin), color = originColor(choice.origin))
            Chip(if (choice.isDraft) words.editableDraft else words.revision(choice.version),
                color = if (choice.isDraft) Palette.Heal else Palette.TextMuted)
        }
        Text(words.entities(choice.revision.entities().size), color = Palette.TextFaint,
            style = MaterialTheme.typography.labelSmall)
        Text(
            words.automationCoverage(
                choice.revision.automationCount(RuleAutomationLevel.FULL),
                choice.revision.automationCount(RuleAutomationLevel.ASSISTED),
                choice.revision.automationCount(RuleAutomationLevel.MANUAL),
            ),
            color = Palette.TextFaint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntityBrowser(
    viewModel: RulesViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onSelect: (String) -> Unit = viewModel::selectEntity,
) {
    val words = strings.rules
    val italian = strings.language.tag == "it"
    var showAdditionalFilters by remember { mutableStateOf(false) }
    Column(modifier.background(Palette.Abyss.copy(alpha = .82f)).padding(10.dp)) {
        RuleTextField(
            value = viewModel.search,
            onValueChange = { viewModel.search = it },
            placeholder = words.searchPlaceholder,
            modifier = Modifier.fillMaxWidth(),
        )
        if (compact) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                RuleKindFilterButtons(viewModel)
            }
        } else {
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                RuleKindFilterButtons(viewModel)
            }
        }
        GameButton(
            if (italian) {
                if (showAdditionalFilters) "Nascondi altri filtri" else "Altri filtri…"
            } else if (showAdditionalFilters) "Hide more filters" else "More filters…",
            dense = true,
            selected = showAdditionalFilters,
            accent = Palette.TextMuted,
            onClick = { showAdditionalFilters = !showAdditionalFilters },
        )
        if (showAdditionalFilters) {
            Eyebrow(words.automation)
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                GameButton(
                    strings.common.all,
                    dense = true,
                    selected = viewModel.automationFilter == null,
                    onClick = { viewModel.automationFilter = null },
                )
                RuleAutomationLevel.entries.forEach { level ->
                    GameButton(
                        automationLabel(level),
                        dense = true,
                        selected = viewModel.automationFilter == level,
                        onClick = { viewModel.automationFilter = level },
                    )
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                listOf(
                    RuleEnabledFilter.ALL to strings.common.all,
                    RuleEnabledFilter.ENABLED to words.active,
                    RuleEnabledFilter.DISABLED to words.disabled,
                ).forEach { (filter, label) ->
                    GameButton(
                        label,
                        dense = true,
                        selected = viewModel.enabledFilter == filter,
                        onClick = { viewModel.enabledFilter = filter },
                    )
                }
            }
            if (viewModel.hasGeneratedParts) {
                GameButton(
                    if (italian) {
                        if (viewModel.showGeneratedParts) "Nascondi componenti generati" else "Mostra componenti tecnici"
                    } else if (viewModel.showGeneratedParts) {
                        "Hide generated components"
                    } else {
                        "Show technical components"
                    },
                    dense = true,
                    selected = viewModel.showGeneratedParts,
                    accent = Palette.TextMuted,
                    onClick = { viewModel.showGeneratedParts = !viewModel.showGeneratedParts },
                )
            }
        }
        val entities = viewModel.visibleEntities
        if (entities.isEmpty()) {
            Text(words.noResults, color = Palette.TextMuted, modifier = Modifier.padding(12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(entities, key = { it.id() }) { entity ->
                    EntityRow(entity, entity.id() == viewModel.selectedEntityId) {
                        onSelect(entity.id())
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleKindFilterButtons(viewModel: RulesViewModel) {
    val italian = strings.language.tag == "it"
    var showExactTypes by remember { mutableStateOf(false) }
    GameButton(
        strings.common.all,
        dense = true,
        selected = viewModel.kindFilter == null && viewModel.intentFamilyFilter == null,
        onClick = {
            viewModel.kindFilter = null
            viewModel.intentFamilyFilter = null
        },
    )
    intentChoices(italian).forEach { choice ->
        GameButton(
            choice.label,
            dense = true,
            selected = viewModel.intentFamilyFilter == choice.family && viewModel.kindFilter == null,
            onClick = {
                viewModel.kindFilter = null
                viewModel.intentFamilyFilter = choice.family
            },
        )
    }
    GameButton(
        if (italian) {
            if (showExactTypes) "Meno filtri" else "Tipi precisi…"
        } else if (showExactTypes) "Fewer filters" else "Exact types…",
        dense = true,
        selected = showExactTypes,
        accent = Palette.TextMuted,
        onClick = { showExactTypes = !showExactTypes },
    )
    if (showExactTypes) {
        exposedKinds.forEach { kind ->
            GameButton(
                kindLabel(kind),
                dense = true,
                selected = viewModel.kindFilter == kind,
                onClick = {
                    viewModel.intentFamilyFilter = null
                    viewModel.kindFilter = kind
                },
            )
        }
    }
}

@Composable
private fun EntityRow(entity: RuleEntity, selected: Boolean, onClick: () -> Unit) {
    val language = strings.language.tag
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(if (selected) Palette.Gold.copy(alpha = .14f) else Palette.Surface, shape)
            .border(1.dp, if (selected) Palette.Gold.copy(alpha = .7f) else Palette.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(entity.name().text(language), color = Palette.Text, style = OnfallTheme.typography.itemTitle,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            buildString {
                append(kindLabel(entity.kind()))
                if (!entity.enabled()) append(" · ").append(strings.rules.disabled)
            },
            color = if (entity.enabled()) Palette.TextFaint else Palette.Critical,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun rulePlainSummary(
    entity: RuleEntity,
    editedAttributes: Map<String, String>,
    language: String,
    editedName: String = entity.name().text(language),
): String = plainRuleSummary(
    entity.kind(),
    editedName,
    entity.attributes() + editedAttributes,
    language == "it",
)

internal fun plainRuleSummary(
    kind: RuleKind,
    name: String,
    attributes: Map<String, String>,
    italian: Boolean,
): String {
    val cleanName = name.ifBlank { if (italian) "Questa regola" else "This rule" }
    fun formulaParts(source: String?): Int = source?.takeIf(String::isNotBlank)?.let {
        runCatching { RuleFormula.compile(it).valueReferences().size + 1 }.getOrDefault(1)
    } ?: 1
    return when (kind) {
        RuleKind.STAT, RuleKind.SKILL, RuleKind.SAVE, RuleKind.DEFENSE -> {
            val parts = formulaParts(
                attributes["derivedFormula"] ?: attributes["formula"] ?: attributes["defaultFormula"],
            )
            if (italian) "$cleanName è un valore calcolato combinando $parts parti."
            else "$cleanName is calculated by combining $parts parts."
        }
        RuleKind.RESOURCE, RuleKind.TRACK -> if (italian) {
            "$cleanName conserva una quantità, con un massimo e una regola di recupero."
        } else "$cleanName stores an amount with a maximum and a recovery rule."
        RuleKind.ROLL, RuleKind.RANDOMIZER -> if (italian) {
            "$cleanName genera un risultato casuale e può confrontarlo con una difficoltà."
        } else "$cleanName produces a random result and can compare it with a target."
        RuleKind.MODIFIER -> {
            val operation = when (attributes["operation"].orEmpty().uppercase()) {
                "ADD" -> if (attributes["valueFormula"]?.trim()?.startsWith("-") == true) {
                    if (italian) "sottrae" else "subtracts"
                } else if (italian) "aggiunge" else "adds"
                "MULTIPLY" -> if (italian) "moltiplica" else "multiplies"
                "SET" -> if (italian) "sostituisce" else "replaces"
                else -> if (italian) "modifica" else "changes"
            }
            if (italian) "$cleanName $operation un valore quando sono soddisfatte le sue condizioni."
            else "$cleanName $operation a value when its conditions are met."
        }
        RuleKind.CONDITION -> if (italian) {
            "$cleanName è uno stato applicabile che può attivare effetti collegati."
        } else "$cleanName is an applicable state that can activate linked effects."
        RuleKind.ACTION -> if (italian) {
            "$cleanName è un’azione con requisiti, costi ed effetti."
        } else "$cleanName is an action with requirements, costs, and effects."
        RuleKind.TRIGGER -> if (italian) {
            "$cleanName reagisce a un evento e applica gli effetti collegati."
        } else "$cleanName reacts to an event and applies its linked effects."
        RuleKind.TABLE -> if (italian) "$cleanName associa risultati a valori o soglie."
            else "$cleanName maps results to values or thresholds."
        RuleKind.PROGRESSION -> if (italian) "$cleanName descrive livelli e avanzamenti."
            else "$cleanName describes levels and advancement."
        RuleKind.HEALTH_MODEL -> if (italian) "$cleanName definisce salute, protezioni e stato a zero."
            else "$cleanName defines health, buffers, and the zero state."
        RuleKind.MOVEMENT -> if (italian) "$cleanName stabilisce come si misurano movimento e distanze."
            else "$cleanName defines how movement and distances are measured."
        RuleKind.SHEET_SECTION -> if (italian) "$cleanName organizza i campi mostrati sulla scheda."
            else "$cleanName organizes the fields shown on the sheet."
        RuleKind.SCENE_PROCEDURE -> if (italian) "$cleanName organizza fasi e azioni di una scena."
            else "$cleanName organizes scene phases and actions."
        RuleKind.TEXT_RULE -> if (italian) "$cleanName viene interpretata e applicata dal tavolo."
            else "$cleanName is interpreted and applied at the table."
        else -> if (italian) "$cleanName aggiunge contenuto utilizzabile dal regolamento."
            else "$cleanName adds content that the ruleset can use."
    }
}

private enum class RuleDetailPurpose { BUILDER, PACKAGES }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDetail(
    viewModel: RulesViewModel,
    activeBattle: BattleViewModel?,
    onNotice: (String) -> Unit,
    purpose: RuleDetailPurpose,
    navigationGate: RuleEditorNavigationGate,
    modifier: Modifier = Modifier,
) {
    val choice = viewModel.selected
    val entity = viewModel.selectedEntity
    val words = strings.rules
    val italian = strings.language.tag == "it"
    Column(modifier.background(Palette.Surface.copy(alpha = .92f))) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (choice == null) {
                Text(words.noResults, color = Palette.TextMuted)
                return@Column
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(choice.name, color = Palette.Text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (choice.readOnly) words.readOnly else if (choice.isDraft) words.editableDraft else words.published,
                        color = if (choice.readOnly) Palette.TextMuted else Palette.Heal,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (!choice.isDraft) {
                    GameButton(
                        if (choice.readOnly) words.fork else words.newRevisionDraft,
                        accent = Palette.Gold,
                        onClick = { viewModel.forkSelected() },
                    )
                }
            }

            if (purpose == RuleDetailPurpose.BUILDER) {
                if (!choice.isDraft) {
                    Text(
                        if (italian) {
                            "Le versioni pubblicate restano intatte. Crea una variante per modificarne le regole."
                        } else {
                            "Published versions stay unchanged. Create a variant to edit their rules."
                        },
                        color = Palette.TextMuted,
                    )
                } else if (entity == null) {
                    Text(
                        if (italian) "Scegli una regola dall’elenco oppure creane una nuova." else
                            "Choose a rule from the list or create a new one.",
                        color = Palette.TextMuted,
                    )
                } else {
                    key(choice.key, entity, strings.language) {
                        EntityDetail(
                            viewModel = viewModel,
                            entity = entity,
                            editable = true,
                            activeBattle = activeBattle,
                            activeRulesetHash = choice.revision.canonicalHash(),
                            onNotice = onNotice,
                            navigationGate = navigationGate,
                        )
                    }
                }
            } else if (!choice.isDraft) {
                Text(
                    if (choice.readOnly) words.forkHint else words.newRevisionHint,
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                viewModel.selectedCompositionLock?.let { lock ->
                    Text(
                        if (italian) {
                            "Composizione riproducibile: ${lock.modules().size} moduli · ${lock.canonicalHash().take(12)}"
                        } else {
                            "Reproducible composition: ${lock.modules().size} modules · ${lock.canonicalHash().take(12)}"
                        },
                        color = Palette.Heal,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (italian) {
                            if (viewModel.selectedCompositionBaseAvailable) "Base esatta installata."
                            else "Snapshot giocabile; base assente per futuri confronti."
                        } else if (viewModel.selectedCompositionBaseAvailable) {
                            "Exact base installed."
                        } else {
                            "Playable snapshot; base missing for future comparisons."
                        },
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (viewModel.moduleComposition == null) {
                    GameButton(
                        if (italian) "Componi con moduli" else "Compose with modules",
                        accent = Palette.Party,
                        subtitle = if (italian) "${viewModel.installedModules.size} moduli installati" else
                            "${viewModel.installedModules.size} installed modules",
                        onClick = viewModel::beginModuleComposition,
                    )
                } else {
                    ModuleCompositionEditor(viewModel)
                }
            } else {
                var rulesetName by remember(choice.key) { mutableStateOf(choice.name) }
                var rulesetDescription by remember(choice.key) { mutableStateOf(choice.revision.description()) }
                var version by remember(choice.key) { mutableStateOf("1.0.0") }
                Eyebrow(words.rulesetDetails)
                Text(strings.common.nameLabel, color = Palette.TextMuted,
                    style = MaterialTheme.typography.labelMedium)
                RuleTextField(rulesetName, { rulesetName = it }, strings.common.nameLabel, Modifier.fillMaxWidth())
                Text(words.descriptionPlaceholder, color = Palette.TextMuted,
                    style = MaterialTheme.typography.labelMedium)
                RuleTextField(rulesetDescription, { rulesetDescription = it }, words.descriptionPlaceholder,
                    Modifier.fillMaxWidth(), multiline = true)
                GameButton(
                    words.saveRulesetDetails,
                    enabled = rulesetName.isNotBlank(),
                    onClick = { viewModel.updateDraftMetadata(rulesetName, rulesetDescription) },
                )
                viewModel.draftChangeSummary?.let { summary ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Chip(words.modifiedCount(summary.modified), Palette.GoldBright)
                        Chip(words.addedCount(summary.added), Palette.Heal)
                        if (summary.disabled > 0) Chip(words.disabledCount(summary.disabled), Palette.Critical)
                    }
                }
                if (viewModel.hasLegacyRuntimeControls) RuntimeEditor(viewModel)
                HorizontalDivider(color = Palette.Line)
                Eyebrow(if (italian) "Crea una versione giocabile" else "Create a playable version")
                Text(words.versionLabel, color = Palette.TextMuted,
                    style = MaterialTheme.typography.labelMedium)
                RuleTextField(version, { version = it }, words.versionLabel, Modifier.width(140.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    GameButton(
                        if (italian) "Controlla prima" else "Validate first",
                        accent = Palette.Party,
                        onClick = { viewModel.validateSelectedDraft() },
                    )
                    GameButton(
                        if (italian) "Crea versione giocabile" else "Create playable version",
                        accent = Palette.Gold,
                        enabled = version.isNotBlank(),
                        onClick = { viewModel.publishSelected(version) },
                    )
                }
            }
        }
        if (purpose == RuleDetailPurpose.BUILDER && choice?.isDraft == true && entity != null) {
            RuleEditorActionBar(viewModel, navigationGate)
        }
    }
}

@Composable
private fun RuleEditorActionBar(viewModel: RulesViewModel, navigationGate: RuleEditorNavigationGate) {
    val italian = strings.language.tag == "it"
    Column(Modifier.fillMaxWidth().background(Palette.Abyss).border(1.dp, Palette.Line).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (navigationGate.dirty) {
                if (italian) "Modifiche non ancora salvate" else "Unsaved changes"
            } else if (italian) "Tutto salvato" else "All changes saved",
            color = if (navigationGate.dirty) Palette.GoldBright else Palette.Heal,
            style = MaterialTheme.typography.labelMedium,
        )
        if (navigationGate.showValidationErrors) {
            navigationGate.validationMessage?.let {
                Text(it, color = Palette.Critical, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GameButton(
                if (italian) "Annulla modifiche" else "Discard changes",
                enabled = navigationGate.dirty,
                accent = Palette.TextMuted,
                onClick = navigationGate::reset,
            )
            GameButton(
                if (italian) "Prova" else "Test",
                accent = Palette.Party,
                onClick = { if (navigationGate.save()) viewModel.validateSelectedDraft() },
            )
            GameButton(
                if (italian) "Salva" else "Save",
                enabled = navigationGate.dirty,
                primary = true,
                onClick = { navigationGate.save() },
            )
        }
    }
}

private data class RuleCreationRecipe(
    val kind: RuleKind,
    val label: String,
    val description: String,
)

private data class EntityEditorSnapshot(
    val name: String,
    val description: String,
    val kind: RuleKind,
    val automation: RuleAutomationLevel,
    val enabled: Boolean,
    val attributes: Map<String, String>,
    val tags: List<String>,
    val authoringMode: AuthoringMode,
)

internal enum class RuleIntentFamily { VALUES, CHECKS, EFFECTS, ACTIONS, CONTENT }

private data class RuleIntentChoice(
    val family: RuleIntentFamily,
    val label: String,
    val description: String,
)

private fun intentChoices(italian: Boolean) = listOf(
    RuleIntentChoice(
        RuleIntentFamily.VALUES,
        if (italian) "Numeri e risorse" else "Numbers and resources",
        if (italian) "Statistiche, valori, salute e progressione" else "Stats, values, health, and progression",
    ),
    RuleIntentChoice(
        RuleIntentFamily.CHECKS,
        if (italian) "Tiri e risultati" else "Rolls and outcomes",
        if (italian) "Dadi, prove, difficoltà e tabelle" else "Dice, checks, targets, and tables",
    ),
    RuleIntentChoice(
        RuleIntentFamily.EFFECTS,
        if (italian) "Bonus, penalità e stati" else "Bonuses, penalties, and states",
        if (italian) "Cambia un valore quando si verifica qualcosa" else "Change a value when something happens",
    ),
    RuleIntentChoice(
        RuleIntentFamily.ACTIONS,
        if (italian) "Azioni ed eventi" else "Actions and events",
        if (italian) "Costi, reazioni, scene e movimento" else "Costs, reactions, scenes, and movement",
    ),
    RuleIntentChoice(
        RuleIntentFamily.CONTENT,
        if (italian) "Scheda e regole manuali" else "Sheet and manual rules",
        if (italian) "Organizza campi o scrivi una regola da decidere al tavolo" else
            "Organize fields or write a table-decided rule",
    ),
)

internal fun intentFamilyFor(kind: RuleKind): RuleIntentFamily = when (kind) {
    RuleKind.STAT, RuleKind.SKILL, RuleKind.SAVE, RuleKind.DEFENSE, RuleKind.VALUE,
    RuleKind.RESOURCE, RuleKind.TRACK, RuleKind.PROGRESSION, RuleKind.HEALTH_MODEL -> RuleIntentFamily.VALUES
    RuleKind.RANDOMIZER, RuleKind.ROLL, RuleKind.TABLE -> RuleIntentFamily.CHECKS
    RuleKind.MODIFIER, RuleKind.CONDITION, RuleKind.DAMAGE_TYPE -> RuleIntentFamily.EFFECTS
    RuleKind.ACTION_ECONOMY, RuleKind.ACTION, RuleKind.TRIGGER, RuleKind.MOVEMENT,
    RuleKind.SCENE_PROCEDURE -> RuleIntentFamily.ACTIONS
    else -> RuleIntentFamily.CONTENT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuilderCreationPanel(
    viewModel: RulesViewModel,
    compact: Boolean,
    onCreate: (GuidedRuleCreationRequest) -> Unit,
) {
    val italian = strings.language.tag == "it"
    var selectedFamily by remember(viewModel.selectedKey) { mutableStateOf<RuleIntentFamily?>(null) }
    var selectedRecipe by remember(viewModel.selectedKey) { mutableStateOf<RuleCreationRecipe?>(null) }
    var ruleName by remember(viewModel.selectedKey) { mutableStateOf("") }
    var ruleDescription by remember(viewModel.selectedKey) { mutableStateOf("") }
    var startingValue by remember(viewModel.selectedKey) { mutableStateOf("0") }
    var maximumValue by remember(viewModel.selectedKey) { mutableStateOf("10") }
    var diceSides by remember(viewModel.selectedKey) { mutableStateOf("20") }
    var rollBonus by remember(viewModel.selectedKey) { mutableStateOf("0") }
    var targetValue by remember(viewModel.selectedKey) { mutableStateOf("10") }
    var modifierTarget by remember(viewModel.selectedKey) { mutableStateOf("") }
    var modifierAmount by remember(viewModel.selectedKey) { mutableStateOf("1") }
    var modifierAdds by remember(viewModel.selectedKey) { mutableStateOf(true) }
    var showCreator by remember(viewModel.selectedKey) { mutableStateOf(viewModel.selectedEntity == null) }
    var showTechnical by remember(viewModel.selectedKey) { mutableStateOf(false) }
    val panelScroll = rememberScrollState()
    Column(
        Modifier.fillMaxWidth()
            .then(if (compact) Modifier else Modifier.heightIn(max = 480.dp).verticalScroll(panelScroll))
            .background(Palette.Surface.copy(alpha = .94f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Crea una regola" else "Create a rule")
        if (showCreator && viewModel.selectedEntity != null) {
            GameButton(
                if (italian) "Chiudi creazione" else "Close creation",
                dense = true,
                accent = Palette.TextMuted,
                onClick = {
                    showCreator = false
                    selectedRecipe = null
                    selectedFamily = null
                },
            )
        }
        if (viewModel.selected?.isDraft != true) {
            Text(
                if (viewModel.selected == null) {
                    if (italian) "Crea un regolamento vuoto per iniziare." else
                        "Create an empty ruleset to get started."
                } else if (italian) {
                    "Prima crea una variante modificabile del regolamento selezionato."
                } else {
                    "First create an editable variant of the selected ruleset."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            GameButton(
                if (viewModel.selected == null) {
                    if (italian) "Crea un regolamento vuoto" else "Create an empty ruleset"
                } else if (italian) "Crea una variante" else "Create a variant",
                accent = Palette.Heal,
                onClick = {
                    if (viewModel.selected == null) viewModel.createBlankRuleset()
                    else viewModel.forkSelected()
                },
            )
            return@Column
        }
        if (!showCreator) {
            GameButton(
                if (italian) "Crea un’altra regola…" else "Create another rule…",
                modifier = Modifier.fillMaxWidth(),
                accent = Palette.Heal,
                onClick = { showCreator = true },
            )
            return@Column
        }
        if (selectedRecipe != null) {
            val recipe = requireNotNull(selectedRecipe)
            GameButton(
                if (italian) "‹ Cambia tipo di regola" else "‹ Change rule type",
                accent = Palette.TextMuted,
                onClick = { selectedRecipe = null },
            )
            Text(recipe.label, color = Palette.Text, style = MaterialTheme.typography.titleMedium)
            Text(recipe.description, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            Eyebrow(if (italian) "1 · Come la riconosci" else "1 · Identify it")
            WizardRuleField(
                if (italian) "Nome della regola" else "Rule name",
                ruleName,
                { ruleName = it },
                errorMessage = if (ruleName.isBlank()) {
                    if (italian) "Inserisci un nome." else "Enter a name."
                } else null,
            )
            WizardRuleField(
                if (italian) "Cosa fa, in una frase" else "What it does, in one sentence",
                ruleDescription,
                { ruleDescription = it },
                multiline = true,
                errorMessage = if (ruleDescription.isBlank()) {
                    if (italian) "Inserisci una breve descrizione." else "Enter a short description."
                } else null,
            )
            Eyebrow(if (italian) "2 · Valori iniziali" else "2 · Starting values")
            when (recipe.kind) {
                RuleKind.STAT -> WizardRuleField(
                    if (italian) "Da quale numero parte?" else "What number does it start from?",
                    startingValue,
                    { startingValue = it },
                    errorMessage = if (startingValue.toBigDecimalOrNull() == null) {
                        if (italian) "Scrivi un numero valido." else "Enter a valid number."
                    } else null,
                )
                RuleKind.RESOURCE -> WizardRuleField(
                    if (italian) "Qual è la capienza massima?" else "What is its maximum amount?",
                    maximumValue,
                    { maximumValue = it },
                    errorMessage = if (maximumValue.toBigDecimalOrNull() == null) {
                        if (italian) "Scrivi un numero valido." else "Enter a valid number."
                    } else null,
                )
                RuleKind.ROLL -> {
                    WizardRuleField(
                        if (italian) "Quante facce ha il dado?" else "How many sides does the die have?",
                        diceSides,
                        { diceSides = it },
                        errorMessage = if (diceSides.toIntOrNull()?.let { it >= 2 } != true) {
                            if (italian) "Usa un numero intero di almeno 2." else "Use a whole number of at least 2."
                        } else null,
                    )
                    WizardRuleField(
                        if (italian) "Quale bonus fisso aggiunge?" else "What fixed bonus does it add?",
                        rollBonus,
                        { rollBonus = it },
                        errorMessage = if (rollBonus.toBigDecimalOrNull() == null) {
                            if (italian) "Scrivi un numero valido." else "Enter a valid number."
                        } else null,
                    )
                    WizardRuleField(
                        if (italian) "Qual è la difficoltà iniziale?" else "What is the starting target?",
                        targetValue,
                        { targetValue = it },
                        errorMessage = if (targetValue.toBigDecimalOrNull() == null) {
                            if (italian) "Scrivi un numero valido." else "Enter a valid number."
                        } else null,
                    )
                }
                RuleKind.MODIFIER -> {
                    val targets = numericRuleCandidates(viewModel, "")
                    Text(if (italian) "Quale valore cambia?" else "Which value does it change?",
                        color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
                    ReferenceButtons(targets, setOf(modifierTarget)) {
                        modifierTarget = it
                    }
                    if (targets.isEmpty()) {
                        Text(
                            if (italian) "Crea prima una statistica o un valore numerico da modificare." else
                                "Create a stat or numeric value to modify first.",
                            color = Palette.Bloodied,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameButton(if (italian) "Aggiunge" else "Adds", selected = modifierAdds,
                            onClick = { modifierAdds = true })
                        GameButton(if (italian) "Sottrae" else "Subtracts", selected = !modifierAdds,
                            onClick = { modifierAdds = false })
                    }
                    WizardRuleField(
                        if (italian) "Di quanto?" else "By how much?",
                        modifierAmount,
                        { modifierAmount = it },
                        errorMessage = if (modifierAmount.toBigDecimalOrNull() == null) {
                            if (italian) "Scrivi un numero valido." else "Enter a valid number."
                        } else null,
                    )
                }
                RuleKind.CONDITION -> Text(
                    if (italian) "Dopo la creazione potrai scegliere effetti e accumuli." else
                        "After creation you can choose effects and stacking.",
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> Text(
                    if (italian) "Le altre scelte pertinenti appariranno nel costruttore." else
                        "Other relevant choices will appear in the builder.",
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val quickValuesValid = when (recipe.kind) {
                RuleKind.STAT -> startingValue.toBigDecimalOrNull() != null
                RuleKind.RESOURCE -> maximumValue.toBigDecimalOrNull() != null
                RuleKind.ROLL -> diceSides.toIntOrNull()?.let { it >= 2 } == true &&
                    rollBonus.toBigDecimalOrNull() != null && targetValue.toBigDecimalOrNull() != null
                RuleKind.MODIFIER -> modifierTarget.isNotBlank() && modifierAmount.toBigDecimalOrNull() != null
                else -> true
            }
            GameButton(
                if (italian) "Crea e continua" else "Create and continue",
                modifier = Modifier.fillMaxWidth(),
                primary = true,
                enabled = ruleName.isNotBlank() && ruleDescription.isNotBlank() && quickValuesValid,
                subtitle = if (italian) "Potrai provare e rifinire la regola subito dopo" else
                    "You can test and refine the rule immediately afterward",
                onClick = {
                    val attributes = when (recipe.kind) {
                        RuleKind.STAT -> mapOf("defaultFormula" to startingValue)
                        RuleKind.RESOURCE -> mapOf(
                            "maximumFormula" to maximumValue,
                            "initialFormula" to "\${maximum}",
                        )
                        RuleKind.ROLL -> mapOf(
                            "mode" to "DICE",
                            "countFormula" to "1",
                            "sidesFormula" to diceSides,
                            "totalFormula" to if (rollBonus.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 0) {
                                "\${roll}"
                            } else "\${roll} + $rollBonus",
                            "targetFormula" to targetValue,
                        )
                        RuleKind.MODIFIER -> mapOf(
                            "targetRef" to modifierTarget,
                            "operation" to "ADD",
                            "valueFormula" to if (modifierAdds) modifierAmount else
                                requireNotNull(modifierAmount.toBigDecimalOrNull()).negate().toPlainString(),
                            "conditionFormula" to "1",
                        )
                        else -> emptyMap()
                    }
                    onCreate(GuidedRuleCreationRequest(recipe.kind, ruleName.trim(),
                        ruleDescription.trim(), attributes))
                    showCreator = false
                    selectedRecipe = null
                    selectedFamily = null
                    ruleName = ""
                    ruleDescription = ""
                },
            )
        } else if (selectedFamily == null) {
            Text(
                if (italian) "Che risultato vuoi ottenere?" else "What outcome do you want?",
                color = Palette.Text,
                style = OnfallTheme.typography.bodyEmphasis,
            )
            intentChoices(italian).forEach { choice ->
                GameButton(
                    choice.label,
                    subtitle = choice.description,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedFamily = choice.family },
                )
            }
        } else {
            GameButton(
                if (italian) "‹ Cambia categoria" else "‹ Change category",
                accent = Palette.TextMuted,
                onClick = { selectedFamily = null },
            )
            Text(
                if (italian) "Scegli ciò che vuoi creare" else "Choose what to create",
                color = Palette.Text,
                style = OnfallTheme.typography.bodyEmphasis,
            )
            creationRecipes(italian).filter { intentFamilyFor(it.kind) == selectedFamily }.forEach { recipe ->
                GameButton(
                    recipe.label,
                    subtitle = recipe.description,
                    modifier = Modifier.fillMaxWidth(),
                    accent = Palette.Heal,
                    onClick = {
                        selectedRecipe = recipe
                        ruleName = recipe.label
                        ruleDescription = recipe.description
                    },
                )
            }
        }
        if (selectedRecipe == null && selectedFamily == null) {
            GameButton(
                if (italian) {
                    if (showTechnical) "Nascondi tipi tecnici" else "Altri tipi per esperti"
                } else if (showTechnical) "Hide technical types" else "More expert types",
                dense = true,
                selected = showTechnical,
                accent = Palette.TextMuted,
                onClick = { showTechnical = !showTechnical },
            )
            if (showTechnical) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    exposedKinds.forEach { kind ->
                        GameButton(kindLabel(kind), dense = true, onClick = {
                            onCreate(GuidedRuleCreationRequest(
                                kind,
                                if (italian) "Nuova regola" else "New rule",
                                if (italian) "Descrivi questa regola." else "Describe this rule.",
                                emptyMap(),
                            ))
                            showCreator = false
                        })
                    }
                }
            }
        }
    }
}

private data class GuidedRuleCreationRequest(
    val kind: RuleKind,
    val name: String,
    val description: String,
    val attributes: Map<String, String>,
)

@Composable
private fun WizardRuleField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false,
    errorMessage: String? = null,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
        RuleTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = label,
            modifier = Modifier.fillMaxWidth(),
            multiline = multiline,
            errorMessage = errorMessage,
        )
        errorMessage?.let { message ->
            Text(message, color = Palette.Critical, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun creationRecipes(italian: Boolean): List<RuleCreationRecipe> = listOf(
    RuleCreationRecipe(
        RuleKind.STAT,
        if (italian) "Numero calcolato" else "Calculated number",
        if (italian) "Statistica, difesa o valore derivato" else "Stat, defense, or derived value",
    ),
    RuleCreationRecipe(
        RuleKind.RESOURCE,
        if (italian) "Risorsa consumabile" else "Consumable resource",
        if (italian) "Punti, cariche, salute o contatore" else "Points, charges, health, or tracker",
    ),
    RuleCreationRecipe(
        RuleKind.ROLL,
        if (italian) "Prova o tiro" else "Check or roll",
        if (italian) "Dadi, totale, difficoltà ed esito" else "Dice, total, target, and outcome",
    ),
    RuleCreationRecipe(
        RuleKind.MODIFIER,
        if (italian) "Bonus o penalità" else "Bonus or penalty",
        if (italian) "Modifica un numero o un valore" else "Change a number or value",
    ),
    RuleCreationRecipe(
        RuleKind.CONDITION,
        if (italian) "Condizione" else "Condition",
        if (italian) "Stato applicabile con effetti collegati" else "Applicable state with linked effects",
    ),
    RuleCreationRecipe(
        RuleKind.DAMAGE_TYPE,
        if (italian) "Tipo di danno" else "Damage type",
        if (italian) "Nuovo danno selezionabile dai contenuti" else "New damage selectable by content",
    ),
    RuleCreationRecipe(
        RuleKind.ACTION,
        if (italian) "Azione" else "Action",
        if (italian) "Costi, requisiti ed effetti" else "Costs, requirements, and effects",
    ),
    RuleCreationRecipe(
        RuleKind.TRIGGER,
        if (italian) "Reazione a un evento" else "Event reaction",
        if (italian) "Attiva effetti quando accade qualcosa" else "Run effects when something happens",
    ),
    RuleCreationRecipe(
        RuleKind.TABLE,
        if (italian) "Tabella" else "Table",
        if (italian) "Valori per livello o risultati casuali" else "Values by level or random outcomes",
    ),
    RuleCreationRecipe(
        RuleKind.PROGRESSION,
        if (italian) "Progressione" else "Progression",
        if (italian) "Livelli, esperienza e avanzamenti" else "Levels, experience, and advancements",
    ),
    RuleCreationRecipe(
        RuleKind.HEALTH_MODEL,
        if (italian) "Salute e stato a zero" else "Health and zero state",
        if (italian) "Combina salute, scudi e condizioni" else "Combine health, shields, and conditions",
    ),
    RuleCreationRecipe(
        RuleKind.MOVEMENT,
        if (italian) "Movimento e mappa" else "Movement and map",
        if (italian) "Griglia, distanze, diagonali ed elevazione" else "Grid, distances, diagonals, and elevation",
    ),
    RuleCreationRecipe(
        RuleKind.SHEET_SECTION,
        if (italian) "Sezione della scheda" else "Sheet section",
        if (italian) "Raggruppa i campi mostrati al giocatore" else "Group fields shown to the player",
    ),
    RuleCreationRecipe(
        RuleKind.SCENE_PROCEDURE,
        if (italian) "Procedura di scena" else "Scene procedure",
        if (italian) "Fasi, azioni e contatori di una scena" else "Phases, actions, and trackers for a scene",
    ),
    RuleCreationRecipe(
        RuleKind.TEXT_RULE,
        if (italian) "Regola decisa al tavolo" else "Table-decided rule",
        if (italian) "Testo libero senza automazione obbligatoria" else "Free text with no required automation",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthoringModeSelector(
    kind: RuleKind,
    selected: AuthoringMode,
    onSelect: (AuthoringMode) -> Unit,
) {
    val language = strings.language.tag
    val italian = language == "it"
    var expanded by remember(kind) { mutableStateOf(selected != AuthoringMode.GUIDED) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Eyebrow(if (italian) "Modifica guidata" else "Guided editing")
            Text(
                if (selected == AuthoringMode.GUIDED) {
                    if (italian) "Mostra soltanto le scelte utili per questa regola." else
                        "Shows only the choices useful for this rule."
                } else if (selected == AuthoringMode.VISUAL) {
                    if (italian) "Stai usando calcoli e opzioni a blocchi." else
                        "You are using block calculations and options."
                } else if (italian) "Stai modificando direttamente i dati del motore." else
                    "You are editing engine data directly.",
                color = if (selected == AuthoringMode.EXPERT) Palette.Bloodied else Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        GameButton(
            if (italian) {
                if (expanded) "Meno opzioni" else "Altre opzioni"
            } else if (expanded) "Fewer options" else "More options",
            dense = true,
            accent = Palette.TextMuted,
            onClick = { expanded = !expanded },
        )
    }
    if (expanded) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                AuthoringMode.GUIDED to if (italian) "Guidata" else "Guided",
                AuthoringMode.VISUAL to if (italian) "Blocchi avanzati" else "Advanced blocks",
                AuthoringMode.EXPERT to if (italian) "Dati del motore" else "Engine data",
            ).forEach { (candidate, label) ->
                GameButton(
                    label,
                    enabled = candidate == AuthoringMode.EXPERT || kind in guidedEditorKinds,
                    selected = selected == candidate,
                    subtitle = when (candidate) {
                        AuthoringMode.GUIDED -> if (italian) "Scelte comuni" else "Common choices"
                        AuthoringMode.VISUAL -> if (italian) "Formule visuali e limiti" else "Visual formulas and limits"
                        AuthoringMode.EXPERT -> if (italian) "ID e formule originali" else "Original IDs and formulas"
                    },
                    onClick = { onSelect(candidate); if (candidate == AuthoringMode.GUIDED) expanded = false },
                )
            }
        }
    }
}

@Composable
private fun EntityDetail(
    viewModel: RulesViewModel,
    entity: RuleEntity,
    editable: Boolean,
    activeBattle: BattleViewModel?,
    activeRulesetHash: String,
    onNotice: (String) -> Unit,
    navigationGate: RuleEditorNavigationGate,
) {
    val language = strings.language.tag
    val words = strings.rules
    var name by remember { mutableStateOf(entity.name().text(language)) }
    var description by remember { mutableStateOf(entity.description().text(language)) }
    var kind by remember { mutableStateOf(entity.kind()) }
    var automation by remember { mutableStateOf(entity.automationLevel()) }
    var enabled by remember { mutableStateOf(entity.enabled()) }
    var attributes by remember { mutableStateOf(entity.attributes().entries.map { it.key to it.value }) }
    var tagText by remember { mutableStateOf(entity.tags().joinToString(", ")) }
    val nameFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }
    var authoringMode by remember(entity.id(), kind) {
        mutableStateOf(
            if (kind in guidedEditorKinds) {
                viewModel.preferredAuthoringMode(entity.id()) ?: AuthoringMode.GUIDED
            } else {
                AuthoringMode.EXPERT
            },
        )
    }
    val initialSnapshot = remember(entity, language) {
        EntityEditorSnapshot(
            entity.name().text(language),
            entity.description().text(language),
            entity.kind(),
            entity.automationLevel(),
            entity.enabled(),
            entity.attributes(),
            entity.tags(),
            viewModel.preferredAuthoringMode(entity.id()) ?: if (entity.kind() in guidedEditorKinds) {
                AuthoringMode.GUIDED
            } else {
                AuthoringMode.EXPERT
            },
        )
    }
    fun setAttribute(attributeKey: String, attributeValue: String) {
        val index = attributes.indexOfFirst { it.first == attributeKey }
        attributes = if (index >= 0) {
            attributes.toMutableList().also { it[index] = attributeKey to attributeValue }
        } else {
            attributes + (attributeKey to attributeValue)
        }
    }
    fun removeAttribute(attributeKey: String) {
        attributes = attributes.filterNot { it.first == attributeKey }
    }
    fun saveCurrent(): Boolean = viewModel.updateEntity(
        entity.id(), name, description, kind,
        if (kind == RuleKind.TEXT_RULE) RuleAutomationLevel.MANUAL else automation,
        enabled,
        attributes.filter { it.first.isNotBlank() }.associate { it.first.trim() to it.second },
        tagText.split(',').map(String::trim).filter(String::isNotBlank),
        replaceAttributes = true,
        authoringMode = authoringMode,
    )
    fun currentSnapshot() = EntityEditorSnapshot(
        name,
        description,
        kind,
        if (kind == RuleKind.TEXT_RULE) RuleAutomationLevel.MANUAL else automation,
        enabled,
        attributes.filter { it.first.isNotBlank() }.associate { it.first.trim() to it.second },
        tagText.split(',').map(String::trim).filter(String::isNotBlank),
        authoringMode,
    )
    val dirty = currentSnapshot() != initialSnapshot
    val validationMessage = when {
        name.isBlank() -> if (language == "it") "Scrivi un nome per la regola." else "Enter a rule name."
        description.isBlank() -> if (language == "it") "Descrivi cosa fa la regola." else "Describe what the rule does."
        else -> null
    }
    fun resetCurrent() {
        name = initialSnapshot.name
        description = initialSnapshot.description
        kind = initialSnapshot.kind
        automation = initialSnapshot.automation
        enabled = initialSnapshot.enabled
        attributes = initialSnapshot.attributes.entries.map { it.key to it.value }
        tagText = initialSnapshot.tags.joinToString(", ")
        authoringMode = initialSnapshot.authoringMode
    }
    SideEffect {
        navigationGate.bind(
            entity.id(),
            dirty,
            validationMessage == null,
            validationMessage,
            ::saveCurrent,
            ::resetCurrent,
        )
    }
    DisposableEffect(entity.id()) {
        onDispose { navigationGate.clear(entity.id()) }
    }
    LaunchedEffect(navigationGate.showValidationErrors, validationMessage) {
        if (!navigationGate.showValidationErrors) return@LaunchedEffect
        if (name.isBlank()) nameFocusRequester.requestFocus()
        else if (description.isBlank()) descriptionFocusRequester.requestFocus()
    }
    Eyebrow(words.rule)
    if (editable) {
        Text(strings.common.nameLabel, color = Palette.TextMuted,
            style = MaterialTheme.typography.labelMedium)
        RuleTextField(
            name,
            { name = it },
            strings.common.nameLabel,
            Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
            errorMessage = if (navigationGate.showValidationErrors && name.isBlank()) validationMessage else null,
        )
        Text(if (language == "it") "Descrizione" else "Description", color = Palette.TextMuted,
            style = MaterialTheme.typography.labelMedium)
        RuleTextField(
            description,
            { description = it },
            words.rule,
            Modifier.fillMaxWidth().focusRequester(descriptionFocusRequester),
            multiline = true,
            errorMessage = if (navigationGate.showValidationErrors && description.isBlank()) validationMessage else null,
        )
        if (navigationGate.showValidationErrors && validationMessage != null) {
            Text(validationMessage, color = Palette.Critical, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            rulePlainSummary(entity, attributes.toMap(), language, name),
            color = Palette.Text,
            style = OnfallTheme.typography.bodyEmphasis,
            modifier = Modifier.fillMaxWidth().background(Palette.Gold.copy(alpha = .08f), RoundedCornerShape(7.dp))
                .border(1.dp, Palette.Gold.copy(alpha = .35f), RoundedCornerShape(7.dp)).padding(9.dp),
        )
        val generatedParts = viewModel.authoringGroupMembers(entity.id()).filter { it.id() != entity.id() }
        if (generatedParts.isNotEmpty()) {
            Text(
                if (language == "it") {
                    "${generatedParts.size} componenti tecnici sono gestiti automaticamente da questa regola."
                } else {
                    "${generatedParts.size} technical components are managed automatically by this rule."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        AuthoringModeSelector(kind, authoringMode) { authoringMode = it }
        val protectedFields = viewModel.protectedAuthoringFields(entity.id())
        if (authoringMode != AuthoringMode.EXPERT && protectedFields.isNotEmpty()) {
            Text(
                if (language == "it") {
                    "${protectedFields.size} dettagli avanzati sono conservati senza modifiche."
                } else {
                    "${protectedFields.size} advanced details are preserved unchanged."
                },
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (authoringMode == AuthoringMode.GUIDED) {
            Text(
                "${kindLabel(kind)} · ${automationLabel(if (kind == RuleKind.TEXT_RULE) RuleAutomationLevel.MANUAL else automation)}",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            if (kind == RuleKind.TEXT_RULE) {
                Text(
                    if (language == "it") "Questa regola resta intenzionalmente affidata al tavolo." else
                        "This rule is intentionally left to the table.",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    GameButton(
                        if (language == "it") "Gestita dall’app" else "App-assisted",
                        dense = true,
                        selected = automation != RuleAutomationLevel.MANUAL,
                        onClick = { automation = RuleAutomationLevel.ASSISTED },
                    )
                    GameButton(
                        if (language == "it") "Decisa al tavolo" else "Table-decided",
                        dense = true,
                        selected = automation == RuleAutomationLevel.MANUAL,
                        onClick = { automation = RuleAutomationLevel.MANUAL },
                    )
                }
            }
        } else {
            Eyebrow(words.ruleType)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                exposedKinds.forEach { candidate ->
                    GameButton(kindLabel(candidate), dense = true, selected = kind == candidate,
                        onClick = { kind = candidate })
                }
            }
            Eyebrow(words.automation)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RuleAutomationLevel.entries.forEach { candidate ->
                    GameButton(automationLabel(candidate), dense = true, selected = automation == candidate,
                        onClick = { automation = candidate })
                }
            }
        }
        if (authoringMode != AuthoringMode.GUIDED) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                Text("${words.enabled}: ${if (enabled) strings.common.yes else strings.common.no}",
                    color = Palette.Text, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (kind != RuleKind.MODIFIER &&
            (authoringMode != AuthoringMode.GUIDED || kind == RuleKind.CONDITION)
        ) {
            val activeByDefault = attributes.toMap()["activeByDefault"]
                ?.toBooleanStrictOrNull() == true
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = activeByDefault,
                    onCheckedChange = { setAttribute("activeByDefault", it.toString()) },
                )
                Text(
                    if (language == "it") {
                        "Effetti collegati attivi all'apertura della sessione"
                    } else {
                        "Linked effects active when the session opens"
                    },
                    color = Palette.Text,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (authoringMode != AuthoringMode.GUIDED && (
            kind in modifierOwnerKinds ||
                (kind == RuleKind.CUSTOM && attributes.toMap().containsKey("elementKind"))
            )
        ) {
            GameButton(
                if (language == "it") "Aggiungi modificatore collegato" else "Add linked modifier",
                dense = true,
                accent = Palette.Heal,
                subtitle = if (language == "it") {
                    "Crea una regola numerica già collegata a questa voce"
                } else {
                    "Create a numeric rule already linked to this entry"
                },
                onClick = {
                    if (saveCurrent()) viewModel.addLinkedModifier(entity.id())
                },
            )
        }
        if (kind != RuleKind.MODIFIER &&
            (authoringMode != AuthoringMode.GUIDED || kind == RuleKind.CONDITION)
        ) {
            GameButton(
                if (language == "it") "Aggiungi effetto universale" else "Add universal effect",
                dense = true,
                accent = Palette.Party,
                subtitle = if (language == "it") {
                    "Collega formule, valori, risorse o condizioni a questa regola"
                } else {
                    "Link formulas, values, resources, or conditions to this rule"
                },
                onClick = {
                    if (saveCurrent()) viewModel.addGenericLinkedModifier(entity.id())
                },
            )
        }
        CompositionLocalProvider(LocalGuidedFormulaEditing provides (authoringMode == AuthoringMode.GUIDED)) {
        if (authoringMode != AuthoringMode.EXPERT) {
            if (kind in setOf(RuleKind.STAT, RuleKind.SAVE, RuleKind.DEFENSE)) {
                StatFormulaEditor(
                    viewModel = viewModel,
                    entityId = entity.id(),
                    attributes = attributes.toMap(),
                    showAdvanced = authoringMode == AuthoringMode.VISUAL,
                    onAttribute = ::setAttribute,
                )
            }
            if (kind == RuleKind.SKILL) {
                SkillFormulaEditor(
                    viewModel = viewModel,
                    entityId = entity.id(),
                    attributes = attributes.toMap(),
                    showAdvanced = authoringMode == AuthoringMode.VISUAL,
                    onAttribute = ::setAttribute,
                )
            }
            if (kind == RuleKind.RESOURCE || kind == RuleKind.TRACK) {
                ResourceFormulaEditor(
                    viewModel,
                    entity.id(),
                    attributes.toMap(),
                    authoringMode == AuthoringMode.VISUAL,
                    ::setAttribute,
                )
            }
            if (kind == RuleKind.RANDOMIZER) {
                RandomizerSchemaEditor(
                    viewModel,
                    entity.id(),
                    attributes.toMap(),
                    authoringMode == AuthoringMode.VISUAL,
                    ::setAttribute,
                )
            }
            if (kind == RuleKind.ROLL) {
                val rollAttributes = attributes.toMap()
                if (rollAttributes.keys.any { it in rollResolutionAttributes }) {
                    RollSchemaEditor(
                        viewModel,
                        entity.id(),
                        rollAttributes,
                        authoringMode == AuthoringMode.VISUAL,
                        ::setAttribute,
                    )
                } else {
                    RandomizerSchemaEditor(
                        viewModel,
                        entity.id(),
                        rollAttributes,
                        authoringMode == AuthoringMode.VISUAL,
                        ::setAttribute,
                    )
                }
            }
            if (kind == RuleKind.ACTION_ECONOMY) {
                ActionEconomySchemaEditor(
                    viewModel,
                    entity.id(),
                    attributes.toMap(),
                    authoringMode == AuthoringMode.VISUAL,
                    ::setAttribute,
                )
            }
            if (kind == RuleKind.ACTION) {
                ActionSchemaEditor(
                    viewModel,
                    entity.id(),
                    attributes.toMap(),
                    authoringMode == AuthoringMode.VISUAL,
                    ::setAttribute,
                )
            }
            if (kind == RuleKind.TRIGGER) {
                TriggerSchemaEditor(
                    viewModel,
                    entity.id(),
                    attributes.toMap(),
                    authoringMode == AuthoringMode.VISUAL,
                    ::setAttribute,
                )
            }
            if (kind == RuleKind.TABLE) {
                TableSchemaEditor(attributes.toMap(), ::setAttribute)
            }
            if (kind == RuleKind.MODIFIER) {
                if (attributes.toMap().containsKey("targetRef") || attributes.toMap().containsKey("valueFormula")) {
                    GenericModifierSchemaEditor(
                        viewModel,
                        entity.id(),
                        attributes.toMap(),
                        authoringMode == AuthoringMode.VISUAL,
                        ::setAttribute,
                    )
                } else {
                    ModifierSchemaEditor(viewModel, entity.id(), attributes.toMap(), ::setAttribute)
                }
            }
            if (kind == RuleKind.VALUE) {
                ValueSchemaEditor(attributes.toMap(), ::setAttribute)
            }
            if (kind in modularRuntimeKinds) {
                ModularRuntimeSchemaEditor(
                    viewModel,
                    entity.id(),
                    kind,
                    attributes.toMap(),
                    authoringMode == AuthoringMode.VISUAL,
                    ::setAttribute,
                )
            }
            if (RulesetCompiler.supportsStatePolicy(kind) && authoringMode == AuthoringMode.VISUAL) {
                StatePolicySchemaEditor(attributes.toMap(), ::setAttribute)
            }
            if (kind == RuleKind.PROGRESSION) {
                ProgressionSchemaEditor(
                    viewModel,
                    entity.id(),
                    attributes.toMap(),
                    authoringMode == AuthoringMode.VISUAL,
                    ::setAttribute,
                    ::removeAttribute,
                )
            }
            if ((authoringMode == AuthoringMode.VISUAL && kind !in guidedEditorKinds) ||
                (kind == RuleKind.VALUE && attributes.toMap()["valueType"] == "REFERENCE")
            ) {
                RuleReferenceEditor(viewModel, entity.id(), kind, attributes.toMap(), ::setAttribute)
            }
        } else {
            Text(
                if (language == "it") {
                    "Modalità esperto: gli ID e le formule cambiano il comportamento del motore."
                } else {
                    "Expert mode: IDs and formulas directly change engine behavior."
                },
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
            Eyebrow(words.attributes)
            attributes.forEachIndexed { index, (attributeKey, attributeValue) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RuleTextField(attributeKey, { changed ->
                        attributes = attributes.toMutableList().also { it[index] = changed to attributeValue }
                    }, words.attributeKey, Modifier.weight(.8f))
                    RuleTextField(attributeValue, { changed ->
                        attributes = attributes.toMutableList().also { it[index] = attributeKey to changed }
                    }, words.attributeValue, Modifier.weight(1f))
                    GameButton(if (language == "it") "Rimuovi" else "Remove", dense = true,
                        accent = Palette.Enemy,
                        onClick = { attributes = attributes.filterIndexed { item, _ -> item != index } })
                }
            }
            GameButton(strings.common.add, dense = true, onClick = {
                val used = attributes.map { it.first }.toSet()
                var index = attributes.size + 1
                var candidate = "parameter$index"
                while (candidate in used) candidate = "parameter${++index}"
                attributes = attributes + (candidate to "")
            })
            if (attributes.any { (key, _) -> key.endsWith("Formula") || key == "budgets" || key == "costs" }) {
                FormulaHelp()
            }
        }
        }
        if (authoringMode != AuthoringMode.GUIDED) {
            RuleTextField(tagText, { tagText = it }, words.tagsPlaceholder, Modifier.fillMaxWidth())
        }
        when (viewModel.selectedEntityChange) {
            DraftEntityChange.INHERITED -> Unit
            DraftEntityChange.MODIFIED -> GameButton(
                words.restoreFromBase,
                dense = true,
                accent = Palette.TextMuted,
                onClick = viewModel::resetSelectedEntityChange,
            )
            DraftEntityChange.ADDED -> GameButton(
                words.removeAddedRule,
                dense = true,
                accent = Palette.Enemy,
                onClick = viewModel::resetSelectedEntityChange,
            )
        }
    } else {
        Text(name, color = Palette.Text, style = MaterialTheme.typography.titleLarge)
        Text(description, color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
        LiveRuleControls(entity, activeBattle, activeRulesetHash, onNotice)
        GameButton(
            words.editInDraft,
            dense = true,
            accent = Palette.Gold,
            onClick = { viewModel.forkSelected(entity.id()) },
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Chip(kindLabel(kind), color = Palette.GoldBright)
        Chip(automationLabel(automation), color = automationColor(automation))
        if (!enabled) Chip(words.disabled, color = Palette.Critical)
        (if (editable) tagText.split(',').map(String::trim).filter(String::isNotBlank) else entity.tags())
            .take(6).forEach { Chip(it) }
    }
    if (entity.source().isNotBlank()) {
        Text("${words.source}: ${entity.source()}${if (entity.sourcePage() > 0) " · p. ${entity.sourcePage()}" else ""}",
            color = Palette.TextFaint, style = MaterialTheme.typography.labelSmall)
    }
    if (!editable && entity.attributes().isNotEmpty()) {
        Eyebrow(words.attributes)
        entity.attributes().forEach { (key, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(key, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                Text(value, color = Palette.Text, style = OnfallTheme.typography.supportingEmphasis,
                    modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** Controlli prodotti dai dati della revisione, senza conoscere D&D o un'edizione specifica. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveRuleControls(
    entity: RuleEntity,
    activeBattle: BattleViewModel?,
    rulesetHash: String,
    onNotice: (String) -> Unit,
) {
    if (activeBattle == null ||
        activeBattle.state.rulesetBinding().canonicalHash() != rulesetHash ||
        activeBattle.state.ruleSession().entities().none { it.id() == entity.id() }
    ) return

    val italian = strings.language.tag == "it"
    var selectedScope by remember(activeBattle.sessionGeneration) {
        mutableStateOf(RuleScope.session())
    }
    var customScopeKind by remember(activeBattle.sessionGeneration) {
        mutableStateOf(RuleScope.Kind.OBJECT)
    }
    var customScopeId by remember(activeBattle.sessionGeneration) { mutableStateOf("") }
    val runtime = activeBattle.genericRuleState(selectedScope) ?: return
    val resolved = activeBattle.state.status() == CombatStatus.RESOLVED
    val manual = entity.automationLevel() == RuleAutomationLevel.MANUAL
    val successNotice: (Boolean, String) -> Unit = { succeeded, text ->
        if (succeeded) onNotice(text) else activeBattle.message?.let(onNotice)
    }

    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .66f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(if (italian) "Nella sessione" else "In this session", Palette.Heal)
        Text(
            if (italian) {
                "Istanza: ${selectedScope.kind().name.lowercase()} · ${selectedScope.id()}"
            } else {
                "Instance: ${selectedScope.kind().name.lowercase()} · ${selectedScope.id()}"
            },
            color = Palette.Text,
            style = OnfallTheme.typography.supportingEmphasis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            GameButton(
                if (italian) "Sessione" else "Session",
                dense = true,
                selected = selectedScope.isSession,
                onClick = { selectedScope = RuleScope.session() },
            )
            activeBattle.state.rosterOrder().forEach { combatantId ->
                val scope = RuleScope.actor(combatantId)
                GameButton(
                    activeBattle.name(combatantId),
                    dense = true,
                    selected = selectedScope == scope,
                    onClick = { selectedScope = scope },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(RuleScope.Kind.OBJECT, RuleScope.Kind.SCENE, RuleScope.Kind.CAMPAIGN).forEach { kind ->
                GameButton(
                    when (kind) {
                        RuleScope.Kind.OBJECT -> if (italian) "Oggetto" else "Object"
                        RuleScope.Kind.SCENE -> if (italian) "Scena" else "Scene"
                        RuleScope.Kind.CAMPAIGN -> if (italian) "Campagna" else "Campaign"
                        else -> kind.name
                    },
                    dense = true,
                    selected = customScopeKind == kind && selectedScope.kind() == kind,
                    onClick = { customScopeKind = kind },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuleTextField(
                customScopeId,
                { customScopeId = it.take(512) },
                if (italian) "ID scope libero" else "Custom scope ID",
                Modifier.weight(1f),
            )
            GameButton(
                if (italian) "Usa" else "Use",
                dense = true,
                enabled = customScopeId.isNotBlank(),
                onClick = {
                    selectedScope = RuleScope(customScopeKind, customScopeId.trim())
                },
            )
        }
        if (manual) {
            Text(
                if (italian) "Regola dichiarativa: l'esito resta al tavolo." else
                    "Declarative rule: resolution remains at the table.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // Anche un trigger concatenato può produrre un effetto TARGET: il bersaglio
        // va quindi scelto per ogni azione/evento eseguibile, non soltanto quando
        // il riferimento diretto contiene già recipient=TARGET.
        val needsTargetScope = !manual && entity.kind() in setOf(RuleKind.ACTION, RuleKind.TRIGGER)
        var selectedEffectTarget by remember(entity.id(), selectedScope) {
            mutableStateOf(selectedScope)
        }
        var customTargetKind by remember(entity.id(), selectedScope) {
            mutableStateOf(RuleScope.Kind.OBJECT)
        }
        var customTargetId by remember(entity.id(), selectedScope) { mutableStateOf("") }
        if (needsTargetScope) {
            Eyebrow(if (italian) "Scope bersaglio degli effetti" else "Effect target scope")
            Text(
                if (italian) {
                    "I costi restano su ${selectedScope.canonicalKey()}; gli effetti TARGET vanno allo scope scelto."
                } else {
                    "Costs stay on ${selectedScope.canonicalKey()}; TARGET effects use the selected scope."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            val targetScopes = (listOf(selectedScope, RuleScope.session()) +
                activeBattle.state.rosterOrder().map { RuleScope.actor(it) }).distinct()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                targetScopes.forEach { scope ->
                    GameButton(
                        when (scope.kind()) {
                            RuleScope.Kind.SESSION -> if (italian) "Sessione" else "Session"
                            RuleScope.Kind.ACTOR -> activeBattle.name(scope.id())
                            else -> scope.canonicalKey()
                        },
                        dense = true,
                        selected = selectedEffectTarget == scope,
                        onClick = { selectedEffectTarget = scope },
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                listOf(RuleScope.Kind.OBJECT, RuleScope.Kind.SCENE, RuleScope.Kind.CAMPAIGN)
                    .forEach { kind ->
                        GameButton(
                            when (kind) {
                                RuleScope.Kind.OBJECT -> if (italian) "Oggetto" else "Object"
                                RuleScope.Kind.SCENE -> if (italian) "Scena" else "Scene"
                                RuleScope.Kind.CAMPAIGN -> if (italian) "Campagna" else "Campaign"
                                else -> kind.name
                            },
                            dense = true,
                            selected = customTargetKind == kind && selectedEffectTarget.kind() == kind,
                            onClick = { customTargetKind = kind },
                        )
                    }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RuleTextField(
                    customTargetId,
                    { customTargetId = it.take(512) },
                    if (italian) "ID scope bersaglio" else "Target scope ID",
                    Modifier.weight(1f),
                )
                GameButton(
                    if (italian) "Usa bersaglio" else "Use target",
                    dense = true,
                    enabled = customTargetId.isNotBlank(),
                    onClick = {
                        selectedEffectTarget = RuleScope(customTargetKind, customTargetId.trim())
                    },
                )
            }
        }
        val grantsRuntimeRules = activeBattle.state.ruleSession().entities().any {
            it.attributes()["ownerRef"] == entity.id()
        }
        if (grantsRuntimeRules) {
            val active = activeBattle.genericRuleActive(entity.id(), selectedScope) == true
            GameButton(
                if (italian) {
                    if (active) "Disattiva effetti collegati" else "Attiva effetti collegati"
                } else {
                    if (active) "Deactivate linked effects" else "Activate linked effects"
                },
                dense = true,
                selected = active,
                enabled = !resolved,
                onClick = {
                    successNotice(
                        activeBattle.setGenericRuleActive(entity.id(), !active, selectedScope),
                        if (italian) "Attivazione aggiornata" else "Activation updated",
                    )
                },
            )
        }
        when (entity.kind()) {
            RuleKind.STAT, RuleKind.SKILL, RuleKind.SAVE, RuleKind.DEFENSE -> {
                activeBattle.genericRuleValue(entity.id(), selectedScope)?.let { value ->
                    Text(
                        (if (italian) "Valore calcolato: " else "Calculated value: ") + value,
                        color = Palette.Text,
                        style = OnfallTheme.typography.bodyEmphasis,
                    )
                    if (!resolved) {
                        var rawValue by remember(entity.id(), selectedScope, value) { mutableStateOf(value) }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RuleTextField(
                                rawValue,
                                { rawValue = it },
                                if (italian) "Override numerico" else "Numeric override",
                                Modifier.weight(1f),
                            )
                            GameButton(
                                strings.common.save,
                                dense = true,
                                onClick = {
                                    successNotice(
                                        activeBattle.setGenericNumericRuleValue(
                                            entity.id(), rawValue, selectedScope,
                                        ),
                                        if (italian) "Valore aggiornato" else "Value updated",
                                    )
                                },
                            )
                        }
                    }
                }
            }
            RuleKind.VALUE -> {
                val current = activeBattle.genericTypedRuleValue(entity.id(), selectedScope)
                if (current != null) {
                    Text(
                        "${current.type().name.lowercase()}: ${current.canonicalValue()}",
                        color = Palette.Text,
                        style = OnfallTheme.typography.bodyEmphasis,
                    )
                    if (entity.attributes()["mutable"].orEmpty().ifBlank { "true" }.toBooleanStrictOrNull() != false &&
                        !resolved
                    ) {
                        val allowed = entity.attributes()["allowedValues"].orEmpty()
                            .split(',').map(String::trim).filter(String::isNotBlank)
                        var rawValue by remember(entity.id(), selectedScope, current) {
                            mutableStateOf(current.canonicalValue())
                        }
                        if (allowed.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                allowed.forEach { candidate ->
                                    GameButton(
                                        candidate,
                                        dense = true,
                                        selected = candidate == current.canonicalValue(),
                                        onClick = {
                                            successNotice(
                                                activeBattle.setGenericRuleValue(
                                                    entity.id(), current.type(), candidate, selectedScope,
                                                ),
                                                if (italian) "Valore aggiornato" else "Value updated",
                                            )
                                        },
                                    )
                                }
                            }
                        } else if (current.type() == RuleValue.Type.REFERENCE) {
                            ReferenceButtons(
                                entities = activeBattle.state.ruleSession().entities()
                                    .filter { it.enabled() && it.id() != entity.id() },
                                selected = setOf(current.canonicalValue()),
                                onSelect = { candidate ->
                                    successNotice(
                                        activeBattle.setGenericRuleValue(
                                            entity.id(), current.type(), candidate, selectedScope,
                                        ),
                                        if (italian) "Riferimento aggiornato" else "Reference updated",
                                    )
                                },
                            )
                        } else if (current.type() == RuleValue.Type.BOOLEAN) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf("true", "false").forEach { candidate ->
                                    GameButton(
                                        candidate,
                                        dense = true,
                                        selected = candidate == current.canonicalValue(),
                                        onClick = {
                                            successNotice(
                                                activeBattle.setGenericRuleValue(
                                                    entity.id(), current.type(), candidate, selectedScope,
                                                ),
                                                if (italian) "Valore aggiornato" else "Value updated",
                                            )
                                        },
                                    )
                                }
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RuleTextField(
                                    rawValue,
                                    { rawValue = it },
                                    if (italian) "Nuovo valore" else "New value",
                                    Modifier.weight(1f),
                                )
                                GameButton(
                                    strings.common.save,
                                    dense = true,
                                    onClick = {
                                        successNotice(
                                            activeBattle.setGenericRuleValue(
                                                entity.id(), current.type(), rawValue, selectedScope,
                                            ),
                                            if (italian) "Valore aggiornato" else "Value updated",
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            RuleKind.RESOURCE, RuleKind.TRACK -> {
                runtime.resources()[entity.id()]?.let { resource ->
                    Text(
                        "${resource.current().toPlainString()} / ${resource.maximum().toPlainString()}",
                        color = Palette.Text,
                        style = OnfallTheme.typography.numberMedium,
                    )
                    if (!resolved) {
                        var current by remember(entity.id(), selectedScope, resource.current()) {
                            mutableStateOf(resource.current().toPlainString())
                        }
                        var maximum by remember(entity.id(), selectedScope, resource.maximum()) {
                            mutableStateOf(resource.maximum().toPlainString())
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RuleTextField(
                                current,
                                { current = it },
                                if (italian) "Attuale" else "Current",
                                Modifier.weight(1f),
                            )
                            RuleTextField(
                                maximum,
                                { maximum = it },
                                if (italian) "Massimo" else "Maximum",
                                Modifier.weight(1f),
                            )
                            GameButton(
                                strings.common.save,
                                dense = true,
                                onClick = {
                                    successNotice(
                                        activeBattle.setGenericResource(
                                            entity.id(), current, maximum, selectedScope,
                                        ),
                                        if (italian) "Risorsa aggiornata" else "Resource updated",
                                    )
                                },
                            )
                        }
                    }
                    val event = entity.attributes()["recoveryEvent"].orEmpty()
                    if (!manual && event.isNotBlank() && event != "MANUAL") {
                        GameButton(
                            if (italian) "Invia $event" else "Fire $event",
                            dense = true,
                            enabled = !resolved,
                            onClick = {
                                successNotice(
                                    activeBattle.fireGenericRuleEvent(event, selectedScope),
                                    if (italian) "Evento $event applicato" else "$event applied",
                                )
                            },
                        )
                    }
                }
            }
            RuleKind.ACTION_ECONOMY -> {
                if (runtime.turnBudget().isEmpty()) {
                    Text(
                        if (italian) "Nessun budget di turno dichiarato." else "No turn budget declared.",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        runtime.turnBudget().forEach { (id, amount) ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GameButton(
                                    "−",
                                    dense = true,
                                    enabled = !resolved && amount > java.math.BigDecimal.ZERO,
                                    onClick = {
                                        successNotice(
                                            activeBattle.setGenericTurnResource(
                                                id,
                                                amount.subtract(java.math.BigDecimal.ONE)
                                                    .max(java.math.BigDecimal.ZERO),
                                                selectedScope,
                                            ),
                                            if (italian) "Budget aggiornato" else "Budget updated",
                                        )
                                    },
                                )
                                Chip("$id · ${amount.toPlainString()}", Palette.GoldBright)
                                GameButton(
                                    "+",
                                    dense = true,
                                    enabled = !resolved,
                                    onClick = {
                                        successNotice(
                                            activeBattle.setGenericTurnResource(
                                                id,
                                                amount.add(java.math.BigDecimal.ONE),
                                                selectedScope,
                                            ),
                                            if (italian) "Budget aggiornato" else "Budget updated",
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            RuleKind.ACTION -> if (!manual) {
                GameButton(
                    if (italian) "Esegui azione" else "Execute action",
                    dense = true,
                    accent = Palette.Heal,
                    enabled = activeBattle.state.status() == CombatStatus.ACTIVE,
                    onClick = {
                        successNotice(
                            activeBattle.executeGenericRuleAction(
                                entity.id(), selectedScope, selectedEffectTarget,
                            ),
                            if (italian) "Azione eseguita" else "Action executed",
                        )
                    },
                )
            }
            RuleKind.TRIGGER -> if (!manual) {
                val event = entity.attributes()["event"].orEmpty()
                GameButton(
                    if (italian) "Invia evento $event" else "Fire $event",
                    dense = true,
                    enabled = !resolved && event.isNotBlank(),
                    onClick = {
                        successNotice(
                            activeBattle.fireGenericRuleEvent(
                                event, selectedScope, selectedEffectTarget,
                            ),
                            if (italian) "Trigger eseguito" else "Trigger executed",
                        )
                    },
                )
            }
            RuleKind.ROLL, RuleKind.RANDOMIZER -> if (!manual) {
                GameButton(
                    if (italian) "Tira" else "Roll",
                    dense = true,
                    accent = Palette.Gold,
                    enabled = !resolved,
                    onClick = {
                        val result = activeBattle.rollGenericRandomizer(entity.id(), selectedScope)
                        if (result != null) onNotice(result) else activeBattle.message?.let(onNotice)
                    },
                )
            }
            RuleKind.CONDITION -> {
                val stacks = runtime.conditionStacks()[entity.id()] ?: 0
                val maximum = entity.attributes()["maximumStacks"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                Text(
                    (if (italian) "Accumuli attivi: " else "Active stacks: ") + stacks,
                    color = Palette.Text,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!resolved) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        GameButton(
                            "−",
                            dense = true,
                            enabled = stacks > 0,
                            onClick = {
                                successNotice(
                                    activeBattle.setGenericConditionStacks(
                                        entity.id(), stacks - 1, selectedScope,
                                    ),
                                    if (italian) "Condizione aggiornata" else "Condition updated",
                                )
                            },
                        )
                        GameButton(
                            "+",
                            dense = true,
                            enabled = stacks < maximum,
                            onClick = {
                                successNotice(
                                    activeBattle.setGenericConditionStacks(
                                        entity.id(), stacks + 1, selectedScope,
                                    ),
                                    if (italian) "Condizione aggiornata" else "Condition updated",
                                )
                            },
                        )
                    }
                }
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValueSchemaEditor(
    attributes: Map<String, String>,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val selected = runCatching {
        RuleValue.Type.valueOf(attributes["valueType"].orEmpty().ifBlank { "TEXT" })
    }.getOrDefault(RuleValue.Type.TEXT)
    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .7f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(if (italian) "Valore indirizzabile" else "Addressable value", Palette.Heal)
        Text(
            if (italian) {
                "Può essere referenziato per ID da formule, azioni, trigger e altre regole."
            } else {
                "It can be referenced by ID from formulas, actions, triggers, and other rules."
            },
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            RuleValue.Type.entries.forEach { type ->
                GameButton(
                    attributeChoiceLabel("valueType", type.name, italian),
                    dense = true,
                    selected = selected == type,
                    onClick = {
                        onAttribute("valueType", type.name)
                        onAttribute(
                            "defaultValue",
                            when (type) {
                                RuleValue.Type.NUMBER -> "0"
                                RuleValue.Type.BOOLEAN -> "false"
                                RuleValue.Type.TEXT, RuleValue.Type.REFERENCE -> ""
                            },
                        )
                        onAttribute("allowedValues", "")
                    },
                )
            }
        }
        if (selected != RuleValue.Type.REFERENCE) {
            RuleTextField(
                attributes["defaultValue"].orEmpty(),
                { onAttribute("defaultValue", it) },
                if (italian) "Valore iniziale" else "Initial value",
                Modifier.fillMaxWidth(),
            )
            RuleTextField(
                attributes["allowedValues"].orEmpty(),
                { onAttribute("allowedValues", it) },
                if (italian) "Valori ammessi, separati da virgola (facoltativo)" else
                    "Allowed values, comma-separated (optional)",
                Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val mutable = attributes["mutable"].orEmpty().ifBlank { "true" }.toBooleanStrictOrNull() != false
            Checkbox(checked = mutable, onCheckedChange = { onAttribute("mutable", it.toString()) })
            Text(
                if (italian) "Modificabile durante la sessione" else "Editable during the session",
                color = Palette.Text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModularRuntimeSchemaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    kind: RuleKind,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val all = viewModel.selected?.revision?.entities().orEmpty().filter { it.enabled() && it.id() != entityId }
    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .7f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(if (italian) "Comportamento modulare" else "Modular behavior", Palette.Party)
        when (kind) {
            RuleKind.VALUE -> {
                if (showAdvanced) {
                    AttributeTextField(attributes, "dimension", if (italian) "Dimensione semantica" else "Semantic dimension", onAttribute)
                    AttributeTextField(attributes, "canonicalUnit", if (italian) "Unità canonica" else "Canonical unit", onAttribute)
                } else {
                    Text(
                        if (italian) "Le unità di misura si configurano nella modalità Blocchi." else
                            "Measurement units are configured in Blocks mode.",
                        color = Palette.TextFaint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            RuleKind.CONDITION -> {
                AttributeTextField(attributes, "maximumStacks", if (italian) "Accumuli massimi" else "Maximum stacks", onAttribute)
                AttributeChoices(if (italian) "Combinazione" else "Stacking", "stacking",
                    listOf("REPLACE", "STACK", "HIGHEST", "SEPARATE_BY_SOURCE"), attributes, onAttribute)
                AttributeCheckBox(attributes, "sourceScoped",
                    if (italian) "Distingui la fonte" else "Track each source", onAttribute)
                AttributeTextField(attributes, "removalEvent", if (italian) "Evento di rimozione" else "Removal event", onAttribute)
            }
            RuleKind.HEALTH_MODEL -> {
                val resources = all.filter { it.kind() == RuleKind.RESOURCE || it.kind() == RuleKind.TRACK }
                val conditions = all.filter { it.kind() == RuleKind.CONDITION }
                Text(if (italian) "Risorsa salute principale" else "Primary health resource",
                    color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                ReferenceButtons(resources, setOf(attributes["primaryResourceRef"].orEmpty())) {
                    onAttribute("primaryResourceRef", it)
                }
                val buffers = attributes["bufferResourceRefs"].orEmpty().split(',').map(String::trim)
                    .filter(String::isNotBlank).toSet()
                Text(if (italian) "Scudi o risorse consumate prima" else "Shields or resources spent first",
                    color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                ReferenceButtons(resources, buffers) { id ->
                    onAttribute(
                        "bufferResourceRefs",
                        (if (id in buffers) buffers - id else buffers + id).sorted().joinToString(","),
                    )
                }
                Text(if (italian) "Condizione quando arriva a zero" else "Condition when it reaches zero",
                    color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                ReferenceButtons(conditions, setOf(attributes["zeroConditionRef"].orEmpty())) {
                    onAttribute("zeroConditionRef", it)
                }
                if (showAdvanced) {
                    Text(if (italian) "Condizione di morte (facoltativa)" else "Death condition (optional)",
                        color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    ReferenceButtons(conditions, setOf(attributes["deathConditionRef"].orEmpty())) {
                        onAttribute("deathConditionRef", it)
                    }
                }
                AttributeChoices(if (italian) "Stato a zero" else "Zero state", "zeroState",
                    listOf("NONE", "DISABLED", "UNCONSCIOUS", "DYING", "DEAD", "MANUAL"), attributes, onAttribute)
                AttributeCheckBox(attributes, "allowsNegative",
                    if (italian) "Consenti valori negativi" else "Allow negative values", onAttribute)
            }
            RuleKind.MOVEMENT -> {
                AttributeChoices(if (italian) "Topologia" else "Topology", "topology",
                    listOf("SQUARE", "HEX_POINTY", "HEX_FLAT", "GRIDLESS", "THEATRE_OF_MIND"), attributes, onAttribute)
                AttributeChoices(if (italian) "Diagonali" else "Diagonals", "diagonalRule",
                    listOf("UNIFORM", "FIVE_TEN_FIVE", "EUCLIDEAN", "MANUAL"), attributes, onAttribute)
                AttributeTextField(attributes, "unitsPerCell", if (italian) "Unità per cella" else "Units per cell", onAttribute)
                AttributeTextField(attributes, "canonicalUnit", if (italian) "Unità canonica" else "Canonical unit", onAttribute)
                AttributeCheckBox(attributes, "elevation", if (italian) "Usa elevazione" else "Use elevation", onAttribute)
                AttributeCheckBox(attributes, "occupancyRequired",
                    if (italian) "Occupa spazio sulla mappa" else "Requires map occupancy", onAttribute)
            }
            RuleKind.SHEET_SECTION -> {
                AttributeChoices(if (italian) "Disposizione" else "Layout", "layout",
                    listOf("LIST", "GRID", "CARDS", "COMPACT"), attributes, onAttribute)
                NumericControl(
                    if (italian) "Colonne" else "Columns",
                    attributes["columns"]?.toIntOrNull() ?: 1,
                    1,
                    12,
                ) { onAttribute("columns", it.toString()) }
                if (showAdvanced) {
                    NumericControl(
                        if (italian) "Ordine" else "Order",
                        attributes["order"]?.toIntOrNull() ?: 0,
                        -1_000_000,
                        1_000_000,
                    ) { onAttribute("order", it.toString()) }
                    FormulaFieldEditor(
                        if (italian) "Mostra quando" else "Show when",
                        attributes["visibilityFormula"].orEmpty().ifBlank { "1" },
                        numericRuleCandidates(viewModel, entityId),
                        booleanResult = true,
                    ) { onAttribute("visibilityFormula", it) }
                }
                val fields = attributes["fieldRefs"].orEmpty().split(',').map(String::trim)
                    .filter(String::isNotBlank).toSet()
                Text(if (italian) "Campi mostrati" else "Displayed fields", color = Palette.TextMuted,
                    style = MaterialTheme.typography.labelSmall)
                ReferenceButtons(all.filter { it.kind() != RuleKind.SHEET_SECTION }, fields) { id ->
                    onAttribute("fieldRefs", (if (id in fields) fields - id else fields + id).sorted().joinToString(","))
                }
            }
            RuleKind.SCENE_PROCEDURE -> {
                AttributeTextField(attributes, "phases",
                    if (italian) "Fasi nell’ordine, separate da virgola" else "Phases in order, comma-separated", onAttribute)
                AttributeCheckBox(attributes, "initiativeRequired",
                    if (italian) "Richiede iniziativa" else "Requires initiative", onAttribute)
                AttributeCheckBox(attributes, "boardRequired",
                    if (italian) "Richiede mappa" else "Requires board", onAttribute)
                val actions = attributes["actionRefs"].orEmpty().split(',').map(String::trim)
                    .filter(String::isNotBlank).toSet()
                Text(if (italian) "Azioni disponibili" else "Available actions", color = Palette.TextMuted,
                    style = MaterialTheme.typography.labelSmall)
                ReferenceButtons(all.filter { it.kind() == RuleKind.ACTION }, actions) { id ->
                    onAttribute("actionRefs", (if (id in actions) actions - id else actions + id).sorted().joinToString(","))
                }
                val trackers = attributes["trackerRefs"].orEmpty().split(',').map(String::trim)
                    .filter(String::isNotBlank).toSet()
                Text(if (italian) "Contatori della scena" else "Scene trackers", color = Palette.TextMuted,
                    style = MaterialTheme.typography.labelSmall)
                ReferenceButtons(
                    all.filter { it.kind() == RuleKind.RESOURCE || it.kind() == RuleKind.TRACK },
                    trackers,
                ) { id ->
                    onAttribute(
                        "trackerRefs",
                        (if (id in trackers) trackers - id else trackers + id).sorted().joinToString(","),
                    )
                }
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatePolicySchemaEditor(
    attributes: Map<String, String>,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(if (italian) "Durata, proprietario e sincronizzazione" else "Lifetime, owner, and sync", Palette.Heal)
        AttributeChoices(if (italian) "Durata" else "Lifetime", "lifetime",
            listOf("ACTION", "TURN", "SCENE", "ENCOUNTER", "SESSION", "CAMPAIGN", "PERMANENT"), attributes, onAttribute)
        AttributeChoices(if (italian) "Proprietario" else "Owner", "owner",
            listOf("SCOPE", "ACTOR", "PARTY", "SESSION", "CAMPAIGN", "GM"), attributes, onAttribute)
        AttributeChoices(if (italian) "Sincronizzazione" else "Synchronization", "syncPolicy",
            listOf("LOCAL_ONLY", "PROPOSE", "AUTO_IF_COMPATIBLE", "NEVER"), attributes, onAttribute)
        AttributeTextField(attributes, "resetEvent",
            if (italian) "Evento di reset personalizzato" else "Custom reset event", onAttribute)
    }
}

@Composable
private fun AttributeTextField(
    attributes: Map<String, String>,
    key: String,
    label: String,
    onAttribute: (String, String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    RuleTextField(attributes[key].orEmpty(), { onAttribute(key, it) }, label, modifier)
}

@Composable
private fun AttributeCheckBox(
    attributes: Map<String, String>,
    key: String,
    label: String,
    onAttribute: (String, String) -> Unit,
) {
    val checked = attributes[key].orEmpty().toBooleanStrictOrNull() == true
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onAttribute(key, it.toString()) })
        Text(label, color = Palette.Text, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttributeChoices(
    label: String,
    key: String,
    choices: List<String>,
    attributes: Map<String, String>,
    onAttribute: (String, String) -> Unit,
) {
    Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        choices.forEach { choice ->
            GameButton(
                attributeChoiceLabel(key, choice, strings.language.tag == "it"),
                dense = true,
                selected = attributes[key].orEmpty().ifBlank { choices.first() } == choice,
                onClick = { onAttribute(key, choice) },
            )
        }
    }
}

private fun attributeChoiceLabel(key: String, choice: String, italian: Boolean): String {
    if (!italian) return when (choice) {
        "DICE_POOL" -> "Dice pool"
        "AT_OR_BELOW" -> "At or below"
        else -> choice.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
    }
    return when (key to choice) {
        "valueType" to "NUMBER" -> "Numero"
        "valueType" to "BOOLEAN" -> "Sì / no"
        "valueType" to "TEXT" -> "Testo"
        "valueType" to "REFERENCE" -> "Collegamento"
        "lookup" to "EXACT" -> "Solo chiave esatta"
        "lookup" to "FLOOR" -> "Valore precedente"
        "lookup" to "CEILING" -> "Valore successivo"
        "lookup" to "NEAREST" -> "Valore più vicino"
        "keep" to "SUM" -> "Somma"
        "keep" to "HIGHEST" -> "Più alto"
        "keep" to "LOWEST" -> "Più basso"
        "keep" to "SUCCESSES" -> "Numero di successi"
        "zeroState" to "NONE" -> "Nessuno"
        "zeroState" to "DISABLED" -> "Fuori gioco"
        "zeroState" to "UNCONSCIOUS" -> "Privo di sensi"
        "zeroState" to "DYING" -> "Morente"
        "zeroState" to "DEAD" -> "Morto"
        "zeroState" to "MANUAL" -> "Deciso al tavolo"
        "topology" to "SQUARE" -> "Griglia quadrata"
        "topology" to "HEX_POINTY" -> "Esagoni verticali"
        "topology" to "HEX_FLAT" -> "Esagoni orizzontali"
        "topology" to "GRIDLESS" -> "Senza griglia"
        "topology" to "THEATRE_OF_MIND" -> "Teatro della mente"
        "diagonalRule" to "UNIFORM" -> "Sempre uguale"
        "diagonalRule" to "FIVE_TEN_FIVE" -> "Alternata"
        "diagonalRule" to "EUCLIDEAN" -> "Distanza reale"
        "diagonalRule" to "MANUAL" -> "Decisa al tavolo"
        "layout" to "LIST" -> "Elenco"
        "layout" to "GRID" -> "Griglia"
        "layout" to "CARDS" -> "Schede"
        "layout" to "COMPACT" -> "Compatta"
        "stacking" to "REPLACE" -> "Sostituisce"
        "stacking" to "STACK" -> "Si accumula"
        "stacking" to "HIGHEST", "stacking" to "HIGHEST_PRIORITY" -> "Vale il più alto"
        "stacking" to "LOWEST", "stacking" to "LOWEST_PRIORITY" -> "Vale il più basso"
        "stacking" to "SEPARATE_BY_SOURCE" -> "Separata per fonte"
        "stacking" to "EXCLUSIVE" -> "Una sola"
        "rounding" to "NONE" -> "Nessuno"
        "rounding" to "FLOOR" -> "Per difetto"
        "rounding" to "CEILING" -> "Per eccesso"
        "rounding" to "HALF_UP" -> "Al più vicino"
        "lifetime" to "ACTION" -> "Azione"
        "lifetime" to "TURN" -> "Turno"
        "lifetime" to "SCENE" -> "Scena"
        "lifetime" to "ENCOUNTER" -> "Incontro"
        "lifetime" to "SESSION" -> "Sessione"
        "lifetime" to "CAMPAIGN" -> "Campagna"
        "lifetime" to "PERMANENT" -> "Permanente"
        "syncPolicy" to "LOCAL_ONLY" -> "Solo locale"
        "syncPolicy" to "PROPOSE" -> "Proponi agli altri"
        "syncPolicy" to "AUTO_IF_COMPATIBLE" -> "Automatica se compatibile"
        "syncPolicy" to "NEVER" -> "Mai"
        else -> choice.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatFormulaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val hasDerivedFormula = attributes["derivedFormula"]?.isNotBlank() == true
    val numericRules = numericRuleCandidates(viewModel, entityId)
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Party.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Come si calcola" else "How it is calculated", Palette.Party)
        Text(
            if (italian) {
                "Combina numeri e valori di altre regole. Ogni blocco può essere sostituito senza scrivere formule."
            } else {
                "Combine numbers and values from other rules. Every block can be replaced without writing formulas."
            },
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        FormulaFieldEditor(
            label = if (italian) "Calcolo principale" else "Main calculation",
            source = if (hasDerivedFormula) {
                attributes.getValue("derivedFormula")
            } else {
                attributes["defaultFormula"] ?: attributes["default"] ?: "0"
            },
            numericRules = numericRules,
            onSource = { onAttribute(if (hasDerivedFormula) "derivedFormula" else "defaultFormula", it) },
        )
        StatRoundingChoices(attributes["rounding"].orEmpty().ifBlank { "NONE" }) {
            onAttribute("rounding", it)
        }
        if (showAdvanced) {
            HorizontalDivider(color = Palette.Line)
            Text(
                if (italian) "Limiti e calcoli avanzati" else "Limits and advanced calculations",
                color = Palette.Text,
                style = OnfallTheme.typography.bodyEmphasis,
            )
            if (hasDerivedFormula) {
                FormulaFieldEditor(
                    if (italian) "Valore iniziale" else "Starting value",
                    attributes["defaultFormula"] ?: attributes["default"] ?: "0",
                    numericRules,
                ) { onAttribute("defaultFormula", it) }
                GameButton(
                    if (italian) "Usa il valore iniziale come calcolo principale" else
                        "Use the starting value as the main calculation",
                    dense = true,
                    accent = Palette.TextMuted,
                    onClick = { onAttribute("derivedFormula", "") },
                )
            } else {
                GameButton(
                    if (italian) "Aggiungi un calcolo derivato" else "Add a derived calculation",
                    dense = true,
                    onClick = {
                        onAttribute(
                            "derivedFormula",
                            attributes["defaultFormula"] ?: attributes["default"] ?: "0",
                        )
                    },
                )
            }
            OptionalFormulaField(
                if (italian) "Valore minimo" else "Minimum value",
                "minimumFormula",
                attributes,
                numericRules,
                onAttribute,
            )
            OptionalFormulaField(
                if (italian) "Valore massimo" else "Maximum value",
                "maximumFormula",
                attributes,
                numericRules,
                onAttribute,
            )
            OptionalFormulaField(
                if (italian) "Modificatore prodotto" else "Produced modifier",
                "modifierFormula",
                attributes,
                numericRules,
                onAttribute,
            )
        } else {
            Text(
                if (italian) {
                    "Per limiti, modificatori e formule derivate passa a Blocchi."
                } else {
                    "Switch to Blocks for limits, modifiers, and derived formulas."
                },
                color = Palette.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatRoundingChoices(selected: String, onSelect: (String) -> Unit) {
    val italian = strings.language.tag == "it"
    Text(
        if (italian) "Se il risultato ha decimali" else "When the result has decimals",
        color = Palette.TextMuted,
        style = MaterialTheme.typography.labelSmall,
    )
    val choices = listOf(
        "NONE" to (if (italian) "Mantienili" else "Keep them"),
        "FLOOR" to (if (italian) "Arrotonda in basso" else "Round down"),
        "HALF_UP" to (if (italian) "Al più vicino" else "To nearest"),
        "CEILING" to (if (italian) "Arrotonda in alto" else "Round up"),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        choices.forEach { (value, label) ->
            GameButton(label, dense = true, selected = selected == value, onClick = { onSelect(value) })
        }
    }
}

@Composable
private fun SkillFormulaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val statRef = attributes["statRef"] ?: attributes["abilityRef"] ?: attributes["ability"].orEmpty()
    val numericRules = numericRuleCandidates(viewModel, entityId)
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Party.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Calcolo della competenza" else "Skill calculation", Palette.Party)
        Text(
            if (italian) "Statistica collegata" else "Linked stat",
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        ReferenceButtons(
            numericRules.filter { it.kind() in setOf(RuleKind.STAT, RuleKind.SAVE, RuleKind.DEFENSE) },
            setOf(statRef),
        ) { onAttribute("statRef", it) }
        FormulaFieldEditor(
            if (italian) "Valore base" else "Base value",
            attributes["formula"].orEmpty().ifBlank {
                if (statRef.isBlank()) "0" else "\${$statRef:modifier}"
            },
            numericRules,
        ) { onAttribute("formula", it) }
        if (showAdvanced) {
            FormulaFieldEditor(
                if (italian) "Bonus quando addestrato" else "Bonus when trained",
                attributes["trainedBonusFormula"].orEmpty().ifBlank { "\${proficiency}" },
                numericRules,
            ) { onAttribute("trainedBonusFormula", it) }
        } else {
            Text(
                if (italian) {
                    "Il bonus da addestramento si modifica nella modalità Blocchi."
                } else {
                    "The training bonus can be changed in Blocks mode."
                },
                color = Palette.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResourceFormulaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val numericRules = numericRuleCandidates(viewModel, entityId)
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Party.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Quantità e recupero" else "Amount and recovery", Palette.Party)
        FormulaFieldEditor(
            if (italian) "Capienza massima" else "Maximum amount",
            attributes["maximumFormula"] ?: attributes["maximum"] ?: "1",
            numericRules,
        ) { onAttribute("maximumFormula", it) }
        FormulaFieldEditor(
            if (italian) "Quantità iniziale" else "Starting amount",
            attributes["initialFormula"].orEmpty().ifBlank { "\${maximum}" },
            numericRules,
        ) { onAttribute("initialFormula", it) }
        Text(
            if (italian) "Quando si recupera" else "When it recovers",
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        val recoveryEvent = attributes["recoveryEvent"].orEmpty().ifBlank { "MANUAL" }
        val events = listOf(
            "MANUAL" to (if (italian) "Solo manualmente" else "Manually only"),
            "TURN_STARTED" to (if (italian) "A inizio turno" else "At turn start"),
            "TURN_ENDED" to (if (italian) "A fine turno" else "At turn end"),
            "REST_COMPLETED" to (if (italian) "Dopo un riposo" else "After a rest"),
            "SCENE_STARTED" to (if (italian) "A inizio scena" else "At scene start"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            events.forEach { (value, label) ->
                GameButton(label, dense = true, selected = recoveryEvent == value,
                    onClick = { onAttribute("recoveryEvent", value) })
            }
        }
        if (events.none { it.first == recoveryEvent }) {
            Text(
                (if (italian) "Evento personalizzato conservato: " else "Preserved custom event: ") + recoveryEvent,
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (showAdvanced) {
            FormulaFieldEditor(
                if (italian) "Quantità dopo il recupero" else "Amount after recovery",
                attributes["recoveryFormula"].orEmpty().ifBlank { "\${maximum}" },
                numericRules,
            ) { onAttribute("recoveryFormula", it) }
        } else {
            Text(
                if (italian) "Il recupero riporta normalmente la risorsa al massimo." else
                    "Recovery normally restores the resource to its maximum.",
                color = Palette.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RandomizerSchemaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val mode = attributes["mode"].orEmpty().ifBlank { "DICE" }
    val all = viewModel.selected?.revision?.entities().orEmpty().filter { it.enabled() && it.id() != entityId }
    val numericRules = numericRuleCandidates(viewModel, entityId)
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Party.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Come genera il risultato" else "How it generates the result", Palette.Party)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(
                "DICE" to (if (italian) "Tira e somma dadi" else "Roll and sum dice"),
                "DICE_POOL" to (if (italian) "Conta i successi" else "Count successes"),
                "TABLE" to (if (italian) "Estrai da una tabella" else "Draw from a table"),
                "MANUAL" to (if (italian) "Inserimento manuale" else "Manual input"),
            ).forEach { (value, label) ->
                GameButton(label, dense = true, selected = mode == value,
                    onClick = { onAttribute("mode", value) })
            }
        }
        when (mode) {
            "DICE", "DICE_POOL" -> {
                FormulaFieldEditor(
                    if (italian) "Numero di dadi" else "Number of dice",
                    attributes["countFormula"] ?: attributes["diceCount"] ?: "1",
                    numericRules,
                ) { onAttribute("countFormula", it) }
                FormulaFieldEditor(
                    if (italian) "Facce di ogni dado" else "Sides on each die",
                    attributes["sidesFormula"] ?: attributes["dieSides"] ?: "6",
                    numericRules,
                ) { onAttribute("sidesFormula", it) }
                if (mode == "DICE_POOL") {
                    FormulaFieldEditor(
                        if (italian) "Ogni dado riesce da" else "Each die succeeds from",
                        attributes["successThresholdFormula"] ?: attributes["successThreshold"] ?: "1",
                        numericRules,
                    ) { onAttribute("successThresholdFormula", it) }
                }
                if (showAdvanced) {
                    val keeps = if (mode == "DICE_POOL") {
                        listOf("SUCCESSES", "SUM", "HIGHEST", "LOWEST")
                    } else {
                        listOf("SUM", "HIGHEST", "LOWEST", "SUCCESSES")
                    }
                    AttributeChoices(
                        if (italian) "Risultato conservato" else "Kept result",
                        "keep",
                        keeps,
                        attributes + ("keep" to attributes["keep"].orEmpty().ifBlank {
                            if (mode == "DICE_POOL") "SUCCESSES" else "SUM"
                        }),
                        onAttribute,
                    )
                }
            }
            "TABLE" -> {
                Text(if (italian) "Tabella da usare" else "Table to use", color = Palette.TextMuted,
                    style = MaterialTheme.typography.labelSmall)
                ReferenceButtons(
                    all.filter { it.kind() == RuleKind.TABLE },
                    setOf(attributes["tableRef"].orEmpty()),
                ) { onAttribute("tableRef", it) }
            }
            else -> Text(
                if (italian) "Il tavolo inserirà il risultato quando serve." else
                    "The table will enter the result when needed.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RollSchemaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val all = viewModel.selected?.revision?.entities().orEmpty().filter { it.enabled() && it.id() != entityId }
    val numericRules = numericRuleCandidates(viewModel, entityId)
    val randomizerRef = attributes["randomizerRef"].orEmpty()
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Party.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Tiro e risultato" else "Roll and outcome", Palette.Party)
        Text(if (italian) "Dadi usati" else "Dice used", color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall)
        ReferenceButtons(
            all.filter { it.kind() == RuleKind.RANDOMIZER || it.kind() == RuleKind.ROLL },
            setOf(randomizerRef),
        ) { onAttribute("randomizerRef", it) }
        if (randomizerRef.isBlank()) {
            Text(
                if (italian) "Scegli prima una regola di dadi." else "Choose a dice rule first.",
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FormulaFieldEditor(
            if (italian) "Totale del tiro" else "Roll total",
            attributes["totalFormula"].orEmpty().ifBlank { "\${roll}" },
            numericRules,
        ) { onAttribute("totalFormula", it) }
        FormulaFieldEditor(
            if (italian) "Difficoltà da raggiungere" else "Target to reach",
            attributes["targetFormula"].orEmpty().ifBlank { "0" },
            numericRules,
        ) { onAttribute("targetFormula", it) }
        Text(if (italian) "Quando riesce" else "When it succeeds", color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall)
        val comparison = attributes["comparison"].orEmpty().ifBlank { "MEET_OR_EXCEED" }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(
                "MEET_OR_EXCEED" to (if (italian) "Totale almeno pari" else "Total meets or exceeds"),
                "EXCEED" to (if (italian) "Totale superiore" else "Total exceeds"),
                "AT_OR_BELOW" to (if (italian) "Totale non superiore" else "Total meets or stays below"),
                "BELOW" to (if (italian) "Totale inferiore" else "Total stays below"),
            ).forEach { (value, label) ->
                GameButton(label, dense = true, selected = comparison == value,
                    onClick = { onAttribute("comparison", value) })
            }
        }
        if (showAdvanced) {
            NumericControl(
                if (italian) "Successo naturale da (0 = nessuno)" else "Natural success from (0 = none)",
                attributes["naturalSuccessMinimum"]?.toIntOrNull() ?: 0, 0, 1_000,
            ) { onAttribute("naturalSuccessMinimum", it.toString()) }
            NumericControl(
                if (italian) "Fallimento naturale fino a (0 = nessuno)" else "Natural failure through (0 = none)",
                attributes["naturalFailureMaximum"]?.toIntOrNull() ?: 0, 0, 1_000,
            ) { onAttribute("naturalFailureMaximum", it.toString()) }
            Text(if (italian) "Tabella degli esiti (facoltativa)" else "Outcome table (optional)",
                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            ReferenceButtons(
                all.filter { it.kind() == RuleKind.TABLE },
                setOf(attributes["outcomeTableRef"].orEmpty()),
            ) { onAttribute("outcomeTableRef", it) }
            Text(if (italian) "Tiro contrapposto (facoltativo)" else "Opposed roll (optional)",
                color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            ReferenceButtons(
                all.filter { it.kind() == RuleKind.ROLL },
                setOf(attributes["opposedRollRef"].orEmpty()),
            ) { onAttribute("opposedRollRef", it) }
        }
    }
}

internal fun numericRuleCandidates(viewModel: RulesViewModel, entityId: String): List<RuleEntity> =
    viewModel.selected?.revision?.entities().orEmpty().filter {
        it.id() != entityId && RulesetCompiler.isDirectNumericFormulaReferenceTarget(it)
    }

private data class FormulaAssignment(val target: String, val formula: String)

private fun formulaAssignments(source: String): List<FormulaAssignment>? {
    if (source.isBlank()) return emptyList()
    val rows = mutableListOf<FormulaAssignment>()
    val targets = mutableSetOf<String>()
    for (raw in source.split(';')) {
        if (raw.isBlank()) continue
        val separator = raw.indexOf('=')
        if (separator <= 0 || separator == raw.lastIndex) return null
        val target = raw.substring(0, separator).trim()
        val formula = raw.substring(separator + 1).trim()
        if (target.isBlank() || formula.isBlank() || !targets.add(target)) return null
        rows += FormulaAssignment(target, formula)
    }
    return rows
}

private fun encodeFormulaAssignments(rows: List<FormulaAssignment>): String =
    rows.joinToString(";") { "${it.target.trim()}=${it.formula.trim()}" }

private val legacyTurnBudgets = linkedMapOf(
    "actions" to "action",
    "bonusActions" to "bonus_action",
    "reactions" to "reaction",
    "moveActions" to "move",
    "swiftActions" to "swift",
    "immediateActions" to "immediate",
    "fullRoundActions" to "full_round",
)

private fun turnBudgetAssignments(attributes: Map<String, String>): List<FormulaAssignment>? {
    if (!attributes["budgets"].isNullOrBlank()) return formulaAssignments(attributes["budgets"].orEmpty())
    return legacyTurnBudgets.mapNotNull { (attribute, target) ->
        attributes[attribute]?.takeIf(String::isNotBlank)?.let { FormulaAssignment(target, it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionEconomySchemaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val numericRules = numericRuleCandidates(viewModel, entityId)
    val rows = turnBudgetAssignments(attributes)
    fun update(changed: List<FormulaAssignment>) {
        onAttribute("budgets", encodeFormulaAssignments(changed))
        legacyTurnBudgets.keys.forEach { onAttribute(it, "") }
    }
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Party.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Cosa si può spendere nel turno" else "What can be spent each turn", Palette.Party)
        if (rows == null) {
            ProtectedAuthoringValue(
                if (italian) "Budget avanzato conservato" else "Advanced budget preserved",
                attributes["budgets"].orEmpty(),
            )
            return@Column
        }
        rows.forEachIndexed { index, row ->
            var targetDraft by remember(index, row.target) { mutableStateOf(row.target) }
            Column(
                Modifier.fillMaxWidth().border(1.dp, Palette.Line, RoundedCornerShape(7.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showAdvanced) {
                    RuleTextField(
                        targetDraft,
                        { targetDraft = it },
                        if (italian) "ID del budget" else "Budget ID",
                        Modifier.fillMaxWidth(),
                    )
                    GameButton(
                        if (italian) "Applica nome" else "Apply name",
                        dense = true,
                        enabled = targetDraft.isNotBlank() && targetDraft.trim() != row.target &&
                            rows.filterIndexed { item, _ -> item != index }.none { it.target == targetDraft.trim() },
                        onClick = {
                            update(rows.toMutableList().also {
                                it[index] = row.copy(target = targetDraft.trim())
                            })
                        },
                    )
                } else {
                    Text(turnBudgetLabel(row.target, italian), color = Palette.Text,
                        style = OnfallTheme.typography.supportingEmphasis)
                }
                FormulaFieldEditor(
                    if (italian) "Quantità disponibile" else "Available amount",
                    row.formula,
                    numericRules,
                ) { formula -> update(rows.toMutableList().also { it[index] = row.copy(formula = formula) }) }
                GameButton(
                    if (italian) "Rimuovi budget" else "Remove budget",
                    dense = true,
                    accent = Palette.Enemy,
                    onClick = { update(rows.filterIndexed { item, _ -> item != index }) },
                )
            }
        }
        val suggestions = listOf("action", "bonus_action", "reaction", "move")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            suggestions.filter { candidate -> rows.none { it.target == candidate } }.forEach { candidate ->
                GameButton(
                    (if (italian) "Aggiungi " else "Add ") + turnBudgetLabel(candidate, italian),
                    dense = true,
                    onClick = { update(rows + FormulaAssignment(candidate, "1")) },
                )
            }
            if (showAdvanced) {
                GameButton(
                    if (italian) "Aggiungi budget personalizzato" else "Add custom budget",
                    dense = true,
                    onClick = {
                        var index = rows.size + 1
                        var candidate = "budget_$index"
                        while (rows.any { it.target == candidate }) candidate = "budget_${++index}"
                        update(rows + FormulaAssignment(candidate, "1"))
                    },
                )
            }
        }
        if (rows.isEmpty()) {
            Text(
                if (italian) "Aggiungi almeno un budget per rendere eseguibili i costi delle azioni." else
                    "Add at least one budget to make action costs executable.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun turnBudgetLabel(id: String, italian: Boolean): String = when (id) {
    "action" -> if (italian) "azione" else "action"
    "bonus_action" -> if (italian) "azione bonus" else "bonus action"
    "reaction" -> if (italian) "reazione" else "reaction"
    "move" -> if (italian) "movimento" else "movement"
    "swift" -> if (italian) "azione veloce" else "swift action"
    "immediate" -> if (italian) "azione immediata" else "immediate action"
    "full_round" -> if (italian) "round completo" else "full round"
    else -> id
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionSchemaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val all = viewModel.selected?.revision?.entities().orEmpty().filter { it.enabled() && it.id() != entityId }
    val numericRules = numericRuleCandidates(viewModel, entityId)
    val costs = formulaAssignments(attributes["costs"].orEmpty())
    val effects = attributes["effectRefs"].orEmpty().split(',').map(String::trim)
        .filter(String::isNotBlank).toSet()
    fun updateCosts(changed: List<FormulaAssignment>) =
        onAttribute("costs", encodeFormulaAssignments(changed))
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Heal.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Costi, requisiti ed effetti" else "Costs, requirements, and effects", Palette.Heal)
        if (showAdvanced) {
            Text(if (italian) "Proprietario (facoltativo)" else "Owner (optional)", color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall)
            ReferenceButtons(all, setOf(attributes["ownerRef"].orEmpty())) { onAttribute("ownerRef", it) }
        }
        FormulaFieldEditor(
            if (italian) "Si può usare quando" else "Can be used when",
            attributes["conditionFormula"].orEmpty().ifBlank { "1" },
            numericRules,
            booleanResult = true,
        ) { onAttribute("conditionFormula", it) }
        Text(if (italian) "Costi" else "Costs", color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall)
        if (costs == null) {
            ProtectedAuthoringValue(
                if (italian) "Costi avanzati conservati" else "Advanced costs preserved",
                attributes["costs"].orEmpty(),
            )
        } else {
            costs.forEachIndexed { index, cost ->
                val targetLabel = when {
                    cost.target.startsWith("turn:") ->
                        (if (italian) "Turno: " else "Turn: ") + turnBudgetLabel(cost.target.removePrefix("turn:"), italian)
                    cost.target.startsWith("resource:") -> {
                        val id = cost.target.removePrefix("resource:")
                        (if (italian) "Risorsa: " else "Resource: ") +
                            (all.firstOrNull { it.id() == id }?.name()?.text(strings.language.tag) ?: id)
                    }
                    else -> cost.target
                }
                Column(
                    Modifier.fillMaxWidth().border(1.dp, Palette.Line, RoundedCornerShape(7.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(targetLabel, color = Palette.Text, style = OnfallTheme.typography.supportingEmphasis)
                    FormulaFieldEditor(
                        if (italian) "Quantità spesa" else "Amount spent",
                        cost.formula,
                        numericRules,
                    ) { formula ->
                        updateCosts(costs.toMutableList().also { it[index] = cost.copy(formula = formula) })
                    }
                    GameButton(
                        if (italian) "Rimuovi costo" else "Remove cost",
                        dense = true,
                        accent = Palette.Enemy,
                        onClick = { updateCosts(costs.filterIndexed { item, _ -> item != index }) },
                    )
                }
            }
            val budgetIds = all.filter { it.kind() == RuleKind.ACTION_ECONOMY }
                .flatMap { turnBudgetAssignments(it.attributes()).orEmpty() }.map { it.target }.distinct()
            val availableCosts = buildList {
                budgetIds.forEach { add("turn:$it" to turnBudgetLabel(it, italian)) }
                all.filter { it.kind() == RuleKind.RESOURCE || it.kind() == RuleKind.TRACK }.forEach {
                    add("resource:${it.id()}" to it.name().text(strings.language.tag))
                }
            }.filter { (target, _) -> costs.none { it.target == target } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                availableCosts.forEach { (target, label) ->
                    GameButton(
                        (if (italian) "Spendi " else "Spend ") + label,
                        dense = true,
                        onClick = { updateCosts(costs + FormulaAssignment(target, "1")) },
                    )
                }
            }
        }
        Text(if (italian) "Effetti applicati" else "Applied effects", color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall)
        ReferenceButtons(all.filter { it.kind() == RuleKind.MODIFIER }, effects) { id ->
            onAttribute("effectRefs", (if (id in effects) effects - id else effects + id).sorted().joinToString(","))
        }
        if (effects.isEmpty()) {
            Text(
                if (italian) "L’azione può avere solo un costo oppure applicare uno o più effetti." else
                    "The action can only spend a cost or apply one or more effects.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerSchemaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val all = viewModel.selected?.revision?.entities().orEmpty().filter { it.enabled() && it.id() != entityId }
    val numericRules = numericRuleCandidates(viewModel, entityId)
    val event = attributes["event"].orEmpty().ifBlank { "MANUAL" }
    var customEvent by remember(entityId, event) { mutableStateOf(event) }
    val effects = attributes["effectRefs"].orEmpty().split(',').map(String::trim)
        .filter(String::isNotBlank).toSet()
    val events = listOf(
        "MANUAL" to (if (italian) "Manuale" else "Manual"),
        "TURN_STARTED" to (if (italian) "Inizio turno" else "Turn starts"),
        "TURN_ENDED" to (if (italian) "Fine turno" else "Turn ends"),
        "REST_COMPLETED" to (if (italian) "Riposo completato" else "Rest completed"),
        "SCENE_STARTED" to (if (italian) "Inizio scena" else "Scene starts"),
        "DAMAGE_TAKEN" to (if (italian) "Danno subito" else "Damage taken"),
    )
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Heal.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Quando accade, applica" else "When this happens, apply", Palette.Heal)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            events.forEach { (value, label) ->
                GameButton(label, dense = true, selected = event == value,
                    onClick = { onAttribute("event", value) })
            }
        }
        if (showAdvanced) {
            RuleTextField(customEvent, { customEvent = it },
                if (italian) "ID evento personalizzato" else "Custom event ID", Modifier.fillMaxWidth())
            GameButton(
                if (italian) "Applica evento" else "Apply event",
                dense = true,
                enabled = customEvent.isNotBlank() && customEvent.trim() != event,
                onClick = { onAttribute("event", customEvent.trim()) },
            )
        } else if (events.none { it.first == event }) {
            Text(
                (if (italian) "Evento personalizzato conservato: " else "Preserved custom event: ") + event,
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FormulaFieldEditor(
            if (italian) "Solo quando" else "Only when",
            attributes["conditionFormula"].orEmpty().ifBlank { "1" },
            numericRules,
            booleanResult = true,
        ) { onAttribute("conditionFormula", it) }
        Text(if (italian) "Effetti applicati" else "Applied effects", color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall)
        ReferenceButtons(all.filter { it.kind() == RuleKind.MODIFIER }, effects) { id ->
            onAttribute("effectRefs", (if (id in effects) effects - id else effects + id).sorted().joinToString(","))
        }
        NumericControl(
            if (italian) "Massimo numero di attivazioni per evento" else "Maximum runs per event",
            attributes["maximumExecutions"]?.toIntOrNull() ?: 1,
            1,
            10_000,
        ) { onAttribute("maximumExecutions", it.toString()) }
        if (showAdvanced) {
            NumericControl(
                if (italian) "Priorità" else "Priority",
                attributes["priority"]?.toIntOrNull() ?: 0,
                -1_000_000,
                1_000_000,
            ) { onAttribute("priority", it.toString()) }
        }
    }
}

private data class TableRowDraft(val threshold: String, val value: String)

private fun tableRows(source: String): List<TableRowDraft>? {
    if (source.isBlank()) return null
    val rows = mutableListOf<TableRowDraft>()
    val thresholds = mutableSetOf<BigDecimal>()
    for (raw in source.split(';')) {
        if (raw.isBlank()) continue
        val separator = raw.indexOf('=')
        if (separator <= 0 || separator == raw.lastIndex) return null
        val threshold = raw.substring(0, separator).trim()
        val value = raw.substring(separator + 1).trim()
        val number = threshold.toBigDecimalOrNull() ?: return null
        if (value.isBlank() || !thresholds.add(number.stripTrailingZeros())) return null
        rows += TableRowDraft(threshold, value)
    }
    return rows.takeIf { it.isNotEmpty() }
}

private fun encodeTableRows(rows: List<TableRowDraft>): String =
    rows.joinToString(";") { "${it.threshold.trim()}=${it.value.trim()}" }

@Composable
private fun ProtectedAuthoringValue(label: String, source: String) {
    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .72f), RoundedCornerShape(7.dp))
            .border(1.dp, Palette.Bloodied.copy(alpha = .75f), RoundedCornerShape(7.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = Palette.Bloodied, style = OnfallTheme.typography.supportingEmphasis)
        if (source.isNotBlank()) {
            Text(source, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (strings.language.tag == "it") {
                "Questo contenuto non viene riscritto. Puoi modificarlo in modalità Esperto."
            } else {
                "This content will not be rewritten. You can edit it in Expert mode."
            },
            color = Palette.TextFaint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableSchemaEditor(
    attributes: Map<String, String>,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val rows = tableRows(attributes["rows"].orEmpty())
    val valueType = attributes["valueType"].orEmpty().ifBlank { "NUMBER" }
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Gold.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Righe della tabella" else "Table rows", Palette.Gold)
        AttributeChoices(
            if (italian) "Tipo di risultato" else "Result type",
            "valueType",
            listOf("NUMBER", "TEXT", "BOOLEAN", "REFERENCE"),
            attributes + ("valueType" to valueType),
            onAttribute,
        )
        AttributeChoices(
            if (italian) "Come scegliere la riga" else "How to choose a row",
            "lookup",
            listOf("EXACT", "FLOOR", "CEILING", "NEAREST"),
            attributes + ("lookup" to attributes["lookup"].orEmpty().ifBlank { "EXACT" }),
            onAttribute,
        )
        if (rows == null) {
            ProtectedAuthoringValue(
                if (italian) "Righe avanzate conservate" else "Advanced rows preserved",
                attributes["rows"].orEmpty(),
            )
            return@Column
        }
        rows.forEachIndexed { index, row ->
            var thresholdDraft by remember(index, row.threshold) { mutableStateOf(row.threshold) }
            var valueDraft by remember(index, row.value) { mutableStateOf(row.value) }
            val thresholdNumber = thresholdDraft.trim().toBigDecimalOrNull()
            val thresholdValid = thresholdNumber != null &&
                rows.filterIndexed { item, _ -> item != index }.none {
                    it.threshold.toBigDecimalOrNull()?.compareTo(thresholdNumber) == 0
                }
            val valueValid = runCatching {
                RuleValue(RuleValue.Type.valueOf(valueType), valueDraft.trim())
            }.isSuccess
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RuleTextField(
                    thresholdDraft,
                    { thresholdDraft = it },
                    if (italian) "Da" else "From",
                    Modifier.weight(.55f),
                )
                RuleTextField(
                    valueDraft,
                    { valueDraft = it },
                    if (italian) "Risultato" else "Result",
                    Modifier.weight(1f),
                )
                GameButton(
                    if (italian) "Applica" else "Apply",
                    dense = true,
                    enabled = thresholdValid && valueDraft.isNotBlank() && valueValid &&
                        (thresholdDraft.trim() != row.threshold || valueDraft.trim() != row.value),
                    onClick = {
                        onAttribute("rows", encodeTableRows(rows.toMutableList().also {
                            it[index] = TableRowDraft(thresholdDraft.trim(), valueDraft.trim())
                        }))
                    },
                )
                GameButton(
                    if (italian) "Rimuovi riga" else "Remove row",
                    dense = true,
                    accent = Palette.Enemy,
                    enabled = rows.size > 1,
                    onClick = {
                        onAttribute("rows", encodeTableRows(rows.filterIndexed { item, _ -> item != index }))
                    },
                )
            }
            if (!valueValid) {
                Text(
                    if (italian) "Il risultato non è valido per il tipo scelto." else
                        "The result is not valid for the selected type.",
                    color = Palette.Bloodied,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        GameButton(
            if (italian) "Aggiungi riga" else "Add row",
            dense = true,
            onClick = {
                val next = rows.mapNotNull { it.threshold.toBigDecimalOrNull() }.maxOrNull()
                    ?.add(BigDecimal.ONE)?.stripTrailingZeros()?.toPlainString() ?: "1"
                onAttribute("rows", encodeTableRows(rows + TableRowDraft(next, when (valueType) {
                    "NUMBER" -> "0"
                    "BOOLEAN" -> "false"
                    "REFERENCE" -> "rule:id"
                    else -> if (italian) "Nuovo risultato" else "New result"
                })))
            },
        )
    }
}

@Composable
private fun OptionalFormulaField(
    label: String,
    key: String,
    attributes: Map<String, String>,
    numericRules: List<RuleEntity>,
    onAttribute: (String, String) -> Unit,
) {
    val source = attributes[key].orEmpty()
    if (source.isBlank()) {
        GameButton(
            if (strings.language.tag == "it") "Aggiungi $label" else "Add $label",
            dense = true,
            onClick = { onAttribute(key, "0") },
        )
    } else {
        FormulaFieldEditor(label, source, numericRules) { onAttribute(key, it) }
        GameButton(
            if (strings.language.tag == "it") "Rimuovi $label" else "Remove $label",
            dense = true,
            accent = Palette.TextMuted,
            onClick = { onAttribute(key, "") },
        )
    }
}

@Composable
private fun FormulaFieldEditor(
    label: String,
    source: String,
    numericRules: List<RuleEntity>,
    booleanResult: Boolean = false,
    onSource: (String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val draft = runCatching { FormulaDraft.parse(source) }.getOrNull()
    Column(
        Modifier.fillMaxWidth().background(Palette.Surface.copy(alpha = .85f), RoundedCornerShape(7.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(7.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, color = Palette.Text, style = OnfallTheme.typography.supportingEmphasis)
        if (draft == null) {
            Text(
                if (italian) {
                    "Questo calcolo non è valido. Apri Esperto per correggere il testo originale."
                } else {
                    "This calculation is invalid. Open Expert to fix its original text."
                },
                color = Palette.Critical,
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }
        if (draft.projectionStatus() == ProjectionStatus.PARTIAL) {
            Text(
                if (italian) {
                    "Le parti avanzate restano protette: puoi cambiare gli altri blocchi senza perderle."
                } else {
                    "Advanced parts stay protected: you can change other blocks without losing them."
                },
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (LocalGuidedFormulaEditing.current) {
            GuidedFormulaEditor(
                expression = draft.expression(),
                numericRules = numericRules,
                booleanResult = booleanResult,
                onChange = { changed ->
                    runCatching { RuleFormula.compile(changed).source() }.onSuccess(onSource)
                },
            )
            FormulaExample(source, booleanResult, numericRules)
            return@Column
        }
        FormulaBlockToolbar(draft.expression(), numericRules, booleanResult) { changed ->
            runCatching { RuleFormula.compile(changed).source() }.onSuccess(onSource)
        }
        FormulaNodeEditor(draft.expression(), numericRules, 0) { changed ->
            runCatching { RuleFormula.compile(changed).source() }.onSuccess(onSource)
        }
        FormulaExample(source, booleanResult, numericRules)
    }
}

private data class SignedFormulaTerm(val subtract: Boolean, val expression: RuleFormula.Expression)

private fun additiveTerms(expression: RuleFormula.Expression): List<SignedFormulaTerm>? {
    fun visit(node: RuleFormula.Expression, subtract: Boolean, output: MutableList<SignedFormulaTerm>): Boolean {
        return when (node) {
            is RuleFormula.BinaryExpression -> when (node.operator()) {
                "+" -> visit(node.left(), subtract, output) && visit(node.right(), subtract, output)
                "-" -> visit(node.left(), subtract, output) && visit(node.right(), !subtract, output)
                else -> false
            }
            is RuleFormula.NumberExpression, is RuleFormula.ValueExpression -> {
                output += SignedFormulaTerm(subtract, node)
                true
            }
            else -> false
        }
    }
    val terms = mutableListOf<SignedFormulaTerm>()
    return if (visit(expression, false, terms) && terms.isNotEmpty()) terms else null
}

private fun expressionFromTerms(terms: List<SignedFormulaTerm>): RuleFormula.Expression {
    val normalized = terms.ifEmpty { listOf(SignedFormulaTerm(false, RuleFormula.NumberExpression(BigDecimal.ZERO))) }
    var result: RuleFormula.Expression = if (normalized.first().subtract) {
        RuleFormula.BinaryExpression(
            "-",
            RuleFormula.NumberExpression(BigDecimal.ZERO),
            normalized.first().expression,
        )
    } else normalized.first().expression
    normalized.drop(1).forEach { term ->
        result = RuleFormula.BinaryExpression(if (term.subtract) "-" else "+", result, term.expression)
    }
    return result
}

@Composable
private fun GuidedFormulaEditor(
    expression: RuleFormula.Expression,
    numericRules: List<RuleEntity>,
    booleanResult: Boolean,
    onChange: (RuleFormula.Expression) -> Unit,
) {
    val italian = strings.language.tag == "it"
    if (booleanResult) {
        GuidedConditionEditor(expression, numericRules, onChange)
        return
    }
    val terms = additiveTerms(expression)
    Text(
        naturalFormulaDescription(expression, numericRules, italian),
        color = Palette.Text,
        style = OnfallTheme.typography.bodyEmphasis,
    )
    if (terms == null) {
        Text(
            if (italian) {
                "Questo calcolo usa moltiplicazioni, limiti o funzioni avanzate. È conservato senza modifiche; apri Altre opzioni → Blocchi avanzati per cambiarlo."
            } else {
                "This calculation uses multiplication, limits, or advanced functions. It is preserved unchanged; open More options → Advanced blocks to change it."
            },
            color = Palette.Bloodied,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        terms.forEachIndexed { index, term ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (index == 0) {
                    Text(if (italian) "Parte da" else "Starts with", color = Palette.TextMuted,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(min = 72.dp))
                } else {
                    GameButton(
                        if (term.subtract) {
                            if (italian) "Sottrai" else "Subtract"
                        } else if (italian) "Aggiungi" else "Add",
                        dense = true,
                        selected = term.subtract,
                        onClick = {
                            onChange(expressionFromTerms(terms.toMutableList().also {
                                it[index] = term.copy(subtract = !term.subtract)
                            }))
                        },
                    )
                }
                GuidedFormulaOperand(
                    term.expression,
                    numericRules,
                    Modifier.weight(1f),
                ) { changed ->
                    onChange(expressionFromTerms(terms.toMutableList().also {
                        it[index] = term.copy(expression = changed)
                    }))
                }
                if (terms.size > 1) {
                    GameButton(
                        if (italian) "Rimuovi" else "Remove",
                        dense = true,
                        accent = Palette.Enemy,
                        onClick = { onChange(expressionFromTerms(terms.filterIndexed { item, _ -> item != index })) },
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GameButton(
                if (italian) "+ Numero" else "+ Number",
                dense = true,
                onClick = { onChange(expressionFromTerms(terms + SignedFormulaTerm(false,
                    RuleFormula.NumberExpression(BigDecimal.ZERO)))) },
            )
            GameButton(
                if (italian) "+ Valore di una regola" else "+ Rule value",
                dense = true,
                enabled = numericRules.isNotEmpty(),
                onClick = { numericRules.firstOrNull()?.let { candidate ->
                    onChange(expressionFromTerms(terms + SignedFormulaTerm(false,
                        RuleFormula.ValueExpression(candidate.id()))))
                } },
            )
        }
    }
}

@Composable
private fun GuidedFormulaOperand(
    expression: RuleFormula.Expression,
    numericRules: List<RuleEntity>,
    modifier: Modifier = Modifier,
    onChange: (RuleFormula.Expression) -> Unit,
) {
    val italian = strings.language.tag == "it"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        when (expression) {
            is RuleFormula.NumberExpression -> {
                var raw by remember(expression.value()) {
                    mutableStateOf(expression.value().stripTrailingZeros().toPlainString())
                }
                RuleTextField(
                    raw,
                    { changed ->
                        raw = changed
                        changed.toBigDecimalOrNull()?.let { onChange(RuleFormula.NumberExpression(it)) }
                    },
                    if (italian) "Numero" else "Number",
                    Modifier.fillMaxWidth(),
                    errorMessage = if (raw.toBigDecimalOrNull() == null) {
                        if (italian) "Inserisci un numero valido." else "Enter a valid number."
                    } else null,
                )
                GameButton(
                    if (italian) "Usa il valore di una regola" else "Use a rule value",
                    dense = true,
                    enabled = numericRules.isNotEmpty(),
                    onClick = { numericRules.firstOrNull()?.let {
                        onChange(RuleFormula.ValueExpression(it.id()))
                    } },
                )
            }
            is RuleFormula.ValueExpression -> {
                val contextChoice = formulaContextChoices(italian).firstOrNull { it.first == expression.id() }
                if (contextChoice != null) {
                    Text(contextChoice.second, color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
                } else {
                    ReferenceButtons(numericRules, setOf(expression.id())) {
                        onChange(RuleFormula.ValueExpression(it))
                    }
                }
                GameButton(
                    if (italian) "Usa un numero" else "Use a number",
                    dense = true,
                    onClick = { onChange(RuleFormula.NumberExpression(BigDecimal.ZERO)) },
                )
            }
            else -> Text(
                if (italian) "Parte avanzata protetta" else "Protected advanced part",
                color = Palette.Bloodied,
            )
        }
    }
}

@Composable
private fun GuidedConditionEditor(
    expression: RuleFormula.Expression,
    numericRules: List<RuleEntity>,
    onChange: (RuleFormula.Expression) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val conditionDescription = (expression as? RuleFormula.NumberExpression)?.let { constant ->
        if (constant.value().compareTo(BigDecimal.ZERO) == 0) {
            if (italian) "Condizione: mai" else "Condition: never"
        } else if (italian) "Condizione: sempre" else "Condition: always"
    } ?: naturalFormulaDescription(expression, numericRules, italian)
    Text(conditionDescription, color = Palette.Text,
        style = OnfallTheme.typography.bodyEmphasis)
    val comparison = expression as? RuleFormula.BinaryExpression
    val supportedComparison = comparison?.takeIf { it.operator() in setOf("<", "<=", ">", ">=", "==", "!=") }
    if (supportedComparison != null) {
        GuidedFormulaOperand(supportedComparison.left(), numericRules, Modifier.fillMaxWidth()) {
            onChange(RuleFormula.BinaryExpression(supportedComparison.operator(), it, supportedComparison.right()))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(
                ">=" to if (italian) "Almeno" else "At least",
                ">" to if (italian) "Più di" else "More than",
                "<=" to if (italian) "Al massimo" else "At most",
                "<" to if (italian) "Meno di" else "Less than",
                "==" to if (italian) "Uguale a" else "Equals",
                "!=" to if (italian) "Diverso da" else "Differs from",
            ).forEach { (operator, label) ->
                GameButton(label, dense = true, selected = supportedComparison.operator() == operator,
                    onClick = { onChange(RuleFormula.BinaryExpression(operator,
                        supportedComparison.left(), supportedComparison.right())) })
            }
        }
        GuidedFormulaOperand(supportedComparison.right(), numericRules, Modifier.fillMaxWidth()) {
            onChange(RuleFormula.BinaryExpression(supportedComparison.operator(), supportedComparison.left(), it))
        }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GameButton(if (italian) "Sempre" else "Always", dense = true,
                onClick = { onChange(RuleFormula.NumberExpression(BigDecimal.ONE)) })
            GameButton(if (italian) "Mai" else "Never", dense = true,
                onClick = { onChange(RuleFormula.NumberExpression(BigDecimal.ZERO)) })
            GameButton(
                if (italian) "Quando un valore è almeno…" else "When a value is at least…",
                dense = true,
                enabled = numericRules.isNotEmpty(),
                onClick = { numericRules.firstOrNull()?.let {
                    onChange(RuleFormula.BinaryExpression(">=", RuleFormula.ValueExpression(it.id()),
                        RuleFormula.NumberExpression(BigDecimal.ZERO)))
                } },
            )
        }
    }
}

private fun formulaContextChoices(italian: Boolean): List<Pair<String, String>> = listOf(
    "score" to if (italian) "Punteggio" else "Score",
    "current" to if (italian) "Valore attuale" else "Current value",
    "maximum" to if (italian) "Valore massimo" else "Maximum value",
    "amount" to if (italian) "Quantità" else "Amount",
    "stacks" to if (italian) "Accumuli" else "Stacks",
    "eventCount" to if (italian) "Numero di attivazioni" else "Event count",
    "roll" to if (italian) "Risultato del tiro" else "Roll result",
    "level" to if (italian) "Livello" else "Level",
    "characterLevel" to if (italian) "Livello del personaggio" else "Character level",
    "classLevel" to if (italian) "Livello nella classe" else "Class level",
    "proficiency" to if (italian) "Bonus di competenza" else "Proficiency bonus",
    "experience" to if (italian) "Esperienza" else "Experience",
)

internal fun naturalFormulaDescription(
    expression: RuleFormula.Expression,
    numericRules: List<RuleEntity>,
    italian: Boolean,
): String {
    fun valueName(id: String): String = numericRules.firstOrNull { it.id() == id }
        ?.name()?.text(if (italian) "it" else "en")
        ?: formulaContextChoices(italian).firstOrNull { it.first == id }?.second
        ?: if (italian) "un valore collegato" else "a linked value"
    fun describe(node: RuleFormula.Expression): String = when (node) {
        is RuleFormula.NumberExpression -> node.value().stripTrailingZeros().toPlainString()
        is RuleFormula.ValueExpression -> valueName(node.id())
        is RuleFormula.UnaryExpression -> if (node.operator() == "-") {
            if (italian) "meno ${describe(node.operand())}" else "minus ${describe(node.operand())}"
        } else describe(node.operand())
        is RuleFormula.BinaryExpression -> {
            val connector = when (node.operator()) {
                "+" -> if (italian) "più" else "plus"
                "-" -> if (italian) "meno" else "minus"
                "*" -> if (italian) "moltiplicato per" else "multiplied by"
                "/" -> if (italian) "diviso per" else "divided by"
                ">=" -> if (italian) "è almeno" else "is at least"
                ">" -> if (italian) "è maggiore di" else "is greater than"
                "<=" -> if (italian) "è al massimo" else "is at most"
                "<" -> if (italian) "è minore di" else "is less than"
                "==" -> if (italian) "è uguale a" else "equals"
                "!=" -> if (italian) "è diverso da" else "differs from"
                "&&" -> if (italian) "e" else "and"
                "||" -> if (italian) "oppure" else "or"
                else -> node.operator()
            }
            "${describe(node.left())} $connector ${describe(node.right())}"
        }
        is RuleFormula.FunctionExpression -> when (node.name()) {
            "min" -> if (italian) "il minore tra ${node.arguments().joinToString(" e ") { describe(it) }}" else
                "the least of ${node.arguments().joinToString(" and ") { describe(it) }}"
            "max" -> if (italian) "il maggiore tra ${node.arguments().joinToString(" e ") { describe(it) }}" else
                "the greatest of ${node.arguments().joinToString(" and ") { describe(it) }}"
            "round" -> if (italian) "${describe(node.arguments().first())}, arrotondato" else
                "${describe(node.arguments().first())}, rounded"
            "floor" -> if (italian) "${describe(node.arguments().first())}, arrotondato in basso" else
                "${describe(node.arguments().first())}, rounded down"
            "ceil" -> if (italian) "${describe(node.arguments().first())}, arrotondato in alto" else
                "${describe(node.arguments().first())}, rounded up"
            "clamp" -> if (italian) "${describe(node.arguments().first())}, entro i limiti scelti" else
                "${describe(node.arguments().first())}, within the chosen limits"
            else -> if (italian) "un calcolo avanzato" else "an advanced calculation"
        }
    }
    return if (italian) "Risultato: ${describe(expression)}" else "Result: ${describe(expression)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormulaBlockToolbar(
    expression: RuleFormula.Expression,
    numericRules: List<RuleEntity>,
    booleanResult: Boolean,
    onChange: (RuleFormula.Expression) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val firstReference = numericRules.firstOrNull()?.id() ?: "score"
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        GameButton(if (italian) "Numero" else "Number", dense = true,
            onClick = { onChange(RuleFormula.NumberExpression(BigDecimal.ZERO)) })
        GameButton(if (italian) "Valore di una regola" else "Rule value", dense = true,
            onClick = { onChange(RuleFormula.ValueExpression(firstReference)) })
        GameButton(if (italian) "+ parte" else "+ part", dense = true,
            onClick = {
                onChange(RuleFormula.BinaryExpression(
                    "+", expression, RuleFormula.NumberExpression(BigDecimal.ZERO),
                ))
            })
        GameButton(if (italian) "× fattore" else "× factor", dense = true,
            onClick = {
                onChange(RuleFormula.BinaryExpression(
                    "*", expression, RuleFormula.NumberExpression(BigDecimal.ONE),
                ))
            })
        GameButton(if (italian) "Arrotonda" else "Round", dense = true,
            onClick = { onChange(RuleFormula.FunctionExpression("round", listOf(expression), "")) })
        GameButton(if (italian) "Non sotto" else "At least", dense = true,
            onClick = {
                onChange(RuleFormula.FunctionExpression(
                    "max", listOf(expression, RuleFormula.NumberExpression(BigDecimal.ZERO)), "",
                ))
            })
        GameButton(if (italian) "Non sopra" else "At most", dense = true,
            onClick = {
                onChange(RuleFormula.FunctionExpression(
                    "min", listOf(expression, RuleFormula.NumberExpression(BigDecimal.TEN)), "",
                ))
            })
        GameButton(if (italian) "Tra due limiti" else "Between limits", dense = true,
            onClick = {
                onChange(RuleFormula.FunctionExpression(
                    "clamp",
                    listOf(
                        expression,
                        RuleFormula.NumberExpression(BigDecimal.ZERO),
                        RuleFormula.NumberExpression(BigDecimal.TEN),
                    ),
                    "",
                ))
            })
        if (booleanResult) {
            GameButton(if (italian) "Confronta" else "Compare", dense = true,
                onClick = {
                    onChange(RuleFormula.BinaryExpression(
                        ">=", expression, RuleFormula.NumberExpression(BigDecimal.ZERO),
                    ))
                })
            GameButton(if (italian) "E anche" else "And also", dense = true,
                onClick = {
                    onChange(RuleFormula.BinaryExpression(
                        "&&", expression, RuleFormula.NumberExpression(BigDecimal.ONE),
                    ))
                })
            GameButton(if (italian) "Oppure" else "Or", dense = true,
                onClick = {
                    onChange(RuleFormula.BinaryExpression(
                        "||", expression, RuleFormula.NumberExpression(BigDecimal.ZERO),
                    ))
                })
            GameButton(if (italian) "Non" else "Not", dense = true,
                onClick = { onChange(RuleFormula.UnaryExpression("!", expression)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormulaNodeEditor(
    expression: RuleFormula.Expression,
    numericRules: List<RuleEntity>,
    depth: Int,
    onChange: (RuleFormula.Expression) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val nestedModifier = Modifier.fillMaxWidth()
        .background(Palette.Night.copy(alpha = .55f), RoundedCornerShape(6.dp))
        .border(1.dp, Palette.Line.copy(alpha = .8f), RoundedCornerShape(6.dp))
        .padding(7.dp)
    if (depth >= 7) {
        Text(
            if (italian) "Blocco annidato protetto" else "Protected nested block",
            color = Palette.Bloodied,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    when (expression) {
        is RuleFormula.NumberExpression -> {
            var raw by remember(expression.value().toPlainString()) {
                mutableStateOf(expression.value().toPlainString())
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                RuleTextField(
                    raw,
                    { changed ->
                        raw = changed
                        changed.toBigDecimalOrNull()?.let { onChange(RuleFormula.NumberExpression(it)) }
                    },
                    if (italian) "Numero" else "Number",
                    Modifier.fillMaxWidth(),
                )
                GameButton(
                    if (italian) "Usa il valore di una regola" else "Use a rule value",
                    dense = true,
                    onClick = {
                        onChange(RuleFormula.ValueExpression(numericRules.firstOrNull()?.id() ?: "score"))
                    },
                )
            }
        }
        is RuleFormula.ValueExpression -> Column(nestedModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (italian) "Prendi il valore da" else "Take the value from",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            val specialChoices = formulaContextChoices(italian)
            val special = specialChoices.map { it.first }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                specialChoices.forEach { (id, label) ->
                    GameButton(label, dense = true, selected = expression.id() == id,
                        onClick = { onChange(RuleFormula.ValueExpression(id)) })
                }
            }
            ReferenceButtons(numericRules, setOf(expression.id())) {
                onChange(RuleFormula.ValueExpression(it))
            }
            if (expression.id() !in special && numericRules.none { it.id() == expression.id() }) {
                Text(
                    (if (italian) "Riferimento conservato: " else "Preserved reference: ") + expression.id(),
                    color = Palette.Bloodied,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            GameButton(if (italian) "Usa un numero" else "Use a number", dense = true,
                onClick = { onChange(RuleFormula.NumberExpression(BigDecimal.ZERO)) })
        }
        is RuleFormula.UnaryExpression -> Column(nestedModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when (expression.operator()) {
                    "-" -> if (italian) "Cambia segno" else "Invert sign"
                    "!" -> if (italian) "Non è vero che" else "It is not true that"
                    else -> if (italian) "Operazione avanzata protetta" else "Protected advanced operation"
                },
                color = Palette.Text,
                style = MaterialTheme.typography.bodySmall,
            )
            FormulaNodeEditor(expression.operand(), numericRules, depth + 1) {
                onChange(RuleFormula.UnaryExpression(expression.operator(), it))
            }
            GameButton(if (italian) "Rimuovi operazione" else "Remove operation", dense = true,
                onClick = { onChange(expression.operand()) })
        }
        is RuleFormula.BinaryExpression -> Column(nestedModifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(if (italian) "Combina due valori" else "Combine two values",
                color = Palette.Text, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("+", "-", "*", "/", "%").forEach { operator ->
                    GameButton(operator, dense = true, selected = expression.operator() == operator,
                        onClick = { onChange(RuleFormula.BinaryExpression(operator, expression.left(), expression.right())) })
                }
            }
            if (expression.operator() in setOf("<", "<=", ">", ">=", "==", "!=")) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    listOf("<", "<=", ">", ">=", "==", "!=").forEach { operator ->
                        GameButton(operator, dense = true, selected = expression.operator() == operator,
                            onClick = {
                                onChange(RuleFormula.BinaryExpression(
                                    operator, expression.left(), expression.right(),
                                ))
                            })
                    }
                }
            } else if (expression.operator() in setOf("&&", "||")) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    listOf("&&" to (if (italian) "E" else "And"),
                        "||" to (if (italian) "Oppure" else "Or")).forEach { (operator, label) ->
                        GameButton(label, dense = true, selected = expression.operator() == operator,
                            onClick = {
                                onChange(RuleFormula.BinaryExpression(
                                    operator, expression.left(), expression.right(),
                                ))
                            })
                    }
                }
            } else if (expression.operator() !in setOf("+", "-", "*", "/", "%")) {
                Text(
                    if (italian) "Confronto o logica protetti" else "Protected comparison or logic",
                    color = Palette.Bloodied,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(if (italian) "Primo valore" else "First value", color = Palette.TextFaint,
                style = MaterialTheme.typography.labelSmall)
            FormulaNodeEditor(expression.left(), numericRules, depth + 1) {
                onChange(RuleFormula.BinaryExpression(expression.operator(), it, expression.right()))
            }
            Text(if (italian) "Secondo valore" else "Second value", color = Palette.TextFaint,
                style = MaterialTheme.typography.labelSmall)
            FormulaNodeEditor(expression.right(), numericRules, depth + 1) {
                onChange(RuleFormula.BinaryExpression(expression.operator(), expression.left(), it))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                GameButton(if (italian) "Usa il primo" else "Use first", dense = true,
                    onClick = { onChange(expression.left()) })
                GameButton(if (italian) "Usa il secondo" else "Use second", dense = true,
                    onClick = { onChange(expression.right()) })
            }
        }
        is RuleFormula.FunctionExpression -> {
            val editableFunctions = setOf("min", "max", "clamp", "abs", "floor", "ceil", "round")
            if (expression.name() !in editableFunctions) {
                Column(nestedModifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        if (italian) "Blocco avanzato protetto: ${expression.name()}" else
                            "Protected advanced block: ${expression.name()}",
                        color = Palette.Bloodied,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (italian) "Il contenuto resta invariato finché non usi Esperto." else
                            "Its contents stay unchanged until you use Expert.",
                        color = Palette.TextFaint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Column(nestedModifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(functionLabel(expression.name(), italian), color = Palette.Text,
                        style = MaterialTheme.typography.bodySmall)
                    expression.arguments().forEachIndexed { index, argument ->
                        FormulaNodeEditor(argument, numericRules, depth + 1) { changed ->
                            val arguments = expression.arguments().toMutableList().also { it[index] = changed }
                            onChange(RuleFormula.FunctionExpression(expression.name(), arguments, expression.textArgument()))
                        }
                    }
                    expression.arguments().firstOrNull()?.let { child ->
                        GameButton(if (italian) "Rimuovi funzione" else "Remove function", dense = true,
                            onClick = { onChange(child) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FormulaExample(source: String, booleanResult: Boolean, numericRules: List<RuleEntity>) {
    val italian = strings.language.tag == "it"
    val compiled = runCatching { RuleFormula.compile(source) }.getOrNull() ?: return
    val references = compiled.valueReferences().sorted().take(8)
    var values by remember(references, numericRules.map { it.id() to it.attributes() }) {
        mutableStateOf(references.associateWith { reference -> exampleValue(reference, numericRules) })
    }
    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .55f), RoundedCornerShape(6.dp))
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            if (italian) "Prova con un esempio" else "Try an example",
            color = Palette.Heal,
            style = OnfallTheme.typography.supportingEmphasis,
        )
        references.forEach { reference ->
            val label = numericRules.firstOrNull { it.id() == reference }?.name()?.text(strings.language.tag)
                ?: formulaContextChoices(italian).firstOrNull { it.first == reference }?.second
                ?: if (italian) "Valore collegato" else "Linked value"
            RuleTextField(
                values[reference].orEmpty(),
                { changed -> values = values + (reference to changed) },
                label,
                Modifier.fillMaxWidth(),
            )
        }
        if (compiled.tableReferences().isNotEmpty()) {
            Text(
                if (italian) {
                    "Il risultato dipende anche da una tabella; la formula resta comunque modificabile a blocchi."
                } else {
                    "The result also depends on a table; the formula can still be edited as blocks."
                },
                color = Palette.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val numericValues = values.mapValues { (_, value) -> value.toBigDecimalOrNull() }
            val result = if (numericValues.values.all { it != null }) {
                runCatching {
                    compiled.evaluate(RuleFormula.context(
                        numericValues.mapValues { it.value ?: BigDecimal.ZERO },
                        emptyMap(),
                    ))
                }.getOrNull()
            } else {
                null
            }
            Text(
                when {
                    result != null && booleanResult -> (if (italian) "Risultato: " else "Result: ") +
                        if (result.compareTo(BigDecimal.ZERO) != 0) {
                            if (italian) "vero" else "true"
                        } else {
                            if (italian) "falso" else "false"
                        }
                    result != null -> (if (italian) "Risultato: " else "Result: ") + result.toPlainString()
                    italian -> "Inserisci numeri validi per vedere il risultato."
                    else -> "Enter valid numbers to see the result."
                },
                color = if (result != null) Palette.Heal else Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun exampleValue(reference: String, numericRules: List<RuleEntity>): String {
    val contextual = when (reference) {
        "level", "characterLevel", "classLevel" -> "1"
        "proficiency" -> "2"
        "current" -> "5"
        "maximum" -> "10"
        "amount", "stacks", "eventCount" -> "1"
        "roll", "score" -> "10"
        else -> null
    }
    if (contextual != null) return contextual
    val entity = numericRules.firstOrNull { it.id() == reference } ?: return "0"
    val candidate = when (entity.kind()) {
        RuleKind.VALUE -> entity.attributes()["defaultValue"]
        RuleKind.RESOURCE, RuleKind.TRACK -> entity.attributes()["maximum"]
            ?: entity.attributes()["maximumFormula"]
        else -> entity.attributes()["default"] ?: entity.attributes()["defaultFormula"]
    } ?: return "0"
    val compiled = runCatching { RuleFormula.compile(candidate) }.getOrNull() ?: return candidate.toBigDecimalOrNull()
        ?.stripTrailingZeros()?.toPlainString() ?: "0"
    if (compiled.valueReferences().isNotEmpty() || compiled.tableReferences().isNotEmpty()) return "0"
    return runCatching {
        compiled.evaluate(RuleFormula.context(emptyMap(), emptyMap())).stripTrailingZeros().toPlainString()
    }.getOrDefault("0")
}

private fun formulaFieldLabel(key: String, italian: Boolean): String = when (key) {
    "derivedFormula", "formula", "default", "defaultFormula" -> if (italian) "Calcolo principale" else "Main calculation"
    "modifierFormula" -> if (italian) "Modificatore prodotto" else "Produced modifier"
    "maximumFormula" -> if (italian) "Valore massimo" else "Maximum value"
    "minimumFormula" -> if (italian) "Valore minimo" else "Minimum value"
    "initialFormula" -> if (italian) "Valore iniziale" else "Starting value"
    "conditionFormula" -> if (italian) "Condizione" else "Condition"
    "targetFormula" -> if (italian) "Difficoltà" else "Target"
    "totalFormula" -> if (italian) "Totale" else "Total"
    else -> if (italian) "Calcolo" else "Calculation"
}

private fun functionLabel(name: String, italian: Boolean): String = when (name) {
    "min" -> if (italian) "Prendi il minore" else "Use the lowest"
    "max" -> if (italian) "Prendi il maggiore" else "Use the highest"
    "clamp" -> if (italian) "Mantieni entro i limiti" else "Keep within limits"
    "abs" -> if (italian) "Valore assoluto" else "Absolute value"
    "floor" -> if (italian) "Arrotonda per difetto" else "Round down"
    "ceil" -> if (italian) "Arrotonda per eccesso" else "Round up"
    "round" -> if (italian) "Arrotonda al più vicino" else "Round to nearest"
    else -> name
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModifierSchemaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    onAttribute: (String, String) -> Unit,
) {
    val language = strings.language
    val ownerRef = attributes["ownerRef"].orEmpty()
    var targetSearch by remember(entityId) { mutableStateOf("") }
    val query = targetSearch.trim().lowercase()
    val candidates = viewModel.selected?.revision?.entities().orEmpty()
        .asSequence()
        .filter {
            it.enabled() && it.id() != entityId &&
                (it.kind() in modifierOwnerKinds ||
                    (it.kind() == RuleKind.CUSTOM && it.attributes().containsKey("elementKind")))
        }
        .filter {
            query.isEmpty() ||
                it.id().lowercase().contains(query) ||
                it.name().text(language.tag).lowercase().contains(query)
        }
        .sortedWith(compareBy<RuleEntity> { it.kind() != RuleKind.CLASS }
            .thenBy { it.name().text(language.tag).lowercase() })
        .take(12)
        .toList()
    val selectedOwner = viewModel.selected?.revision?.entity(ownerRef)

    Column(
        Modifier.fillMaxWidth()
            .background(Palette.Abyss.copy(alpha = .7f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(if (language.tag == "it") "Collegamento eseguibile" else "Executable link", Palette.Heal)
        Text(
            if (selectedOwner == null) {
                if (language.tag == "it") "Scegli la classe, il privilegio o l'azione che concede il modificatore."
                else "Choose the class, feature, or action that grants this modifier."
            } else {
                (if (language.tag == "it") "Collegato a: " else "Linked to: ") +
                    selectedOwner.name().text(language.tag)
            },
            color = if (selectedOwner == null) Palette.Critical else Palette.Text,
            style = MaterialTheme.typography.bodySmall,
        )
        RuleTextField(
            targetSearch,
            { targetSearch = it },
            if (language.tag == "it") "Cerca una regola da collegare" else "Search for a rule to link",
            Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            candidates.forEach { candidate ->
                GameButton(
                    candidate.name().text(language.tag),
                    dense = true,
                    selected = candidate.id() == ownerRef,
                    subtitle = kindLabel(candidate.kind()),
                    onClick = {
                        onAttribute("ownerRef", candidate.id())
                        if (candidate.kind() != RuleKind.CLASS) onAttribute("minimumLevel", "1")
                    },
                )
            }
        }
        Eyebrow(if (language.tag == "it") "Valore modificato" else "Modified value")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            EffectTarget.entries.forEach { target ->
                GameButton(
                    target.label(language),
                    dense = true,
                    selected = attributes["target"] == target.name,
                    onClick = { onAttribute("target", target.name) },
                )
            }
        }
        NumericControl(
            if (language.tag == "it") "Modificatore" else "Modifier",
            attributes["amount"]?.toIntOrNull() ?: 1,
            -100,
            100,
        ) { onAttribute("amount", it.toString()) }
        if (selectedOwner?.kind() == RuleKind.CLASS) {
            NumericControl(
                if (language.tag == "it") "Dal livello di classe" else "From class level",
                attributes["minimumLevel"]?.toIntOrNull() ?: 1,
                1,
                999,
            ) { onAttribute("minimumLevel", it.toString()) }
        } else if (selectedOwner != null) {
            Text(
                if (language.tag == "it") {
                    "Il modificatore dell'elemento è attivo quando quella regola è acquisita o usata."
                } else {
                    "The element modifier is active when that rule is acquired or used."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Eyebrow(if (language.tag == "it") "Condizione" else "Condition")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            EffectCondition.entries.forEach { condition ->
                GameButton(
                    condition.label(language).ifBlank {
                        if (language.tag == "it") "Sempre" else "Always"
                    },
                    dense = true,
                    selected = attributes["condition"] == condition.name,
                    onClick = { onAttribute("condition", condition.name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenericModifierSchemaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val application = attributes["application"].orEmpty().ifBlank { "STATIC" }
    val targetKinds = when (application) {
        "CHANGE_RESOURCE" -> setOf(RuleKind.RESOURCE, RuleKind.TRACK)
        "ADD_CONDITION", "REMOVE_CONDITION" -> setOf(RuleKind.CONDITION)
        "SET_VALUE" -> setOf(RuleKind.VALUE)
        else -> setOf(RuleKind.STAT, RuleKind.SKILL, RuleKind.SAVE, RuleKind.DEFENSE, RuleKind.VALUE)
    }
    val entities = viewModel.selected?.revision?.entities().orEmpty().filter {
        it.enabled() && it.id() != entityId
    }
    val numericEntities = entities.filter { it.kind() in setOf(
        RuleKind.STAT, RuleKind.SKILL, RuleKind.SAVE, RuleKind.DEFENSE,
        RuleKind.VALUE, RuleKind.RESOURCE, RuleKind.TRACK,
    ) }
    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .7f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(if (italian) "Effetto universale eseguibile" else "Executable universal effect", Palette.Party)
        Text(
            if (italian) {
                "Scegli cosa cambia, chi riceve l'effetto e di quanto."
            } else {
                "Choose what changes, who receives the effect, and by how much."
            },
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Eyebrow(if (italian) "Applicazione" else "Application")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("STATIC", "CHANGE_VALUE", "SET_VALUE", "CHANGE_RESOURCE", "ADD_CONDITION", "REMOVE_CONDITION")
                .forEach { candidate ->
                    GameButton(effectApplicationLabel(candidate, italian),
                        dense = true, selected = application == candidate,
                        onClick = {
                            onAttribute("application", candidate)
                            if (candidate == "STATIC") onAttribute("recipient", "SELF")
                            onAttribute("targetRef", "")
                        })
                }
        }
        if (application != "STATIC") {
            Eyebrow(if (italian) "Destinatario dell'effetto" else "Effect recipient")
            Text(
                if (italian) {
                    "Chi lo usa, il bersaglio scelto oppure lo stato condiviso."
                } else {
                    "The user, the chosen target, or shared session state."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                listOf("SELF", "TARGET", "SESSION").forEach { recipient ->
                    GameButton(
                        effectRecipientLabel(recipient, italian),
                        dense = true,
                        selected = attributes["recipient"].orEmpty().ifBlank { "SELF" } == recipient,
                        onClick = { onAttribute("recipient", recipient) },
                    )
                }
            }
        }
        Eyebrow(if (italian) "Regola che concede l'effetto" else "Rule granting the effect")
        ReferenceButtons(
            entities = entities,
            selected = setOf(attributes["ownerRef"].orEmpty()),
            onSelect = { onAttribute("ownerRef", it) },
        )
        Eyebrow(if (italian) "Valore bersaglio" else "Target value")
        ReferenceButtons(
            entities = entities.filter { candidate ->
                candidate.kind() in targetKinds && when (application) {
                    "STATIC" -> candidate.kind() != RuleKind.VALUE ||
                        candidate.attributes()["valueType"].orEmpty().ifBlank { "TEXT" } == "NUMBER"
                    "CHANGE_VALUE" -> candidate.kind() != RuleKind.VALUE ||
                        (candidate.attributes()["valueType"].orEmpty().ifBlank { "TEXT" } == "NUMBER" &&
                            candidate.attributes()["mutable"].orEmpty().ifBlank { "true" } != "false")
                    "SET_VALUE" -> candidate.attributes()["mutable"].orEmpty().ifBlank { "true" } != "false"
                    else -> true
                }
            },
            selected = setOf(attributes["targetRef"].orEmpty()),
            onSelect = { onAttribute("targetRef", it) },
        )
        if (application in setOf("STATIC", "CHANGE_VALUE", "CHANGE_RESOURCE")) {
            Eyebrow(if (italian) "Operazione" else "Operation")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("ADD", "MULTIPLY", "SET", "MINIMUM", "MAXIMUM").forEach { operation ->
                    GameButton(effectOperationLabel(operation, italian), dense = true,
                        selected = attributes["operation"].orEmpty().ifBlank { "ADD" } == operation,
                        onClick = { onAttribute("operation", operation) })
                }
            }
        }
        if (application !in setOf("SET_VALUE", "REMOVE_CONDITION")) {
            FormulaFieldEditor(
                if (italian) {
                    if (application == "ADD_CONDITION") "Numero di accumuli" else "Quantità dell'effetto"
                } else {
                    if (application == "ADD_CONDITION") "Number of stacks" else "Effect amount"
                },
                attributes["valueFormula"] ?: attributes["amount"] ?: "0",
                numericEntities,
            ) { onAttribute("valueFormula", it) }
        }
        if (application == "SET_VALUE") {
            Eyebrow(if (italian) "Valore tipizzato" else "Typed value")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                RuleValue.Type.entries.forEach { type ->
                    GameButton(
                        type.name.lowercase().replaceFirstChar(Char::uppercase),
                        dense = true,
                        selected = attributes["valueType"].orEmpty().ifBlank { "TEXT" } == type.name,
                        onClick = {
                            onAttribute("valueType", type.name)
                            onAttribute(
                                "valueLiteral",
                                when (type) {
                                    RuleValue.Type.NUMBER -> "0"
                                    RuleValue.Type.BOOLEAN -> "false"
                                    RuleValue.Type.TEXT, RuleValue.Type.REFERENCE -> ""
                                },
                            )
                        },
                    )
                }
            }
            val selectedType = runCatching {
                RuleValue.Type.valueOf(attributes["valueType"].orEmpty().ifBlank { "TEXT" })
            }.getOrDefault(RuleValue.Type.TEXT)
            if (selectedType == RuleValue.Type.REFERENCE) {
                ReferenceButtons(
                    entities = entities,
                    selected = setOf(attributes["valueLiteral"].orEmpty()),
                    onSelect = { onAttribute("valueLiteral", it) },
                )
            } else {
                RuleTextField(
                    attributes["valueLiteral"].orEmpty(),
                    { onAttribute("valueLiteral", it) },
                    if (italian) "Valore da assegnare" else "Value to assign",
                    Modifier.fillMaxWidth(),
                )
            }
        }
        val conditionSource = attributes["conditionFormula"].orEmpty().ifBlank { "1" }
        if (showAdvanced) {
            FormulaFieldEditor(
                if (italian) "Si applica quando" else "Applies when",
                conditionSource,
                numericEntities,
                booleanResult = true,
            ) { onAttribute("conditionFormula", it) }
            AttributeChoices(
                if (italian) "Come si combina con effetti simili" else "How it combines with similar effects",
                "stacking",
                listOf("STACK", "HIGHEST_PRIORITY", "LOWEST_PRIORITY", "REPLACE_BY_SOURCE"),
                attributes + ("stacking" to attributes["stacking"].orEmpty().ifBlank {
                    if (attributes["group"].orEmpty().isBlank()) "STACK" else "HIGHEST_PRIORITY"
                }),
                onAttribute,
            )
            AttributeChoices(
                if (italian) "Momento di applicazione" else "Application phase",
                "phase",
                listOf("BASE", "ADDITIVE", "MULTIPLICATIVE", "CLAMP", "OVERRIDE", "LEGACY"),
                attributes + ("phase" to attributes["phase"].orEmpty().ifBlank { "LEGACY" }),
                onAttribute,
            )
            AttributeTextField(
                attributes,
                "group",
                if (italian) "Gruppo di combinazione (facoltativo)" else "Stacking group (optional)",
                onAttribute,
            )
        } else if (conditionSource == "1") {
            Text(
                if (italian) "Si applica sempre." else "Always applies.",
                color = Palette.Heal,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                if (italian) {
                    "Ha una condizione avanzata conservata. Apri Blocchi per modificarla."
                } else {
                    "It has a preserved advanced condition. Open Blocks to edit it."
                },
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun effectApplicationLabel(value: String, italian: Boolean): String = when (value) {
    "STATIC" -> if (italian) "Modifica un valore" else "Modify a value"
    "CHANGE_VALUE" -> if (italian) "Cambia un valore durante il gioco" else "Change a value during play"
    "SET_VALUE" -> if (italian) "Assegna un valore" else "Assign a value"
    "CHANGE_RESOURCE" -> if (italian) "Cambia una risorsa" else "Change a resource"
    "ADD_CONDITION" -> if (italian) "Aggiunge una condizione" else "Add a condition"
    "REMOVE_CONDITION" -> if (italian) "Rimuove una condizione" else "Remove a condition"
    else -> value
}

private fun effectRecipientLabel(value: String, italian: Boolean): String = when (value) {
    "SELF" -> if (italian) "Chi lo usa" else "User"
    "TARGET" -> if (italian) "Bersaglio" else "Target"
    "SESSION" -> if (italian) "Sessione" else "Session"
    else -> value
}

private fun effectOperationLabel(value: String, italian: Boolean): String = when (value) {
    "ADD" -> if (italian) "Somma" else "Add"
    "MULTIPLY" -> if (italian) "Moltiplica" else "Multiply"
    "SET" -> if (italian) "Sostituisci" else "Replace"
    "MINIMUM" -> if (italian) "Imponi un minimo" else "Set a minimum"
    "MAXIMUM" -> if (italian) "Imponi un massimo" else "Set a maximum"
    else -> value
}

private data class ReferenceField(
    val key: String,
    val kinds: Set<RuleKind>,
    val multiple: Boolean = false,
)

@Composable
private fun ProgressionSchemaEditor(
    viewModel: RulesViewModel,
    entityId: String,
    attributes: Map<String, String>,
    showAdvanced: Boolean,
    onAttribute: (String, String) -> Unit,
    onRemoveAttribute: (String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val tables = viewModel.selected?.revision?.entities().orEmpty().filter {
        it.enabled() && it.id() != entityId && it.kind() == RuleKind.TABLE
    }
    val tracks = attributes.entries.filter { it.key.startsWith("track.") }
        .sortedBy { it.key }
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(if (italian) "Livelli e avanzamenti" else "Levels and advancements", Palette.Heal)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                NumericControl(
                    if (italian) "Livello iniziale" else "Starting level",
                    attributes["minimumLevel"]?.toIntOrNull() ?: 1,
                    0,
                    1_000_000,
                ) { onAttribute("minimumLevel", it.toString()) }
            }
            Column(Modifier.weight(1f)) {
                NumericControl(
                    if (italian) "Livello massimo" else "Maximum level",
                    (attributes["maximumLevel"] ?: attributes["maximumCharacterLevel"])?.toIntOrNull() ?: 20,
                    (attributes["minimumLevel"]?.toIntOrNull() ?: 1).coerceAtLeast(0),
                    1_000_000,
                ) {
                    onAttribute("maximumLevel", it.toString())
                    onAttribute("maximumCharacterLevel", "")
                }
            }
        }
        Text(if (italian) "Tabella esperienza (facoltativa)" else "Experience table (optional)",
            color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        ReferenceButtons(tables, setOf(attributes["experienceTableRef"].orEmpty())) {
            onAttribute("experienceTableRef", it)
        }
        if (!attributes["experienceTableRef"].isNullOrBlank()) {
            AttributeCheckBox(
                attributes,
                "defaultExperience",
                if (italian) "Usa come curva esperienza predefinita" else "Use as default experience curve",
                onAttribute,
            )
        } else if (attributes["defaultExperience"]?.toBooleanStrictOrNull() == true) {
            Text(
                if (italian) "Scegli una tabella esperienza per mantenere questa curva come predefinita." else
                    "Choose an experience table to keep this as the default curve.",
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(if (italian) "Avanzamenti per livello" else "Per-level advancements",
            color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        tracks.forEach { (key, tableRef) ->
            val trackId = key.removePrefix("track.")
            var trackIdDraft by remember(key) { mutableStateOf(trackId) }
            Column(
                Modifier.fillMaxWidth().border(1.dp, Palette.Line, RoundedCornerShape(7.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showAdvanced) {
                    RuleTextField(
                        trackIdDraft,
                        { trackIdDraft = it },
                        if (italian) "Nome avanzamento" else "Advancement name",
                        Modifier.fillMaxWidth(),
                    )
                    val normalized = trackIdDraft.trim()
                    GameButton(
                        if (italian) "Applica nome" else "Apply name",
                        dense = true,
                        enabled = normalized.isNotBlank() && normalized != trackId &&
                            !attributes.containsKey("track.$normalized"),
                        onClick = {
                            onRemoveAttribute(key)
                            onAttribute("track.$normalized", tableRef)
                        },
                    )
                } else {
                    Text(progressionTrackLabel(trackId, italian), color = Palette.Text,
                        style = OnfallTheme.typography.supportingEmphasis)
                }
                ReferenceButtons(tables, setOf(tableRef)) { onAttribute(key, it) }
                GameButton(
                    if (italian) "Rimuovi avanzamento" else "Remove advancement",
                    dense = true,
                    accent = Palette.Enemy,
                    onClick = { onRemoveAttribute(key) },
                )
            }
        }
        if (tables.isEmpty()) {
            Text(
                if (italian) "Crea prima una tabella: ogni avanzamento legge i valori da lì." else
                    "Create a table first: every advancement reads its values from one.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val commonTracks = listOf("baseAttack", "fortitude", "reflex", "will", "skillPoints", "casterLevel")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                commonTracks.filter { candidate -> attributes["track.$candidate"] == null }.forEach { candidate ->
                    GameButton(
                        (if (italian) "Aggiungi " else "Add ") + progressionTrackLabel(candidate, italian),
                        dense = true,
                        onClick = { onAttribute("track.$candidate", tables.first().id()) },
                    )
                }
                if (showAdvanced) {
                    GameButton(
                        if (italian) "Aggiungi avanzamento personalizzato" else "Add custom advancement",
                        dense = true,
                        onClick = {
                            var index = tracks.size + 1
                            var candidate = "track_$index"
                            while (attributes.containsKey("track.$candidate")) candidate = "track_${++index}"
                            onAttribute("track.$candidate", tables.first().id())
                        },
                    )
                }
            }
        }
    }
}

private fun progressionTrackLabel(id: String, italian: Boolean): String = when (id) {
    "baseAttack" -> if (italian) "attacco base" else "base attack"
    "fortitude", "save.fortitude" -> if (italian) "tempra" else "fortitude"
    "reflex", "save.reflex" -> if (italian) "riflessi" else "reflex"
    "will", "save.will" -> if (italian) "volontà" else "will"
    "skillPoints", "skillPointsGainedAtLevel" -> if (italian) "punti abilità" else "skill points"
    "casterLevel" -> if (italian) "livello incantatore" else "caster level"
    else -> id
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleReferenceEditor(
    viewModel: RulesViewModel,
    entityId: String,
    kind: RuleKind,
    attributes: Map<String, String>,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    val all = viewModel.selected?.revision?.entities().orEmpty().filter { it.enabled() && it.id() != entityId }
    val fields = when (kind) {
        RuleKind.SKILL -> listOf(ReferenceField("statRef", setOf(RuleKind.STAT, RuleKind.SAVE, RuleKind.DEFENSE)))
        RuleKind.CLASS -> listOf(ReferenceField("progressionEntityRef", setOf(RuleKind.PROGRESSION)))
        RuleKind.PROGRESSION -> buildList {
            add(ReferenceField("experienceTableRef", setOf(RuleKind.TABLE)))
            attributes.keys.filter { it.startsWith("track.") && it.length > "track.".length }
                .sorted().forEach { add(ReferenceField(it, setOf(RuleKind.TABLE))) }
        }
        RuleKind.RANDOMIZER -> listOf(ReferenceField("tableRef", setOf(RuleKind.TABLE)))
        RuleKind.ROLL -> if (attributes.containsKey("randomizerRef")) {
            listOf(
                ReferenceField("randomizerRef", setOf(RuleKind.RANDOMIZER, RuleKind.ROLL)),
                ReferenceField("outcomeTableRef", setOf(RuleKind.TABLE)),
                ReferenceField("opposedRollRef", setOf(RuleKind.ROLL)),
            )
        } else {
            listOf(ReferenceField("tableRef", setOf(RuleKind.TABLE)))
        }
        RuleKind.ACTION -> listOf(
            ReferenceField("ownerRef", RuleKind.entries.toSet()),
            ReferenceField("effectRefs", setOf(RuleKind.MODIFIER), multiple = true),
        )
        RuleKind.TRIGGER -> listOf(ReferenceField("effectRefs", setOf(RuleKind.MODIFIER), multiple = true))
        RuleKind.HEALTH_MODEL -> listOf(
            ReferenceField("primaryResourceRef", setOf(RuleKind.RESOURCE, RuleKind.TRACK)),
            ReferenceField("bufferResourceRefs", setOf(RuleKind.RESOURCE, RuleKind.TRACK), multiple = true),
            ReferenceField("zeroConditionRef", setOf(RuleKind.CONDITION)),
            ReferenceField("deathConditionRef", setOf(RuleKind.CONDITION)),
        )
        RuleKind.SHEET_SECTION -> listOf(
            ReferenceField("fieldRefs", RuleKind.entries.toSet() - RuleKind.SHEET_SECTION, multiple = true),
        )
        RuleKind.SCENE_PROCEDURE -> listOf(
            ReferenceField("actionRefs", setOf(RuleKind.ACTION), multiple = true),
            ReferenceField("trackerRefs", setOf(RuleKind.RESOURCE, RuleKind.TRACK), multiple = true),
        )
        RuleKind.VALUE -> if (attributes["valueType"] == "REFERENCE") {
            listOf(
                ReferenceField("defaultValue", RuleKind.entries.toSet()),
                ReferenceField("allowedValues", RuleKind.entries.toSet(), multiple = true),
            )
        } else {
            emptyList()
        }
        else -> emptyList()
    }
    val formulaBearing = attributes.keys.any { it.endsWith("Formula") || it == "budgets" || it == "costs" }
    if (all.isEmpty() && fields.isEmpty() && !formulaBearing) return
    var search by remember(entityId, "references") { mutableStateOf("") }
    val query = search.trim().lowercase()
    val visible = all.filter {
        query.isEmpty() || it.id().lowercase().contains(query) ||
            it.name().text(strings.language.tag).lowercase().contains(query)
    }
    Column(
        Modifier.fillMaxWidth().background(Palette.Night.copy(alpha = .65f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(if (italian) "Riferimenti strutturati" else "Structured references", Palette.Heal)
        RuleTextField(search, { search = it },
            if (italian) "Cerca per nome o tipo" else "Search by name or type", Modifier.fillMaxWidth())
        fields.forEach { field ->
            val selected = attributes[field.key].orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet()
            Text(referenceFieldLabel(field.key, italian), color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall)
            ReferenceButtons(
                entities = visible.filter { it.kind() in field.kinds },
                selected = selected,
                onSelect = { id ->
                    onAttribute(
                        field.key,
                        if (field.multiple) {
                            (if (id in selected) selected - id else selected + id).sorted().joinToString(",")
                        } else {
                            id
                        },
                    )
                },
            )
        }
        val linked = attributes["links"].orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet()
        Text(
            if (italian) "links · collegamenti dichiarativi per qualunque regola" else
                "links · declarative links for any rule",
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        ReferenceButtons(
            entities = visible.take(18),
            selected = linked,
            onSelect = { id ->
                onAttribute("links", (if (id in linked) linked - id else linked + id).sorted().joinToString(","))
            },
        )
        if (formulaBearing) {
            Text(
                if (italian) {
                    "Questa regola contiene calcoli. Le parti non ancora disponibili a blocchi restano in Esperto."
                } else {
                    "This rule contains calculations. Parts not available as blocks yet remain in Expert."
                },
                color = Palette.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun referenceFieldLabel(key: String, italian: Boolean): String = when (key) {
    "statRef" -> if (italian) "Statistica usata" else "Used stat"
    "progressionEntityRef" -> if (italian) "Progressione usata" else "Used progression"
    "experienceTableRef" -> if (italian) "Tabella dell’esperienza" else "Experience table"
    "randomizerRef" -> if (italian) "Dadi usati" else "Used dice"
    "outcomeTableRef" -> if (italian) "Tabella dei risultati" else "Outcome table"
    "opposedRollRef" -> if (italian) "Tiro contrapposto" else "Opposed roll"
    "ownerRef" -> if (italian) "Regola che lo concede" else "Granting rule"
    "effectRefs" -> if (italian) "Effetti applicati" else "Applied effects"
    "primaryResourceRef" -> if (italian) "Risorsa di salute" else "Health resource"
    "bufferResourceRefs" -> if (italian) "Protezioni consumate prima" else "Buffers spent first"
    "zeroConditionRef" -> if (italian) "Stato a zero" else "Zero condition"
    "deathConditionRef" -> if (italian) "Stato di morte" else "Death condition"
    "fieldRefs" -> if (italian) "Campi mostrati" else "Displayed fields"
    "actionRefs" -> if (italian) "Azioni disponibili" else "Available actions"
    "trackerRefs" -> if (italian) "Contatori della scena" else "Scene trackers"
    "defaultValue" -> if (italian) "Valore iniziale" else "Starting value"
    "allowedValues" -> if (italian) "Valori consentiti" else "Allowed values"
    else -> if (key.startsWith("track.")) {
        if (italian) "Tabella dell’avanzamento" else "Advancement table"
    } else if (italian) "Regola collegata" else "Linked rule"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferenceButtons(
    entities: List<RuleEntity>,
    selected: Set<String>,
    onSelect: (String) -> Unit,
) {
    val language = strings.language.tag
    val italian = language == "it"
    var expanded by remember(entities.map { it.id() }, selected) { mutableStateOf(false) }
    var search by remember(entities.map { it.id() }) { mutableStateOf("") }
    var recentIds by remember(entities.map { it.id() }) { mutableStateOf<List<String>>(emptyList()) }
    val query = search.trim().lowercase()
    val selectedEntities = entities.filter { it.id() in selected }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        selectedEntities.forEach { candidate ->
            GameButton(
                candidate.name().text(language),
                modifier = Modifier.fillMaxWidth(),
                selected = candidate.id() in selected,
                subtitle = referenceSubtitle(candidate, language),
                onClick = { onSelect(candidate.id()) },
            )
        }
        (selected - selectedEntities.map { it.id() }.toSet()).filter(String::isNotBlank).forEach { id ->
            Text(
                (if (italian) "Collegamento conservato ma non disponibile: " else
                    "Preserved unavailable link: ") + id,
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        GameButton(
            when {
                expanded && italian -> "Chiudi elenco"
                expanded -> "Close list"
                selectedEntities.isEmpty() && italian -> "Scegli una regola…"
                selectedEntities.isEmpty() -> "Choose a rule…"
                italian -> "Aggiungi o cambia…"
                else -> "Add or change…"
            },
            accent = Palette.TextMuted,
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            RuleTextField(
                search,
                { search = it },
                if (italian) "Cerca per nome, tipo o descrizione" else "Search by name, type, or description",
                Modifier.fillMaxWidth(),
            )
            val visible = entities.filter { candidate ->
                query.isBlank() ||
                    candidate.name().text(language).lowercase().contains(query) ||
                    candidate.description().text(language).lowercase().contains(query) ||
                    kindLabel(candidate.kind()).lowercase().contains(query) ||
                    candidate.id().lowercase().contains(query)
            }.sortedWith(
                compareBy<RuleEntity> { candidate -> candidate.id() !in selected }
                    .thenBy { candidate -> recentIds.indexOf(candidate.id()).let { if (it < 0) Int.MAX_VALUE else it } }
                    .thenBy { candidate -> candidate.name().text(language).lowercase() },
            )
            visible.take(8).forEach { candidate ->
                val description = candidate.description().text(language)
                val subtitle = referenceSubtitle(candidate, language)
                GameButton(
                    candidate.name().text(language),
                    modifier = Modifier.fillMaxWidth(),
                    selected = candidate.id() in selected,
                    subtitle = buildString {
                        append(subtitle)
                        if (description.isNotBlank()) append(" — ").append(description.take(90))
                    },
                    onClick = {
                        recentIds = (listOf(candidate.id()) + recentIds.filterNot { it == candidate.id() }).take(5)
                        onSelect(candidate.id())
                    },
                )
            }
            if (visible.size > 8) {
                Text(
                    if (italian) "Altri ${visible.size - 8} risultati: restringi la ricerca." else
                        "${visible.size - 8} more results: narrow your search.",
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (visible.isEmpty()) {
                Text(
                    if (italian) "Nessun collegamento trovato" else "No matching link",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun referenceSubtitle(candidate: RuleEntity, language: String): String = buildString {
    append(kindLabel(candidate.kind())).append(" · ").append(originLabel(candidate.origin()))
    if (RulesetCompiler.isDirectNumericFormulaReferenceTarget(candidate)) {
        append(if (language == "it") " · valore iniziale " else " · starting value ")
        append(exampleValue(candidate.id(), listOf(candidate)))
    }
}

@Composable
private fun FormulaHelp() {
    val italian = strings.language.tag == "it"
    Text(
        if (italian) {
            "Formule: \${id-regola}, \${id-regola:modifier}, lookup(\"id-tabella\", chiave), " +
                "if, min, max, clamp, floor, ceil e round. Niente codice o accesso esterno."
        } else {
            "Formulas: \${rule-id}, \${rule-id:modifier}, lookup(\"table-id\", key), " +
                "if, min, max, clamp, floor, ceil, and round. No code or external access."
        },
        color = Palette.TextFaint,
        style = MaterialTheme.typography.bodySmall,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleCompositionEditor(viewModel: RulesViewModel) {
    val draft = viewModel.moduleComposition ?: return
    val italian = strings.language.tag == "it"
    var modulePath by remember(draft.baseCanonicalHash) { mutableStateOf("") }
    var name by remember(draft.baseCanonicalHash) { mutableStateOf(draft.name) }
    var description by remember(draft.baseCanonicalHash) { mutableStateOf(draft.description) }
    var version by remember(draft.baseCanonicalHash) { mutableStateOf(draft.version) }

    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .72f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Party.copy(alpha = .75f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(if (italian) "Composizione modulare" else "Modular composition", Palette.Party)
        Text(
            if (italian) {
                "La revisione risultante è appiattita e giocabile; ordine, hash e scelte di conflitto restano nel lock."
            } else {
                "The resulting revision is flattened and playable; order, hashes, and conflict choices remain in its lock."
            },
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        RuleTextField(
            modulePath,
            { modulePath = it },
            if (italian) "Percorso modulo (.onfall-rules-module)" else "Module path (.onfall-rules-module)",
            Modifier.fillMaxWidth(),
        )
        GameButton(
            if (italian) "Installa modulo" else "Install module",
            dense = true,
            enabled = modulePath.isNotBlank(),
            onClick = { viewModel.importModule(modulePath) },
        )

        Eyebrow(if (italian) "Catalogo moduli" else "Module catalog")
        if (viewModel.installedModules.isEmpty()) {
            Text(
                if (italian) "Nessun modulo installato." else "No modules installed.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            viewModel.installedModules.forEach { module ->
                val selected = module.canonicalHash() in draft.orderedModuleHashes
                GameButton(
                    module.name().text(strings.language.tag),
                    dense = true,
                    selected = selected,
                    subtitle = buildString {
                        append(module.version()).append(" · ").append(module.canonicalHash().take(12))
                        append(if (italian) " · origine " else " · origin ")
                        append(module.origin().name)
                        if (module.dependencies().isNotEmpty()) {
                            append(if (italian) " · dipende da " else " · depends on ")
                            append(module.dependencies().joinToString { it.moduleId() })
                        }
                        if (module.incompatibleModuleIds().isNotEmpty()) {
                            append(if (italian) " · incompatibile con " else " · incompatible with ")
                            append(module.incompatibleModuleIds().joinToString())
                        }
                    },
                    onClick = { viewModel.toggleCompositionModule(module.canonicalHash()) },
                )
            }
        }

        if (draft.orderedModuleHashes.isNotEmpty()) {
            Eyebrow(if (italian) "Ordine di applicazione" else "Application order")
            draft.orderedModuleHashes.forEachIndexed { index, hash ->
                val module = viewModel.module(hash) ?: return@forEachIndexed
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "${index + 1}. ${module.name().text(strings.language.tag)}",
                        color = Palette.Text,
                        style = OnfallTheme.typography.supportingEmphasis,
                        modifier = Modifier.weight(1f),
                    )
                    GameButton("↑", dense = true, enabled = index > 0,
                        onClick = { viewModel.moveCompositionModule(hash, -1) })
                    GameButton("↓", dense = true, enabled = index < draft.orderedModuleHashes.lastIndex,
                        onClick = { viewModel.moveCompositionModule(hash, 1) })
                }
            }
        }

        viewModel.compositionIssues.forEach { issue ->
            CompositionIssueEditor(viewModel, issue)
        }
        viewModel.compositionError?.let { error ->
            Text(error, color = Palette.Critical, style = MaterialTheme.typography.bodySmall)
        }

        Eyebrow(if (italian) "Revisione risultante" else "Resulting revision")
        RuleTextField(name, {
            name = it
            viewModel.updateCompositionMetadata(name, description, version)
        }, strings.common.nameLabel, Modifier.fillMaxWidth())
        RuleTextField(description, {
            description = it
            viewModel.updateCompositionMetadata(name, description, version)
        }, strings.rules.descriptionPlaceholder, Modifier.fillMaxWidth(), multiline = true)
        RuleTextField(version, {
            version = it
            viewModel.updateCompositionMetadata(name, description, version)
        }, strings.rules.versionLabel, Modifier.width(120.dp))

        if (viewModel.compositionHasLegacyRuntimeControls) {
            RuntimeEditor(draft.runtime) { changed ->
                viewModel.updateCompositionRuntime { changed }
            }
        }
        viewModel.compositionPreview?.let { preview ->
            Text(
                if (italian) {
                    "Anteprima valida: ${preview.revision().entities().size} regole"
                } else {
                    "Valid preview: ${preview.revision().entities().size} rules"
                },
                color = Palette.Heal,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (viewModel.compositionChanges.isNotEmpty()) {
            Eyebrow(
                if (italian) {
                    "Differenze per campo (${viewModel.compositionChanges.size})"
                } else {
                    "Field changes (${viewModel.compositionChanges.size})"
                },
            )
            viewModel.compositionChanges.take(50).forEach { change ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        change.path,
                        color = Palette.Party,
                        style = OnfallTheme.typography.supportingEmphasis,
                    )
                    Text(
                        "${change.before ?: "∅"} → ${change.after ?: "∅"}",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (viewModel.compositionChanges.size > 50) {
                Text(
                    if (italian) {
                        "+${viewModel.compositionChanges.size - 50} differenze non mostrate"
                    } else {
                        "+${viewModel.compositionChanges.size - 50} changes not shown"
                    },
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GameButton(
                if (italian) "Pubblica composizione" else "Publish composition",
                dense = true,
                accent = Palette.Gold,
                enabled = viewModel.canPublishComposition,
                onClick = viewModel::publishModuleComposition,
            )
            GameButton(
                if (italian) "Annulla" else "Cancel",
                dense = true,
                accent = Palette.TextMuted,
                onClick = viewModel::cancelModuleComposition,
            )
        }
    }
}

@Composable
private fun CompositionIssueEditor(
    viewModel: RulesViewModel,
    issue: RulesetCompositionIssue,
) {
    val italian = strings.language.tag == "it"
    Column(
        Modifier.fillMaxWidth().background(Palette.Critical.copy(alpha = .08f), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Critical.copy(alpha = .55f), RoundedCornerShape(6.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            issue.code().name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase) +
                issue.field()?.let { " · ${it.path()}" }.orEmpty(),
            color = Palette.Critical,
            style = OnfallTheme.typography.supportingEmphasis,
        )
        Text(issue.detail(), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        if (issue.code() == RulesetCompositionIssue.Code.FIELD_CONFLICT) {
            Text(
                if (italian) "Scegli il modulo vincente:" else "Choose the winning module:",
                color = Palette.Text,
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                issue.candidateWinners().forEach { candidate ->
                    val module = viewModel.module(candidate.canonicalHash())
                    GameButton(
                        module?.name()?.text(strings.language.tag) ?: candidate.moduleId(),
                        dense = true,
                        subtitle = candidate.canonicalHash().take(12),
                        onClick = { viewModel.chooseCompositionWinner(issue, candidate.canonicalHash()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimeEditor(viewModel: RulesViewModel) {
    val runtime = viewModel.selected?.revision?.runtime() ?: return
    RuntimeEditor(runtime) { changed -> viewModel.updateRuntime { changed } }
}

@Composable
private fun RuntimeEditor(
    runtime: RulesetRuntimeConfig,
    onChange: (RulesetRuntimeConfig) -> Unit,
) {
    val words = strings.rules
    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .7f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(words.runtimeTitle, Palette.Heal)
        Text(words.runtimeHint, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        NumericControl(words.criticalThreshold, runtime.criticalHitMinimumNatural(), 2, 20) {
            onChange(runtime.withCriticalHitMinimumNatural(it))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = runtime.naturalOneAlwaysMisses(),
                onCheckedChange = { checked ->
                    onChange(runtime.withNaturalOneAlwaysMisses(checked))
                },
            )
            Text(words.naturalOneMisses, color = Palette.Text, style = MaterialTheme.typography.bodySmall)
        }
        NumericControl(words.maximumExhaustion, runtime.maximumExhaustion(), 1, 20) {
            onChange(runtime.withMaximumExhaustion(it))
        }
        NumericControl(words.exhaustionD20Penalty, runtime.exhaustionD20PenaltyPerLevel(), 0, 20) {
            onChange(runtime.withExhaustionD20PenaltyPerLevel(it))
        }
        NumericControl(words.exhaustionSpeedPenalty, runtime.exhaustionSpeedPenaltyFeetPerLevel(), 0, 100, 5) {
            onChange(runtime.withExhaustionSpeedPenaltyFeetPerLevel(it))
        }
        NumericControl(words.proficiencyBase, runtime.proficiencyBonusBase(), -20, 20) { value ->
            onChange(runtime.withProficiency(value, runtime.proficiencyLevelsPerIncrease(),
                maxOf(value, runtime.proficiencyBonusMaximum())))
        }
        NumericControl(words.proficiencyInterval, runtime.proficiencyLevelsPerIncrease(), 1, 20) { value ->
            onChange(runtime.withProficiency(runtime.proficiencyBonusBase(), value, runtime.proficiencyBonusMaximum()))
        }
        NumericControl(words.proficiencyMaximum, runtime.proficiencyBonusMaximum(),
            runtime.proficiencyBonusBase(), 50) { value ->
            onChange(runtime.withProficiency(
                runtime.proficiencyBonusBase(), runtime.proficiencyLevelsPerIncrease(), value,
            ))
        }
    }
}

@Composable
private fun NumericControl(
    label: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    step: Int = 1,
    onValue: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f))
        GameButton("−", dense = true, enabled = value > minimum,
            onClick = { onValue((value - step).coerceAtLeast(minimum)) })
        Text(value.toString(), color = Palette.Text,
            style = OnfallTheme.typography.numberCompact, modifier = Modifier.widthIn(min = 28.dp))
        GameButton("+", dense = true, enabled = value < maximum,
            onClick = { onValue((value + step).coerceAtMost(maximum)) })
    }
}

@Composable
private fun RuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    errorMessage: String? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = !multiline,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = Palette.Text),
        cursorBrush = SolidColor(Palette.Gold),
        modifier = modifier
            .semantics { errorMessage?.let { error(it) } }
            .background(Palette.Abyss, shape)
            .border(1.dp, if (errorMessage == null) Palette.Line else Palette.Critical, shape)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodySmall)
                field()
            }
        },
    )
}

@Composable
private fun GoldenVerticalRule() {
    Box(Modifier.width(1.dp).fillMaxSize().background(Palette.Gold.copy(alpha = .45f)))
}

@Composable
private fun automationLabel(level: RuleAutomationLevel): String = when (level) {
    RuleAutomationLevel.FULL -> strings.rules.fullyAutomated
    RuleAutomationLevel.ASSISTED -> strings.rules.assisted
    RuleAutomationLevel.MANUAL -> strings.rules.manual
}

private fun automationColor(level: RuleAutomationLevel) = when (level) {
    RuleAutomationLevel.FULL -> Palette.Heal
    RuleAutomationLevel.ASSISTED -> Palette.GoldBright
    RuleAutomationLevel.MANUAL -> Palette.TextMuted
}

@Composable
private fun originLabel(origin: app.d6d.rules.model.RulesetOrigin): String = when (origin) {
    app.d6d.rules.model.RulesetOrigin.BUNDLED_STANDARD -> strings.rules.standard
    app.d6d.rules.model.RulesetOrigin.HOMEBREW -> strings.rules.homebrew
    app.d6d.rules.model.RulesetOrigin.IMPORTED -> strings.rules.imported
    app.d6d.rules.model.RulesetOrigin.SESSION_LOCAL -> strings.rules.sessionLocal
}

private fun originColor(origin: app.d6d.rules.model.RulesetOrigin) = when (origin) {
    app.d6d.rules.model.RulesetOrigin.BUNDLED_STANDARD -> Palette.TextMuted
    app.d6d.rules.model.RulesetOrigin.HOMEBREW -> Palette.GoldBright
    app.d6d.rules.model.RulesetOrigin.IMPORTED -> Palette.Party
    app.d6d.rules.model.RulesetOrigin.SESSION_LOCAL -> Palette.Heal
}

@Composable
private fun kindLabel(kind: RuleKind): String = when (kind) {
    RuleKind.CORE_MECHANIC -> if (strings.language.tag == "it") "Meccanica" else "Core"
    RuleKind.ROLL -> if (strings.language.tag == "it") "Tiro" else "Roll"
    RuleKind.RANDOMIZER -> if (strings.language.tag == "it") "Generatore casuale" else "Randomizer"
    RuleKind.STAT -> if (strings.language.tag == "it") "Caratteristica" else "Ability"
    RuleKind.SKILL -> if (strings.language.tag == "it") "Abilità" else "Skill"
    RuleKind.SAVE -> if (strings.language.tag == "it") "Tiro salvezza" else "Save"
    RuleKind.DEFENSE -> if (strings.language.tag == "it") "Difesa" else "Defense"
    RuleKind.VALUE -> if (strings.language.tag == "it") "Valore" else "Value"
    RuleKind.MODIFIER -> if (strings.language.tag == "it") "Modificatore" else "Modifier"
    RuleKind.RESOURCE -> if (strings.language.tag == "it") "Risorsa" else "Resource"
    RuleKind.TRACK -> if (strings.language.tag == "it") "Tracciato" else "Track"
    RuleKind.ACTION -> if (strings.language.tag == "it") "Azione" else "Action"
    RuleKind.ACTION_ECONOMY -> if (strings.language.tag == "it") "Economia azioni" else "Action economy"
    RuleKind.CONDITION -> if (strings.language.tag == "it") "Condizione" else "Condition"
    RuleKind.DAMAGE_TYPE -> if (strings.language.tag == "it") "Tipo di danno" else "Damage type"
    RuleKind.HEALTH_MODEL -> if (strings.language.tag == "it") "Salute" else "Health"
    RuleKind.MOVEMENT -> if (strings.language.tag == "it") "Movimento" else "Movement"
    RuleKind.PROGRESSION -> if (strings.language.tag == "it") "Progressione" else "Progression"
    RuleKind.CLASS -> if (strings.language.tag == "it") "Classe" else "Class"
    RuleKind.SUBCLASS -> if (strings.language.tag == "it") "Sottoclasse" else "Subclass"
    RuleKind.BACKGROUND -> "Background"
    RuleKind.FEATURE -> if (strings.language.tag == "it") "Privilegio" else "Feature"
    RuleKind.FEAT -> if (strings.language.tag == "it") "Talento" else "Feat"
    RuleKind.SPELL -> if (strings.language.tag == "it") "Incantesimo" else "Spell"
    RuleKind.ITEM -> if (strings.language.tag == "it") "Oggetto" else "Item"
    RuleKind.TABLE -> if (strings.language.tag == "it") "Tabella" else "Table"
    RuleKind.TRIGGER -> if (strings.language.tag == "it") "Innesco" else "Trigger"
    RuleKind.SCENE_PROCEDURE -> if (strings.language.tag == "it") "Procedura di scena" else "Scene procedure"
    RuleKind.SHEET_SECTION -> if (strings.language.tag == "it") "Sezione scheda" else "Sheet section"
    RuleKind.TEXT_RULE -> if (strings.language.tag == "it") "Regola testuale" else "Text rule"
    RuleKind.CUSTOM -> if (strings.language.tag == "it") "Personalizzata" else "Custom"
}
