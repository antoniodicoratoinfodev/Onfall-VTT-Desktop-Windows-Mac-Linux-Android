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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageType
import app.d6d.sheet.italianLabel
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.italianLabel
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.theme.Palette
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
    var damageType by remember { mutableStateOf(DamageType.SLASHING) }
    var conditionType by remember { mutableStateOf(ConditionType.PRONE) }

    val target = targetId?.let(viewModel::combatant)
    val amount = amountText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val duration = durationText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val commandsEnabled = viewModel.status == CombatStatus.ACTIVE && targetId != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(12.dp),
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Strumenti del tavolo",
                            color = Palette.Text,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "Danni, cure e condizioni del combattente scelto.",
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    GameButton("Chiudi", accent = Palette.TextMuted, onClick = onDismiss)
                }
                OrnateDivider(color = Palette.GoldDim)

                Eyebrow("Combattente interessato")
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
                        Chip("${combatant.currentHitPoints()}/${combatant.snapshot().maxHitPoints()} PF", Palette.Text)
                        if (combatant.temporaryHitPoints() > 0) {
                            Chip("${combatant.temporaryHitPoints()} PF temporanei", Palette.Temporary)
                        }
                        Chip("Sfinimento ${combatant.exhaustionLevel()}", Palette.Bloodied)
                        when {
                            combatant.dead() -> Chip("Morto", Palette.Critical)
                            combatant.stable() -> Chip("Stabile", Palette.Heal)
                            combatant.unconscious() -> Chip("Privo di sensi", Palette.Bloodied)
                        }
                    }
                }

                Eyebrow("Punti ferita")
                LabeledNumberField(
                    label = "Quantità",
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit).take(5) },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    GameButton(
                        "Applica danno",
                        accent = Palette.Enemy,
                        enabled = commandsEnabled && amount > 0,
                        onClick = { targetId?.let { viewModel.applyManualDamage(it, amount, damageType) } },
                    )
                    GameButton(
                        "Cura",
                        accent = Palette.Heal,
                        enabled = commandsEnabled && amount > 0,
                        onClick = { targetId?.let { viewModel.heal(it, amount) } },
                    )
                    GameButton(
                        "PF temporanei",
                        accent = Palette.Temporary,
                        enabled = commandsEnabled && amount > 0,
                        onClick = { targetId?.let { viewModel.grantTemporary(it, amount) } },
                    )
                }
                Text("Tipo del danno", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DamageType.entries.forEach { type ->
                        GameButton(
                            label = type.italianLabel,
                            accent = if (type == damageType) Palette.Gold else Palette.TextFaint,
                            selected = type == damageType,
                            onClick = { damageType = type },
                        )
                    }
                }

                Eyebrow("Condizioni")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ConditionType.entries
                        .filterNot { it == ConditionType.EXHAUSTION || it == ConditionType.CUSTOM }
                        .forEach { type ->
                            GameButton(
                                label = type.italianLabel,
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
                        label = "Round (0 = manuale)",
                        value = durationText,
                        onValueChange = { durationText = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.weight(1f),
                    )
                    GameButton(
                        "Aggiungi condizione",
                        accent = Palette.Bloodied,
                        enabled = commandsEnabled,
                        onClick = { targetId?.let { viewModel.addCondition(it, conditionType, duration) } },
                    )
                }
                if (!target?.conditions().isNullOrEmpty()) {
                    Text("Clicca una condizione per rimuoverla", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        target.conditions().forEach { condition ->
                            GameButton(
                                label = "Rimuovi ${condition.type().italianLabel}",
                                accent = Palette.TextMuted,
                                enabled = commandsEnabled,
                                onClick = { targetId?.let { viewModel.removeCondition(it, condition.id()) } },
                            )
                        }
                    }
                }

                Eyebrow("Morte e sfinimento")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    GameButton(
                        "Tiro contro morte",
                        accent = Palette.Bloodied,
                        enabled = commandsEnabled,
                        onClick = { targetId?.let(viewModel::rollDeathSave) },
                    )
                    GameButton(
                        "Stabilizza",
                        accent = Palette.Heal,
                        enabled = commandsEnabled,
                        onClick = { targetId?.let(viewModel::stabilize) },
                    )
                    (0..6).forEach { level ->
                        GameButton(
                            label = "Sfinimento $level",
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
