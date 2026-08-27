package app.d6d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import app.d6d.ui.cursors.CursorPreferences
import app.d6d.ui.encounter.EncounterBuilderScreen
import app.d6d.ui.encounter.EncounterBuilderViewModel
import app.d6d.ui.encounter.EncounterMode
import app.d6d.ui.encounter.newEncounterPresentation
import app.d6d.sheet.SheetStore
import app.d6d.ui.roster.RosterScreen
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.roster.RosterViewModel
import app.d6d.sheet.ImageStore
import app.d6d.ui.images.FilePicker
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.maps.BundledMaps
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.i18n.LocalStrings
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.stringsFor
import app.d6d.ui.i18n.strings
import app.d6d.ui.layout.LayoutStore
import app.d6d.ui.layout.LocalUiLayout
import app.d6d.ui.layout.UiLayoutState
import app.d6d.ui.settings.AppPreferencesState
import app.d6d.ui.settings.LocalAppPreferences
import app.d6d.ui.settings.PreferencesStore
import app.d6d.ui.settings.SettingsScreen
import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.ui.session.OpenGameSession
import app.d6d.ui.session.SessionWorkspace
import app.d6d.ui.session.WorkspaceRecoveryStore
import app.d6d.ui.session.WorkspaceOpenResult
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.state.awaitEnemyCpuTurnEnd
import app.d6d.ui.state.CombatResourceSink
import app.d6d.ui.state.CombatantEditSink
import app.d6d.content.srd521it.SrdBeasts
import app.d6d.ui.theme.AppTheme
import app.d6d.ui.theme.AtmosphericBackground
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.Palette
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class Destination(val icon: AppGlyph) {
    BATTAGLIA(AppGlyph.SWORDS),
    INCONTRO(AppGlyph.D20),
    COMPENDIO(AppGlyph.TOME),
    IMPOSTAZIONI(AppGlyph.GEAR),
}

/** Nome della voce di navigazione, nella lingua in uso. */
fun Destination.label(strings: Strings): String = when (this) {
    Destination.BATTAGLIA -> strings.nav.battle
    Destination.INCONTRO -> strings.nav.game
    Destination.COMPENDIO -> strings.nav.compendium
    Destination.IMPOSTAZIONI -> strings.nav.settings
}

// Larghezze della barra di navigazione desktop. Parte al minimo — solo le icone —
// e si allarga trascinando il bordo, esattamente come le colonne squadra e nemici.
// Oltre la soglia compaiono anche le etichette: sotto resta compatta.
private val RAIL_MIN = 54.dp
private val RAIL_MAX = 240.dp

