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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
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
 *
 * Con la modalita' modifica attiva la striscia diventa modificabile: si tocca un
 * riquadro per renderlo il turno corrente e si usano ◀ ▶ per riordinarlo.
 */
@Composable
fun TurnOrderStrip(
    viewModel: BattleViewModel,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
) {
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
                editing = editing,
                canMoveEarlier = index > 0,
                canMoveLater = index < groups.lastIndex,
            )
        }
    }
}

@Composable
private fun TurnChip(
    viewModel: BattleViewModel,
    group: List<String>,
    current: Boolean,
    editing: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
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
        editing -> Modifier.border(1.dp, Palette.Gold.copy(alpha = 0.4f), shape)
        targeted -> Modifier.border(1.5.dp, accent.copy(alpha = 0.9f), shape)
        else -> Modifier.border(1.dp, Palette.Line, shape)
    }
    val chipState = buildList {
        if (current) add("Turno corrente")
        if (targeted) add("Bersaglio selezionato")
        if (simultaneous) add("Turno simultaneo")
        if (allDown) add("Tutti sconfitti")
    }.ifEmpty { listOf("Turno successivo") }.joinToString(". ")

    // In modifica il tocco imposta il turno corrente; altrimenti seleziona il bersaglio.
    val onChipClick: () -> Unit = if (editing) {
        { viewModel.setCurrentTurn(group.first()) }
    } else {
        { viewModel.selectedTargetId = group.first() }
    }

    Column(
        Modifier
            .heightIn(min = 44.dp)
            .widthIn(max = 220.dp)
            .background(
                when {
                    // Il turno corrente e' una velatura d'oro che sfuma verso il
                    // basso: acceso in cima, quieto sotto, come una lama di luce.
                    current -> Brush.verticalGradient(
                        listOf(Palette.Gold.copy(alpha = 0.24f), Palette.Gold.copy(alpha = 0.08f)),
                    )
                    targeted -> SolidColor(accent.copy(alpha = 0.1f))
                    else -> SolidColor(Palette.Surface)
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
                onClickLabel = if (editing) {
                    "Rendi corrente il turno di ${viewModel.name(group.first())}"
                } else {
                    "Seleziona ${viewModel.name(group.first())} come bersaglio"
                },
                onClick = onChipClick,
            )
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

        if (editing) {
            Row(
                Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoveButton("◀", enabled = canMoveEarlier) { viewModel.moveTurn(group.first(), -1) }
                Text(
                    text = if (current) "corrente" else "rendi corrente",
                    color = if (current) Palette.GoldBright else Palette.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable(onClick = onChipClick).padding(horizontal = 4.dp, vertical = 1.dp),
                )
                MoveButton("▶", enabled = canMoveLater) { viewModel.moveTurn(group.first(), +1) }
            }
        }
    }
}

/** Comando minuto per spostare un turno nella coda. */
@Composable
private fun MoveButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Palette.Gold else Palette.TextFaint
    Text(
        text = glyph,
        color = tint,
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(Palette.SurfaceHigh, RoundedCornerShape(4.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
