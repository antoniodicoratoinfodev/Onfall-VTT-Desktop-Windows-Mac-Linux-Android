package app.d6d.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.CombatState
import app.d6d.engine.CombatSession
import app.d6d.persistence.session.SessionArchive
import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.persistence.session.SessionSummary
import app.d6d.ui.state.BattleViewModel
import java.io.IOException

enum class SessionSaveResult {
    SAVED,
    NOT_NEEDED,
    NAME_COLLISION,
    OPEN_IN_ANOTHER_TAB,
    NO_CURRENT_SESSION,
    FAILED,
}

enum class SessionLoadResult {
    LOADED,
    UNSAVED_CHANGES,
    FAILED,
}

/** Documento gia' fotografato sul contesto UI e sicuro da scrivere su I/O. */
class PreparedSessionPersistence internal constructor(
    internal val owner: SessionManager,
    internal val session: CombatSession,
    internal val state: CombatState,
    internal val presentation: Map<String, String>,
    internal val generation: Long,
    internal val currentName: String,
    internal val currentSlug: String?,
    internal val savedGeneration: Long?,
    internal val hasUnsavedChanges: Boolean,
)

/**
 * Salvataggio e ricarica delle sessioni.
 *
 * Il combattimento salvato porta con se' tutto: stato, mappa con segnaposti,
 * registro completo, stato del generatore casuale e le scelte di presentazione
 * del tavolo. Riaprire una sessione la riporta esattamente dov'era, compresi i
 * tiri futuri, perche' il seme e lo stato del generatore vengono conservati.
 */
