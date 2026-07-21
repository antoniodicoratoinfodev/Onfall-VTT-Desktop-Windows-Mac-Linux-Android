package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.ui.components.CombatantPortrait
import app.d6d.ui.components.ConditionChip
import app.d6d.ui.components.EditableValue
import app.d6d.ui.components.Faction
import app.d6d.ui.components.HealthBar
import app.d6d.ui.components.ResourcePips
import app.d6d.ui.components.color
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.Palette

/**
 * Carta di un combattente nelle barre laterali: squadra a sinistra, nemici a destra.
 *
 * Deve restare leggibile a colpo d'occhio durante il turno, quindi mostra solo
 * cio' che serve a decidere: PF, CA, condizioni e risorse del turno.
 */
@Composable
fun CombatantRailCard(
    viewModel: BattleViewModel,
    combatantId: String,
    faction: Faction,
    modifier: Modifier = Modifier,
) {
    val combatant = viewModel.combatant(combatantId) ?: return
    val snapshot = combatant.snapshot()
    // In un turno simultaneo sono attivi tutti i membri del gruppo, non solo il primo.
    val active = viewModel.isActive(combatantId)
    val targeted = viewModel.effectiveTargetId() == combatantId
    val defeated = combatant.defeated()
    val budget = viewModel.budget(combatantId)

    val borderColor = when {
        active -> Palette.Gold
        targeted -> faction.color
        else -> Palette.Line
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (active) Palette.SurfaceHigh else Palette.Surface,
                RoundedCornerShape(10.dp),
            )
            .border(if (active || targeted) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { viewModel.selectedTargetId = combatantId }
            .padding(9.dp)
            .alpha(if (defeated) 0.5f else 1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CombatantPortrait(
                name = snapshot.name(),
                currentHitPoints = combatant.currentHitPoints(),
                maxHitPoints = snapshot.maxHitPoints(),
                faction = faction,
                active = active,
                diameter = 42.dp,
            )

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                EditableValue(
                    value = snapshot.name(),
                    editMode = viewModel.editMode,
                    onCommit = { viewModel.editCombatant(combatantId, name = it) },
                    fieldWidth = 128.dp,
                ) {
                    Text(
                        text = snapshot.name(),
                        color = if (defeated) Palette.TextMuted else Palette.Text,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditableValue(
                        value = snapshot.armorClass().toString(),
                        editMode = viewModel.editMode,
                        numeric = true,
                        fieldWidth = 46.dp,
                        onCommit = { text ->
                            text.trim().toIntOrNull()?.let {
                                viewModel.editCombatant(combatantId, armorClass = it)
                            }
                        },
                    ) {
                        Text(
                            text = "CA ${snapshot.armorClass()}",
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    EditableValue(
                        value = snapshot.maxHitPoints().toString(),
                        editMode = viewModel.editMode,
                        numeric = true,
                        fieldWidth = 52.dp,
                        onCommit = { text ->
                            text.trim().toIntOrNull()?.let {
                                viewModel.editCombatant(combatantId, maxHitPoints = it)
                            }
                        },
                    ) {
                        Text(
                            text = "PF max ${snapshot.maxHitPoints()}",
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    viewModel.initiativeScore(combatantId)?.let {
                        Text(
                            text = "IN $it",
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        HealthBar(
            current = combatant.currentHitPoints(),
            max = snapshot.maxHitPoints(),
            temporary = combatant.temporaryHitPoints(),
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildString {
                    append("${combatant.currentHitPoints()}/${snapshot.maxHitPoints()}")
                    if (combatant.temporaryHitPoints() > 0) append(" +${combatant.temporaryHitPoints()}")
                },
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
            )
            if (active && budget != null) {
                ResourcePips(
                    actionAvailable = budget.actionAvailable(),
                    bonusAvailable = budget.bonusActionAvailable(),
                    reactionAvailable = budget.reactionAvailable(),
                )
            }
        }

        if (combatant.conditions().isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                combatant.conditions().forEach { condition ->
                    ConditionChip(
                        type = condition.type(),
                        rounds = condition.duration().remainingOccurrences(),
                    )
                }
            }
        }
    }
}
