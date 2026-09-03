package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.SaveAbility
import app.d6d.ui.components.Chip
import app.d6d.ui.components.dismissDialogOnTap
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.keepDialogOpenOnTap
import app.d6d.i18n.label
import app.d6d.rules.model.RuleScope
import app.d6d.ui.i18n.strings
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.OnfallTheme
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush

/**
 * Strumenti da tavolo per tutto ciò che non è un normale tiro per colpire.
 *
 * Restano separati dai comandi frequenti per non trasformare la barra del turno
 * in una plancia affollata, ma usano comunque il motore: ogni operazione finisce
 * nel registro ed è annullabile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BattleToolsDialog(
    viewModel: BattleViewModel,
    open: Boolean,
    onDismiss: () -> Unit,
) {
    if (!open) return

    val strings = strings
    val words = strings.battle
    val language = strings.language
    val combatantIds = viewModel.partyIds + viewModel.enemyIds
    var targetId by remember(viewModel.sessionGeneration) {
        mutableStateOf(
            viewModel.selectedTargetId
                ?: viewModel.inspectedCombatantId
                ?: viewModel.activeActorId
                ?: combatantIds.firstOrNull(),
        )
    }
    if (targetId !in combatantIds) targetId = combatantIds.firstOrNull()

    var amountText by remember { mutableStateOf("1") }
    var durationText by remember { mutableStateOf("0") }
    var abilityCheckModifierText by remember { mutableStateOf("0") }
    var abilityCheckAbility by remember { mutableStateOf(SaveAbility.STRENGTH) }
    val damageTypes = viewModel.availableDamageTypes
    val conditionTypes = viewModel.availableConditionTypes
    var damageType by remember(viewModel.sessionGeneration, damageTypes) {
        mutableStateOf(damageTypes.firstOrNull() ?: DamageType.UNTYPED)
    }
    var conditionType by remember(viewModel.sessionGeneration, conditionTypes) {
        mutableStateOf(conditionTypes.firstOrNull() ?: ConditionType.CUSTOM)
    }

    val target = targetId?.let(viewModel::combatant)
    val amount = amountText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val duration = durationText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val abilityCheckModifier = abilityCheckModifierText.toIntOrNull()
    val cpuLocked = viewModel.enemyCpuBatchPending && !viewModel.editMode
    val commandsEnabled = viewModel.status == CombatStatus.ACTIVE && targetId != null && !cpuLocked

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().dismissDialogOnTap(onDismiss).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val dialogShape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .panelBrush(dialogShape)
                    .border(1.dp, Palette.Bronze.copy(alpha = 0.6f), dialogShape)
                    .ornateFrame(accent = Palette.Gold, alpha = 0.5f)
                    .keepDialogOpenOnTap()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (cpuLocked) {
                    Text(
                        words.toolsSuspendedDuringCpu,
                        color = Palette.Enemy,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            words.tableToolsTitle,
                            color = Palette.Text,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            words.tableToolsSubtitle,
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    GameButton(strings.common.close, accent = Palette.TextMuted, onClick = onDismiss)
                }
                OrnateDivider(color = Palette.GoldDim)

                Eyebrow(words.affectedCombatant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    combatantIds.forEach { id ->
                        val selected = id == targetId
                        GameButton(
                            label = viewModel.name(id),
                            accent = when {
                                selected -> Palette.Gold
                                viewModel.isParty(id) -> Palette.Party
                                else -> Palette.Enemy
                            },
                            selected = selected,
                            onClick = { targetId = id },
                        )
                    }
                }

                target?.let { combatant ->
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (viewModel.tacticalHitPointControlsAvailable) {
                            Chip(
                                words.hitPointsShort(
                                    combatant.currentHitPoints(),
                                    combatant.snapshot().maxHitPoints(),
                                ),
                                Palette.Text,
                            )
                            if (combatant.temporaryHitPoints() > 0) {
                                Chip(
                                    words.temporaryHitPointsOf(combatant.temporaryHitPoints()),
                                    Palette.Temporary,
                                )
                            }
                        }
                        if (viewModel.tacticalExhaustionControlsAvailable) {
                            Chip(words.exhaustionLevel(combatant.exhaustionLevel()), Palette.Bloodied)
                        }
                        if (viewModel.tacticalDeathSaveControlsAvailable) {
                            when {
                                combatant.dead() -> Chip(words.dead, Palette.Critical)
                                combatant.stable() -> Chip(words.stable, Palette.Heal)
                                combatant.unconscious() -> Chip(words.unconscious, Palette.Bloodied)
                            }
                        }
                    }
                }

                if (viewModel.tacticalD20ControlsAvailable) {
                    Eyebrow(words.abilityCheck)
                    Text(
                        words.abilityCheckHint,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SaveAbility.entries.forEach { ability ->
                            val selected = ability == abilityCheckAbility
                            GameButton(
                                label = ability.label(language),
                                accent = if (selected) Palette.Gold else Palette.TextFaint,
                                selected = selected,
                                onClick = { abilityCheckAbility = ability },
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        LabeledNumberField(
                            label = strings.sheet.modifier,
                            value = abilityCheckModifierText,
                            onValueChange = { abilityCheckModifierText = signedIntegerInput(it) },
                            modifier = Modifier.weight(1f),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                words.rollModeFromBar(viewModel.rollMode.label(language)),
                                color = Palette.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            GameButton(
                                words.rollCheck,
                                accent = Palette.Party,
                                enabled = commandsEnabled && abilityCheckModifier != null,
                                onClick = {
                                    val selectedTarget = targetId
                                    val modifier = abilityCheckModifier
                                    if (selectedTarget != null && modifier != null) {
                                        viewModel.rollAbilityCheck(
                                            selectedTarget,
                                            abilityCheckAbility,
                                            modifier,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                if (viewModel.tacticalHitPointControlsAvailable) {
                    Eyebrow(words.hitPoints)
                    LabeledNumberField(
                        label = words.amount,
                        value = amountText,
                        onValueChange = { amountText = it.filter(Char::isDigit).take(5) },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        GameButton(
                            words.applyDamage,
                            accent = Palette.Enemy,
                            enabled = commandsEnabled && amount > 0 && damageTypes.isNotEmpty(),
                            onClick = { targetId?.let { viewModel.applyManualDamage(it, amount, damageType) } },
                        )
                        GameButton(
                            strings.sheet.heal,
                            accent = Palette.Heal,
                            enabled = commandsEnabled && amount > 0,
                            onClick = { targetId?.let { viewModel.heal(it, amount) } },
                        )
                        GameButton(
                            words.temporaryHitPoints,
                            accent = Palette.Temporary,
                            enabled = commandsEnabled && amount > 0,
                            onClick = { targetId?.let { viewModel.grantTemporary(it, amount) } },
                        )
                    }
                    Text(words.damageTypeLabel, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        damageTypes.forEach { type ->
                            GameButton(
                                label = type.label(language),
                                accent = if (type == damageType) Palette.Gold else Palette.TextFaint,
                                selected = type == damageType,
                                onClick = { damageType = type },
                            )
                        }
                    }
                }

                Eyebrow(strings.sheet.conditions)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    conditionTypes.forEach { type ->
                            GameButton(
                                label = type.label(language),
                                accent = if (type == conditionType) Palette.Gold else Palette.TextFaint,
                                selected = type == conditionType,
                                onClick = { conditionType = type },
                            )
                        }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LabeledNumberField(
                        label = words.roundsManualHint,
                        value = durationText,
                        onValueChange = { durationText = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.weight(1f),
                    )
                    GameButton(
                        words.addCondition,
                        accent = Palette.Bloodied,
                        enabled = commandsEnabled && conditionTypes.isNotEmpty(),
                        onClick = { targetId?.let { viewModel.addCondition(it, conditionType, duration) } },
                    )
                }
                if (!target?.conditions().isNullOrEmpty()) {
                    Text(words.clickConditionToRemove, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        target.conditions().forEach { condition ->
                            GameButton(
                                label = words.removeCondition(condition.type().label(language)),
                                accent = Palette.TextMuted,
                                enabled = commandsEnabled,
                                onClick = { targetId?.let { viewModel.removeCondition(it, condition.id()) } },
                            )
                        }
                    }
                }

                GenericRuleTools(
                    viewModel = viewModel,
                    combatantIds = combatantIds,
                    focusedTargetId = targetId,
                    commandsEnabled = commandsEnabled,
                )

                if (
                    viewModel.tacticalDeathSaveControlsAvailable ||
                    viewModel.tacticalExhaustionControlsAvailable
                ) {
                    Eyebrow(words.deathAndExhaustion)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (viewModel.tacticalDeathSaveControlsAvailable) {
                            GameButton(
                                words.deathSaveRoll,
                                accent = Palette.Bloodied,
                                enabled = commandsEnabled,
                                onClick = { targetId?.let(viewModel::rollDeathSave) },
                            )
                            GameButton(
                                strings.sheet.stabilize,
                                accent = Palette.Heal,
                                enabled = commandsEnabled,
                                onClick = { targetId?.let(viewModel::stabilize) },
                            )
                        }
                        if (viewModel.tacticalExhaustionControlsAvailable) {
                            val maximumExhaustion = target?.maximumExhaustion() ?: 0
                            (0..maximumExhaustion).forEach { level ->
                                GameButton(
                                    label = words.exhaustionLevel(level),
                                    accent = if (target?.exhaustionLevel() == level) Palette.Gold else Palette.TextFaint,
                                    selected = target?.exhaustionLevel() == level,
                                    enabled = commandsEnabled,
                                    onClick = { targetId?.let { viewModel.setExhaustion(it, level) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenericRuleTools(
    viewModel: BattleViewModel,
    combatantIds: List<String>,
    focusedTargetId: String?,
    commandsEnabled: Boolean,
) {
    val actions = viewModel.genericRuleActions
    val resources = viewModel.genericRuleResources
    val conditions = viewModel.genericRuleConditions
    val healthModels = viewModel.genericHealthModels
    if (actions.isEmpty() && resources.isEmpty() && conditions.isEmpty() && healthModels.isEmpty()) return

    val language = strings.language
    val italian = language.tag == "it"
    val initialTarget = focusedTargetId ?: combatantIds.firstOrNull()
    var selectedTargetIds by remember(viewModel.sessionGeneration) {
        mutableStateOf(initialTarget?.let(::setOf).orEmpty())
    }
    val availableTargetIds = selectedTargetIds.intersect(combatantIds.toSet())
    val focusedScope = focusedTargetId?.let(RuleScope::actor) ?: RuleScope.session()
    val sourceScope = viewModel.activeActorId?.let(RuleScope::actor) ?: RuleScope.session()
    val targetScopes = availableTargetIds.sorted().map(RuleScope::actor)

    OrnateDivider(color = Palette.Party)
    Eyebrow(if (italian) "Regole del regolamento attivo" else "Active ruleset tools", Palette.Party)
    Text(
        if (italian) {
            "Questi controlli provengono dalla revisione incorporata nella sessione, non da campi D&D impliciti."
        } else {
            "These controls come from the revision embedded in this session, not from implicit D&D fields."
        },
        color = Palette.TextMuted,
        style = MaterialTheme.typography.bodySmall,
    )

    if (actions.isNotEmpty()) {
        Text(
            if (italian) "Bersagli delle azioni (selezione multipla)" else "Action targets (multi-select)",
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            combatantIds.forEach { id ->
                GameButton(
                    label = viewModel.name(id),
                    dense = true,
                    selected = id in availableTargetIds,
                    accent = if (viewModel.isParty(id)) Palette.Party else Palette.Enemy,
                    onClick = {
                        selectedTargetIds = if (id in selectedTargetIds) {
                            selectedTargetIds - id
                        } else {
                            selectedTargetIds + id
                        }
                    },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            actions.forEach { action ->
                GameButton(
                    label = action.name().text(language.tag),
                    subtitle = action.description().text(language.tag).takeIf(String::isNotBlank),
                    accent = Palette.Heal,
                    enabled = commandsEnabled && targetScopes.isNotEmpty(),
                    onClick = {
                        viewModel.executeGenericRuleAction(action.id(), sourceScope, targetScopes)
                    },
                )
            }
        }
    }

    if (healthModels.isNotEmpty()) {
        Eyebrow(if (italian) "Salute modulare" else "Modular health", Palette.Heal)
        healthModels.forEach { health ->
            val primaryId = health.attributes()["primaryResourceRef"].orEmpty()
            val primary = viewModel.genericRuleState(focusedScope)?.resources()?.get(primaryId)
            Text(
                buildString {
                    append(health.name().text(language.tag))
                    if (primary != null) {
                        append(": ").append(primary.current().toPlainString())
                            .append(" / ").append(primary.maximum().toPlainString())
                    } else if (primaryId.isNotBlank()) {
                        append(" · ").append(primaryId)
                    }
                },
                color = Palette.Text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (resources.isNotEmpty()) {
        Eyebrow(if (italian) "Risorse e tracciati" else "Resources and tracks", Palette.Temporary)
        resources.forEach { entity ->
            val state = viewModel.genericRuleState(focusedScope)?.resources()?.get(entity.id())
            if (state != null) {
                GenericResourceEditor(
                    label = entity.name().text(language.tag),
                    entityId = entity.id(),
                    current = state.current().toPlainString(),
                    maximum = state.maximum().toPlainString(),
                    enabled = commandsEnabled,
                    onSave = { current, maximum ->
                        viewModel.setGenericResource(entity.id(), current, maximum, focusedScope)
                    },
                )
                entity.attributes()["recoveryEvent"]
                    ?.takeIf { it.isNotBlank() && it != "MANUAL" }
                    ?.let { event ->
                        GameButton(
                            label = if (italian) "Invia $event" else "Fire $event",
                            dense = true,
                            enabled = commandsEnabled,
                            onClick = { viewModel.fireGenericRuleEvent(event, focusedScope) },
                        )
                    }
            }
        }
    }

    if (conditions.isNotEmpty()) {
        Eyebrow(if (italian) "Condizioni modulari" else "Modular conditions", Palette.Bloodied)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            conditions.forEach { entity ->
                val stacks = viewModel.genericRuleState(focusedScope)?.conditionStacks()?.get(entity.id()) ?: 0
                val maximum = entity.attributes()["maximumStacks"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameButton(
                        "−",
                        dense = true,
                        enabled = commandsEnabled && stacks > 0,
                        onClick = { viewModel.setGenericConditionStacks(entity.id(), stacks - 1, focusedScope) },
                    )
                    Chip("${entity.name().text(language.tag)} · $stacks/$maximum", Palette.Bloodied)
                    GameButton(
                        "+",
                        dense = true,
                        enabled = commandsEnabled && stacks < maximum,
                        onClick = { viewModel.setGenericConditionStacks(entity.id(), stacks + 1, focusedScope) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GenericResourceEditor(
    label: String,
    entityId: String,
    current: String,
    maximum: String,
    enabled: Boolean,
    onSave: (String, String) -> Unit,
) {
    val italian = strings.language.tag == "it"
    var currentDraft by remember(entityId, current) { mutableStateOf(current) }
    var maximumDraft by remember(entityId, maximum) { mutableStateOf(maximum) }
    Text(label, color = Palette.Text, style = OnfallTheme.typography.bodyEmphasis)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        LabeledNumberField(
            if (italian) "Attuale" else "Current",
            currentDraft,
            { currentDraft = decimalInput(it) },
            Modifier.weight(1f),
        )
        LabeledNumberField(
            if (italian) "Massimo" else "Maximum",
            maximumDraft,
            { maximumDraft = decimalInput(it) },
            Modifier.weight(1f),
        )
        GameButton(
            strings.common.save,
            dense = true,
            enabled = enabled && currentDraft.toBigDecimalOrNull() != null && maximumDraft.toBigDecimalOrNull() != null,
            onClick = { onSave(currentDraft, maximumDraft) },
        )
    }
}

private fun decimalInput(value: String): String = value.filterIndexed { index, char ->
    char.isDigit() || char == '.' || char == ',' || char == '-' && index == 0
}.replace(',', '.')

private fun signedIntegerInput(value: String): String {
    val negative = value.startsWith('-')
    val digits = value.filter(Char::isDigit).take(6)
    return if (negative) "-$digits" else digits
}

@Composable
private fun LabeledNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Palette.Text),
            cursorBrush = SolidColor(Palette.Gold),
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Night, RoundedCornerShape(7.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(7.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}
