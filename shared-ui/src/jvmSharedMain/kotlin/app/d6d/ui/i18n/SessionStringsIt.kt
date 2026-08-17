package app.d6d.ui.i18n

/** Partite aperte e salvate, in italiano. */
internal object SessionStringsIt : SessionStrings {

    override val noOpenGame = "Nessuna partita aperta"
    override val noOpenGameHint =
        "Scegli una partita inclusa, parti dai tuoi template oppure riapri una sessione salvata."
    override val newGame = "Nuova partita"
    override val openSessions = "Sessioni aperte"
    override val sessionLabel = "Sessione"
    override val sessionsLabel = "Sessioni"
    override val draftLabel = "Bozza"
    override val openSessionsHint = "Mappe, turni, dadi, registro e Annulla restano indipendenti."
    override val goToMap = "Vai alla mappa"
    override val saveOrManage = "Salva / gestisci"
    override val closeTab = "Chiudi scheda"
    override val actionsOnActive = "Azioni sulla sessione attiva"
    override val pickAGame =
        "Seleziona una partita. Ogni scheda conserva la propria mappa e il proprio combattimento."
    override val switchSession = "Cambia sessione"
    override val unsavedDraft = "Bozza non salvata"
    override val unsavedChanges = "Modifiche non salvate"
    override val draftToSave = "Bozza da salvare"
    override val changesToSave = "Modifiche da salvare"
    override val unnamedGame = "Partita senza nome"
    override val saveFirst = "Salva prima"
    override val closeWithoutSaving = "Chiudi senza salvare"
    override fun openCount(count: Int, state: String) = "$count aperte · $state · Gestisci"
    override fun openSessionsCount(count: Int) = "Sessioni aperte · $count"
    override fun savedAtRound(round: Int) = "Salvata · round $round"
    override fun closingLosesDraft(name: String) =
        "«$name» non è mai stata salvata. Chiudendo la scheda la bozza verrà persa."
    override fun closingKeepsFile(name: String) = "«$name» rimarrà nell'archivio solo fino " +
        "all'ultimo salvataggio. La chiusura della scheda non elimina il file salvato."

    override val autosaveToCheck = "Autosave da controllare"
    override val toSave = "Da salvare"
    override val saveExplainer = "Una sessione salvata conserva combattimento, mappa, " +
        "segnaposti, registro completo e stato dei dadi: riaprendola i tiri futuri sono gli stessi."
    override val saveExplainerTab =
        " Viene aperta in una scheda indipendente senza chiudere le altre."
    override val unsavedBattleChanges = "Ci sono modifiche alla battaglia non ancora salvate."
    override val saveWithName = "Salva con nome"
    override val noSavedSession = "Nessuna sessione salvata."
    override val openInTab = "Apri in scheda"
    override val replaceSessionTitle = "Sostituire la sessione?"
    override val replaceSessionBody =
        "Esiste già una sessione con questo nome. Il salvataggio precedente verrà sostituito."
    override val discardChangesTitle = "Scartare le modifiche?"
    override val discardAndOpen = "Scarta e apri"
    override val deleteSessionTitle = "Eliminare la sessione?"
    override val openSavedSessionTitle = "Apri una sessione salvata"
    override val emptyArchive = "L'archivio è vuoto: nessuna sessione è ancora stata salvata."
    override val damagedFile = "File danneggiato"
    override val preparedSessionsHint =
        "Passa a un'altra mappa senza chiudere o ricaricare la partita corrente."
    override fun autosaveToCheckWithCount(count: Int) = "Autosave da controllare · $count aperte"
    override fun toSaveWithCount(count: Int) = "Da salvare · $count aperte"
    override fun openTabs(count: Int) = "$count aperte"
    override fun preparedSessions(count: Int) = "Sessioni preparate ($count)"
    override fun savedSessions(count: Int) = "Sessioni salvate ($count)"
    override fun discardChangesBody(name: String) =
        "Aprendo «$name» perderai le modifiche non salvate della battaglia corrente."
    override fun deleteSessionBody(name: String) =
        "«$name» verrà eliminata definitivamente dal dispositivo."
    override fun round(value: Int) = "Round $value"
    override fun combatants(count: Int) = "$count combattenti"

    override val alreadyOpenInAnotherTab = "Questa sessione è già aperta in un'altra scheda."
    override val alreadyOpenPickAnotherName =
        "Questa sessione è già aperta in un'altra scheda. Attivala oppure scegli un altro nome."
    override val nameTakenConfirmOverwrite =
        "Esiste già una sessione con questo nome. Scegli un altro nome o conferma la sovrascrittura."
    override val currentSessionHasUnsavedChanges =
        "La sessione corrente contiene modifiche non salvate."
    override val saveWithNameToEnableAutosave =
        "Salva prima la sessione con un nome per attivare il salvataggio automatico."
    override val autosavePausedFileInAnotherTab =
        "Salvataggio sospeso: il file è collegato a un'altra scheda aperta."
    override val nameChangedUseSave =
        "Il nome della sessione è cambiato: usa Salva per scegliere il nuovo file."
    override val sessionDeleted = "Sessione eliminata."
    override val snapshotSavedTableMovedOn =
        "Snapshot salvato; il tavolo è cambiato durante la scrittura e resta da salvare."
    override val sessionSaved = "Sessione salvata."
    override val savePostponedForCpuTurn =
        "Salvataggio rimandato: il turno CPU deve essere consolidato dall'interfaccia."
    override val invalidSnapshotForThisSession =
        "Snapshot di salvataggio non valido per questa sessione."
    override val previousDraftRecovered = "Bozza della sessione precedente recuperata."
    override val checkSessionSave = "controlla il salvataggio della sessione."
    override fun closeTabUsingFile(name: String) = "Chiudi prima la scheda che usa «$name»."
    override fun diskError(detail: String) = "Errore su disco: $detail"
    override fun invalidSession(detail: String) = "Sessione non valida: $detail"
    override fun sessionLoaded(name: String) = "Sessione «$name» caricata."
    override fun savedButListNotRefreshed(detail: String) =
        "Sessione salvata, ma l'elenco non è aggiornabile: $detail"
    override fun gameOpenedInNewTab(name: String) = "Partita «$name» aperta in una nuova scheda."
    override fun alreadyOpenTabActivated(name: String) = "«$name» era già aperta: scheda attivata."
    override fun sessionOpenedInNewTab(name: String) =
        "Sessione «$name» aperta in una nuova scheda."
    override fun hasUnsavedChanges(name: String) = "«$name» contiene modifiche non salvate."
    override fun tabClosedNoneLeft(name: String) =
        "Scheda «$name» chiusa: nessuna partita aperta."
    override fun tabClosed(name: String) = "Scheda «$name» chiusa."
    override fun recoveredSessions(count: Int) =
        "Recuperate $count sessioni dalla chiusura precedente."
    override fun autosaveFailures(count: Int, detail: String) =
        "$count autosave non riusciti: $detail"

    override val currentBattleNotSaved = "La battaglia corrente non è salvata"
    override val currentBattleNotSavedBody = "Avviando il nuovo incontro perderai lo stato " +
        "corrente. Puoi tornare alla battaglia e salvarla con un nome."
    override val keepPreparing = "Continua a preparare"
    override val goBackAndSave = "Torna e salva"
    override val discardAndStart = "Scarta e avvia"
}
