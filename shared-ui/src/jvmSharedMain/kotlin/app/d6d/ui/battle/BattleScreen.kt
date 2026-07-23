package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.CombatStatus
import app.d6d.ui.components.Chip
import app.d6d.ui.components.CombatantPortrait
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.Faction
import app.d6d.ui.components.HorizontalResizeHandle
import app.d6d.ui.components.PanelScrollbar
import app.d6d.ui.components.ScaledDensity
import app.d6d.ui.components.VerticalResizeHandle
import kotlin.math.roundToInt
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.layout.LocalUiLayout
import app.d6d.ui.session.SessionManager
import app.d6d.ui.session.SessionMenuButton
import app.d6d.ui.session.SessionMenuDialog
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.theme.Palette

// Altezza a cui i riquadri dell'ordine turni hanno la dimensione naturale (scala
// 1). Piu' la fascia supera questa quota, piu' i riquadri crescono; sotto,
// rimpiccioliscono. Cosi' il contenuto riempie sempre la fascia mentre la si
// trascina, invece di restare fermo con dello spazio vuoto.
private val TOP_BAR_BASE = 64.dp

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
    val layout = LocalUiLayout.current
    val density = LocalDensity.current
    Column(modifier.fillMaxSize().background(Palette.Night)) {
        BattleTopBar(viewModel, sessions, compact)
        // Filo d'oro sotto l'intestazione: chiude la fascia dei turni come il
        // bordo inciso di un pannello, senza il peso di un bordo pieno.
        GoldenRule()
        // Il bordo inferiore della fascia turni: trascinandolo verso il basso la
        // fascia cresce. Solo sul desktop e solo quando l'ordine turni e' visibile.
        if (!compact && !layout.turnsCollapsed) {
            HorizontalResizeHandle(
                onDrag = { dragPx ->
                    layout.topBarHeight = (layout.topBarHeight + with(density) { dragPx.toDp() })
                        .coerceIn(56.dp, 240.dp)
                },
            )
        }
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
                                .coerceIn(76.dp, 480.dp)
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
    dropTarget: TokenPlacementDrag? = null,
) {
    val standing = ids.count { viewModel.combatant(it)?.defeated() == false }
    val accent = if (faction == Faction.PARTY) Palette.Party else Palette.Enemy
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
            Eyebrow(title, color = accent)
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
                        dropTarget = dropTarget,
                    )
                }
            }
            PanelScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun BattleTopBar(
    viewModel: BattleViewModel,
    sessions: SessionManager,
    compact: Boolean,
) {
    // Il pannello ordine turni si puo' contrarre per liberare spazio in cima.
    val layout = LocalUiLayout.current

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
                TurnOrderStrip(viewModel, Modifier.weight(1f), editing = viewModel.editMode)
                EditModeButton(viewModel)
                SessionMenuButton(sessions)
            }
        }
        return
    }

    // In modifica ogni riquadro turno guadagna i comandi ◀ ▶ e "rendi corrente":
    // la fascia cresce quel tanto che basta a ospitarli, senza rimpicciolire i
    // turni. La scala dei riquadri resta quella scelta dall'altezza a riposo.
    val turnScale = (layout.topBarHeight / TOP_BAR_BASE).coerceIn(0.6f, 3.5f)
    val barHeight = if (viewModel.editMode) layout.topBarHeight + 28.dp * turnScale else layout.topBarHeight

    Row(
        Modifier
            .fillMaxWidth()
            .then(if (layout.turnsCollapsed) Modifier else Modifier.height(barHeight))
            .background(Palette.Surface)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BattleTitle(sessions)

        // La fascia si apre e si chiude cliccando l'etichetta "Ordine dei turni"
        // (non c'e' piu' un tasto separato). Da collassata resta solo l'etichetta,
        // per riaprirla, poi lo spazio elastico; da espansa la colonna riempie
        // l'altezza scelta e i riquadri vi si scalano di conseguenza.
        if (layout.turnsCollapsed) {
            TurnsLabel(collapsed = true) { layout.turnsCollapsed = false }
            Spacer(Modifier.weight(1f))
        } else {
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ScaledDensity(turnScale) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TurnsLabel(collapsed = false) { layout.turnsCollapsed = true }
                            if (viewModel.isSimultaneousTurn) Chip("Turno simultaneo", Palette.GoldBright)
                        }
                        TurnOrderStrip(viewModel, editing = viewModel.editMode)
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (viewModel.status == CombatStatus.DRAFT || viewModel.status == CombatStatus.READY) {
                GameButton(
                    label = if (viewModel.simultaneousTies) "Parità insieme" else "Parità separate",
                    accent = if (viewModel.simultaneousTies) Palette.GoldBright else Palette.TextMuted,
                    selected = viewModel.simultaneousTies,
                    dense = true,
                    onClick = { viewModel.simultaneousTies = !viewModel.simultaneousTies },
                )
            }
            EditModeButton(viewModel)
            SessionMenuButton(sessions, dense = true)
            Chip(text = "Round ${viewModel.round}", color = Palette.Gold)
            Chip(text = viewModel.status.italianLabel, color = viewModel.status.tint)
        }
    }
}

/**
 * Etichetta "Ordine dei turni", ora cliccabile: apre o chiude la fascia dei turni
 * al posto del vecchio tasto "Turni". Il chevron indica lo stato, e resta
 * raggiungibile anche a fascia chiusa per poterla riaprire.
 */
@Composable
private fun TurnsLabel(collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = if (collapsed) "Mostra l'ordine dei turni" else "Nascondi l'ordine dei turni",
                onClick = onToggle,
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow("Ordine dei turni")
        Text(
            text = if (collapsed) "▸" else "▾",
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
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
