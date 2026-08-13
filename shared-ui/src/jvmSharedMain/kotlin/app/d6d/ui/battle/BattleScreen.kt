package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.CombatStatus
import app.d6d.persistence.session.SessionSummary
import app.d6d.ui.components.Chip
import app.d6d.ui.components.CombatantPortrait
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.Faction
import app.d6d.ui.components.HorizontalResizeHandle
import app.d6d.ui.components.PanelScrollbar
import app.d6d.ui.components.VerticalResizeHandle
import kotlin.math.roundToInt
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.layout.LocalUiLayout
import app.d6d.ui.layout.TurnOrderDisplayMode
import app.d6d.ui.roster.RosterViewModel
import app.d6d.ui.session.SessionManager
import app.d6d.ui.session.SessionMenuButton
import app.d6d.ui.session.SessionMenuDialog
import app.d6d.ui.session.SessionWorkspace
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.state.EnemyCpuSpeed
import app.d6d.ui.state.italianLabel
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.theme.Palette
import kotlinx.coroutines.delay

private val TURN_ORDER_LABEL_SPACE = 24.dp
private val TURN_ORDER_EDIT_EXTRA_HEIGHT = 28.dp
private val TURN_ORDER_COMPACT_BREAKPOINT = 960.dp
private val TURN_ORDER_MIN_HEIGHT = 56.dp
private val TURN_ORDER_MAX_HEIGHT = 240.dp

/**
 * Seam testabile dello scheduler: dopo l'attesa ricontrolla sia guard sia identita' del turno.
 *
 * L'attesa iniziale separa il passaggio del turno dal primo comando nemico; le
 * pause fra un comando e il successivo appartengono invece alla riproduzione
 * ritmata del ViewModel.
 */
internal suspend fun scheduleEnemyCpuTurnIfStillCurrent(
    viewModel: BattleViewModel,
    scheduledTurnKey: String?,
    delayMillis: Long = viewModel.enemyCpuSpeed.openingDelayMillis,
) {
    if (!viewModel.shouldScheduleEnemyCpu || viewModel.enemyCpuTurnKey != scheduledTurnKey) return
    delay(delayMillis.coerceAtLeast(0L))
    if (viewModel.shouldScheduleEnemyCpu && viewModel.enemyCpuTurnKey == scheduledTurnKey) {
        viewModel.playEnemyCpuTurnPaced()
    }
}

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
    workspace: SessionWorkspace,
    roster: RosterViewModel,
    compact: Boolean,
    onOpenCombatantSheet: (String) -> Unit,
    onCreateRosterCharacter: () -> Unit,
    onCreateRosterCreature: () -> Unit,
    onOpenSavedSession: (SessionSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalUiLayout.current
    val density = LocalDensity.current
    val cpuTurnKey = viewModel.enemyCpuTurnKey
    LaunchedEffect(
        viewModel.sessionGeneration,
        cpuTurnKey,
        viewModel.enemyCpuEnabled,
        viewModel.editMode,
        viewModel.status,
        viewModel.enemyCpuTurnSuppressed,
        viewModel.enemyCpuBatchCompleted,
    ) {
        scheduleEnemyCpuTurnIfStillCurrent(viewModel, cpuTurnKey)
    }
    // Il fondale resta trasparente in tutta la cornice di battaglia. BattleStage
    // isola invece mappa, griglia e relativi controlli con una superficie opaca.
    Column(modifier.fillMaxSize()) {
        BattleTopBar(
            viewModel,
            sessions,
            workspace.openSessions.size,
            workspace.autosaveWarning != null,
            compact,
        )
        // Filo d'oro sotto l'intestazione: chiude la fascia dei turni come il
        // bordo inciso di un pannello, senza il peso di un bordo pieno.
        GoldenRule()
        EnemyCpuBanner(viewModel, compact)
        // Il bordo inferiore della fascia turni: trascinandolo verso il basso la
        // fascia cresce. Solo sul desktop e solo quando l'ordine turni e' visibile.
        if (!compact && layout.turnOrderDisplayMode != TurnOrderDisplayMode.HIDDEN) {
            HorizontalResizeHandle(
                onDrag = { dragPx ->
                    layout.topBarHeight = (layout.topBarHeight + with(density) { dragPx.toDp() })
                        .coerceIn(TURN_ORDER_MIN_HEIGHT, TURN_ORDER_MAX_HEIGHT)
                },
            )
        }
        SessionMenuDialog(
            manager = sessions,
            onOpenInNewTab = onOpenSavedSession,
            workspace = workspace,
        )

        viewModel.actionResolution?.let { resolution ->
            val tone = if (resolution.isHit) Palette.Heal else Palette.GoldBright
            Text(
                text = "Risolto subito · ${resolution.text}",
                color = tone,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tone.copy(alpha = 0.13f))
                    .clickable { viewModel.dismissActionResolution() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }

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
            CompactBattleBody(
                viewModel,
                portraits,
                roster,
                onOpenCombatantSheet,
                onCreateRosterCharacter,
                onCreateRosterCreature,
                Modifier.weight(1f),
            )
        } else {
            WideBattleBody(
                viewModel,
                portraits,
                roster,
                onOpenCombatantSheet,
                onCreateRosterCharacter,
                onCreateRosterCreature,
                Modifier.weight(1f),
            )
        }
    }
}

