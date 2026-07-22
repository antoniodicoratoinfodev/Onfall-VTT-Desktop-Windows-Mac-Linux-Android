package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.ui.components.Faction
import app.d6d.ui.components.color
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.Palette

/**
 * Ordine dei turni, in piccolo e al centro dell'intestazione.
 *
 * Ogni riquadro e' un turno, non un combattente: quando due creature hanno
 * pareggiato l'iniziativa e il tavolo ha scelto di farle giocare insieme, il
 * riquadro le contiene entrambe e mostra l'indicatore di simultaneita'.
 */
@Composable
fun TurnOrderStrip(viewModel: BattleViewModel, modifier: Modifier = Modifier) {
    val groups = viewModel.turnGroups
    if (groups.isEmpty()) return

    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEachIndexed { index, group ->
            TurnChip(
                viewModel = viewModel,
                group = group,
                current = index == viewModel.turnIndex,
            )
        }
    }
}

@Composable
private fun TurnChip(
    viewModel: BattleViewModel,
    group: List<String>,
    current: Boolean,
) {
    val simultaneous = group.size > 1
    val allDown = group.all { viewModel.combatant(it)?.defeated() == true }
    val selectedTarget = viewModel.effectiveTargetId()
    val targeted = selectedTarget != null && selectedTarget in group
    val names = group.joinToString(" + ") { viewModel.name(it) }

    // Un gruppo misto (alleati e nemici insieme) non ha un colore di fazione unico.
    val factions = group.map { if (viewModel.isParty(it)) Faction.PARTY else Faction.ENEMY }.toSet()
    val accent = when {
        allDown -> Palette.TextFaint
        factions.size > 1 -> Palette.Gold
        else -> factions.first().color
    }

    val shape = RoundedCornerShape(7.dp)
    val outline = when {
        current -> Modifier.border(1.5.dp, Palette.GoldBright, shape)
        targeted -> Modifier.border(1.5.dp, accent.copy(alpha = 0.9f), shape)
        else -> Modifier
    }
    val chipState = buildList {
        if (current) add("Turno corrente")
        if (targeted) add("Bersaglio selezionato")
        if (simultaneous) add("Turno simultaneo")
        if (allDown) add("Tutti sconfitti")
    }.ifEmpty { listOf("Turno successivo") }.joinToString(". ")

    Column(
        Modifier
            .heightIn(min = 44.dp)
            .widthIn(max = 220.dp)
            .background(
                when {
                    current -> Palette.Gold.copy(alpha = 0.14f)
                    targeted -> accent.copy(alpha = 0.1f)
                    else -> Palette.Night
                },
                shape,
            )
            .then(outline)
            .semantics {
                contentDescription = "Turno di $names"
                stateDescription = chipState
                selected = targeted
            }
            .clickable(
                role = Role.Button,
                onClickLabel = "Seleziona ${viewModel.name(group.first())} come bersaglio",
            ) { viewModel.selectedTargetId = group.first() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .alpha(if (allDown) 0.45f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = names,
                color = when {
                    current -> Palette.Text
                    targeted -> accent
                    else -> Palette.TextMuted
                },
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = buildString {
                append("Iniziativa ${viewModel.initiativeScore(group.first()) ?: "—"}")
                if (simultaneous) append(" · insieme")
                if (targeted) append(" · bersaglio")
            },
            color = when {
                current -> Palette.Gold
                targeted -> accent
                else -> Palette.TextFaint
            },
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
