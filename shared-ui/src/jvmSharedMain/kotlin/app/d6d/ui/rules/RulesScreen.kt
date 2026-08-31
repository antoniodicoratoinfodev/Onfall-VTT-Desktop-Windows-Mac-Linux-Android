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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.CombatStatus
import app.d6d.i18n.label
import app.d6d.rules.character.EffectCondition
import app.d6d.rules.character.EffectTarget
import app.d6d.rules.model.RuleAutomationLevel
import app.d6d.rules.model.RuleEntity
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RuleScope
import app.d6d.rules.model.RuleValue
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.i18n.strings
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.Palette

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
private val statefulKinds = setOf(
    RuleKind.STAT,
    RuleKind.SKILL,
    RuleKind.SAVE,
    RuleKind.DEFENSE,
    RuleKind.VALUE,
    RuleKind.RESOURCE,
    RuleKind.TRACK,
    RuleKind.CONDITION,
    RuleKind.ACTION_ECONOMY,
)

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
    var localNotice by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(words.title, color = Palette.Text, fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge)
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

        OriginFilters(viewModel, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))

        if (compact) {
            CompactRulesContent(viewModel, activeBattle, onNotice = { localNotice = it }, Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize()) {
                RulesetList(viewModel, Modifier.width(270.dp).fillMaxSize())
                GoldenVerticalRule()
                EntityBrowser(viewModel, Modifier.width(360.dp).fillMaxSize())
                GoldenVerticalRule()
                RuleDetail(
                    viewModel,
                    activeBattle,
                    onNotice = { localNotice = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun OriginFilters(viewModel: RulesViewModel, modifier: Modifier = Modifier) {
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
                onClick = { viewModel.changeOriginFilter(filter) },
            )
        }
    }
}

@Composable
private fun RulesetList(viewModel: RulesViewModel, modifier: Modifier = Modifier) {
    val words = strings.rules
    var portablePath by remember { mutableStateOf("") }
    LazyColumn(
        modifier.background(Palette.Surface.copy(alpha = .88f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item { Eyebrow(words.ruleset) }
        item {
            GameButton(
                words.newBlankRuleset,
                dense = true,
                accent = Palette.Heal,
                onClick = viewModel::createBlankRuleset,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RuleTextField(
                    portablePath,
                    { portablePath = it },
                    if (strings.language.tag == "it") {
                        "Percorso file regolamento (.json)"
                    } else {
                        "Ruleset file path (.json)"
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
                }
            }
        }
        items(viewModel.choices, key = { it.key }) { choice ->
            RulesetCard(choice, choice.key == viewModel.selectedKey) { viewModel.selectRuleset(choice.key) }
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
        Text(choice.name, color = Palette.Text, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
) {
    val words = strings.rules
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
        val entities = viewModel.visibleEntities
        if (entities.isEmpty()) {
            Text(words.noResults, color = Palette.TextMuted, modifier = Modifier.padding(12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(entities, key = { it.id() }) { entity ->
                    EntityRow(entity, entity.id() == viewModel.selectedEntityId) {
                        viewModel.selectEntity(entity.id())
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleKindFilterButtons(viewModel: RulesViewModel) {
    GameButton(
        strings.common.all,
        dense = true,
        selected = viewModel.kindFilter == null,
        onClick = { viewModel.kindFilter = null },
    )
    exposedKinds.forEach { kind ->
        GameButton(
            kindLabel(kind),
            dense = true,
            selected = viewModel.kindFilter == kind,
            onClick = { viewModel.kindFilter = kind },
        )
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
        Text(entity.name().text(language), color = Palette.Text, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDetail(
    viewModel: RulesViewModel,
    activeBattle: BattleViewModel?,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choice = viewModel.selected
    val entity = viewModel.selectedEntity
    val words = strings.rules
    Column(
        modifier.background(Palette.Surface.copy(alpha = .92f)).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (choice == null) return@Column
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(choice.name, color = Palette.Text, fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium)
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
        if (!choice.isDraft) {
            Text(
                if (choice.readOnly) words.forkHint else words.newRevisionHint,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (choice.isDraft) {
            var rulesetName by remember(choice.key) { mutableStateOf(choice.name) }
            var rulesetDescription by remember(choice.key) { mutableStateOf(choice.revision.description()) }
            Eyebrow(words.rulesetDetails)
            RuleTextField(
                rulesetName,
                { rulesetName = it },
                strings.common.nameLabel,
                Modifier.fillMaxWidth(),
            )
            RuleTextField(
                rulesetDescription,
                { rulesetDescription = it },
                words.descriptionPlaceholder,
                Modifier.fillMaxWidth(),
                multiline = true,
            )
            GameButton(
                words.saveRulesetDetails,
                dense = true,
                enabled = rulesetName.isNotBlank(),
                onClick = { viewModel.updateDraftMetadata(rulesetName, rulesetDescription) },
            )
            viewModel.draftChangeSummary?.let { summary ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Chip(words.modifiedCount(summary.modified), Palette.GoldBright)
                    Chip(words.addedCount(summary.added), Palette.Heal)
                    if (summary.disabled > 0) Chip(words.disabledCount(summary.disabled), Palette.Critical)
                }
            }
            if (viewModel.hasLegacyRuntimeControls) RuntimeEditor(viewModel)
            var newKind by remember(choice.key) { mutableStateOf(RuleKind.CUSTOM) }
            Eyebrow(words.ruleType)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                exposedKinds.forEach { candidate ->
                    GameButton(
                        kindLabel(candidate),
                        dense = true,
                        selected = newKind == candidate,
                        onClick = { newKind = candidate },
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                GameButton(words.addCustomRule, dense = true, accent = Palette.Heal,
                    subtitle = kindLabel(newKind), onClick = { viewModel.addRule(newKind) })
                var version by remember(choice.key) { mutableStateOf("1.0.0") }
                RuleTextField(version, { version = it }, words.versionLabel, Modifier.width(100.dp))
                GameButton(words.publish, dense = true, accent = Palette.Gold,
                    enabled = version.isNotBlank(), onClick = { viewModel.publishSelected(version) })
            }
        }

        if (!choice.isDraft) {
            val resolved = activeBattle?.state?.status() == CombatStatus.RESOLVED
            GameButton(
                words.applyToSession,
                accent = Palette.Heal,
                enabled = activeBattle != null &&
                    !resolved &&
                    activeBattle.state.rulesetBinding().canonicalHash() != choice.revision.canonicalHash(),
                subtitle = when {
                    activeBattle == null -> words.noOpenSession
                    resolved -> words.cannotApplyResolved
                    else -> words.applyPausesSession
                },
                onClick = {
                    val cpuWasEnabled = activeBattle?.enemyCpuEnabled == true
                    if (activeBattle?.applyRuleset(choice.revision) == true) {
                        onNotice(
                            if (cpuWasEnabled && !activeBattle.enemyCpuEnabled) {
                                words.appliedWithManualCpu(choice.name)
                            } else {
                                words.applied(choice.name)
                            },
                        )
                    } else {
                        activeBattle?.message?.let(onNotice)
                    }
                },
            )
        }

        HorizontalDivider(color = Palette.Line)
        if (entity == null) {
            Text(words.noResults, color = Palette.TextMuted)
        } else {
            // Anche il contenuto effettivo fa parte della chiave: dopo un ripristino
            // dalla base i campi locali non devono conservare i vecchi override.
            key(choice.key, entity, strings.language) {
                EntityDetail(
                    viewModel = viewModel,
                    entity = entity,
                    editable = choice.isDraft,
                    activeBattle = activeBattle,
                    activeRulesetHash = choice.revision.canonicalHash(),
                    onNotice = onNotice,
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
    fun setAttribute(attributeKey: String, attributeValue: String) {
        val index = attributes.indexOfFirst { it.first == attributeKey }
        attributes = if (index >= 0) {
            attributes.toMutableList().also { it[index] = attributeKey to attributeValue }
        } else {
            attributes + (attributeKey to attributeValue)
        }
    }
    Eyebrow(words.rule)
    if (editable) {
        RuleTextField(name, { name = it }, strings.common.nameLabel, Modifier.fillMaxWidth())
        RuleTextField(description, { description = it }, words.rule, Modifier.fillMaxWidth(), multiline = true)
        Eyebrow(words.ruleType)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = enabled, onCheckedChange = { enabled = it })
            Text("${words.enabled}: ${if (enabled) strings.common.yes else strings.common.no}",
                color = Palette.Text, style = MaterialTheme.typography.bodySmall)
        }
        if (kind != RuleKind.MODIFIER) {
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
        if (
            kind in modifierOwnerKinds ||
            (kind == RuleKind.CUSTOM && attributes.toMap().containsKey("elementKind"))
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
                onClick = { viewModel.addLinkedModifier(entity.id()) },
            )
        }
        if (kind != RuleKind.MODIFIER) {
            GameButton(
                if (language == "it") "Aggiungi effetto universale" else "Add universal effect",
                dense = true,
                accent = Palette.Party,
                subtitle = if (language == "it") {
                    "Collega formule, valori, risorse o condizioni a questa regola"
                } else {
                    "Link formulas, values, resources, or conditions to this rule"
                },
                onClick = { viewModel.addGenericLinkedModifier(entity.id()) },
            )
        }
        if (kind == RuleKind.MODIFIER) {
            if (attributes.toMap().containsKey("targetRef") || attributes.toMap().containsKey("valueFormula")) {
                GenericModifierSchemaEditor(viewModel, entity.id(), attributes.toMap(), ::setAttribute)
            } else {
                ModifierSchemaEditor(viewModel, entity.id(), attributes.toMap(), ::setAttribute)
            }
        }
        if (kind == RuleKind.VALUE) {
            ValueSchemaEditor(attributes.toMap(), ::setAttribute)
        }
        if (kind in modularRuntimeKinds) {
            ModularRuntimeSchemaEditor(kind, attributes.toMap(), ::setAttribute)
        }
        if (kind in statefulKinds) {
            StatePolicySchemaEditor(attributes.toMap(), ::setAttribute)
        }
        RuleReferenceEditor(viewModel, entity.id(), kind, attributes.toMap(), ::setAttribute)
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
                GameButton("×", dense = true, accent = Palette.Enemy,
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
        RuleTextField(tagText, { tagText = it }, words.tagsPlaceholder, Modifier.fillMaxWidth())
        GameButton(
            words.saveDraft,
            dense = true,
            enabled = name.isNotBlank() && description.isNotBlank(),
            onClick = {
                viewModel.updateEntity(
                    entity.id(), name, description, kind, automation, enabled,
                    attributes.filter { it.first.isNotBlank() }.associate { it.first.trim() to it.second },
                    tagText.split(',').map(String::trim).filter(String::isNotBlank),
                )
            },
        )
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
        Text(name, color = Palette.Text, fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge)
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
                Text(value, color = Palette.Text, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
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
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
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
                        fontWeight = FontWeight.Bold,
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
                        fontWeight = FontWeight.Bold,
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
                        fontWeight = FontWeight.Bold,
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
                    type.name.lowercase().replaceFirstChar(Char::uppercase),
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
    kind: RuleKind,
    attributes: Map<String, String>,
    onAttribute: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .7f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(if (italian) "Comportamento modulare" else "Modular behavior", Palette.Party)
        when (kind) {
            RuleKind.VALUE -> {
                AttributeTextField(attributes, "dimension", if (italian) "Dimensione semantica" else "Semantic dimension", onAttribute)
                AttributeTextField(attributes, "canonicalUnit", if (italian) "Unità canonica" else "Canonical unit", onAttribute)
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AttributeTextField(attributes, "order", if (italian) "Ordine" else "Order", onAttribute, Modifier.weight(1f))
                    AttributeTextField(attributes, "columns", if (italian) "Colonne" else "Columns", onAttribute, Modifier.weight(1f))
                }
                AttributeTextField(attributes, "visibilityFormula",
                    if (italian) "Formula di visibilità" else "Visibility formula", onAttribute)
            }
            RuleKind.SCENE_PROCEDURE -> {
                AttributeTextField(attributes, "phases",
                    if (italian) "Fasi, separate da virgola" else "Phases, comma-separated", onAttribute)
                AttributeCheckBox(attributes, "initiativeRequired",
                    if (italian) "Richiede iniziativa" else "Requires initiative", onAttribute)
                AttributeCheckBox(attributes, "boardRequired",
                    if (italian) "Richiede mappa" else "Requires board", onAttribute)
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
                choice.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase),
                dense = true,
                selected = attributes[key].orEmpty().ifBlank { choices.first() } == choice,
                onClick = { onAttribute(key, choice) },
            )
        }
    }
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
    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .7f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(if (italian) "Effetto universale eseguibile" else "Executable universal effect", Palette.Party)
        Text(
            if (italian) {
                "Il bersaglio è un ID di regola, il valore è una formula sicura. L'ID non dipende dal nome mostrato."
            } else {
                "The target is a rule ID and the value is a safe formula. IDs do not depend on display names."
            },
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Eyebrow(if (italian) "Applicazione" else "Application")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("STATIC", "CHANGE_VALUE", "SET_VALUE", "CHANGE_RESOURCE", "ADD_CONDITION", "REMOVE_CONDITION")
                .forEach { candidate ->
                    GameButton(candidate.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
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
                    "SELF modifica chi esegue; TARGET il bersaglio scelto; SESSION lo stato condiviso."
                } else {
                    "SELF changes the executor; TARGET the selected target; SESSION shared state."
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
                        recipient.lowercase().replaceFirstChar(Char::uppercase),
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
            entities = entities.filter { it.kind() in targetKinds },
            selected = setOf(attributes["targetRef"].orEmpty()),
            onSelect = { onAttribute("targetRef", it) },
        )
        if (application in setOf("STATIC", "CHANGE_VALUE", "CHANGE_RESOURCE")) {
            Eyebrow(if (italian) "Operazione" else "Operation")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("ADD", "MULTIPLY", "SET", "MINIMUM", "MAXIMUM").forEach { operation ->
                    GameButton(operation.lowercase().replaceFirstChar(Char::uppercase), dense = true,
                        selected = attributes["operation"].orEmpty().ifBlank { "ADD" } == operation,
                        onClick = { onAttribute("operation", operation) })
                }
            }
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
        FormulaHelp()
    }
}

private data class ReferenceField(
    val key: String,
    val kinds: Set<RuleKind>,
    val multiple: Boolean = false,
)

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
        RuleKind.PROGRESSION -> listOf(ReferenceField("experienceTableRef", setOf(RuleKind.TABLE)))
        RuleKind.RANDOMIZER, RuleKind.ROLL -> listOf(ReferenceField("tableRef", setOf(RuleKind.TABLE)))
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
            if (italian) "Cerca nome o ID" else "Search name or ID", Modifier.fillMaxWidth())
        fields.forEach { field ->
            val selected = attributes[field.key].orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet()
            Text(field.key, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
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
        if (formulaBearing) FormulaHelp()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferenceButtons(
    entities: List<RuleEntity>,
    selected: Set<String>,
    onSelect: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        entities.take(18).forEach { candidate ->
            GameButton(
                candidate.name().text(strings.language.tag),
                dense = true,
                selected = candidate.id() in selected,
                subtitle = candidate.id(),
                onClick = { onSelect(candidate.id()) },
            )
        }
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

@Composable
private fun RuntimeEditor(viewModel: RulesViewModel) {
    val runtime = viewModel.selected?.revision?.runtime() ?: return
    val words = strings.rules
    Column(
        Modifier.fillMaxWidth().background(Palette.Abyss.copy(alpha = .7f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(words.runtimeTitle, Palette.Heal)
        Text(words.runtimeHint, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        NumericControl(words.criticalThreshold, runtime.criticalHitMinimumNatural(), 2, 20) {
            viewModel.updateRuntime { config -> config.withCriticalHitMinimumNatural(it) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = runtime.naturalOneAlwaysMisses(),
                onCheckedChange = { checked ->
                    viewModel.updateRuntime { config -> config.withNaturalOneAlwaysMisses(checked) }
                },
            )
            Text(words.naturalOneMisses, color = Palette.Text, style = MaterialTheme.typography.bodySmall)
        }
        NumericControl(words.maximumExhaustion, runtime.maximumExhaustion(), 1, 20) {
            viewModel.updateRuntime { config -> config.withMaximumExhaustion(it) }
        }
        NumericControl(words.exhaustionD20Penalty, runtime.exhaustionD20PenaltyPerLevel(), 0, 20) {
            viewModel.updateRuntime { config -> config.withExhaustionD20PenaltyPerLevel(it) }
        }
        NumericControl(words.exhaustionSpeedPenalty, runtime.exhaustionSpeedPenaltyFeetPerLevel(), 0, 100, 5) {
            viewModel.updateRuntime { config -> config.withExhaustionSpeedPenaltyFeetPerLevel(it) }
        }
        NumericControl(words.proficiencyBase, runtime.proficiencyBonusBase(), -20, 20) { value ->
            viewModel.updateRuntime { config ->
                config.withProficiency(value, config.proficiencyLevelsPerIncrease(),
                    maxOf(value, config.proficiencyBonusMaximum()))
            }
        }
        NumericControl(words.proficiencyInterval, runtime.proficiencyLevelsPerIncrease(), 1, 20) { value ->
            viewModel.updateRuntime { config ->
                config.withProficiency(config.proficiencyBonusBase(), value, config.proficiencyBonusMaximum())
            }
        }
        NumericControl(words.proficiencyMaximum, runtime.proficiencyBonusMaximum(),
            runtime.proficiencyBonusBase(), 50) { value ->
            viewModel.updateRuntime { config ->
                config.withProficiency(config.proficiencyBonusBase(), config.proficiencyLevelsPerIncrease(), value)
            }
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
        Text(value.toString(), color = Palette.Text, fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 28.dp))
        GameButton("+", dense = true, enabled = value < maximum,
            onClick = { onValue((value + step).coerceAtMost(maximum)) })
    }
}

@Composable
private fun CompactRulesContent(
    viewModel: RulesViewModel,
    activeBattle: BattleViewModel?,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 190.dp).background(Palette.Surface.copy(alpha = .9f)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                GameButton(
                    strings.rules.newBlankRuleset,
                    dense = true,
                    accent = Palette.Heal,
                    onClick = viewModel::createBlankRuleset,
                )
            }
            items(viewModel.choices, key = { it.key }) { choice ->
                RulesetCard(choice, choice.key == viewModel.selectedKey) { viewModel.selectRuleset(choice.key) }
            }
        }
        GoldenRule()
        EntityBrowser(
            viewModel,
            Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 300.dp),
            compact = true,
        )
        GoldenRule()
        RuleDetail(viewModel, activeBattle, onNotice, Modifier.fillMaxSize())
    }
}

@Composable
private fun RuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = !multiline,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = Palette.Text),
        cursorBrush = SolidColor(Palette.Gold),
        modifier = modifier.background(Palette.Abyss, shape).border(1.dp, Palette.Line, shape)
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
