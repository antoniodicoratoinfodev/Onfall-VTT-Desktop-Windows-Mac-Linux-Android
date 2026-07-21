package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.D20Mode
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.Palette

/** Pulsante in stile gioco: bordo acceso, riempimento scuro, etichetta marcata. */
@Composable
fun GameButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Gold,
    enabled: Boolean = true,
    subtitle: String? = null,
) {
    val tint = if (enabled) accent else Palette.TextFaint
    Column(
        modifier = modifier
            .background(tint.copy(alpha = if (enabled) 0.11f else 0.05f), RoundedCornerShape(8.dp))
            .border(1.dp, tint.copy(alpha = if (enabled) 0.65f else 0.28f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = label,
            color = tint,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val ActivationCost.italianLabel: String
    get() = when (this) {
        ActivationCost.ACTION -> "Azione"
        ActivationCost.BONUS_ACTION -> "Azione Bonus"
        ActivationCost.REACTION -> "Reazione"
        ActivationCost.LEGENDARY_ACTION -> "Azione Leggendaria"
        ActivationCost.NONE -> "Gratuita"
    }

private val D20Mode.italianLabel: String
    get() = when (this) {
        D20Mode.NORMAL -> "Normale"
        D20Mode.ADVANTAGE -> "Vantaggio"
        D20Mode.DISADVANTAGE -> "Svantaggio"
    }

/**
 * Comandi del turno.
 *
 * Le capacita' vengono lette dallo snapshot del combattente attivo: e' il motore
 * a decidere se un'azione e' legale, qui si mostra soltanto cio' che possiede.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandBar(viewModel: BattleViewModel, modifier: Modifier = Modifier) {
    val activeId = viewModel.activeCombatantId
    val abilities = activeId?.let { viewModel.abilities(it) }.orEmpty()
    val budget = activeId?.let { viewModel.budget(it) }

    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Night)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            abilities.forEach { ability ->
                val affordable = when (ability.activationCost()) {
                    ActivationCost.ACTION -> budget?.actionAvailable() ?: false
                    ActivationCost.BONUS_ACTION -> budget?.bonusActionAvailable() ?: false
                    ActivationCost.REACTION -> budget?.reactionAvailable() ?: false
                    else -> true
                }
                GameButton(
                    label = "▶ ${ability.name()}",
                    subtitle = "${ability.activationCost().italianLabel} · +${ability.attackBonus()} · ${ability.rangeFeet()} ft",
                    accent = Palette.Gold,
                    enabled = affordable,
                    onClick = { viewModel.attack(ability.id()) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Vantaggio e Svantaggio non si sommano: sono tre stati esclusivi.
            D20Mode.entries.forEach { mode ->
                val selected = viewModel.rollMode == mode
                GameButton(
                    label = mode.italianLabel,
                    accent = if (selected) Palette.Party else Palette.TextMuted,
                    onClick = { viewModel.rollMode = mode },
                )
            }

            Row(Modifier.weight(1f)) {}

            GameButton(
                label = "↶ Annulla",
                accent = Palette.TextMuted,
                enabled = viewModel.canUndo,
                onClick = { viewModel.undo() },
            )
            GameButton(
                label = "Fine turno ⏭",
                accent = Palette.Heal,
                onClick = { viewModel.endTurn() },
            )
        }
    }
}
