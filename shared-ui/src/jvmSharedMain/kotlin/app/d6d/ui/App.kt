package app.d6d.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.d6d.persistence.catalog.ActorCatalogStore
import app.d6d.engine.CombatSession
import app.d6d.ui.battle.BattleScreen
import app.d6d.ui.components.AppGlyph
import app.d6d.ui.components.GlyphIcon
import app.d6d.ui.components.VerticalResizeHandle
import app.d6d.ui.components.initials
import app.d6d.ui.content.SampleEncounter
import app.d6d.ui.encounter.EncounterBuilderScreen
import app.d6d.ui.encounter.EncounterBuilderViewModel
import app.d6d.ui.encounter.EncounterMode
import app.d6d.sheet.SheetStore
import app.d6d.ui.roster.RosterScreen
import app.d6d.ui.roster.RosterViewModel
import app.d6d.sheet.ImageStore
import app.d6d.ui.images.FilePicker
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.layout.LayoutStore
import app.d6d.ui.layout.LocalUiLayout
import app.d6d.ui.layout.UiLayoutState
import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.ui.session.SessionManager
import app.d6d.ui.session.UnsavedSessionDialog
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.AppTheme
import app.d6d.ui.theme.AtmosphericBackground
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.Palette
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

enum class Destination(val label: String, val icon: AppGlyph) {
    BATTAGLIA("Battaglia", AppGlyph.SWORDS),
    INCONTRO("Nuova partita", AppGlyph.D20),
    COMPENDIO("Compendio", AppGlyph.TOME),
}

// Larghezze della barra di navigazione desktop. Parte al minimo — solo le icone —
// e si allarga trascinando il bordo, esattamente come le colonne squadra e nemici.
// Oltre la soglia compaiono anche le etichette: sotto resta compatta.
private val RAIL_MIN = 54.dp
private val RAIL_MAX = 240.dp
private val RAIL_LABELS_FROM = 96.dp

/**
 * Radice dell'applicazione, condivisa fra desktop e Android.
 *
 * `compact` e' l'unico interruttore di forma: sotto una certa larghezza si passa
 * alla shell mobile. Motore, stato e schermate restano gli stessi, come chiede il
 * documento quando distingue shell adattive da un motore unico.
 */
@Composable
fun AppRoot(
    dataDirectory: Path,
    compact: Boolean,
    modifier: Modifier = Modifier,
    // Desktop e Android aprono selettori di file diversi: la shell lo fornisce.
    filePicker: FilePicker = FilePicker { null },
) {
    AppTheme {
        var destination by remember { mutableStateOf(Destination.BATTAGLIA) }

        // Il roster unifica schede e compendio: le schede sono la fonte, il catalogo
        // da combattimento ne discende.
        val roster = remember {
            RosterViewModel(
                ActorCatalogStore(dataDirectory),
                SheetStore(dataDirectory.resolve("schede.json")),
            )
        }
        // La taglia dei segnaposti viene dal Compendio; le correzioni in battaglia
        // confluiscono nella scheda autorevole.
        val battleViewModel = remember {
            BattleViewModel(
                SampleEncounter.startedSession(),
                footprintProvider = { definitionId -> roster.footprintFor(definitionId) },
            ) { definitionId, snapshot ->
                check(roster.applyCombatEdit(definitionId, snapshot)) {
                    roster.sheets.status ?: "Impossibile aggiornare la scheda collegata."
                }
            }
        }
        val encounterBuilder = remember { EncounterBuilderViewModel(roster) }
        val portraits = remember { PortraitRepository(ImageStore(dataDirectory), filePicker) }
        val sessions = remember {
            SessionManager(SessionArchiveStore(dataDirectory.resolve("sessions")), battleViewModel).also {
                it.beginUnsavedSession("Cripta dei predoni")
            }
        }

        // Disposizione dei pannelli: larghezze, collassi, zoom e posizione delle
        // targhe. Si carica all'avvio dallo stesso file trasportabile e vi torna a
        // ogni modifica, cosi' l'interfaccia riapre com'era stata lasciata.
        val layout = remember {
            val store = LayoutStore(dataDirectory.resolve("layout.json"))
            UiLayoutState(store.load(), store)
        }
        // `snapshotFlow` osserva i valori senza ricomporre la radice a ogni frame
        // di trascinamento; `collectLatest` annulla l'attesa precedente, quindi si
        // scrive su disco solo quando i pannelli si fermano.
        LaunchedEffect(layout) {
            snapshotFlow { layout.snapshot() }.collectLatest {
                delay(600)
                layout.persist()
            }
        }

        // Dopo il primo salvataggio con nome, ogni comando viene riversato nello
        // stesso file con un breve debounce. Un incontro nuovo resta invece una
        // bozza esplicita: nessun nome o file viene inventato silenziosamente.
        LaunchedEffect(
            battleViewModel.state,
            battleViewModel.presentationState(),
            sessions.currentSlug,
        ) {
            if (sessions.currentSlug != null && sessions.hasUnsavedChanges) {
                delay(1_200)
                sessions.flushAutosave()
            }
        }

        var pendingEncounter by remember { mutableStateOf<Triple<CombatSession, String, EncounterMode>?>(null) }
        val adoptEncounter: (CombatSession, String, EncounterMode) -> Unit = { session, name, mode ->
            battleViewModel.adopt(session, mapOf("encounterMode" to mode.name))
            sessions.beginUnsavedSession(name)
            encounterBuilder.restartWizard()
            destination = Destination.BATTAGLIA
        }
        val requestEncounter: (CombatSession, String, EncounterMode) -> Unit = { session, name, mode ->
            if (sessions.hasUnsavedChanges) {
                pendingEncounter = Triple(session, name, mode)
            } else {
                adoptEncounter(session, name, mode)
            }
        }

        // Il passaggio fra le schermate e' una dissolvenza breve invece di uno
        // scatto: i view model vivono sopra, quindi nulla viene perso o ricreato.
        val content: @Composable (Modifier) -> Unit = { contentModifier ->
            Crossfade(
                targetState = destination,
                modifier = contentModifier,
                animationSpec = tween(220),
                label = "destinazione",
            ) { shown ->
                when (shown) {
                    Destination.BATTAGLIA ->
                        BattleScreen(battleViewModel, portraits, sessions, compact = compact, modifier = Modifier.fillMaxSize())

                    Destination.INCONTRO ->
                        EncounterBuilderScreen(
                            viewModel = encounterBuilder,
                            compact = compact,
                            onStarted = { session, name, mode ->
                                requestEncounter(session, name, mode)
                            },
                            onOpenCompendium = { destination = Destination.COMPENDIO },
                            modifier = Modifier.fillMaxSize(),
                        )

                    Destination.COMPENDIO ->
                        RosterScreen(roster, portraits, compact = compact, modifier = Modifier.fillMaxSize())
                }
            }
        }

        CompositionLocalProvider(LocalUiLayout provides layout) {
            Box(modifier.fillMaxSize()) {
                // Fondale atmosferico condiviso: resta fermo mentre le schermate si
                // dissolvono sopra. La battaglia dipinge il proprio fondo opaco,
                // quindi la mappa tattica non lo vede e resta pulita e leggibile.
                AtmosphericBackground(Modifier.fillMaxSize())

                if (compact) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
                        GoldenRule()
                        BottomNav(destination) { destination = it }
                    }
                } else {
                    val density = LocalDensity.current
                    // Parte gia' aperta ma al minimo; il bordo si trascina per allargarla.
                    Row(Modifier.fillMaxSize()) {
                        if (layout.railOpen) {
                            NavRail(
                                current = destination,
                                width = layout.railWidth,
                                onSelect = { destination = it },
                                onCollapse = { layout.railOpen = false },
                            )
                            VerticalResizeHandle(
                                onDrag = { dragPx ->
                                    layout.railWidth = (layout.railWidth + with(density) { dragPx.toDp() })
                                        .coerceIn(RAIL_MIN, RAIL_MAX)
                                },
                            )
                        } else {
                            // Chiusa: resta una striscia sottile col solo tasto per riaprirla,
                            // cosi' non copre i contenuti e resta sempre raggiungibile.
                            CollapsedRail(onExpand = { layout.railOpen = true })
                        }
                        content(Modifier.weight(1f))
                    }
                }

                UnsavedSessionDialog(
                    open = pendingEncounter != null,
                    onDismiss = { pendingEncounter = null },
                    onSaveFirst = {
                        pendingEncounter = null
                        destination = Destination.BATTAGLIA
                        sessions.menuOpen = true
                    },
                    onDiscard = {
                        pendingEncounter?.let { (session, name, mode) -> adoptEncounter(session, name, mode) }
                        pendingEncounter = null
                    },
                )
            }
        }
    }
}