/** Layout desktop: tre aree persistenti piu' il registro sempre a vista. */
@Composable
private fun WideBattleBody(
    viewModel: BattleViewModel,
    portraits: PortraitRepository,
    roster: RosterViewModel,
    onOpenCombatantSheet: (String) -> Unit,
    onCreateRosterCharacter: () -> Unit,
    onCreateRosterCreature: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layout = LocalUiLayout.current
    // Trascinamento di un personaggio dalle barre laterali fino alla mappa.
    val dropTarget = remember { TokenPlacementDrag() }
    var overlayCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(modifier.onGloballyPositioned { overlayCoords = it }) {
        Row(Modifier.fillMaxSize()) {
            Rail(
                viewModel = viewModel,
                title = "Squadra",
                ids = viewModel.partyIds,
                faction = Faction.PARTY,
                roster = roster,
                onOpenSheet = onOpenCombatantSheet,
                onCreateRosterCharacter = onCreateRosterCharacter,
                onCreateRosterCreature = onCreateRosterCreature,
                modifier = Modifier.width(layout.squadWidth),
                dropTarget = dropTarget,
            )

            // Trascinando verso destra la squadra si allarga a scapito del palco.
            VerticalResizeHandle(
                onDrag = { dragPx ->
                    layout.squadWidth = (layout.squadWidth + with(density) { dragPx.toDp() })
                        .coerceIn(150.dp, 420.dp)
                },
            )

            Column(Modifier.weight(1f)) {
                // Le targhe flottanti non vivono nel palco ma nell'overlay a tutta
                // area (sotto), cosi' si possono trascinare anche sopra le barre.
                BattleStage(
                    viewModel,
                    portraits,
                    Modifier.weight(1f),
                    dropTarget = dropTarget,
                    floatingPlates = false,
                )
                // Il bordo superiore della fascia comandi: trascinandolo verso l'alto
                // la fascia cresce a scapito della mappa. Sparisce quando i comandi
                // sono contratti, che non hanno un'altezza da regolare.
                if (!layout.commandsCollapsed) {
                    HorizontalResizeHandle(
                        onDrag = { dragPx ->
                            layout.commandBarHeight = (layout.commandBarHeight - with(density) { dragPx.toDp() })
                                .coerceIn(160.dp, 520.dp)
                        },
                    )
                }
                CommandBar(viewModel, compact = false)
            }

            // Il bordo sinistro dei nemici: trascinandolo verso sinistra la colonna cresce.
            VerticalResizeHandle(
                onDrag = { dragPx ->
                    layout.enemyWidth = (layout.enemyWidth - with(density) { dragPx.toDp() })
                        .coerceIn(200.dp, 480.dp)
                },
            )

            Column(Modifier.width(layout.enemyWidth)) {
                Rail(
                    viewModel = viewModel,
                    title = "Nemici",
                    ids = viewModel.enemyIds,
                    faction = Faction.ENEMY,
                    roster = roster,
                    onOpenSheet = onOpenCombatantSheet,
                    onCreateRosterCharacter = onCreateRosterCharacter,
                    onCreateRosterCreature = onCreateRosterCreature,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    dropTarget = dropTarget,
                )
                CollapsibleBattleLog(viewModel)
            }
        }

        // Targhe flottanti: ospitate qui coprono l'intera area di battaglia, quindi
        // si spostano liberamente sopra barre, palco e registro.
        FloatingCombatantPlates(viewModel)

        TokenDragGhost(viewModel, dropTarget, overlayCoords)
    }
}

