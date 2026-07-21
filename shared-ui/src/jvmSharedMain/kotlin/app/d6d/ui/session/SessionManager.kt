package app.d6d.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.persistence.session.SessionSummary
import app.d6d.ui.state.BattleViewModel
import java.io.IOException

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
) {

    var sessions by mutableStateOf<List<SessionSummary>>(emptyList())
        private set

    var status by mutableStateOf<String?>(null)

    /** Nome dell'ultima sessione salvata o caricata, proposto al salvataggio successivo. */
    var currentName by mutableStateOf("")

    var menuOpen by mutableStateOf(false)

    init {
        refresh()
    }

    fun refresh() = guard(null) {
        sessions = store.list()
    }

    fun save(displayName: String) = guard("Sessione salvata.") {
        val slug = store.save(displayName, battle.session, battle.presentationState())
        currentName = displayName.ifBlank { slug }
        sessions = store.list()
    }

    fun load(summary: SessionSummary) = guard("Sessione «${summary.displayName}» caricata.") {
        val archive = store.load(summary.slug)
        battle.adopt(archive.session, archive.presentation)
        currentName = archive.summary().displayName
        menuOpen = false
    }

    fun delete(summary: SessionSummary) = guard("Sessione eliminata.") {
        store.delete(summary.slug)
        sessions = store.list()
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
}
