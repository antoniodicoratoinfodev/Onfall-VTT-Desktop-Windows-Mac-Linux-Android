package app.d6d.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.board.BoardDocument
import app.d6d.engine.CombatSession
import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.persistence.session.SessionSummary
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.i18n.localizedSessionError
import app.d6d.ui.board.BoardController
import app.d6d.ui.state.BattleViewModel
import java.io.IOException

/** Esito dell'apertura di un archivio nel workspace. */
enum class WorkspaceOpenResult {
    OPENED,
    ALREADY_OPEN,
    FAILED,
}

/** Esito della richiesta di chiusura di una scheda di partita. */
enum class WorkspaceCloseResult {
    CLOSED,
    UNSAVED_CHANGES,
    NOT_FOUND,
}

internal data class WorkspaceRecoveryKey(
    val activeId: String?,
    val sessions: List<WorkspaceSessionRecoveryKey>,
)

internal data class WorkspaceSessionRecoveryKey(
    val id: String,
    val displayName: String,
    val currentSlug: String?,
    val stateRevision: Long,
    val eventCount: Int,
    val sessionGeneration: Long,
    val boardRevision: Long,
    val presentation: Map<String, String>,
)

/**
 * Una partita realmente aperta.
 *
 * Il [battle] non viene mai condiviso con un'altra voce: motore, RNG, Undo,
 * mappa, selezioni e registro restano quindi autonomi. [manager] conserva invece
 * il collegamento eventuale al singolo file dell'archivio.
 */
class OpenGameSession internal constructor(
    val id: String,
    val battle: BattleViewModel,
    val board: BoardController,
    val manager: SessionManager,
) {
    val displayName: String get() = manager.currentDisplayName
}

/** Insieme di snapshot gia' chiusi sul contesto UI, pronto per il dispatcher I/O. */
class PreparedWorkspacePersistence internal constructor(
    internal val sessions: List<PreparedWorkspaceSession>,
)

internal data class PreparedWorkspaceSession(
    val opened: OpenGameSession,
    val persistence: PreparedSessionPersistence,
)

/**
 * Workspace multi-sessione dell'app.
 *
 * Le schede aperte restano vive in memoria anche quando non sono attive. Un file
 * salvato ha un solo proprietario alla volta: riaprire lo stesso slug porta alla
 * scheda esistente e due autosave non possono mai contendersi lo stesso JSON.
 */
