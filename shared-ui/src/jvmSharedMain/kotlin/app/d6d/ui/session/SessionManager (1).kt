package app.d6d.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.CombatState
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

    val isDirty: Boolean get() = hasUnsavedChanges
    val hasCurrentSave: Boolean
        get() = currentSlug != null && savedGeneration == battle.sessionGeneration
    val currentDirty: Boolean get() = hasCurrentSave && hasUnsavedChanges
    val unsaved: Boolean get() = hasUnsavedChanges

    var menuOpen by mutableStateOf(false)

    init {
        refresh()
    }

    fun refresh() = guard(null) {
        sessions = store.list()
    }

    /**
     * Salva senza sovrascrivere per coincidenza un'altra sessione con lo stesso
     * slug. La sovrascrittura e' automatica solo per il file attualmente aperto;
     * per ogni altro file deve essere richiesta esplicitamente.
     */
    fun save(displayName: String, overwriteExisting: Boolean = false): SessionSaveResult {
        val requestedSlug = SessionArchiveStore.slugify(displayName)
        if (slugOwnedByAnotherTab(requestedSlug)) {
            status = "Questa sessione è già aperta in un'altra scheda. Attivala oppure scegli un altro nome."
            return SessionSaveResult.OPEN_IN_ANOTHER_TAB
        }
        val ownsRequestedFile = currentSlug == requestedSlug && savedGeneration == battle.sessionGeneration
        if (store.exists(requestedSlug) && !ownsRequestedFile && !overwriteExisting) {
            status = "Esiste già una sessione con questo nome. Scegli un altro nome o conferma la sovrascrittura."
            return SessionSaveResult.NAME_COLLISION
        }
        return persist(displayName, requestedSlug, showSuccess = true)
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
        val slug = currentSlug
        if (slug == null || savedGeneration != battle.sessionGeneration) {
            status = "Salva prima la sessione con un nome per attivare il salvataggio automatico."
            return SessionSaveResult.NO_CURRENT_SESSION
        }
        if (slugOwnedByAnotherTab(slug)) {
            status = "Salvataggio sospeso: il file è collegato a un'altra scheda aperta."
            return SessionSaveResult.OPEN_IN_ANOTHER_TAB
        }
        if (!hasUnsavedChanges) return SessionSaveResult.NOT_NEEDED
        if (SessionArchiveStore.slugify(currentName) != slug) {
            status = "Il nome della sessione è cambiato: usa Salva per scegliere il nuovo file."
            return SessionSaveResult.NAME_COLLISION
        }
        return persist(currentName, slug, showSuccess = false)
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

    private fun persist(
        displayName: String,
        expectedSlug: String,
        showSuccess: Boolean,
    ): SessionSaveResult {
        return try {
            val slug = store.save(displayName, battle.session, battle.presentationState())
            check(slug == expectedSlug) { "Il nome del file della sessione è cambiato durante il salvataggio" }
            currentSlug = slug
            currentName = displayName.trim().ifBlank { slug }
            markSaved()
            // Il file e' gia' salvo se l'aggiornamento dell'elenco dovesse fallire.
            sessions = try {
                store.list()
            } catch (failure: IOException) {
                status = "Sessione salvata, ma l'elenco non è aggiornabile: ${failure.message}"
                return SessionSaveResult.SAVED
            }
            if (showSuccess) status = "Sessione salvata."
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

    private fun markSaved() {
        savedState = battle.state
        savedPresentation = battle.presentationState()
        savedDisplayName = currentName
        savedGeneration = battle.sessionGeneration
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
