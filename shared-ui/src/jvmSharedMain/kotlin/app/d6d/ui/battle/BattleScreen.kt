package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.CombatStatus
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.Faction
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.session.SessionManager
import app.d6d.ui.session.SessionMenuButton
import app.d6d.ui.session.SessionMenuDialog
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.Palette

/**
 * Schermata di combattimento.
 *
 * `compact` distingue le due shell che il documento chiede di NON unificare:
 * il desktop tiene squadra, palco e nemici visibili insieme, il mobile mostra
 * una superficie alla volta. Il motore e lo stato sono gli stessi.
 */
@Composable
fun BattleScreen(
    viewModel: BattleViewModel,
    portraits: PortraitRepository,
    sessions: SessionManager,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Palette.Night)) {
        BattleTopBar(viewModel, sessions)
        SessionMenuDialog(sessions)

        viewModel.message?.let { text ->
            Text(
                text = "⚠  $text",
                color = Palette.Bloodied,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.Bloodied.copy(alpha = 0.13f))
                    .clickable { viewModel.dismissMessage() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }

        if (compact) {
            CompactBattleBody(viewModel, portraits, Modifier.weight(1f))
        } else {
            WideBattleBody(viewModel, portraits, Modifier.weight(1f))
        }
    }
}

/** Layout desktop: tre aree persistenti piu' il registro sempre a vista. */
@Composable
private fun WideBattleBody(
    viewModel: BattleViewModel,
    portraits: PortraitRepository,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Rail(
                viewModel = viewModel,
                title = "Squadra",
                ids = viewModel.partyIds,
                faction = Faction.PARTY,
                modifier = Modifier.width(252.dp),
            )

            Column(Modifier.weight(1f)) {
                BattleStage(viewModel, portraits, Modifier.weight(1f))
                CommandBar(viewModel)
            }

            Rail(
                viewModel = viewModel,
                title = "Nemici",
                ids = viewModel.enemyIds,
                faction = Faction.ENEMY,
                modifier = Modifier.width(252.dp),
            )
        }
        BattleLog(viewModel, Modifier.height(158.dp))
    }
}

/** Layout mobile: una superficie alla volta, comandi sempre raggiungibili col pollice. */
@Composable
private fun CompactBattleBody(
    viewModel: BattleViewModel,
    portraits: PortraitRepository,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(CompactTab.PALCO) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactTab.entries.forEach { entry ->
                GameButton(
                    label = entry.label,
                    accent = if (tab == entry) Palette.Gold else Palette.TextMuted,
                    onClick = { tab = entry },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                CompactTab.PALCO -> BattleStage(viewModel, portraits)
                CompactTab.SQUADRA -> Rail(
                    viewModel = viewModel,
                    title = "Squadra",
                    ids = viewModel.partyIds,
                    faction = Faction.PARTY,
                    modifier = Modifier.fillMaxSize(),
                )

                CompactTab.NEMICI -> Rail(
                    viewModel = viewModel,
                    title = "Nemici",
                    ids = viewModel.enemyIds,
                    faction = Faction.ENEMY,
                    modifier = Modifier.fillMaxSize(),
                )

                CompactTab.REGISTRO -> BattleLog(viewModel, Modifier.fillMaxSize())
            }
        }

        CommandBar(viewModel)
    }
}

private enum class CompactTab(val label: String) {
    PALCO("Mappa"),
    SQUADRA("Squadra"),
    NEMICI("Nemici"),
    REGISTRO("Registro"),
}

@Composable
private fun Rail(
    viewModel: BattleViewModel,
    title: String,
    ids: List<String>,
    faction: Faction,
    modifier: Modifier = Modifier,
) {
    val standing = ids.count { viewModel.combatant(it)?.defeated() == false }
    Column(
        modifier
            .fillMaxSize()
            .background(Palette.Surface.copy(alpha = 0.45f))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Eyebrow(title, color = if (faction == Faction.PARTY) Palette.Party else Palette.Enemy)
            Text(
                text = "$standing/${ids.size} in piedi",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(ids) { id ->
                CombatantRailCard(viewModel = viewModel, combatantId = id, faction = faction)
            }
        }
    }
}

@Composable
private fun BattleTopBar(viewModel: BattleViewModel, sessions: SessionManager) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(Palette.Gold, RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Text(
                text = "⚔",
                color = Palette.Abyss,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column {
            Text(
                text = "Cripta dei Predoni",
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Contenuti originali · Compatibile con 5.5e / SRD",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Ordine dei turni al centro, in piccolo.
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Eyebrow("Ordine dei turni")
                if (viewModel.isSimultaneousTurn) {
                    Chip("⇄ TURNO SIMULTANEO", Palette.GoldBright)
                }
            }
            TurnOrderStrip(viewModel)
        }

        GameButton(
            label = if (viewModel.simultaneousTies) "⇄ Parita' insieme" else "⇄ Parita' separate",
            accent = if (viewModel.simultaneousTies) Palette.GoldBright else Palette.TextMuted,
            onClick = { viewModel.simultaneousTies = !viewModel.simultaneousTies },
        )
        GameButton(
            label = if (viewModel.editMode) "✎ Modifica ON" else "✎ Edit",
            accent = if (viewModel.editMode) Palette.Heal else Palette.TextMuted,
            onClick = { viewModel.editMode = !viewModel.editMode },
        )

        SessionMenuButton(sessions)

        Chip(text = "ROUND ${viewModel.round}", color = Palette.Gold)
        Chip(text = viewModel.status.italianLabel, color = viewModel.status.tint)
    }
}

private val CombatStatus.italianLabel: String
    get() = when (this) {
        CombatStatus.DRAFT -> "Bozza"
        CombatStatus.READY -> "Pronto"
        CombatStatus.ACTIVE -> "Attivo"
        CombatStatus.PAUSED -> "In pausa"
        CombatStatus.RESOLVED -> "Risolto"
    }

private val CombatStatus.tint
    get() = when (this) {
        CombatStatus.ACTIVE -> Palette.Heal
        CombatStatus.PAUSED -> Palette.Bloodied
        CombatStatus.RESOLVED -> Palette.TextMuted
        else -> Palette.Party
    }
