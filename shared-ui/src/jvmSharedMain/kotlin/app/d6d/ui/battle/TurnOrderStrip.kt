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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
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
import app.d6d.ui.i18n.strings
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
    showInitiative: Boolean = true,
    cardScale: Float = 1f,
) {
    val groups = viewModel.turnGroups
    if (groups.isEmpty()) return
    val layoutScale = cardScale.coerceIn(0.9f, 1.12f)

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
                showInitiative = showInitiative,
                layoutScale = layoutScale,
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
    showInitiative: Boolean,
    layoutScale: Float,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
) {
    val simultaneous = group.size > 1
    val allDown = group.all {
        viewModel.combatant(it)?.let { combatant -> combatant.defeated() || combatant.dead() } == true
    }
    val selectedTarget = viewModel.selectedTargetId
    val targeted = selectedTarget != null && selectedTarget in group
    val inspected = viewModel.inspectedCombatantId in group
    val names = group.joinToString(" + ") { viewModel.name(it) }

    // Un gruppo misto (alleati e nemici insieme) non ha un colore di fazione unico.
    val factions = group.map { if (viewModel.isParty(it)) Faction.PARTY else Faction.ENEMY }.toSet()
    val turnFaction = factions.singleOrNull()
    val accent = when {
        allDown -> Palette.TextFaint
        factions.size > 1 -> Palette.Gold
        else -> factions.first().color
    }

    val shape = RoundedCornerShape(7.dp)
    val outline = when {
        allDown -> Modifier.border(1.dp, Palette.TextFaint.copy(alpha = 0.7f), shape)
        current -> Modifier.border(1.5.dp, Palette.GoldBright, shape)
        editing -> Modifier.border(1.dp, Palette.Gold.copy(alpha = 0.4f), shape)
        targeted -> Modifier.border(1.5.dp, accent.copy(alpha = 0.9f), shape)
        inspected -> Modifier.border(1.5.dp, Palette.Text.copy(alpha = 0.8f), shape)
        else -> Modifier.border(1.dp, Palette.Line, shape)
    }
    val words = strings.battle
    val chipState = buildList {
        if (current) add(words.currentTurn)
        if (targeted) add(words.selectedTarget)
        if (inspected) add(words.inspectedSheet)
        if (simultaneous) add(words.simultaneousTurn)
        if (allDown) add(words.allAtZeroTurnSkipped)
    }.ifEmpty { listOf(words.nextTurn) }.joinToString(". ")

    // La mira ha precedenza sulla modifica: dopo aver scelto un'abilita', il clic
    // deve sempre significare "questo e' il bersaglio". Fuori dalla mira ispeziona.
    fun onMemberClick(combatantId: String) {
        when {
            viewModel.singleTargeting != null -> viewModel.onCombatantClicked(combatantId)
            editing -> viewModel.setCurrentTurn(combatantId)
            else -> viewModel.onCombatantClicked(combatantId)
        }
    }
    val primaryId = group.firstOrNull {
        viewModel.combatant(it)?.let { combatant -> !combatant.defeated() && !combatant.dead() } == true
    } ?: group.first()
    val onChipClick: () -> Unit = { onMemberClick(primaryId) }

    Column(
        Modifier
            // Cambiano solo le misure della card. La tipografia resta alla densita'
            // nativa, evitando il nuovo hinting dei glyph durante il resize.
            .heightIn(min = 44.dp * layoutScale)
            .widthIn(max = 220.dp * layoutScale)
            .clip(shape)
            .background(
                when {
                    allDown -> SolidColor(Palette.SurfaceHigh)
                    // Il turno corrente e' una velatura d'oro che sfuma verso il
                    // basso: acceso in cima, quieto sotto, come una lama di luce.
                    current -> Brush.verticalGradient(
                        listOf(Palette.Gold.copy(alpha = 0.24f), Palette.Gold.copy(alpha = 0.08f)),
                    )
                    targeted -> SolidColor(accent.copy(alpha = 0.1f))
                    inspected -> SolidColor(Palette.Text.copy(alpha = 0.06f))
                    else -> SolidColor(Palette.Surface)
                },
                shape,
            )
            // Stessa strip delle carte laterali: blu per la squadra, rossa per
            // i nemici. Un turno misto resta neutro.
            .drawBehind {
                turnFaction?.let { faction ->
                    drawRect(
                        color = faction.color.copy(alpha = 0.9f),
                        size = Size(3.dp.toPx(), size.height),
                    )
                }
            }
            .then(outline)
            .semantics {
                contentDescription = words.turnOf(names)
                stateDescription = chipState
                selected = inspected
            }
            .clickable(
                role = Role.Button,
                onClickLabel = if (viewModel.singleTargeting != null) {
                    words.chooseAsTarget(viewModel.name(primaryId))
                } else if (editing) {
                    words.makeTurnCurrent(viewModel.name(group.first()))
                } else {
                    words.showAbilitiesOf(viewModel.name(primaryId))
                },
                onClick = onChipClick,
            )
            .padding(
                horizontal = 9.dp * layoutScale,
                vertical = 5.dp * layoutScale,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Quando l'iniziativa e' nascosta resta una sola riga: centrare l'intero
        // blocco evita che il nome rimanga appoggiato al bordo superiore.
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            group.forEachIndexed { index, id ->
                if (index > 0) {
                    Text("+", color = Palette.TextFaint, style = MaterialTheme.typography.bodySmall)
                }
                val memberDown = viewModel.combatant(id)?.let { it.defeated() || it.dead() } == true
                Text(
                    text = viewModel.name(id),
                    color = when {
                        memberDown -> Palette.TextFaint
                        allDown -> Palette.TextFaint
                        current -> Palette.Text
                        selectedTarget == id -> accent
                        viewModel.inspectedCombatantId == id -> Palette.Text
                        else -> Palette.TextMuted
                    },
                    fontWeight = if (current && !memberDown) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = if (simultaneous) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = words.select(viewModel.name(id)),
                        ) { onMemberClick(id) }
                    } else {
                        Modifier
                    },
                )
            }
        }
        val secondaryText = buildList {
            if (showInitiative) {
                add(words.initiativeIs(viewModel.initiativeScore(group.first())?.toString() ?: "—"))
            }
            if (simultaneous) add(words.togetherShort)
            if (targeted) add(words.targetedShort)
            if (allDown) add(words.zeroHitPointsTurnSkipped)
        }.joinToString(" · ")
        if (secondaryText.isNotEmpty()) {
            Text(
                text = secondaryText,
                color = when {
                    allDown -> Palette.TextFaint
                    current -> Palette.Gold
                    targeted -> accent
                    else -> Palette.TextFaint
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (editing) {
            Row(
                Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoveButton("◀", enabled = canMoveEarlier, muted = allDown) {
                    viewModel.moveTurn(group.first(), -1)
                }
                Text(
                    text = if (allDown) words.turnSkipped else if (current) words.isCurrent else words.makeCurrent,
                    color = if (allDown) Palette.TextFaint else if (current) Palette.GoldBright else Palette.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clickable(enabled = !allDown, onClick = onChipClick)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
                MoveButton("▶", enabled = canMoveLater, muted = allDown) {
                    viewModel.moveTurn(group.first(), +1)
                }
            }
        }
    }
}

/** Comando minuto per spostare un turno nella coda. */
@Composable
private fun MoveButton(glyph: String, enabled: Boolean, muted: Boolean = false, onClick: () -> Unit) {
    val tint = if (enabled && !muted) Palette.Gold else Palette.TextFaint
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
