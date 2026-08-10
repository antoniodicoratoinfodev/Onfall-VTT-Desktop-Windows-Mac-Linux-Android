package app.d6d.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.AppGlyph
import app.d6d.ui.components.GlyphIcon
import app.d6d.ui.components.VerticalResizeHandle
import app.d6d.ui.components.initials
import app.d6d.ui.content.SessionTemplates
import app.d6d.ui.cursors.CursorPreferences
import app.d6d.ui.encounter.EncounterBuilderScreen
import app.d6d.ui.encounter.EncounterBuilderViewModel
import app.d6d.ui.encounter.EncounterMode
import app.d6d.sheet.SheetStore
import app.d6d.ui.roster.RosterScreen
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.roster.RosterViewModel
import app.d6d.sheet.ImageStore
import app.d6d.ui.images.FilePicker
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.layout.LayoutStore
import app.d6d.ui.layout.LocalUiLayout
import app.d6d.ui.layout.UiLayoutState
import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.ui.session.SessionWorkspace
import app.d6d.ui.session.WorkspaceRecoveryStore
import app.d6d.ui.session.WorkspaceOpenResult
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.state.CombatResourceSink
import app.d6d.ui.state.CombatantEditSink
import app.d6d.content.srd521it.SrdBeasts
import app.d6d.ui.theme.AppTheme
import app.d6d.ui.theme.AtmosphericBackground
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.Palette
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class Destination(val label: String, val icon: AppGlyph) {
    BATTAGLIA("Battaglia", AppGlyph.SWORDS),
    INCONTRO("Partita", AppGlyph.D20),
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
    // Solo il desktop offre cursori personalizzati; null mantiene Android invariato.
    cursorPreferences: CursorPreferences? = null,
    // Il desktop puo' limitare il solo fondale sui renderer piu' costosi. Null
    // mantiene l'animazione sincronizzata al display, come su macOS e Android.
    atmosphericAnimationFrameMillis: Long? = null,
    atmosphericAnimationRunning: Boolean = true,
    // Il desktop inoltra qui la richiesta della X della finestra, così le bozze
    // multi-sessione non possono essere perse senza una conferma esplicita.
    exitRequested: Boolean = false,
    onExitRequestHandled: () -> Unit = {},
    onExitConfirmed: () -> Unit = {},
) {
    AppTheme {
        val uiScope = rememberCoroutineScope()
        var destination by remember { mutableStateOf(Destination.BATTAGLIA) }
        var requestedCompendiumItemId by remember { mutableStateOf<String?>(null) }
        var requestedCompendiumNewKind by remember { mutableStateOf<RosterKind?>(null) }

        // Il roster unifica schede e compendio: le schede sono la fonte, il catalogo
        // da combattimento ne discende.
        val roster = remember {
            RosterViewModel(
                // Il catalogo e' una proiezione rigenerabile delle schede: copie
                // versionate a ogni uso di risorsa raddoppiavano l'I/O sincrono,
                // soprattutto costoso su NTFS/Defender.
                ActorCatalogStore(dataDirectory, ActorCatalogStore.DEFAULT_BASE_NAME, 0),
                SheetStore(dataDirectory.resolve("schede.json")),
                loadOnCreate = false,
            )
        }
        // Ogni scheda aperta riceve un BattleViewModel distinto. La taglia iniziale
        // arriva dal Compendio e viene fotografata nel documento; le correzioni e
        // gli usi di risorsa confluiscono nella scheda autorevole quando esiste.
        val battleFactory: (CombatSession) -> BattleViewModel = remember(roster) {
            { session ->
                BattleViewModel(
                    session,
                    footprintProvider = { definitionId -> roster.footprintFor(definitionId) },
                    passiveProvider = { abilityId -> roster.abilityIsPassive(abilityId) },
                    actorProvider = { definitionId -> roster.definitionFor(definitionId) },
                    wildShapeProvider = { abilityId -> SrdBeasts.byId(abilityId)?.toActorDefinition() },
                    druidLevelProvider = { definitionId -> roster.druidLevelFor(definitionId) },
                    resourceSink = CombatResourceSink { definitionId, resources ->
                        if (!roster.applyCombatResources(definitionId, resources)) {
                            error(roster.sheets.status ?: "Risorse della scheda non salvate.")
                        }
                    },
                    editSink = CombatantEditSink { definitionId, snapshot ->
                        if (!roster.applyCombatEdit(definitionId, snapshot)) {
                            error(roster.sheets.status ?: "Correzione della scheda non salvata.")
                        }
                    },
                )
            }
        }
        val encounterBuilder = remember { EncounterBuilderViewModel(roster) }
        val portraits = remember {
            PortraitRepository(ImageStore(dataDirectory), filePicker, uiScope, loadOnCreate = false)
        }
        // All'avvio si apre il template piu' semplice: un tavolo nuovo trova una
        // partita giocabile invece di una mappa vuota, e le altre due si scelgono
        // dalla procedura Nuova partita.
        val workspace = remember {
            val opening = SessionTemplates.default
            SessionWorkspace(
                store = SessionArchiveStore(dataDirectory.resolve("sessions")),
                initialSession = opening.startedSession(),
                initialDisplayName = opening.name,
                battleFactory = battleFactory,
                refreshManagersOnCreate = false,
            )
        }
        val recoveryStore = remember { WorkspaceRecoveryStore(dataDirectory) }
        var recoveryReady by remember { mutableStateOf(false) }
        // Il Compendio deve contenere tutto cio' che l'app distribuisce, non solo
        // quello che l'utente ha aperto: l'archivio viene popolato alla prima
        // installazione, quindi su uno gia' esistente le partite incluse
        // nominerebbero schede che non ci sono. Si rimettono solo le mancanti.
        LaunchedEffect(roster) {
            runCatching {
                runDiskIo {
                    roster.initialize()
                    roster.installIncludedContent()
                    portraits.reload()
                }
            }
            val recovered = runCatching { runDiskIo { recoveryStore.load() } }.getOrNull()
            if (recovered != null) workspace.restoreRecovery(recovered)
            runCatching { runDiskIo { workspace.refreshSessionLists() } }
            recoveryReady = true
        }
        // La bozza segue ogni scheda aperta, ma aspetta che l'avvio abbia finito:
        // il tavolo predefinito non deve sovrascrivere un recupero ancora da leggere.
        LaunchedEffect(workspace, recoveryReady) {
            if (!recoveryReady) return@LaunchedEffect
            snapshotFlow { workspace.recoveryKey() }.collectLatest {
                delay(500)
                val snapshot = workspace.recoverySnapshot()
                runDiskIo { runCatching { recoveryStore.save(snapshot) } }
            }
        }
        val activeSession = workspace.activeSession
        val battleViewModel = activeSession.battle
        val sessions = activeSession.manager
        var exitWarningOpen by remember { mutableStateOf(false) }

        LaunchedEffect(exitRequested) {
            if (exitRequested) {
                // Prima prova a chiudere pulitamente gli autosave nominati. Se
                // restano bozze o errori, la decisione torna all'utente.
                runDiskIo { workspace.flushAutosaves() }
                if (workspace.openSessions.none { it.manager.hasUnsavedChanges }) {
                    runDiskIo { runCatching { recoveryStore.clear() } }
                    onExitConfirmed()
                } else {
                    exitWarningOpen = true
                }
                onExitRequestHandled()
            }
        }

        // Disposizione dei pannelli: larghezze, collassi, zoom e posizione delle
        // targhe. Si carica all'avvio dallo stesso file trasportabile e vi torna a
        // ogni modifica, cosi' l'interfaccia riapre com'era stata lasciata.
        val layoutStore = remember { LayoutStore(dataDirectory.resolve("layout.json")) }
        val layout = remember { UiLayoutState(store = layoutStore) }
        LaunchedEffect(layout) {
            layout.restore(runDiskIo { layoutStore.load() })
        }
        // `snapshotFlow` osserva i valori senza ricomporre la radice a ogni frame
        // di trascinamento; `collectLatest` annulla l'attesa precedente, quindi si
        // scrive su disco solo quando i pannelli si fermano.
        LaunchedEffect(layout) {
            snapshotFlow { layout.snapshot() }.collectLatest {
                delay(600)
                runDiskIo { runCatching { layout.persist() } }
            }
        }

        // Ogni scheda conserva il proprio effetto di autosave anche quando non e'
        // attiva: passare a un'altra mappa non cancella il debounce della prima.
        workspace.openSessions.forEach { opened ->
            key(opened.id) {
                LaunchedEffect(
                    opened.battle.state,
                    opened.battle.presentationState(),
                    opened.manager.currentSlug,
                ) {
                    if (opened.manager.currentSlug != null && opened.manager.hasUnsavedChanges) {
                        delay(1_200)
                        runDiskIo { workspace.flushAutosave(opened) }
                    }
                }
            }
        }

        // Un dispose della radice (chiusura desktop o ricreazione Activity) forza
        // gli autosave già nominati; le bozze restano intenzionalmente da salvare.
        DisposableEffect(workspace) {
            onDispose { workspace.flushAutosaves() }
        }

        val openEncounter: (CombatSession, String, EncounterMode) -> Unit = { session, name, mode ->
            workspace.openNew(session, name, mapOf("encounterMode" to mode.name))
            encounterBuilder.restartWizard()
            destination = Destination.BATTAGLIA
        }

        val openSavedSession: (app.d6d.persistence.session.SessionSummary) -> Unit = { summary ->
            uiScope.launch {
                when (runDiskIo { workspace.openSaved(summary) }) {
                    WorkspaceOpenResult.OPENED,
                    WorkspaceOpenResult.ALREADY_OPEN -> {
                        encounterBuilder.restartWizard()
                        destination = Destination.BATTAGLIA
                    }
                    WorkspaceOpenResult.FAILED -> {
                        sessions.status = workspace.status
                        sessions.menuOpen = true
                    }
                }
            }
        }

        // Le schermate sono volutamente scambiate in modo diretto. Una dissolvenza
        // full-screen teneva vive, misurava e disegnava entrambe per 220 ms: fra
        // mappa ed editor del Compendio il picco era molto visibile su Windows.
        // I view model vivono sopra questo ramo, quindi lo stato applicativo resta.
        val content: @Composable (Modifier) -> Unit = { contentModifier ->
            when (destination) {
                Destination.BATTAGLIA ->
                    key(activeSession.id) {
                        BattleScreen(
                            viewModel = battleViewModel,
                            portraits = portraits,
                            sessions = sessions,
                            workspace = workspace,
                            roster = roster,
                            compact = compact,
                            onOpenCombatantSheet = { definitionId ->
                                if (roster.items.any { it.id == definitionId }) {
                                    requestedCompendiumItemId = definitionId
                                    requestedCompendiumNewKind = null
                                    destination = Destination.COMPENDIO
                                } else {
                                    battleViewModel.showMessage(
                                        "La scheda collegata a questo combattente non è presente nel Compendio.",
                                    )
                                }
                            },
                            onCreateRosterCharacter = {
                                requestedCompendiumItemId = null
                                requestedCompendiumNewKind = RosterKind.PERSONAGGIO
                                destination = Destination.COMPENDIO
                            },
                            onCreateRosterCreature = {
                                requestedCompendiumItemId = null
                                requestedCompendiumNewKind = RosterKind.CREATURA
                                destination = Destination.COMPENDIO
                            },
                            onOpenSavedSession = openSavedSession,
                            modifier = contentModifier,
                        )
                    }

                Destination.INCONTRO ->
                    EncounterBuilderScreen(
                        viewModel = encounterBuilder,
                        compact = compact,
                        workspace = workspace,
                        onStarted = { session, name, mode ->
                            openEncounter(session, name, mode)
                        },
                        onOpenBattle = { destination = Destination.BATTAGLIA },
                        onOpenCompendium = {
                            requestedCompendiumItemId = null
                            destination = Destination.COMPENDIO
                        },
                        modifier = contentModifier,
                    )

                Destination.COMPENDIO ->
                    RosterScreen(
                        viewModel = roster,
                        portraits = portraits,
                        compact = compact,
                        requestedItemId = requestedCompendiumItemId,
                        onRequestedItemHandled = { requestedCompendiumItemId = null },
                        requestedNewKind = requestedCompendiumNewKind,
                        onRequestedNewHandled = { requestedCompendiumNewKind = null },
                        cursorPreferences = cursorPreferences,
                        modifier = contentModifier,
                    )
            }
        }

        CompositionLocalProvider(LocalUiLayout provides layout) {
            Box(modifier.fillMaxSize()) {
                // Fondale atmosferico condiviso: resta fermo mentre le schermate si
                // alternano sopra. La battaglia dipinge il proprio fondo opaco,
                // quindi la mappa tattica non lo vede e resta pulita e leggibile.
                AtmosphericBackground(
                    modifier = Modifier.fillMaxSize(),
                    animationFrameIntervalMillis = atmosphericAnimationFrameMillis,
                    animationRunning = atmosphericAnimationRunning,
                )

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

            }

            if (exitWarningOpen) {
                val dirtySessions = workspace.openSessions.filter { it.manager.hasUnsavedChanges }
                val draftCount = dirtySessions.count { it.manager.currentSlug == null }
                val names = dirtySessions.take(4).joinToString(" · ") {
                    if (it.displayName.length <= 38) {
                        it.displayName
                    } else {
                        it.displayName.take(37).trimEnd() + "…"
                    }
                }
                AlertDialog(
                    onDismissRequest = {
                        exitWarningOpen = false
                        destination = Destination.INCONTRO
                    },
                    containerColor = Palette.Surface,
                    title = {
                        Text(
                            "Partite non salvate",
                            color = Palette.Text,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    text = {
                        Text(
                            buildString {
                                append("${dirtySessions.size} sessioni hanno modifiche da salvare")
                                if (draftCount > 0) append(", di cui $draftCount mai salvate")
                                append(".\n\n")
                                append(names)
                                if (dirtySessions.size > 4) append(" · …")
                                append("\n\nTorna a Partita per salvarle o chiuderle una per una.")
                            },
                            color = Palette.TextMuted,
                        )
                    },
                    confirmButton = {
                        GameButton("Torna e salva", accent = Palette.Heal, onClick = {
                            exitWarningOpen = false
                            destination = Destination.INCONTRO
                        })
                    },
                    dismissButton = {
                        GameButton("Esci senza salvare", accent = Palette.Enemy, onClick = {
                            exitWarningOpen = false
                            uiScope.launch {
                                runDiskIo { runCatching { recoveryStore.clear() } }
                                onExitConfirmed()
                            }
                        })
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
