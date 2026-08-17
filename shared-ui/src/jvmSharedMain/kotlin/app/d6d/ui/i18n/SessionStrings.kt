package app.d6d.ui.i18n

import app.d6d.i18n.AppLanguage

/**
 * Le partite aperte e quelle salvate.
 *
 * Schede, salvataggi, autosave e recuperi dopo una chiusura brusca. E' il
 * fascicolo che parla piu' spesso di file, quindi anche quello dove una frase
 * vaga costa di piu': chi legge sta decidendo se rischiare di perdere qualcosa.
 */
interface SessionStrings {

    // --- pannello delle partite aperte ------------------------------------------

    val noOpenGame: String
    val noOpenGameHint: String
    val newGame: String
    val openSessions: String
    val sessionLabel: String
    val sessionsLabel: String
    val draftLabel: String
    val openSessionsHint: String
    val goToMap: String
    val saveOrManage: String
    val closeTab: String
    val actionsOnActive: String
    val pickAGame: String
    val switchSession: String
    val unsavedDraft: String
    val unsavedChanges: String
    val draftToSave: String
    val changesToSave: String
    val unnamedGame: String
    val saveFirst: String
    val closeWithoutSaving: String
    fun openCount(count: Int, state: String): String
    fun openSessionsCount(count: Int): String
    fun savedAtRound(round: Int): String
    fun closingLosesDraft(name: String): String
    fun closingKeepsFile(name: String): String

    // --- menu dei salvataggi ------------------------------------------------------

    val autosaveToCheck: String
    val toSave: String
    val saveExplainer: String
    val saveExplainerTab: String
    val unsavedBattleChanges: String
    val saveWithName: String
    val noSavedSession: String
    val openInTab: String
    val replaceSessionTitle: String
    val replaceSessionBody: String
    val discardChangesTitle: String
    val discardAndOpen: String
    val deleteSessionTitle: String
    val openSavedSessionTitle: String
    val emptyArchive: String
    val damagedFile: String
    val preparedSessionsHint: String
    fun autosaveToCheckWithCount(count: Int): String
    fun toSaveWithCount(count: Int): String
    fun openTabs(count: Int): String
    fun preparedSessions(count: Int): String
    fun savedSessions(count: Int): String
    fun discardChangesBody(name: String): String
    fun deleteSessionBody(name: String): String
    fun round(value: Int): String
    fun combatants(count: Int): String

    // --- esiti del salvataggio ------------------------------------------------------

    val alreadyOpenInAnotherTab: String
    val alreadyOpenPickAnotherName: String
    val nameTakenConfirmOverwrite: String
    val currentSessionHasUnsavedChanges: String
    val saveWithNameToEnableAutosave: String
    val autosavePausedFileInAnotherTab: String
    val nameChangedUseSave: String
    val sessionDeleted: String
    val snapshotSavedTableMovedOn: String
    val sessionSaved: String
    val savePostponedForCpuTurn: String
    val invalidSnapshotForThisSession: String
    val previousDraftRecovered: String
    val checkSessionSave: String
    fun closeTabUsingFile(name: String): String
    fun diskError(detail: String): String
    fun invalidSession(detail: String): String
    fun sessionLoaded(name: String): String
    fun savedButListNotRefreshed(detail: String): String
    fun gameOpenedInNewTab(name: String): String
    fun alreadyOpenTabActivated(name: String): String
    fun sessionOpenedInNewTab(name: String): String
    fun hasUnsavedChanges(name: String): String
    fun tabClosedNoneLeft(name: String): String
    fun tabClosed(name: String): String
    fun recoveredSessions(count: Int): String
    fun autosaveFailures(count: Int, detail: String): String

    // --- avviso prima di scartare -------------------------------------------------------

    val currentBattleNotSaved: String
    val currentBattleNotSavedBody: String
    val keepPreparing: String
    val goBackAndSave: String
    val discardAndStart: String
}

/**
 * Traduce i dettagli tecnici prodotti dall'archivio delle sessioni.
 *
 * Il livello di presentazione aggiunge gia' un'intestazione localizzata, ma
 * alcuni codec storici emettono ancora il dettaglio in una lingua fissa. I
 * percorsi JSON restano intenzionalmente invariati: servono a capire quale
 * campo del salvataggio sia danneggiato.
 */