/** Segnaposto fantasma che segue il puntatore durante il trascinamento dalle barre. */
@Composable
private fun TokenDragGhost(
    viewModel: BattleViewModel,
    dropTarget: TokenPlacementDrag,
    overlayCoords: LayoutCoordinates?,
) {
    val id = dropTarget.activeId ?: return
    val combatant = viewModel.combatant(id) ?: return
    val snapshot = combatant.snapshot()
    val coords = overlayCoords ?: return
    val density = LocalDensity.current
    val diameterPx = with(density) { 46.dp.toPx() }

    Box(
        Modifier
            .offset {
                val local = coords.windowToLocal(dropTarget.windowPosition)
                IntOffset(
                    (local.x - diameterPx / 2f).roundToInt(),
                    (local.y - diameterPx / 2f).roundToInt(),
                )
            }
            .alpha(0.9f),
    ) {
        CombatantPortrait(
            name = snapshot.name(),
            currentHitPoints = combatant.currentHitPoints(),
            maxHitPoints = snapshot.maxHitPoints(),
            faction = if (dropTarget.isParty) Faction.PARTY else Faction.ENEMY,
            active = false,
            diameter = 46.dp,
        )
    }
}

/** Registro desktop sotto i nemici: il bordo superiore si trascina per ridimensionare o collassare. */
@Composable
private fun CollapsibleBattleLog(viewModel: BattleViewModel) {
    val density = LocalDensity.current
    val layout = LocalUiLayout.current
    val minHeight = 130.dp
    val maxHeight = 360.dp
    val defaultHeight = 230.dp
    val collapsed = layout.logCollapsed
    val panelHeight = if (collapsed) 42.dp else layout.logHeight

    Column(
        Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .background(Palette.Abyss.copy(alpha = 0.88f))
            .border(1.dp, Palette.Line),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Surface.copy(alpha = 0.91f))
                .pointerInput(collapsed) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val dragDp = with(density) { dragAmount.toDp() }
                            if (collapsed && dragAmount < 0f) {
                                layout.logHeight = defaultHeight
                                layout.logCollapsed = false
                            } else if (!collapsed) {
                                layout.logHeight = (layout.logHeight - dragDp)
                                    .coerceIn(72.dp, maxHeight)
                            }
                        },
                        onDragEnd = {
                            if (!collapsed && layout.logHeight < minHeight) {
                                layout.logHeight = defaultHeight
                                layout.logCollapsed = true
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
                    text = "REGISTRO EVENTI",
                    color = Palette.Gold,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!collapsed) {
                    Text(
                        text = "Trascina verso il basso per collassare",
                        color = Palette.TextFaint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                text = if (collapsed) "Apri ▸" else "Collassa ▾",
                color = Palette.TextMuted,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clickable {
                    if (collapsed) layout.logHeight = defaultHeight
                    layout.logCollapsed = !collapsed
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
    roster: RosterViewModel,
    onOpenCombatantSheet: (String) -> Unit,
    onCreateRosterCharacter: () -> Unit,
    onCreateRosterCreature: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(CompactTab.PALCO) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().background(Palette.Surface.copy(alpha = 0.91f)).padding(6.dp),
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
                    roster = roster,
                    onOpenSheet = onOpenCombatantSheet,
                    onCreateRosterCharacter = onCreateRosterCharacter,
                    onCreateRosterCreature = onCreateRosterCreature,
                    modifier = Modifier.fillMaxSize(),
                )

                CompactTab.NEMICI -> Rail(
                    viewModel = viewModel,
                    title = "Nemici",
                    ids = viewModel.enemyIds,
                    faction = Faction.ENEMY,
                    roster = roster,
                    onOpenSheet = onOpenCombatantSheet,
                    onCreateRosterCharacter = onCreateRosterCharacter,
                    onCreateRosterCreature = onCreateRosterCreature,
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
    roster: RosterViewModel,
    onOpenSheet: (String) -> Unit,
    onCreateRosterCharacter: () -> Unit,
    onCreateRosterCreature: () -> Unit,
    modifier: Modifier = Modifier,
    dropTarget: TokenPlacementDrag? = null,
) {
    val standing = ids.count {
        viewModel.combatant(it)?.let { combatant -> !combatant.defeated() && !combatant.dead() } == true
    }
    val accent = if (faction == Faction.PARTY) Palette.Party else Palette.Enemy
    var rosterDialogOpen by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            // Gradiente verticale appena percettibile: la colonna emerge dal buio
            // in alto e vi si dissolve in basso, invece di essere una lastra piatta.
            .background(
                Brush.verticalGradient(
                    listOf(Palette.Surface.copy(alpha = 0.62f), Palette.Surface.copy(alpha = 0.22f)),
                ),
            )
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Eyebrow(title, color = accent)
                if (viewModel.editMode) {
                    GameButton(
                        "+",
                        accent = accent,
                        dense = true,
                        onClick = { rosterDialogOpen = true },
                    )
                }
            }
            Text(
                text = "$standing/${ids.size} in piedi",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // Fregio di fazione sotto l'intestazione: il rombo al centro della riga
        // riprende il colore della colonna, argento o brace.
        OrnateDivider(color = accent.copy(alpha = 0.65f))
        val listState = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(ids) { id ->
                    CombatantRailCard(
                        viewModel = viewModel,
                        combatantId = id,
                        faction = faction,
                        onOpenSheet = onOpenSheet,
                        dropTarget = dropTarget,
                    )
                }
            }
            PanelScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }

    BattleRosterDialog(
        open = rosterDialogOpen,
        targetFaction = faction,
        viewModel = viewModel,
        roster = roster,
        onCreateCharacter = {
            rosterDialogOpen = false
            onCreateRosterCharacter()
        },
        onCreateCreature = {
            rosterDialogOpen = false
            onCreateRosterCreature()
        },
        onDismiss = { rosterDialogOpen = false },
    )
}

@Composable
private fun BattleTopBar(
    viewModel: BattleViewModel,
    sessions: SessionManager,
    openSessionCount: Int,
    autosaveWarning: Boolean,
    compact: Boolean,
) {
    val layout = LocalUiLayout.current
    val turnOrderMode = layout.turnOrderDisplayMode

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Il vecchio layout resta intatto. Cambia ramo in base allo spazio che
        // arriva davvero alla battaglia, quindi rail e resize non soffocano il centro.
        val useCompactLayout = compact || maxWidth < TURN_ORDER_COMPACT_BREAKPOINT
        val labelNeedsOwnRow = maxWidth < 520.dp
        if (useCompactLayout) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Surface.copy(alpha = 0.92f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BattleMark()
                    BattleTitle(sessions, modifier = Modifier.weight(1f))
                    if (viewModel.status != CombatStatus.ACTIVE) {
                        Chip(text = viewModel.status.italianLabel, color = viewModel.status.tint)
                    }
                }
                if (labelNeedsOwnRow) {
                    // Sulle finestre molto strette l'etichetta usa tutta la riga:
                    // i pulsanti non possono piu' ridurne la larghezza.
                    TurnsLabel(
                        turnOrderMode,
                        layout::cycleTurnOrderDisplayMode,
                        Modifier.align(Alignment.CenterHorizontally),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (turnOrderMode != TurnOrderDisplayMode.HIDDEN) {
                            Box(
                                Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                TurnOrderStrip(
                                    viewModel,
                                    editing = viewModel.editMode,
                                    showInitiative = turnOrderMode == TurnOrderDisplayMode.WITH_INITIATIVE,
                                )
                            }
                        } else {
                            Box(Modifier.weight(1f))
                        }
                        CompactTurnOrderActions(
                            viewModel = viewModel,
                            sessions = sessions,
                            openSessionCount = openSessionCount,
                            autosaveWarning = autosaveWarning,
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            TurnsLabel(turnOrderMode, layout::cycleTurnOrderDisplayMode)
                            if (turnOrderMode != TurnOrderDisplayMode.HIDDEN) {
                                TurnOrderStrip(
                                    viewModel,
                                    editing = viewModel.editMode,
                                    showInitiative = turnOrderMode == TurnOrderDisplayMode.WITH_INITIATIVE,
                                )
                            }
                        }
                        CompactTurnOrderActions(
                            viewModel = viewModel,
                            sessions = sessions,
                            openSessionCount = openSessionCount,
                            autosaveWarning = autosaveWarning,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
            return@BoxWithConstraints
        }

        val viewportHeight = layout.topBarHeight.coerceIn(
            TURN_ORDER_MIN_HEIGHT,
            TURN_ORDER_MAX_HEIGHT,
        )
        val turnCardScale = turnOrderCardScale(viewportHeight)
        val barHeight = viewportHeight + TURN_ORDER_LABEL_SPACE +
            if (viewModel.editMode) TURN_ORDER_EDIT_EXTRA_HEIGHT else 0.dp
        val tight = maxWidth < 1260.dp

        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (turnOrderMode == TurnOrderDisplayMode.HIDDEN) {
                        Modifier
                    } else {
                        Modifier.height(barHeight)
                    },
                )
                .background(Palette.Surface.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BattleTitle(
                sessions,
                Modifier.widthIn(max = if (tight) 170.dp else 220.dp),
            )

            // La scritta conserva la sua fascia di 24 dp; sotto scorre soltanto
            // l'ordine. Nessuna tessera o badge puo' disegnarle sopra.
            Column(
                Modifier
                    .weight(1f)
                    .align(Alignment.Top)
                    .then(
                        if (turnOrderMode == TurnOrderDisplayMode.HIDDEN) {
                            Modifier.height(TURN_ORDER_LABEL_SPACE)
                        } else {
                            Modifier.fillMaxHeight()
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(TURN_ORDER_LABEL_SPACE)) {
                    TurnsLabel(
                        turnOrderMode,
                        layout::cycleTurnOrderDisplayMode,
                        Modifier.align(Alignment.TopCenter),
                    )
                }
                if (turnOrderMode != TurnOrderDisplayMode.HIDDEN) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TurnOrderStrip(
                            viewModel,
                            editing = viewModel.editMode,
                            showInitiative = turnOrderMode == TurnOrderDisplayMode.WITH_INITIATIVE,
                            cardScale = turnCardScale,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (viewModel.status == CombatStatus.DRAFT || viewModel.status == CombatStatus.READY) {
                    GameButton(
                        label = when {
                            !tight && viewModel.simultaneousTies -> "Parità insieme"
                            !tight -> "Parità separate"
                            viewModel.simultaneousTies -> "Parità unite"
                            else -> "Parità divise"
                        },
                        accent = if (viewModel.simultaneousTies) Palette.GoldBright else Palette.TextMuted,
                        selected = viewModel.simultaneousTies,
                        dense = true,
                        onClick = { viewModel.simultaneousTies = !viewModel.simultaneousTies },
                    )
                }
                EditModeButton(viewModel)
                SessionMenuButton(
                    sessions,
                    modifier = Modifier.widthIn(max = if (tight) 120.dp else 150.dp),
                    dense = true,
                    openSessionCount = openSessionCount,
                    autosaveWarning = autosaveWarning,
                )
                // Senza CPU la fascia nemica non compare mai: senza questo segno
                // nulla direbbe al tavolo che gli avversari li muove lui.
                if (!viewModel.enemyCpuEnabled) {
                    Chip(text = "Sandbox", color = Palette.Party)
                }
                if (viewModel.status != CombatStatus.ACTIVE) {
                    Chip(text = viewModel.status.italianLabel, color = viewModel.status.tint)
                }
            }
        }
    }
}

@Composable
private fun EnemyCpuBanner(viewModel: BattleViewModel, compact: Boolean) {
    if (!viewModel.enemyCpuEnabled || viewModel.enemyCpuTurnKey == null) return
    val suspended = viewModel.enemyCpuTurnSuppressed
    val completed = viewModel.enemyCpuBatchCompleted
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Enemy.copy(alpha = if (suspended) 0.08f else 0.13f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (suspended) {
                "Batch CPU annullato e sospeso per questo turno."
            } else if (viewModel.editMode) {
                "CPU in pausa mentre la modalità Modifica è attiva."
            } else if (completed) {
                "I nemici hanno agito · completa il turno con gli alleati."
            } else {
                // Mentre il turno scorre la fascia racconta il comando in corso:
                // e' l'unico posto dove un'azione gia' risolta resta leggibile.
                viewModel.enemyCpuActionLabel?.takeIf { viewModel.enemyCpuBusy }
                    ?: "CPU · ${viewModel.enemyCpuDifficulty.italianLabel} sta decidendo…"
            },
            color = if (suspended) Palette.TextMuted else Palette.Enemy,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (suspended) {
            GameButton(
                label = "Riprendi CPU",
                accent = Palette.Enemy,
                dense = true,
                onClick = viewModel::resumeEnemyCpuTurn,
            )
        }
        EnemyCpuSpeedPicker(viewModel, compact)
    }
}

/**
 * Ritmo con cui la CPU mostra il proprio turno.
 *
 * Sta nella fascia perche' e' li' che il tavolo si accorge di non star vedendo
 * nulla: la scelta vale subito, anche a turno gia' avviato, e viene salvata con
 * la sessione. Sulla shell stretta un solo comando cicla fra i ritmi.
 */
@Composable
private fun EnemyCpuSpeedPicker(viewModel: BattleViewModel, compact: Boolean) {
    if (compact) {
        val speeds = EnemyCpuSpeed.values()
        GameButton(
            label = "Ritmo · ${viewModel.enemyCpuSpeed.italianLabel}",
            accent = Palette.Gold,
            dense = true,
            onClick = {
                viewModel.enemyCpuSpeed = speeds[(viewModel.enemyCpuSpeed.ordinal + 1) % speeds.size]
            },
        )
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Ritmo",
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        EnemyCpuSpeed.values().forEach { speed ->
            val selected = viewModel.enemyCpuSpeed == speed
            GameButton(
                label = speed.italianLabel,
                accent = if (selected) Palette.Gold else Palette.TextMuted,
                selected = selected,
                dense = true,
                onClick = { viewModel.enemyCpuSpeed = speed },
            )
        }
    }
}

@Composable
private fun CompactTurnOrderActions(
    viewModel: BattleViewModel,
    sessions: SessionManager,
    openSessionCount: Int,
    autosaveWarning: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditModeButton(viewModel)
        SessionMenuButton(
            sessions,
            modifier = Modifier.widthIn(max = 130.dp),
            dense = true,
            openSessionCount = openSessionCount,
            autosaveWarning = autosaveWarning,
        )
    }
}

/**
 * Il resize continua a cambiare le dimensioni delle card, con pochi livelli
 * stabili. Il fattore non viene mai applicato alla densita' o alla tipografia.
 */
private fun turnOrderCardScale(viewportHeight: Dp): Float = when {
    viewportHeight < 64.dp -> 0.9f
    viewportHeight >= 128.dp -> 1.12f
    viewportHeight >= 96.dp -> 1.08f
    viewportHeight >= 80.dp -> 1.04f
    else -> 1f
}

/**
 * Etichetta "Ordine dei turni": ogni clic passa da nascosto a solo ordine, poi
 * all'ordine con iniziativa e infine torna a nasconderlo.
 */
@Composable
private fun TurnsLabel(
    mode: TurnOrderDisplayMode,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nextAction = when (mode) {
        TurnOrderDisplayMode.HIDDEN -> "Mostra l'ordine dei turni senza iniziativa"
        TurnOrderDisplayMode.ORDER_ONLY -> "Mostra anche i valori di iniziativa"
        TurnOrderDisplayMode.WITH_INITIATIVE -> "Nascondi l'ordine dei turni"
    }
    Row(
        modifier
            // Il testo decide la larghezza naturale e non viene mai forzato nei
            // 132 dp precedenti, insufficienti con font/DPI di Windows.
            .widthIn(min = 168.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = nextAction,
                onClick = onCycle,
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ORDINE DEI TURNI",
            color = Palette.Gold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = if (mode == TurnOrderDisplayMode.HIDDEN) "▸" else "▾",
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
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
        // Nessun sottotitolo: quel testo lungo allargava il pulsante e comprimeva
        // la fascia turni. In modifica i comandi di riordino (◀ ▶ e "rendi
        // corrente") compaiono direttamente sui riquadri, dove servono.
        label = if (viewModel.editMode) "Modifica attiva" else "Modifica",
        accent = if (viewModel.editMode) Palette.Heal else Palette.TextMuted,
        selected = viewModel.editMode,
        dense = true,
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