class SessionWorkspace(
    private val store: SessionArchiveStore,
    private val battleFactory: (CombatSession) -> BattleViewModel = { BattleViewModel(it) },
    private val refreshManagersOnCreate: Boolean = true,
) {

    /** Vocabolario in uso: qui non arriva `LocalStrings`, siamo fuori da Compose. */
    private val words get() = AppLocale.current.session

    private val entries = mutableStateListOf<OpenGameSession>()
    private var nextId = 1L

    var activeId by mutableStateOf<String?>(null)
        private set

    var status by mutableStateOf<String?>(null)
        private set

    private var autosaveWarnings by mutableStateOf<Map<String, String>>(emptyMap())

    val autosaveWarning: String?
        get() = when (autosaveWarnings.size) {
            0 -> null
            1 -> autosaveWarnings.values.first()
            else -> words.autosaveFailures(autosaveWarnings.size, "") +
                autosaveWarnings.values.joinToString(" · ")
        }

    val openSessions: List<OpenGameSession> get() = entries

    internal fun onLanguageChanged() {
        status = null
        autosaveWarnings = emptyMap()
        entries.forEach {
            it.manager.clearLocalizedStatus()
            // Anche il riscontro gia' composto dentro ogni partita aperta: la
            // conferma di un attacco e i numeri sopra i combattenti non sanno
            // ridirsi, e restare nella lingua di prima si vede.
            it.battle.onLanguageChanged()
        }
    }

    /**
     * Partita in primo piano, oppure null.
     *
     * L'assenza e' uno stato ordinario, non un errore: l'applicazione si apre
     * cosi', su Partita e senza nulla di gia' avviato, e ci torna quando il tavolo
     * chiude l'ultima scheda. Battaglia e i comandi di sessione devono quindi
     * saperlo reggere invece di darlo per scontato.
     */
    val activeSession: OpenGameSession?
        get() = entries.firstOrNull { it.id == activeId } ?: entries.firstOrNull()

    val hasOpenSessions: Boolean get() = entries.isNotEmpty()

    /**
     * Salvataggi presenti su disco.
     *
     * Vive nel workspace e non nel [SessionManager] di un documento perche'
     * l'elenco serve anche quando non c'e' alcun documento: e' proprio da li' che
     * si riapre una partita a workspace vuoto.
     */
    var savedSessions by mutableStateOf<List<SessionSummary>>(emptyList())
        private set

    init {
        if (refreshManagersOnCreate) refreshArchive()
    }

    /** Rilegge dal disco l'elenco dei salvataggi; un errore diventa uno stato, non un'eccezione. */
    fun refreshArchive() {
        try {
            savedSessions = store.list()
        } catch (failure: IOException) {
            status = words.diskError(localizedDetail(failure))
        }
    }

    /**
     * Aggiunge una nuova partita senza sostituire quella corrente.
     *
     * Gli ingombri dei token non ancora posizionati vengono fotografati subito
     * nella presentation: una futura modifica della taglia nel Compendio varrà
     * per le partite nuove, non cambierà retroattivamente quelle già aperte.
     */
    fun openNew(
        session: CombatSession,
        displayName: String,
        presentation: Map<String, String> = emptyMap(),
        boardDocument: BoardDocument = BoardDocument.empty(),
    ): OpenGameSession {
        val id = newId()
        val battle = battleFactory(session)
        val board = BoardController(boardDocument)
        if (presentation.isNotEmpty()) battle.adopt(session, presentation)
        freezeUnplacedFootprints(battle)
        val manager = managerFor(id, battle, board).also {
            it.beginUnsavedSession(displayName)
        }
        val opened = OpenGameSession(id, battle, board, manager)
        entries += opened
        activeId = id
        status = words.gameOpenedInNewTab(opened.displayName)
        return opened
    }

    /** Apre un salvataggio in una nuova scheda oppure attiva quella che lo possiede già. */
    fun openSaved(summary: SessionSummary): WorkspaceOpenResult {
        entries.firstOrNull { it.manager.currentSlug == summary.slug }?.let { existing ->
            activeId = existing.id
            status = words.alreadyOpenTabActivated(existing.displayName)
            existing.manager.menuOpen = false
            return WorkspaceOpenResult.ALREADY_OPEN
        }

        return try {
            val archive = store.load(summary.slug)
            val id = newId()
            val battle = battleFactory(archive.session)
            val board = BoardController(archive.board)
            val manager = managerFor(id, battle, board)
            manager.attachLoaded(archive)
            // Migrazione trasparente dei salvataggi più vecchi, nei quali la taglia
            // dei token non posizionati dipendeva ancora dal Compendio corrente.
            freezeUnplacedFootprints(battle)
            val opened = OpenGameSession(id, battle, board, manager)
            entries += opened
            activeId = id
            status = words.sessionOpenedInNewTab(opened.displayName)
            WorkspaceOpenResult.OPENED
        } catch (failure: IOException) {
            status = words.diskError(localizedDetail(failure))
            WorkspaceOpenResult.FAILED
        } catch (failure: IllegalArgumentException) {
            status = words.invalidSession(localizedDetail(failure))
            WorkspaceOpenResult.FAILED
        } catch (failure: IllegalStateException) {
            status = words.invalidSession(localizedDetail(failure))
            WorkspaceOpenResult.FAILED
        }
    }

    fun activate(id: String): Boolean {
        if (entries.none { it.id == id }) return false
        activeId = id
        status = null
        return true
    }

    /**
     * Chiude soltanto la scheda, mai il suo file.
     *
     * Anche l'ultima si puo' chiudere: il workspace vuoto e' lo stesso stato in
     * cui l'applicazione si apre, e riporta il tavolo su Partita.
     */
    fun requestClose(
        id: String,
        discardUnsavedChanges: Boolean = false,
    ): WorkspaceCloseResult {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return WorkspaceCloseResult.NOT_FOUND

        val closing = entries[index]
        if (closing.manager.hasUnsavedChanges && !discardUnsavedChanges) {
            activeId = closing.id
            status = words.hasUnsavedChanges(closing.displayName)
            return WorkspaceCloseResult.UNSAVED_CHANGES
        }

        entries.removeAt(index)
        clearAutosaveWarning(closing.id)
        if (activeId == closing.id) {
            activeId = entries.getOrNull(index.coerceAtMost(entries.lastIndex))?.id
        }
        status = if (entries.isEmpty()) {
            words.tabClosedNoneLeft(closing.displayName)
        } else {
            words.tabClosed(closing.displayName)
        }
        return WorkspaceCloseResult.CLOSED
    }

    /**
     * Chiude sul contesto UI gli eventuali playback prima di una scrittura globale.
     * Va chiamato prima di spostare [flushAutosaves] sul dispatcher del disco.
     */
    fun prepareForPersistence(): PreparedWorkspacePersistence {
        val openedSessions = entries.toList()
        return PreparedWorkspacePersistence(
            openedSessions.map { opened ->
                PreparedWorkspaceSession(opened, opened.manager.prepareForPersistence())
            },
        )
    }

    /**
     * Autosave di tutte le partite collegate a un file, anche se non sono attive.
     *
     * Non consolida i turni CPU: una scheda con un playback in corso viene
     * rifiutata dal proprio manager e finisce fra gli avvisi. Chi chiude o salva
     * davvero usa [prepareForPersistence] e l'overload che ne riceve l'esito.
     */
    fun flushAutosaves() {
        entries.forEach { opened ->
            if (opened.manager.currentSlug != null && opened.manager.hasUnsavedChanges) {
                flushAutosave(opened)
            }
        }
    }

    /** Writer puro: usa soltanto documenti gia' preparati, anche se una tab viene chiusa. */
    fun flushAutosaves(prepared: PreparedWorkspacePersistence) {
        prepared.sessions.forEach { document ->
            if (
                document.persistence.currentSlug != null &&
                document.persistence.hasUnsavedChanges
            ) {
                flushAutosave(document.opened, document.persistence)
            }
        }
    }

    /** Aggiorna gli elenchi archivio di tutte le schede aperte. */
    fun refreshSessionLists() {
        refreshArchive()
        entries.forEach { it.manager.refresh() }
    }

    /** Chiave leggera osservabile da Compose per ritardare la scrittura della bozza. */
    internal fun recoveryKey(): WorkspaceRecoveryKey = WorkspaceRecoveryKey(
        activeId = activeId,
        sessions = entries.map { opened ->
            WorkspaceSessionRecoveryKey(
                id = opened.id,
                displayName = opened.manager.currentName,
                currentSlug = opened.manager.currentSlug,
                stateRevision = opened.battle.state.revision(),
                eventCount = opened.battle.events.size,
                sessionGeneration = opened.battle.sessionGeneration,
                boardRevision = opened.board.revision,
                presentation = opened.battle.presentationState(),
            )
        },
    )

    /** Cattura tutti i documenti aperti, comprese le bozze mai salvate. */
    internal fun recoverySnapshot(): WorkspaceRecovery {
        val openedSessions = entries.toList()
        val activeIndex = openedSessions.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        val snapshots = openedSessions.map { opened ->
            opened to opened.manager.snapshotForPersistence()
        }
        return WorkspaceRecovery(
            activeIndex = activeIndex,
            sessions = snapshots.map { (opened, snapshot) ->
                RecoveredGameSession(
                    displayName = snapshot.currentName.ifBlank { snapshot.state.encounterId() },
                    currentSlug = snapshot.currentSlug,
                    session = snapshot.session,
                    presentation = snapshot.presentation,
                    board = snapshot.board,
                )
            },
        )
    }

    /** Ripopola il workspace con le bozze lasciate da un arresto anomalo. */
    internal fun restoreRecovery(recovery: WorkspaceRecovery): Boolean {
        if (recovery.sessions.isEmpty()) return false
        val slugs = recovery.sessions.mapNotNull { it.currentSlug }
        if (slugs.size != slugs.toSet().size) return false

        val restored = recovery.sessions.map { recovered ->
            val id = newId()
            val battle = battleFactory(recovered.session)
            val board = BoardController(recovered.board)
            battle.adopt(recovered.session, recovered.presentation)
            freezeUnplacedFootprints(battle)
            val manager = managerFor(id, battle, board).also {
                it.attachRecovered(recovered.currentSlug, recovered.displayName)
            }
            OpenGameSession(id, battle, board, manager)
        }
        entries.clear()
        entries.addAll(restored)
        autosaveWarnings = emptyMap()
        activeId = restored[recovery.activeIndex.coerceIn(0, restored.lastIndex)].id
        status = if (restored.size == 1) {
            words.previousDraftRecovered
        } else {
            words.recoveredSessions(restored.size)
        }
        return true
    }

    /**
     * Esegue l'autosave di una scheda e porta l'eventuale errore anche a livello
     * workspace: in questo modo resta visibile mentre l'utente sta lavorando su
     * un'altra mappa.
     */
    fun flushAutosave(opened: OpenGameSession): SessionSaveResult {
        if (entries.none { it.id == opened.id }) return SessionSaveResult.NO_CURRENT_SESSION
        val result = opened.manager.flushAutosave()
        updateAutosaveWarning(opened, result, opened.displayName)
        return result
    }

    /** Autosave di uno snapshot preparato; non consulta sessione, nome o slug vivi. */
    fun flushAutosave(
        opened: OpenGameSession,
        prepared: PreparedSessionPersistence,
    ): SessionSaveResult {
        val result = opened.manager.flushAutosave(prepared)
        val displayName = prepared.currentName.ifBlank { prepared.state.encounterId() }
        updateAutosaveWarning(opened, result, displayName)
        return result
    }

    private fun updateAutosaveWarning(
        opened: OpenGameSession,
        result: SessionSaveResult,
        displayName: String,
    ) {
        if (
            result == SessionSaveResult.FAILED ||
            result == SessionSaveResult.OPEN_IN_ANOTHER_TAB ||
            result == SessionSaveResult.NO_CURRENT_SESSION ||
            result == SessionSaveResult.NAME_COLLISION
        ) {
            val message = "«$displayName»: " +
                (opened.manager.status ?: words.checkSessionSave)
            autosaveWarnings = autosaveWarnings + (opened.id to message)
        } else if (result == SessionSaveResult.SAVED || result == SessionSaveResult.NOT_NEEDED) {
            clearAutosaveWarning(opened.id)
        }
    }

    fun dismissStatus() {
        status = null
    }

    fun dismissAutosaveWarning() {
        autosaveWarnings = emptyMap()
    }

    /** Rimuove un vecchio avviso dopo un salvataggio manuale riuscito. */
    fun reconcileAutosaveWarning() {
        val resolved = autosaveWarnings.keys.filter { ownerId ->
            val owner = entries.firstOrNull { it.id == ownerId }
            owner == null || owner.manager.currentSlug == null || !owner.manager.hasUnsavedChanges
        }
        if (resolved.isNotEmpty()) {
            autosaveWarnings = autosaveWarnings - resolved.toSet()
        }
    }

    private fun managerFor(id: String, battle: BattleViewModel, board: BoardController): SessionManager =
        SessionManager(
            store = store,
            battle = battle,
            board = board,
            slugOwnedByAnotherTab = { slug ->
                entries.any { it.id != id && it.manager.currentSlug == slug }
            },
            refreshOnCreate = refreshManagersOnCreate,
        )

    private fun freezeUnplacedFootprints(battle: BattleViewModel) {
        battle.state.combatants().keys.forEach { combatantId ->
            if (battle.placementOf(combatantId) == null && combatantId !in battle.footprints) {
                battle.setFootprint(combatantId, battle.squaresPerSideFor(combatantId))
            }
        }
    }

    private fun clearAutosaveWarning(ownerId: String) {
        if (ownerId in autosaveWarnings) autosaveWarnings = autosaveWarnings - ownerId
    }

    private fun localizedDetail(failure: Throwable): String =
        localizedSessionError(failure.message.orEmpty(), AppLocale.language)

    private fun newId(): String = "sessione-aperta-${nextId++}"
}