@Composable
private fun NavRail(
    current: Destination,
    width: Dp,
    onSelect: (Destination) -> Unit,
    onCollapse: () -> Unit,
) {
    // Sotto la soglia si mostrano solo le icone: la barra resta leggibile anche
    // stretta, come le colonne squadra e nemici quando si restringono.
    val showLabels = width >= RAIL_LABELS_FROM
    Column(
        Modifier
            .width(width)
            .fillMaxSize()
            .background(Palette.Abyss)
            .padding(vertical = 7.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Tasto per chiudere la barra: in cima, dove non copre le voci.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .clickable(role = Role.Button, onClick = onCollapse)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (showLabels) "‹  Chiudi" else "‹",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Box(
            Modifier
                .background(
                    Brush.verticalGradient(listOf(Palette.GoldBright, Palette.Gold)),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Text(
                // Iniziali derivate dal nome: si aggiornano da sole quando
                // il nome commerciale viene deciso.
                text = initials(AppIdentity.displayName),
                color = Palette.Abyss,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Destination.entries.forEach { entry ->
            NavItem(entry, entry == current, showLabels, Modifier.fillMaxWidth()) { onSelect(entry) }
        }
    }
}

/** Barra chiusa: una striscia sottile col solo tasto per riaprirla. */
@Composable
private fun CollapsedRail(onExpand: () -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(26.dp)
            .background(Palette.Abyss)
            .padding(vertical = 7.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable(role = Role.Button, onClick = onExpand)
                .padding(vertical = 6.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "›", color = Palette.Gold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BottomNav(current: Destination, onSelect: (Destination) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Abyss)
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Destination.entries.forEach { entry ->
            NavItem(entry, entry == current, showLabel = true, Modifier.weight(1f)) { onSelect(entry) }
        }
    }
}

@Composable
private fun NavItem(
    destination: Destination,
    selected: Boolean,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) Palette.GoldBright else Palette.TextMuted
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            // La voce attiva e' una velatura d'oro bordata, non un blocco pieno:
            // si legge come "qui" senza gridare piu' del contenuto.
            .then(
                if (selected) {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(Palette.Gold.copy(alpha = 0.20f), Palette.Gold.copy(alpha = 0.06f)),
                            ),
                            shape,
                        )
                        .border(1.dp, Palette.Gold.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                },
            )
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        GlyphIcon(destination.icon, tint = tint, size = 20.dp)
        if (showLabel) {
            Text(
                text = destination.label,
                color = tint,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