class SessionManager(
    private val store: SessionArchiveStore,
    private val battle: BattleViewModel,
    /**
     * Lease fornito dal workspace multi-sessione. Impedisce che due schede aperte
     * diventino proprietarie dello stesso file e si sovrascrivano via autosave.
     */
    private val slugOwnedByAnotherTab: (String) -> Boolean = { false },
    refreshOnCreate: Boolean = true,
) {

    var sessions by mutableStateOf<List<SessionSummary>>(emptyList())
        private set

    var status by mutableStateOf<String?>(null)

    /** Nome dell'ultima sessione salvata o caricata, proposto al salvataggio successivo. */
    var currentName by mutableStateOf("")

    /** Slug del file a cui appartiene davvero la sessione aperta. */
    var currentSlug by mutableStateOf<String?>(null)
        private set

    val currentDisplayName: String
        get() = currentName.ifBlank { battle.displayName }

    // Anche le baseline sono stato Compose: quando un autosave le aggiorna senza
    // cambiare slug, titolo e tab devono spegnere subito l'indicatore "da salvare".
    private var savedState by mutableStateOf<CombatState?>(null)
    private var savedPresentation by mutableStateOf<Map<String, String>?>(null)
    private var savedDisplayName by mutableStateOf<String?>(null)
    private var savedGeneration by mutableStateOf<Long?>(null)

    /** Include sia lo stato del motore sia le scelte di presentazione. */
    val hasUnsavedChanges: Boolean
        get() = savedState != battle.state ||
            savedPresentation != battle.presentationState() ||
            savedGeneration != battle.sessionGeneration ||
            (savedDisplayName != null && currentName != savedDisplayName)

    val hasCurrentSave: Boolean
        get() = currentSlug != null && savedGeneration == battle.sessionGeneration
    val currentDirty: Boolean get() = hasCurrentSave && hasUnsavedChanges

    var menuOpen by mutableStateOf(false)

    init {
        if (refreshOnCreate) refresh()
    }

    fun refresh() = guard(null) {
        sessions = store.list()
    }

    /**
     * Porta il modello a un confine persistibile prima di passare al dispatcher I/O.
     *
     * Deve essere chiamato dal contesto UI: un turno CPU e' una sequenza di
     * comandi visibili ma un'unica operazione logica, quindi il file non deve mai
     * fotografarlo fra due passi. [save] e [flushAutosave] restano invece
     * operazioni bloccanti di solo archivio, adatte al dispatcher del disco.
     */
    fun prepareForPersistence(): PreparedSessionPersistence {
        battle.settleEnemyCpuTurn()
        return capturePersistenceSnapshot()
    }

    /**
     * Salva senza sovrascrivere per coincidenza un'altra sessione con lo stesso
     * slug. La sovrascrittura e' automatica solo per il file attualmente aperto;
     * per ogni altro file deve essere richiesta esplicitamente.
     */
    fun save(displayName: String, overwriteExisting: Boolean = false): SessionSaveResult {
        if (!persistenceReady()) return SessionSaveResult.FAILED
        return save(capturePersistenceSnapshot(), displayName, overwriteExisting)
    }

    /** Scrive soltanto il payload preparato, anche se il tavolo nel frattempo avanza. */
    fun save(
        prepared: PreparedSessionPersistence,
        displayName: String,
        overwriteExisting: Boolean = false,
    ): SessionSaveResult {
        if (!owns(prepared)) return SessionSaveResult.FAILED
        val requestedSlug = SessionArchiveStore.slugify(displayName)
        if (slugOwnedByAnotherTab(requestedSlug)) {
            status = "Questa sessione è già aperta in un'altra scheda. Attivala oppure scegli un altro nome."
            return SessionSaveResult.OPEN_IN_ANOTHER_TAB
        }
        val ownsRequestedFile =
            prepared.currentSlug == requestedSlug && prepared.savedGeneration == prepared.generation
        if (store.exists(requestedSlug) && !ownsRequestedFile && !overwriteExisting) {
            status = "Esiste già una sessione con questo nome. Scegli un altro nome o conferma la sovrascrittura."
            return SessionSaveResult.NAME_COLLISION
        }
        return persist(prepared, displayName, requestedSlug, showSuccess = true)
    }

    /**
     * Variante protetta che la UI puo' usare prima di cambiare sessione. Passare
     * `discardUnsavedChanges = true` equivale a una conferma esplicita dell'utente.
     */
    fun requestLoad(
        summary: SessionSummary,
        discardUnsavedChanges: Boolean = false,
    ): SessionLoadResult {
        if (hasUnsavedChanges && !discardUnsavedChanges) {
            status = "La sessione corrente contiene modifiche non salvate."
            return SessionLoadResult.UNSAVED_CHANGES
        }
        return loadInternal(summary)
    }

    /** Compatibilita' con il comando Apri esistente, che costituisce una conferma esplicita. */
    fun load(summary: SessionSummary): SessionLoadResult = loadInternal(summary)

    /**
     * Flush silenzioso e atomico del file corrente, adatto a chiusura e autosave.
     * Non inventa un nome e non puo' quindi sovrascrivere una sessione estranea.
     */
    fun flushAutosave(): SessionSaveResult {
        if (!persistenceReady()) return SessionSaveResult.FAILED
        return flushAutosave(capturePersistenceSnapshot())
    }

    /** Autosave del documento preparato: il writer non rilegge il modello vivo. */
    fun flushAutosave(prepared: PreparedSessionPersistence): SessionSaveResult {
        if (!owns(prepared)) return SessionSaveResult.FAILED
        val slug = prepared.currentSlug
        if (slug == null || prepared.savedGeneration != prepared.generation) {
            status = "Salva prima la sessione con un nome per attivare il salvataggio automatico."
            return SessionSaveResult.NO_CURRENT_SESSION
        }
        if (slugOwnedByAnotherTab(slug)) {
            status = "Salvataggio sospeso: il file è collegato a un'altra scheda aperta."
            return SessionSaveResult.OPEN_IN_ANOTHER_TAB
        }
        if (!prepared.hasUnsavedChanges) return SessionSaveResult.NOT_NEEDED
        if (SessionArchiveStore.slugify(prepared.currentName) != slug) {
            status = "Il nome della sessione è cambiato: usa Salva per scegliere il nuovo file."
            return SessionSaveResult.NAME_COLLISION
        }
        return persist(prepared, prepared.currentName, slug, showSuccess = false)
    }

    /** Scollega in modo esplicito un incontro appena creato dal file aperto prima. */
    fun beginUnsavedSession(displayName: String = battle.displayName) {
        clearCurrentSave()
        currentName = displayName.trim()
        status = null
    }

    fun delete(summary: SessionSummary) {
        if (slugOwnedByAnotherTab(summary.slug)) {
            status = "Chiudi prima la scheda che usa «${summary.displayName}»."
            return
        }
        guard("Sessione eliminata.") {
            store.delete(summary.slug)
            if (currentSlug == summary.slug) clearCurrentSave()
            sessions = store.list()
        }
    }

    fun dismissStatus() {
        status = null
    }

    /**
     * Le operazioni su disco possono fallire: qui diventano messaggi.
     *
     * Un salvataggio non riuscito non deve far cadere l'applicazione nel mezzo di
     * una partita, e un file danneggiato non deve impedire di aprirne altri.
     */
    private fun guard(successMessage: String?, block: () -> Unit) {
        try {
            block()
            if (successMessage != null) status = successMessage
        } catch (failure: IOException) {
            status = "Errore su disco: ${failure.message}"
        } catch (failure: IllegalArgumentException) {
            status = "Sessione non valida: ${failure.message}"
        }
    }

    private fun loadInternal(summary: SessionSummary): SessionLoadResult {
        if (slugOwnedByAnotherTab(summary.slug)) {
            status = "Questa sessione è già aperta in un'altra scheda."
            return SessionLoadResult.FAILED
        }
        return try {
            val archive = store.load(summary.slug)
            attachLoaded(archive, announce = true)
            SessionLoadResult.LOADED
        } catch (failure: IOException) {
            status = "Errore su disco: ${failure.message}"
            SessionLoadResult.FAILED
        } catch (failure: IllegalArgumentException) {
            status = "Sessione non valida: ${failure.message}"
            SessionLoadResult.FAILED
        }
    }

    /**
     * Collega a questo documento un archivio già letto dal workspace.
     *
     * È `internal` perché non è un comando utente: serve ad aprire il file in una
     * nuova scheda con un BattleViewModel dedicato, senza rileggerlo e soprattutto
     * senza sostituire la partita attiva.
     */
    internal fun attachLoaded(archive: SessionArchive, announce: Boolean = false) {
        battle.adopt(archive.session, archive.presentation)
        currentSlug = archive.summary().slug
        currentName = archive.summary().displayName
        markSaved()
        menuOpen = false
        status = if (announce) {
            "Sessione «${archive.summary().displayName}» caricata."
        } else {
            null
        }
    }

    /**
     * Ricollega una scheda recuperata al suo eventuale file senza dichiararla
     * già salvata: il contenuto della bozza può essere più recente dell'archivio.
     */
    internal fun attachRecovered(currentSlug: String?, displayName: String) {
        this.currentSlug = currentSlug
        currentName = displayName.trim()
        savedState = null
        savedPresentation = null
        savedDisplayName = currentName
        // Mantiene attivo l'autosave per le sessioni che possedevano già un file.
        savedGeneration = battle.sessionGeneration
        menuOpen = false
        status = null
    }

    private fun persist(
        prepared: PreparedSessionPersistence,
        displayName: String,
        expectedSlug: String,
        showSuccess: Boolean,
    ): SessionSaveResult {
        return try {
            val slug = store.save(displayName, prepared.session, prepared.presentation)
            check(slug == expectedSlug) { "Il nome del file della sessione è cambiato durante il salvataggio" }
            val persistedName = displayName.trim().ifBlank { slug }
            // Un writer lento non deve ricollegare una nuova partita o annullare
            // un rename concorrente. Se il documento e' ancora lo stesso, la
            // baseline resta esattamente quella serializzata: ogni comando
            // successivo continua quindi a risultare dirty.
            val stillSameDocument =
                battle.sessionGeneration == prepared.generation &&
                    currentSlug == prepared.currentSlug &&
                    currentName == prepared.currentName
            if (stillSameDocument) {
                currentSlug = slug
                currentName = persistedName
                markSaved(prepared, persistedName)
            } else {
                status = "Snapshot salvato; il tavolo e' cambiato durante la scrittura e resta da salvare."
            }
            // Il file e' gia' salvo se l'aggiornamento dell'elenco dovesse fallire.
            sessions = try {
                store.list()
            } catch (failure: IOException) {
                status = "Sessione salvata, ma l'elenco non è aggiornabile: ${failure.message}"
                return SessionSaveResult.SAVED
            }
            if (showSuccess && stillSameDocument) status = "Sessione salvata."
            SessionSaveResult.SAVED
        } catch (failure: IOException) {
            status = "Errore su disco: ${failure.message}"
            SessionSaveResult.FAILED
        } catch (failure: IllegalArgumentException) {
            status = "Sessione non valida: ${failure.message}"
            SessionSaveResult.FAILED
        } catch (failure: IllegalStateException) {
            status = "Sessione non valida: ${failure.message}"
            SessionSaveResult.FAILED
        }
    }

    /** Ultima difesa: il codice I/O non completa mai un playback dal proprio thread. */
    private fun persistenceReady(): Boolean {
        if (!battle.enemyCpuBusy) return true
        status = "Salvataggio rimandato: il turno CPU deve essere consolidato dall'interfaccia."
        return false
    }

    /** Snapshot senza settlement, per fallback sincroni gia' dichiarati ready. */
    internal fun snapshotForPersistence(): PreparedSessionPersistence = capturePersistenceSnapshot()

    private fun capturePersistenceSnapshot(): PreparedSessionPersistence {
        val snapshot = battle.persistenceSnapshot()
        val capturedName = currentName
        val capturedSlug = currentSlug
        val capturedSavedGeneration = savedGeneration
        return PreparedSessionPersistence(
            owner = this,
            session = snapshot.session,
            state = snapshot.state,
            presentation = snapshot.presentation,
            generation = snapshot.generation,
            currentName = capturedName,
            currentSlug = capturedSlug,
            savedGeneration = capturedSavedGeneration,
            hasUnsavedChanges = savedState != snapshot.state ||
                savedPresentation != snapshot.presentation ||
                capturedSavedGeneration != snapshot.generation ||
                (savedDisplayName != null && capturedName != savedDisplayName),
        )
    }

    private fun owns(prepared: PreparedSessionPersistence): Boolean {
        if (prepared.owner === this) return true
        status = "Snapshot di salvataggio non valido per questa sessione."
        return false
    }

    private fun markSaved(prepared: PreparedSessionPersistence, displayName: String) {
        savedState = prepared.state
        savedPresentation = prepared.presentation
        savedDisplayName = displayName
        savedGeneration = prepared.generation
    }

    private fun markSaved() {
        markSaved(capturePersistenceSnapshot(), currentName)
    }

    private fun clearCurrentSave() {
        currentSlug = null
        currentName = ""
        savedState = null
        savedPresentation = null
        savedDisplayName = null
        savedGeneration = null
    }
}