// La soglia e' tarata sull'etichetta piu' lunga: sotto questa larghezza
// «Impostazioni» andrebbe a capo a meta' parola, che si legge peggio della sola
// icona. Va rialzata se un giorno arrivasse una voce con un nome ancora piu' lungo.
private val RAIL_LABELS_FROM = 116.dp

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
        var destination by remember { mutableStateOf(Destination.INCONTRO) }
        var compactNavigationOpen by remember { mutableStateOf(true) }
        var requestedCompendiumItemId by remember { mutableStateOf<String?>(null) }
        var requestedCompendiumNewKind by remember { mutableStateOf<RosterKind?>(null) }

        // Carica lingua e disposizione prima dei contenuti inclusi. In
        // particolare la lingua deve essere gia' definitiva quando il roster
        // costruisce le schede dei template: far partire i due caricamenti in
        // parallelo rendeva il risultato dipendente da quale disco finiva prima.
        val layoutStore = remember { LayoutStore(dataDirectory.resolve("layout.json")) }
        val layout = remember { UiLayoutState(store = layoutStore) }
        var layoutReady by remember { mutableStateOf(false) }
        LaunchedEffect(layout) {
            layout.restore(runDiskIo { layoutStore.load() })
            layoutReady = true
        }

        val preferencesStore = remember { PreferencesStore(dataDirectory.resolve("preferences.json")) }
        val preferences = remember { AppPreferencesState(store = preferencesStore) }
        var preferencesReady by remember { mutableStateOf(false) }
        LaunchedEffect(preferences) {
            preferences.restore(runDiskIo { preferencesStore.load() })
            // Allinea il vocabolario prima di sbloccare l'inizializzazione del
            // roster, non in un effetto concorrente della ricomposizione dopo.
            AppLocale.use(preferences.language)
            preferencesReady = true
        }

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
                    wildShapeProvider = { abilityId ->
                        SrdBeasts.byId(abilityId, AppLocale.language)?.toActorDefinition()
                    },
                    druidLevelProvider = { definitionId -> roster.druidLevelFor(definitionId) },
                    // Fuori dalla composizione il vocabolario si legge da
                    // `AppLocale`: qui non arriva alcun `LocalStrings`.
                    resourceSink = CombatResourceSink { definitionId, resources ->
                        if (!roster.applyCombatResources(definitionId, resources)) {
                            error(
                                roster.sheets.status
                                    ?: AppLocale.current.nav.sheetResourcesNotSaved,
                            )
                        }
                    },
                    editSink = CombatantEditSink { definitionId, snapshot ->
                        if (!roster.applyCombatEdit(definitionId, snapshot)) {
                            error(
                                roster.sheets.status
                                    ?: AppLocale.current.nav.sheetEditNotSaved,
                            )
                        }
                    },
                )
            }
        }
        val encounterBuilder = remember { EncounterBuilderViewModel(roster) }
        val portraits = remember {
            PortraitRepository(ImageStore(dataDirectory), filePicker, uiScope, loadOnCreate = false)
        }
        // All'avvio non si apre alcuna partita: il tavolo decide da Partita se
        // cominciarne una inclusa, costruirla dai propri template o riaprire un
        // salvataggio. Nessuno si ritrova dentro uno scontro che non ha scelto.
        val workspace = remember {
            SessionWorkspace(
                store = SessionArchiveStore(dataDirectory.resolve("sessions")),
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
        LaunchedEffect(roster, preferencesReady) {
            if (!preferencesReady) return@LaunchedEffect
            runCatching {
                runDiskIo {
                    roster.initialize()
                    roster.installIncludedContent()
                }
            }
            // La rilettura dell'indice ha un `runCatching` tutto suo, e non e' un
            // vezzo di stile: `syncMaps` confronta cio' che c'e' su disco con cio'
            // che l'indice conosce e **riscrive** l'indice. Su un indice non letto
            // crederebbe che non ci sia niente e salverebbe quel niente sopra le
            // mappe di chi gioca. Finche' stavano nello stesso `runCatching`, un
            // inciampo qualunque delle righe sopra — il Compendio che non si apre —
            // portava con se' anche questa, e la sincronizzazione partiva lo stesso.
            val indexRead = runCatching { runDiskIo { portraits.reload() } }.isSuccess
            if (indexRead) {
                runCatching { portraits.syncMaps(BundledMaps.seeds()) }
            }
            val recovered = runCatching { runDiskIo { recoveryStore.load() } }.getOrNull()
            // Un recupero riuscito riporta il tavolo dov'era: chi si e' schiantato
            // a meta' scontro ritrova lo scontro, non la procedura guidata.
            if (recovered != null && workspace.restoreRecovery(recovered)) {
                destination = Destination.BATTAGLIA
            }
            runCatching { runDiskIo { workspace.refreshSessionLists() } }
            recoveryReady = true
        }
        // La bozza segue ogni scheda aperta, ma aspetta che l'avvio abbia finito:
        // il tavolo predefinito non deve sovrascrivere un recupero ancora da leggere.
        LaunchedEffect(workspace, recoveryReady) {
            if (!recoveryReady) return@LaunchedEffect
            snapshotFlow { workspace.recoveryKey() }.collectLatest {
                delay(500)
                // La bozza aspetta la fine di un eventuale turno CPU: un recupero
                // che riparte da mezzo turno nemico non è quello che il tavolo
                // stava guardando.
                workspace.openSessions.forEach { it.battle.awaitEnemyCpuTurnEnd() }
                val snapshot = workspace.recoverySnapshot()
                runDiskIo { runCatching { recoveryStore.save(snapshot) } }
            }
        }
        val activeSession = workspace.activeSession
        var exitWarningOpen by remember { mutableStateOf(false) }

        LaunchedEffect(exitRequested) {
            if (exitRequested) {
                // Prima prova a chiudere pulitamente gli autosave nominati. Se
                // restano bozze o errori, la decisione torna all'utente.
                val prepared = workspace.prepareForPersistence()
                runDiskIo { workspace.flushAutosaves(prepared) }
                runDiskIo {
                    if (layoutReady) runCatching { layout.persist() }
                    if (preferencesReady) runCatching { preferences.persist() }
                }
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
        // `snapshotFlow` osserva i valori senza ricomporre la radice a ogni frame
        // di trascinamento; `collectLatest` annulla l'attesa precedente, quindi si
        // scrive su disco solo quando i pannelli si fermano.
        LaunchedEffect(layout, layoutReady) {
            if (!layoutReady) return@LaunchedEffect
            snapshotFlow { layout.snapshot() }.collectLatest {
                delay(600)
                runDiskIo { runCatching { layout.persist() } }
            }
        }

        // Preferenze dell'applicazione: separate dalla disposizione perche' sono
        // un'altra domanda — non dove stanno i pannelli, ma come l'app si comporta.
        // Stesso ciclo: caricamento fuori dal thread grafico, scrittura ritardata.
        LaunchedEffect(preferences, preferencesReady) {
            if (!preferencesReady) return@LaunchedEffect
            snapshotFlow { preferences.snapshot() }.collectLatest {
                delay(600)
                runDiskIo { runCatching { preferences.persist() } }
            }
        }
        // La lingua scelta raggiunge anche chi non vive in composizione: i view
        // model che rifiutano un comando, il racconto del turno nemico, gli errori
        // di salvataggio. Dentro la composizione ci pensa `LocalStrings` piu' sotto.
        LaunchedEffect(preferences.language, encounterBuilder, roster, portraits, workspace) {
            AppLocale.use(preferences.language)
            encounterBuilder.onLanguageChanged()
            roster.onLanguageChanged()
            portraits.onLanguageChanged()
            workspace.onLanguageChanged()
        }
        // Il ritmo della CPU e' una preferenza di chi guarda, non una proprieta'
        // dello scontro: da qui raggiunge subito le partite gia' aperte e quella
        // che la procedura sta preparando. Il valore fotografato in un salvataggio
        // resta leggibile, ma questo lo supera: la fonte di verita' e' una sola.
        LaunchedEffect(preferences, workspace, encounterBuilder) {
            // Osserva anche l'elenco delle schede aperte, non solo la preferenza:
            // una partita aperta dopo l'ultimo cambio arriva col ritmo scritto nel
            // proprio salvataggio, e va allineata anche lei.
            snapshotFlow { preferences.enemyCpuSpeed to workspace.openSessions.toList() }
                .collect { (speed, sessions) ->
                    encounterBuilder.enemyCpuSpeed = speed
                    sessions.forEach { it.battle.enemyCpuSpeed = speed }
                }
        }

        // Ogni scheda conserva il proprio effetto di autosave anche quando non e'
        // attiva: passare a un'altra mappa non cancella il debounce della prima.
        workspace.openSessions.forEach { opened ->
            key(opened.id) { SessionAutosaveEffect(workspace, opened) }
        }

        // Una ricreazione della shell (in particolare Activity su Android) non
        // passa dalla conferma desktop. Lo snapshot e l'eventuale settlement
        // restano sul contesto Compose; lo scope indipendente sopravvive invece
        // alla composizione abbastanza da completare il solo writer su I/O.
        val disposalPersistenceScope = remember(workspace) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        DisposableEffect(workspace, layout, preferences) {
            onDispose {
                val prepared = workspace.prepareForPersistence()
                disposalPersistenceScope.launch {
                    runCatching { workspace.flushAutosaves(prepared) }
                    // Se la shell viene distrutta mentre il caricamento iniziale
                    // e' ancora in corso, non sovrascrive i file esistenti con i
                    // valori predefiniti che erano visibili solo per un istante.
                    if (layoutReady) runCatching { layout.persist() }
                    if (preferencesReady) runCatching { preferences.persist() }
                }
            }
        }

        val openEncounter: (CombatSession, String, Map<String, String>) -> Unit =
            { session, name, presentation ->
                workspace.openNew(session, name, presentation)
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
                    // Il motivo del rifiuto vive gia' nello stato del workspace,
                    // che Partita mostra: non serve un secondo canale.
                    WorkspaceOpenResult.FAILED -> destination = Destination.INCONTRO
                }
            }
        }

        // Le schermate sono volutamente scambiate in modo diretto. Una dissolvenza
        // full-screen teneva vive, misurava e disegnava entrambe per 220 ms: fra
        // mappa ed editor del Compendio il picco era molto visibile su Windows.
        // I view model vivono sopra questo ramo, quindi lo stato applicativo resta.
        val content: @Composable (Modifier) -> Unit = { contentModifier ->
            when (destination) {
                Destination.BATTAGLIA -> if (activeSession == null) {
                    // Senza partita aperta la mappa non ha nulla da mostrare: si
                    // dice perche', e si offre la strada per cominciarne una.
                    NoOpenSessionScreen(
                        onOpenNewGame = { destination = Destination.INCONTRO },
                        modifier = contentModifier,
                    )
                } else {
                    key(activeSession.id) {
                        BattleScreen(
                            viewModel = activeSession.battle,
                            portraits = portraits,
                            sessions = activeSession.manager,
                            workspace = workspace,
                            roster = roster,
                            compact = compact,
                            onOpenCombatantSheet = { definitionId ->
                                if (roster.items.any { it.id == definitionId }) {
                                    requestedCompendiumItemId = definitionId
                                    requestedCompendiumNewKind = null
                                    destination = Destination.COMPENDIO
                                } else {
                                    activeSession.battle.showMessage { it.nav.sheetNotInCompendium }
                                }
                            },
                            onCreateRosterCharacter = {
                                requestedCompendiumItemId = null
                                requestedCompendiumNewKind = RosterKind.PERSONAGGIO
                                destination = Destination.COMPENDIO
                            },
                            onCreateRosterNpc = {
                                requestedCompendiumItemId = null
                                requestedCompendiumNewKind = RosterKind.NPC
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
                }

                Destination.INCONTRO ->
                    EncounterBuilderScreen(
                        viewModel = encounterBuilder,
                        compact = compact,
                        workspace = workspace,
                        onStarted = openEncounter,
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
                        modifier = contentModifier,
                    )

                Destination.IMPOSTAZIONI ->
                    SettingsScreen(
                        preferences = preferences,
                        layout = layout,
                        dataDirectory = dataDirectory,
                        compact = compact,
                        cursorPreferences = cursorPreferences,
                        modifier = contentModifier,
                    )
            }
        }

        CompositionLocalProvider(
            LocalUiLayout provides layout,
            LocalAppPreferences provides preferences,
            // Il vocabolario entra qui e scende fino all'ultima etichetta. Cambia
            // lingua e a essere ridisegnato e' solo chi legge davvero del testo.
            LocalStrings provides stringsFor(preferences.language),
        ) {
            Box(modifier.fillMaxSize()) {
                // Fondale atmosferico condiviso: resta fermo mentre le schermate si
                // alternano sopra. La battaglia dipinge il proprio fondo opaco,
                // quindi la mappa tattica non lo vede e resta pulita e leggibile.
                AtmosphericBackground(
                    modifier = Modifier.fillMaxSize(),
                    animationFrameIntervalMillis = atmosphericAnimationFrameMillis,
                    // La shell ferma l'animazione quando la finestra non serve;
                    // l'utente puo' fermarla sempre, dalle Impostazioni.
                    animationRunning = atmosphericAnimationRunning && preferences.animatedBackdrop,
                )

                if (compact) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
                        CompactBottomNavigation(
                            current = destination,
                            open = compactNavigationOpen,
                            onOpenChange = { compactNavigationOpen = it },
                            onSelect = { destination = it },
                        )
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
                val text = strings.nav
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
                            text.unsavedGamesTitle,
                            color = Palette.Text,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    text = {
                        Text(
                            text.unsavedGamesBody(
                                dirty = dirtySessions.size,
                                neverSaved = draftCount,
                                names = names,
                                truncated = dirtySessions.size > 4,
                            ),
                            color = Palette.TextMuted,
                        )
                    },
                    confirmButton = {
                        GameButton(text.returnAndSave, accent = Palette.Heal, onClick = {
                            exitWarningOpen = false
                            destination = Destination.INCONTRO
                        })
                    },
                    dismissButton = {
                        GameButton(text.exitWithoutSaving, accent = Palette.Enemy, onClick = {
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

/**
 * Navigazione mobile richiudibile.
 *
 * Un tocco sulla presa o un trascinamento verso il basso tolgono dal tavolo le
 * quattro destinazioni. Rimane soltanto una striscia sottile: non sottrae spazio
 * alla mappa ma offre sempre il gesto inverso per riaprire la navigazione.
 */
@Composable
private fun CompactBottomNavigation(
    current: Destination,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onSelect: (Destination) -> Unit,
) {
    val density = LocalDensity.current
    var dragDistance by remember { mutableStateOf(0f) }
    val thresholdPx = with(density) { 30.dp.toPx() }
    val dragModifier = Modifier.pointerInput(open, thresholdPx) {
        detectVerticalDragGestures(
            onDragStart = { dragDistance = 0f },
            onDragCancel = { dragDistance = 0f },
            onDragEnd = {
                if (open && dragDistance > thresholdPx) onOpenChange(false)
                if (!open && dragDistance < -thresholdPx) onOpenChange(true)
                dragDistance = 0f
            },
            onVerticalDrag = { change, amount ->
                change.consume()
                dragDistance += amount
            },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Abyss)
            .then(dragModifier),
    ) {
        GoldenRule()
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (open) 25.dp else 31.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (open) strings.nav.collapseRail else strings.nav.expandRail,
                ) { onOpenChange(!open) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (open) "⌄" else "⌃",
                color = Palette.Gold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (open) BottomNav(current, onSelect)
    }
}

/**
 * Battaglia senza alcuna partita aperta.
 *
 * E' lo stato in cui l'applicazione si apre. Non e' un errore ne' un caricamento
 * in corso: e' un tavolo sgombro, e l'unica cosa da dire e' dove si comincia.
 */
@Composable
private fun NoOpenSessionScreen(
    onOpenNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = strings.nav
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlyphIcon(AppGlyph.SWORDS, tint = Palette.Gold, size = 44.dp)
            Text(
                text = text.noOpenSessionTitle,
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = text.noOpenSessionBody,
                color = Palette.TextMuted,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            GameButton(text.goToGame, accent = Palette.Gold, primary = true, onClick = onOpenNewGame)
        }
    }
}

/**
 * Autosave con debounce di una singola scheda aperta.
 *
 * Vive in una composable propria per un motivo preciso: `key` apre un gruppo ma
 * non un nuovo ambito di ricomposizione, quindi leggere `state` e
 * `presentationState()` accanto agli altri effetti faceva ricomporre l'intera
 * radice a ogni attacco, spostamento e clic sul bersaglio. Qui le stesse letture
 * invalidano soltanto questa foglia, che non disegna nulla.
 */
@Composable
private fun SessionAutosaveEffect(workspace: SessionWorkspace, opened: OpenGameSession) {
    LaunchedEffect(
        opened.battle.state,
        opened.battle.presentationState(),
        opened.board.revision,
        opened.manager.currentSlug,
    ) {
        if (opened.manager.currentSlug != null && opened.manager.hasUnsavedChanges) {
            delay(1_200)
            opened.battle.awaitEnemyCpuTurnEnd()
            val prepared = opened.manager.prepareForPersistence()
            runDiskIo { workspace.flushAutosave(opened, prepared) }
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
    val strings = strings
    Column(
        Modifier
            .width(width)
            .fillMaxSize()
            .background(Palette.Abyss)
            .padding(vertical = 7.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Tasto per chiudere la barra: in cima, dove non copre le voci. Il chevron
        // da solo non e' un nome pronunciabile, quindi lo dichiara la semantica.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .clickable(role = Role.Button, onClick = onCollapse)
                .semantics { contentDescription = strings.nav.collapseRail }
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (showLabels) "‹  ${strings.nav.collapseRailShort}" else "‹",
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
    val label = strings.nav.expandRail
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
                .semantics { contentDescription = label }
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
    val label = destination.label(strings)
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
        // Con l'etichetta visibile il nome lo dice gia' il testo sotto al glifo:
        // ripeterlo qui lo farebbe annunciare due volte.
        GlyphIcon(
            glyph = destination.icon,
            tint = tint,
            size = 20.dp,
            contentDescription = label.takeUnless { showLabel },
        )
        if (showLabel) {
            Text(
                text = label,
                color = tint,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