internal fun localizedSessionError(detail: String, language: AppLanguage): String {
    if (detail.isBlank()) return detail
    return when (language) {
        AppLanguage.ENGLISH -> localizeSessionErrorInEnglish(detail)
        AppLanguage.ITALIAN -> localizeSessionErrorInItalian(detail)
    }
}

private fun localizeSessionErrorInEnglish(detail: String): String {
    UNSUPPORTED_ARCHIVE_VERSION_IT.matchEntire(detail)?.let { match ->
        return "Unsupported archive version: ${match.groupValues[1]}"
    }
    return when (detail) {
        "La sessione salvata non contiene un combattimento" ->
            "The saved session contains no combat."
        "Il nome del file della sessione è cambiato durante il salvataggio" ->
            "The session filename changed while it was being saved."
        else -> detail
    }
}

private fun localizeSessionErrorInItalian(detail: String): String {
    INVALID_COMBAT_SESSION_JSON_EN.matchEntire(detail)?.let { match ->
        val path = match.groupValues[1]
        val reason = localizedCombatJsonReason(match.groupValues[2])
        return "JSON della sessione di combattimento non valido in $path: $reason"
    }
    JSON_PARSE_LOCATION_EN.matchEntire(detail)?.let { match ->
        val line = match.groupValues[2]
        val column = match.groupValues[3]
        val offset = match.groupValues[4]
        return "JSON non valido alla riga $line, colonna $column (offset $offset)."
    }
    PATH_ERROR_EN.matchEntire(detail)?.let { match ->
        val subject = when (match.groupValues[1]) {
            "JSON data path" -> "Il percorso dei dati JSON"
            "Backup path" -> "Il percorso dei backup"
            "Source" -> "Il percorso sorgente"
            else -> "Il percorso di destinazione"
        }
        val problem = when (match.groupValues[2]) {
            "is not a regular file" -> "non è un file regolare"
            "is not a directory" -> "non è una cartella"
            else -> "non ha una cartella superiore"
        }
        return "$subject $problem: ${match.groupValues[3]}"
    }
    return when (detail) {
        "Session counters cannot be negative" ->
            "I contatori della sessione non possono essere negativi."
        else -> detail
    }
}

private fun localizedCombatJsonReason(reason: String): String {
    UNSUPPORTED_COMBAT_SCHEMA_EN.matchEntire(reason)?.let { match ->
        return "versione dello schema non supportata ${match.groupValues[1]}"
    }
    ENUM_VALUE_EN.matchEntire(reason)?.let { match ->
        val kind = if (match.groupValues[1] == "unknown") "sconosciuto" else "duplicato"
        return "valore enum $kind '${match.groupValues[2]}'"
    }
    return when (reason) {
        "contains duplicate values" -> "contiene valori duplicati"
        "expected a string" -> "era attesa una stringa"
        "missing required value" -> "manca un valore obbligatorio"
        "expected an object" -> "era atteso un oggetto"
        "expected an array" -> "era atteso un array"
        "expected a boolean" -> "era atteso un valore booleano"
        "expected a number" -> "era atteso un numero"
        "expected a finite number" -> "era atteso un numero finito"
        "integer is outside the 32-bit range" -> "l'intero è fuori dall'intervallo a 32 bit"
        "expected an integer" -> "era atteso un numero intero"
        "expected an integer in the 64-bit range" ->
            "era atteso un numero intero nell'intervallo a 64 bit"
        "object keys must be strings" -> "le chiavi dell'oggetto devono essere stringhe"
        else -> reason
    }
}

private val UNSUPPORTED_ARCHIVE_VERSION_IT =
    Regex("""Versione dell'archivio non supportata: (-?\d+)""")
private val INVALID_COMBAT_SESSION_JSON_EN =
    Regex("""Invalid combat session JSON at (.+?): (.+)""")
private val JSON_PARSE_LOCATION_EN =
    Regex("""(.+) at line (\d+), column (\d+) \(offset (\d+)\)""")
private val PATH_ERROR_EN = Regex(
    """(JSON data path|Backup path|Source|Destination) """ +
        """(is not a regular file|is not a directory|has no parent directory): (.+)""",
)
private val UNSUPPORTED_COMBAT_SCHEMA_EN =
    Regex("""unsupported schema version (-?\d+)""")
private val ENUM_VALUE_EN = Regex("""(unknown|duplicate) enum value '(.+)'""")
