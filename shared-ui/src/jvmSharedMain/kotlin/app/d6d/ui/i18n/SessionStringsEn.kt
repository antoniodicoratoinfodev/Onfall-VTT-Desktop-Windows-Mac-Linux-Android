package app.d6d.ui.i18n

/** Partite aperte e salvate, in inglese. */
internal object SessionStringsEn : SessionStrings {

    override val noOpenGame = "No game open"
    override val noOpenGameHint =
        "Pick a bundled game, start from your templates, or reopen a saved session."
    override val newGame = "New game"
    override val openSessions = "Open sessions"
    override val sessionLabel = "Session"
    override val sessionsLabel = "Sessions"
    override val draftLabel = "Draft"
    override val openSessionsHint = "Maps, turns, dice, log and Undo stay independent."
    override val goToMap = "Go to the map"
    override val saveOrManage = "Save / manage"
    override val closeTab = "Close tab"
    override val actionsOnActive = "Actions on the active session"
    override val pickAGame = "Pick a game. Each tab keeps its own map and its own fight."
    override val switchSession = "Switch session"
    override val unsavedDraft = "Unsaved draft"
    override val unsavedChanges = "Unsaved changes"
    override val draftToSave = "Draft to save"
    override val changesToSave = "Changes to save"
    override val unnamedGame = "Unnamed game"
    override val saveFirst = "Save first"
    override val closeWithoutSaving = "Close without saving"
    override fun openCount(count: Int, state: String) = "$count open · $state · Manage"
    override fun openSessionsCount(count: Int) = "Open sessions · $count"
    override fun savedAtRound(round: Int) = "Saved · round $round"
    override fun closingLosesDraft(name: String) =
        "“$name” has never been saved. Closing the tab loses the draft."
    override fun closingKeepsFile(name: String) = "“$name” will stay in the archive as it was " +
        "at the last save. Closing the tab does not delete the saved file."

    override val autosaveToCheck = "Autosave needs checking"
    override val toSave = "To save"
    override val saveExplainer = "A saved session keeps the fight, the map, the tokens, the " +
        "whole log and the state of the dice: reopen it and future rolls come out the same."
    override val saveExplainerTab = " It opens in its own tab without closing the others."
    override val unsavedBattleChanges = "There are battle changes that have not been saved yet."
    override val saveWithName = "Save as"
    override val noSavedSession = "No saved session."
    override val openInTab = "Open in a tab"
    override val replaceSessionTitle = "Replace the session?"
    override val replaceSessionBody =
        "A session with this name already exists. The previous save will be replaced."
    override val discardChangesTitle = "Discard the changes?"
    override val discardAndOpen = "Discard and open"
    override val deleteSessionTitle = "Delete the session?"
    override val openSavedSessionTitle = "Open a saved session"
    override val emptyArchive = "The archive is empty: no session has been saved yet."
    override val damagedFile = "Damaged file"
    override val preparedSessionsHint =
        "Switch to another map without closing or reloading the current game."
    override fun autosaveToCheckWithCount(count: Int) = "Autosave needs checking · $count open"
    override fun toSaveWithCount(count: Int) = "To save · $count open"
    override fun openTabs(count: Int) = "$count open"
    override fun preparedSessions(count: Int) = "Prepared sessions ($count)"
    override fun savedSessions(count: Int) = "Saved sessions ($count)"
    override fun discardChangesBody(name: String) =
        "Opening “$name” loses the unsaved changes to the current battle."
    override fun deleteSessionBody(name: String) =
        "“$name” will be deleted from this device for good."
    override fun round(value: Int) = "Round $value"
    override fun combatants(count: Int) = "$count combatants"

    override val alreadyOpenInAnotherTab = "This session is already open in another tab."
    override val alreadyOpenPickAnotherName =
        "This session is already open in another tab. Switch to it, or pick another name."
    override val nameTakenConfirmOverwrite =
        "A session with this name already exists. Pick another name, or confirm the overwrite."
    override val currentSessionHasUnsavedChanges = "The current session has unsaved changes."
    override val saveWithNameToEnableAutosave =
        "Save the session under a name first to turn on autosave."
    override val autosavePausedFileInAnotherTab =
        "Autosave paused: the file belongs to another open tab."
    override val nameChangedUseSave =
        "The session name has changed: use Save to choose the new file."
    override val sessionDeleted = "Session deleted."
    override val snapshotSavedTableMovedOn =
        "Snapshot saved; the table changed while writing and still needs saving."
    override val sessionSaved = "Session saved."
    override val savePostponedForCpuTurn =
        "Save postponed: the CPU turn has to be committed by the interface first."
    override val invalidSnapshotForThisSession = "That save snapshot does not fit this session."
    override val previousDraftRecovered = "The previous session's draft was recovered."
    override val checkSessionSave = "check the session save."
    override fun closeTabUsingFile(name: String) = "Close the tab using “$name” first."
    override fun diskError(detail: String) = "Disk error: $detail"
    override fun invalidSession(detail: String) = "Invalid session: $detail"
    override fun sessionLoaded(name: String) = "Session “$name” loaded."
    override fun savedButListNotRefreshed(detail: String) =
        "Session saved, but the list could not be refreshed: $detail"
    override fun gameOpenedInNewTab(name: String) = "Game “$name” opened in a new tab."
    override fun alreadyOpenTabActivated(name: String) =
        "“$name” was already open: switched to its tab."
    override fun sessionOpenedInNewTab(name: String) = "Session “$name” opened in a new tab."
    override fun hasUnsavedChanges(name: String) = "“$name” has unsaved changes."
    override fun tabClosedNoneLeft(name: String) = "Tab “$name” closed: no game open."
    override fun tabClosed(name: String) = "Tab “$name” closed."
    override fun recoveredSessions(count: Int) =
        "Recovered $count sessions from the previous shutdown."
    override fun autosaveFailures(count: Int, detail: String) = "$count autosaves failed: $detail"

    override val currentBattleNotSaved = "The current battle is not saved"
    override val currentBattleNotSavedBody = "Starting the new encounter loses the current " +
        "state. You can go back to the battle and save it under a name."
    override val keepPreparing = "Keep preparing"
    override val goBackAndSave = "Go back and save"
    override val discardAndStart = "Discard and start"
}
