package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.sheet.feetWithMetres
import app.d6d.ui.components.Chip
import app.d6d.ui.components.ConditionChip
import app.d6d.ui.components.Faction
import app.d6d.ui.components.HealthBar
import app.d6d.ui.components.color
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.Palette

/**
 * Il centro della schermata: mappa tattica con le targhe sovrapposte agli angoli.
 *
 * La mappa prende tutto lo spazio perche' e' li' che si gioca; bersaglio e attore
 * di turno restano leggibili in due riquadri compatti, cosi' non si perde nulla
 * di quello che serve a decidere.
 */
@Composable
fun BattleStage(
    viewModel: BattleViewModel,
    portraits: PortraitRepository,
    modifier: Modifier = Modifier,
) {
    var cellSize by remember { mutableStateOf(46.dp) }
    var showGrid by remember { mutableStateOf(true) }

    Column(modifier.fillMaxSize()) {
        if (viewModel.mapConfigured) {
            MapControls(
                viewModel = viewModel,
                portraits = portraits,
                cellSize = cellSize,
                onCellSizeChange = { cellSize = it },
                showGrid = showGrid,
                onShowGridChange = { showGrid = it },
            )
        }

        Box(Modifier.weight(1f)) {
            BattleMapView(
                viewModel = viewModel,
                portraits = portraits,
                cellSize = cellSize,
                showGrid = showGrid,
                modifier = Modifier.fillMaxSize(),
            )

            viewModel.effectiveTargetId()?.let { targetId ->
                StagePlate(
                    viewModel = viewModel,
                    combatantId = targetId,
                    role = "Bersaglio selezionato",
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                )
            }

            viewModel.activeCombatantId?.let { activeId ->
                StagePlate(
                    viewModel = viewModel,
                    combatantId = activeId,
                    role = if (viewModel.isSimultaneousTurn) "Turno condiviso" else "Turno attivo",
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                )
            }

            if (viewModel.mapConfigured) {
                MapLegend(viewModel, Modifier.align(Alignment.BottomEnd).padding(10.dp))
            }
        }
    }
}

/**
 * Legenda: scala della griglia e distanza fra attore e bersaglio.
 *
 * La distanza compare solo quando entrambi sono posizionati, perche' senza
 * coordinate complete il documento vieta di dichiararla.
 */
@Composable
private fun MapLegend(viewModel: BattleViewModel, modifier: Modifier = Modifier) {
    val grid = viewModel.battleMap.grid()
    val active = viewModel.activeCombatantId
    val target = viewModel.effectiveTargetId()
    val distance = if (active != null && target != null) viewModel.distanceFeet(active, target) else null
    val movement = active?.let { viewModel.budget(it)?.movementRemainingFeet() }
    val description = buildString {
        append("Scala della mappa: una casella equivale a ${feetWithMetres(grid.feetPerSquare())}.")
        if (movement != null) append(" Movimento residuo: ${feetWithMetres(movement)}.")
        append(
            distance?.let { " Distanza dal bersaglio: ${feetWithMetres(it)}." }
                ?: " Distanza dal bersaglio non determinata.",
        )
    }

    Column(
        modifier
            .background(Palette.Abyss.copy(alpha = 0.86f), RoundedCornerShape(8.dp))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = "1 casella = ${feetWithMetres(grid.feetPerSquare())}",
            color = Palette.Gold,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
        )
        if (movement != null) {
            Text(
                text = "Movimento residuo: ${feetWithMetres(movement)}",
                color = Palette.Party,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = distance?.let { "Distanza dal bersaglio: ${feetWithMetres(it)}" }
                ?: "Distanza non determinata",
            color = if (distance != null) Palette.Text else Palette.TextFaint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Targa compatta di un combattente, sovrapposta a un angolo della mappa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StagePlate(
    viewModel: BattleViewModel,
    combatantId: String,
    role: String,
    modifier: Modifier = Modifier,
) {
    val combatant = viewModel.combatant(combatantId) ?: return
    val snapshot = combatant.snapshot()
    val faction = if (viewModel.isParty(combatantId)) Faction.PARTY else Faction.ENEMY
    val isTarget = viewModel.effectiveTargetId() == combatantId
    val isActive = viewModel.isActive(combatantId)
    val accent = if (isTarget) faction.color else Palette.Gold
    val state = buildString {
        append("${combatant.currentHitPoints()} punti ferita su ${snapshot.maxHitPoints()}.")
        if (isTarget) append(" Bersaglio selezionato.")
        if (isActive) append(" Turno attivo.")
        if (combatant.defeated()) append(" Sconfitto.")
    }

    Column(
        modifier
            .width(232.dp)
            .background(
                if (isTarget) faction.color.copy(alpha = 0.13f) else Palette.Surface.copy(alpha = 0.93f),
                RoundedCornerShape(10.dp),
            )
            .border(
                if (isTarget) 2.dp else 1.dp,
                accent.copy(alpha = if (isTarget) 0.9f else 0.58f),
                RoundedCornerShape(10.dp),
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$role: ${snapshot.name()}"
                stateDescription = state
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = role.uppercase(),
            color = accent,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = snapshot.name(),
            color = Palette.Text,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
        )

        HealthBar(
            current = combatant.currentHitPoints(),
            max = snapshot.maxHitPoints(),
            temporary = combatant.temporaryHitPoints(),
            height = 9.dp,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("${combatant.currentHitPoints()}/${snapshot.maxHitPoints()} PF", Palette.Text)
            Chip("CA ${snapshot.armorClass()}", Palette.Gold)
            if (combatant.bloodied() && !combatant.defeated()) {
                Chip("INSANGUINATO", Palette.Bloodied)
            }
        }

        // La taglia non si sceglie qui: e' un'informazione dell'attore, si imposta
        // nel Compendio e da li' determina la dimensione del segnaposto sulla mappa.

        if (combatant.concentration() != null) {
            Text(
                text = "Concentrazione",
                color = Palette.Temporary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (combatant.conditions().isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                combatant.conditions().take(4).forEach {
                    ConditionChip(type = it.type(), rounds = it.duration().remainingOccurrences())
                }
            }
        }
    }
}
