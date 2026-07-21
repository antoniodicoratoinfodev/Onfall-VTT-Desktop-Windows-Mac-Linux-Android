package app.d6d.ui.battle

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

    // Pulsazione tenue sul turno corrente, per farlo trovare a colpo d'occhio.
    val pulse by rememberInfiniteTransition(label = "turnPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "turnPulseValue",
    )

    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEachIndexed { index, group ->
            if (index > 0) {
                Text(
                    text = "›",
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TurnChip(
                viewModel = viewModel,
                group = group,
                current = index == viewModel.turnIndex,
                pulse = pulse,
            )
        }
    }
}

@Composable
private fun TurnChip(
    viewModel: BattleViewModel,
    group: List<String>,
    current: Boolean,
    pulse: Float,
) {
    val simultaneous = group.size > 1
    val allDown = group.all { viewModel.combatant(it)?.defeated() == true }

    // Un gruppo misto (alleati e nemici insieme) non ha un colore di fazione unico.
    val factions = group.map { if (viewModel.isParty(it)) Faction.PARTY else Faction.ENEMY }.toSet()
    val accent = when {
        allDown -> Palette.TextFaint
        factions.size > 1 -> Palette.Gold
        else -> factions.first().color
    }

    val border = if (current) Palette.GoldBright else accent.copy(alpha = 0.4f)

    Column(
        Modifier
            .background(
                if (current) Palette.Gold.copy(alpha = 0.13f * pulse) else Palette.Night,
                RoundedCornerShape(6.dp),
            )
            .border(if (current) 1.5.dp else 1.dp, border, RoundedCornerShape(6.dp))
            .clickable { viewModel.selectedTargetId = group.first() }
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .alpha(if (allDown) 0.45f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (simultaneous) {
                // Indicatore dei turni giocati insieme.
                Text(
                    text = "⇄",
                    color = Palette.GoldBright,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = group.joinToString(" + ") { viewModel.name(it) },
                color = if (current) Palette.Text else Palette.TextMuted,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = buildString {
                append(viewModel.initiativeScore(group.first()) ?: "—")
                if (simultaneous) append("  insieme")
            },
            color = if (current) Palette.Gold else Palette.TextFaint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
