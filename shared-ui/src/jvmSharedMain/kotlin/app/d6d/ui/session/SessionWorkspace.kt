package app.d6d.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.engine.CombatSession
import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.persistence.session.SessionSummary
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
    LAST_SESSION,
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
    val manager: SessionManager,
) {
    val displayName: String get() = manager.currentDisplayName
}

/**
 * Workspace multi-sessione dell'app.
 *
 * Le schede aperte restano vive in memoria anche quando non sono attive. Un file
 * salvato ha un solo proprietario alla volta: riaprire lo stesso slug porta alla
 * scheda esistente e due autosave non possono mai contendersi lo stesso JSON.
 */
class SessionWorkspace(
    private val store: SessionArchiveStore,
    initialSession: CombatSession,
    initialDisplayName: String,
    private val battleFactory: (CombatSession) -> BattleViewModel = { BattleViewModel(it) },
    private val refreshManagersOnCreate: Boolean = true,
) {
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
            else -> "${autosaveWarnings.size} autosave non riusciti: " +
                autosaveWarnings.values.joinToString(" · ")
        }

    val openSessions: List<OpenGameSession> get() = entries

    val activeSession: OpenGameSession
        get() = requireNotNull(entries.firstOrNull { it.id == activeId } ?: entries.firstOrNull()) {
            "Il workspace deve contenere almeno una sessione aperta"
        }

    init {
        openNew(initialSession, initialDisplayName)
        // L'apertura iniziale è lo stato normale dell'app, non una notifica utile.
        status = null
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
    ): OpenGameSession {
        val id = newId()
        val battle = battleFactory(session)
        if (presentation.isNotEmpty()) battle.adopt(session, presentation)
        freezeUnplacedFootprints(battle)
        val manager = managerFor(id, battle).also {
            it.beginUnsavedSession(displayName)
        }
        val opened = OpenGameSession(id, battle, manager)
        entries += opened
        activeId = id
        status = "Partita «${opened.displayName}» aperta in una nuova scheda."
        return opened
    }

    /** Apre un salvataggio in una nuova scheda oppure attiva quella che lo possiede già. */
    fun openSaved(summary: SessionSummary): WorkspaceOpenResult {
        entries.firstOrNull { it.manager.currentSlug == summary.slug }?.let { existing ->
            activeId = existing.id
            status = "«${existing.displayName}» era già aperta: scheda attivata."
            existing.manager.menuOpen = false
            return WorkspaceOpenResult.ALREADY_OPEN
        }

        return try {
            val archive = store.load(summary.slug)
            val id = newId()
            val battle = battleFactory(archive.session)
            val manager = managerFor(id, battle)
            manager.attachLoaded(archive)
            // Migrazione trasparente dei salvataggi più vecchi, nei quali la taglia
            // dei token non posizionati dipendeva ancora dal Compendio corrente.
            freezeUnplacedFootprints(battle)
            val opened = OpenGameSession(id, battle, manager)
            entries += opened
            activeId = id
            status = "Sessione «${opened.displayName}» aperta in una nuova scheda."
            WorkspaceOpenResult.OPENED
        } catch (failure: IOException) {
            status = "Errore su disco: ${failure.message}"
            WorkspaceOpenResult.FAILED
        } catch (failure: IllegalArgumentException) {
            status = "Sessione non valida: ${failure.message}"
            WorkspaceOpenResult.FAILED
        } catch (failure: IllegalStateException) {
            status = "Sessione non valida: ${failure.message}"
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
     * L'ultima resta aperta perche' Battaglia e il menu archivio hanno sempre
     * bisogno di un documento attivo. Crearne o aprirne un'altra sblocca subito
     * la chiusura.
     */
    fun requestClose(
        id: String,
        discardUnsavedChanges: Boolean = false,
    ): WorkspaceCloseResult {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return WorkspaceCloseResult.NOT_FOUND
        if (entries.size == 1) {
            status = "Apri o crea un'altra partita prima di chiudere l'ultima scheda."
            return WorkspaceCloseResult.LAST_SESSION
        }

        val closing = entries[index]
        if (closing.manager.hasUnsavedChanges && !discardUnsavedChanges) {
            activeId = closing.id
            status = "«${closing.displayName}» contiene modifiche non salvate."
            return WorkspaceCloseResult.UNSAVED_CHANGES
        }

        entries.removeAt(index)
        clearAutosaveWarning(closing.id)
        if (activeId == closing.id) {
            activeId = entries[index.coerceAtMost(entries.lastIndex)].id
        }
        status = "Scheda «${closing.displayName}» chiusa."
        return WorkspaceCloseResult.CLOSED
    }

    /** Autosave di tutte le partite collegate a un file, anche se non sono attive. */
    fun flushAutosaves() {
        entries.forEach { opened ->
            if (opened.manager.currentSlug != null && opened.manager.hasUnsavedChanges) {
                flushAutosave(opened)
            }
        }
    }

    /** Aggiorna gli elenchi archivio di tutte le schede aperte. */
    fun refreshSessionLists() {
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
                presentation = opened.battle.presentationState(),
            )
        },
    )

    /** Cattura tutti i documenti aperti, comprese le bozze mai salvate. */
    internal fun recoverySnapshot(): WorkspaceRecovery {
        val activeIndex = entries.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        return WorkspaceRecovery(
            activeIndex = activeIndex,
            sessions = entries.map { opened ->
                RecoveredGameSession(
                    displayName = opened.manager.currentName.ifBlank { opened.displayName },
                    currentSlug = opened.manager.currentSlug,
                    session = opened.battle.session,
                    presentation = opened.battle.presentationState(),
                )
            },
        )
    }

    /** Sostituisce il tavolo iniziale con una bozza lasciata da un arresto anomalo. */
    internal fun restoreRecovery(recovery: WorkspaceRecovery): Boolean {
        if (recovery.sessions.isEmpty()) return false
        val slugs = recovery.sessions.mapNotNull { it.currentSlug }
        if (slugs.size != slugs.toSet().size) return false

        val restored = recovery.sessions.map { recovered ->
            val id = newId()
            val battle = battleFactory(recovered.session)
            battle.adopt(recovered.session, recovered.presentation)
            freezeUnplacedFootprints(battle)
            val manager = managerFor(id, battle).also {
                it.attachRecovered(recovered.currentSlug, recovered.displayName)
            }
            OpenGameSession(id, battle, manager)
        }
        entries.clear()
        entries.addAll(restored)
        autosaveWarnings = emptyMap()
        activeId = restored[recovery.activeIndex.coerceIn(0, restored.lastIndex)].id
        status = if (restored.size == 1) {
            "Bozza della sessione precedente recuperata."
        } else {
            "Recuperate ${restored.size} sessioni dalla chiusura precedente."
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
        if (
            result == SessionSaveResult.FAILED ||
            result == SessionSaveResult.OPEN_IN_ANOTHER_TAB ||
            result == SessionSaveResult.NO_CURRENT_SESSION ||
            result == SessionSaveResult.NAME_COLLISION
        ) {
            val message = "«${opened.displayName}»: " +
                (opened.manager.status ?: "controlla il salvataggio della sessione.")
            autosaveWarnings = autosaveWarnings + (opened.id to message)
        } else if (result == SessionSaveResult.SAVED || result == SessionSaveResult.NOT_NEEDED) {
            clearAutosaveWarning(opened.id)
        }
        return result
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

    private fun managerFor(id: String, battle: BattleViewModel): SessionManager =
        SessionManager(
            store = store,
            battle = battle,
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

    private fun newId(): String = "sessione-aperta-${nextId++}"
}
