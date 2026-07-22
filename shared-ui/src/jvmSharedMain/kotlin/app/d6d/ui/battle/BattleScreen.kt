package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.CombatStatus
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.Faction
import app.d6d.ui.components.VerticalResizeHandle
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
        BattleTopBar(viewModel, sessions, compact)
        SessionMenuDialog(sessions)

        viewModel.message?.let { text ->
            Text(
                text = "Avviso · $text",
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
    val density = LocalDensity.current
    var squadWidth by remember { mutableStateOf(230.dp) }
    var enemyWidth by remember { mutableStateOf(310.dp) }

    Row(modifier) {
        Rail(
            viewModel = viewModel,
            title = "Squadra",
            ids = viewModel.partyIds,
            faction = Faction.PARTY,
            modifier = Modifier.width(squadWidth),
        )

        // Trascinando verso destra la squadra si allarga a scapito del palco.
        VerticalResizeHandle(
            onDrag = { dragPx ->
                squadWidth = (squadWidth + with(density) { dragPx.toDp() })
                    .coerceIn(150.dp, 420.dp)
            },
        )

        Column(Modifier.weight(1f)) {
            BattleStage(viewModel, portraits, Modifier.weight(1f))
            CommandBar(viewModel, compact = false)
        }

        // Il bordo sinistro dei nemici: trascinandolo verso sinistra la colonna cresce.
        VerticalResizeHandle(
            onDrag = { dragPx ->
                enemyWidth = (enemyWidth - with(density) { dragPx.toDp() })
                    .coerceIn(200.dp, 480.dp)
            },
        )

        Column(Modifier.width(enemyWidth)) {
            Rail(
                viewModel = viewModel,
                title = "Nemici",
                ids = viewModel.enemyIds,
                faction = Faction.ENEMY,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            CollapsibleBattleLog(viewModel)
        }
    }
}

/** Registro desktop sotto i nemici: il bordo superiore si trascina per ridimensionare o collassare. */
@Composable
private fun CollapsibleBattleLog(viewModel: BattleViewModel) {
    val density = LocalDensity.current
    val minHeightPx = with(density) { 130.dp.toPx() }
    val maxHeightPx = with(density) { 360.dp.toPx() }
    val defaultHeightPx = with(density) { 230.dp.toPx() }
    var expandedHeightPx by remember { mutableStateOf(defaultHeightPx) }
    var collapsed by remember { mutableStateOf(false) }
    val panelHeight = if (collapsed) 42.dp else with(density) { expandedHeightPx.toDp() }

    Column(
        Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .background(Palette.Abyss)
            .border(1.dp, Palette.Line),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Surface)
                .pointerInput(collapsed) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            if (collapsed && dragAmount < 0f) {
                                expandedHeightPx = defaultHeightPx
                                collapsed = false
                            } else if (!collapsed) {
                                expandedHeightPx = (expandedHeightPx - dragAmount)
                                    .coerceIn(with(density) { 72.dp.toPx() }, maxHeightPx)
                            }
                        },
                        onDragEnd = {
                            if (!collapsed && expandedHeightPx < minHeightPx) {
                                expandedHeightPx = defaultHeightPx
                                collapsed = true
                            }
                        },
                    )
                }
                .padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "↕ REGISTRO EVENTI",
                    color = Palette.Gold,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!collapsed) {
                    Text(
                        text = "Trascina verso il basso per collassare",
                        color = Palette.TextFaint,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                text = if (collapsed) "Apri ↑" else "Collassa ↓",
                color = Palette.TextMuted,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clickable {
                    if (collapsed) expandedHeightPx = defaultHeightPx
                    collapsed = !collapsed
                }.padding(5.dp),
            )
        }
        if (!collapsed) {
            BattleLog(
                viewModel = viewModel,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                showHeader = false,
            )
        }
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
                    selected = tab == entry,
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

        CommandBar(viewModel, compact = true)
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
private fun BattleTopBar(
    viewModel: BattleViewModel,
    sessions: SessionManager,
    compact: Boolean,
) {
    if (compact) {
        Column(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BattleMark()
                BattleTitle(sessions, modifier = Modifier.weight(1f))
                Chip(text = "Round ${viewModel.round}", color = Palette.Gold)
                Chip(text = viewModel.status.italianLabel, color = viewModel.status.tint)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TurnOrderStrip(viewModel, Modifier.weight(1f))
                EditModeButton(viewModel)
                SessionMenuButton(sessions)
            }
        }
        return
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BattleTitle(sessions)

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
                if (viewModel.isSimultaneousTurn) Chip("Turno simultaneo", Palette.GoldBright)
            }
            TurnOrderStrip(viewModel)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (viewModel.status == CombatStatus.DRAFT || viewModel.status == CombatStatus.READY) {
                GameButton(
                    label = if (viewModel.simultaneousTies) "Parità insieme" else "Parità separate",
                    accent = if (viewModel.simultaneousTies) Palette.GoldBright else Palette.TextMuted,
                    selected = viewModel.simultaneousTies,
                    onClick = { viewModel.simultaneousTies = !viewModel.simultaneousTies },
                )
            }
            EditModeButton(viewModel)
            SessionMenuButton(sessions)
            Chip(text = "Round ${viewModel.round}", color = Palette.Gold)
            Chip(text = viewModel.status.italianLabel, color = viewModel.status.tint)
        }
    }
}

@Composable
private fun BattleMark() {
    Box(
        Modifier
            .background(Palette.Gold, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(
            text = "T",
            color = Palette.Abyss,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun BattleTitle(sessions: SessionManager, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = sessions.currentDisplayName.ifBlank { "Incontro senza nome" },
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (sessions.hasUnsavedChanges) "Modifiche non salvate" else "Sessione salvata",
            color = if (sessions.hasUnsavedChanges) Palette.Bloodied else Palette.TextMuted,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EditModeButton(viewModel: BattleViewModel) {
    GameButton(
        label = if (viewModel.editMode) "Modifica attiva" else "Modifica",
        subtitle = if (viewModel.editMode) "Trascina i token per riposizionarli liberamente" else null,
        accent = if (viewModel.editMode) Palette.Heal else Palette.TextMuted,
        selected = viewModel.editMode,
        onClick = { viewModel.editMode = !viewModel.editMode },
    )
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
