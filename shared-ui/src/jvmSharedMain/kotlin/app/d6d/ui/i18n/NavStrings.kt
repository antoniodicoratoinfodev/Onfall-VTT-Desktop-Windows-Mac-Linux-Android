package app.d6d.ui.i18n

/**
 * La cornice dell'applicazione: barra di navigazione, schermo vuoto, uscita.
 *
 * E' il poco testo che non appartiene a nessuna schermata perche' le contiene
 * tutte.
 */
interface NavStrings {
    val battle: String
    val game: String
    val compendium: String
    val settings: String

    val collapseRail: String
    val expandRail: String
    /** Testo del tasto che chiude la barra, quando c'e' spazio per una parola. */
    val collapseRailShort: String

    val noOpenSessionTitle: String
    val noOpenSessionBody: String
    val goToGame: String

    val unsavedGamesTitle: String

    /**
     * Quante partite hanno modifiche da salvare, e quante non sono mai state
     * salvate. Una funzione e non due stringhe da concatenare: l'italiano e
     * l'inglese non mettono nello stesso punto ne' il numero ne' il verbo.
     */
    fun unsavedGamesBody(dirty: Int, neverSaved: Int, names: String, truncated: Boolean): String

    val returnAndSave: String
    val exitWithoutSaving: String

    /** Il combattente non ha una scheda corrispondente nel Compendio. */
    val sheetNotInCompendium: String
    val sheetResourcesNotSaved: String
    val sheetEditNotSaved: String
}

internal object NavStringsIt : NavStrings {
    override val battle = "Battaglia"
    override val game = "Partita"
    override val compendium = "Compendio"
    override val settings = "Impostazioni"

    override val collapseRail = "Chiudi la barra di navigazione"
    override val expandRail = "Apri la barra di navigazione"
    override val collapseRailShort = "Chiudi"

    override val noOpenSessionTitle = "Nessuna partita aperta"
    override val noOpenSessionBody =
        "Vai su Partita per cominciarne una nuova o riaprire un salvataggio."
    override val goToGame = "Vai a Partita"

    override val unsavedGamesTitle = "Partite non salvate"

    override fun unsavedGamesBody(
        dirty: Int,
        neverSaved: Int,
        names: String,
        truncated: Boolean,
    ): String = buildString {
        if (dirty == 1) {
            append("1 partita ha modifiche da salvare")
        } else {
            append("$dirty partite hanno modifiche da salvare")
        }
        if (neverSaved > 0) {
            append(if (neverSaved == 1) ", di cui una mai salvata" else ", di cui $neverSaved mai salvate")
        }
        append(".\n\n")
        append(names)
        if (truncated) append(" · …")
        append("\n\nTorna a Partita per salvarle o chiuderle una per una.")
    }

    override val returnAndSave = "Torna e salva"
    override val exitWithoutSaving = "Esci senza salvare"

    override val sheetNotInCompendium =
        "La scheda collegata a questo combattente non è presente nel Compendio."
    override val sheetResourcesNotSaved = "Risorse della scheda non salvate."
    override val sheetEditNotSaved = "Correzione della scheda non salvata."
}

internal object NavStringsEn : NavStrings {
    override val battle = "Battle"
    override val game = "Game"
    override val compendium = "Compendium"
    override val settings = "Settings"

    override val collapseRail = "Collapse the navigation bar"
    override val expandRail = "Expand the navigation bar"
    override val collapseRailShort = "Collapse"

    override val noOpenSessionTitle = "No game open"
    override val noOpenSessionBody =
        "Go to Game to start a new one or reopen a saved game."
    override val goToGame = "Go to Game"

    override val unsavedGamesTitle = "Unsaved games"

    override fun unsavedGamesBody(
        dirty: Int,
        neverSaved: Int,
        names: String,
        truncated: Boolean,
    ): String = buildString {
        if (dirty == 1) {
            append("1 game has unsaved changes")
        } else {
            append("$dirty games have unsaved changes")
        }
        if (neverSaved > 0) {
            append(
                if (neverSaved == 1) {
                    ", one of which has never been saved"
                } else {
                    ", $neverSaved of which have never been saved"
                },
            )
        }
        append(".\n\n")
        append(names)
        if (truncated) append(" · …")
        append("\n\nGo back to Game to save or close them one by one.")
    }

    override val returnAndSave = "Go back and save"
    override val exitWithoutSaving = "Quit without saving"

    override val sheetNotInCompendium =
        "The sheet this combatant comes from is not in the Compendium."
    override val sheetResourcesNotSaved = "Sheet resources were not saved."
    override val sheetEditNotSaved = "The correction to the sheet was not saved."
}
