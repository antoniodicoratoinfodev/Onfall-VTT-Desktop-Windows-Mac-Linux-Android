package app.d6d.ui.i18n

/**
 * La procedura che apre una partita.
 *
 * Cinque passaggi — da dove si parte, chi partecipa, la griglia, la modalita', la
 * CPU avversaria — piu' i testi che spiegano che cosa cambia ciascuna scelta. E'
 * il punto dove l'applicazione parla di piu', perche' e' l'unico in cui chi la usa
 * sta ancora decidendo invece di giocare.
 */
interface EncounterStrings {

    // --- passaggi -------------------------------------------------------------

    fun step(current: Int, total: Int, description: String): String
    val stepSource: String
    val stepRules: String
    val stepParticipants: String
    val stepGrid: String
    val stepMode: String
    val stepDifficulty: String

    // --- passaggio 1: da dove si parte -----------------------------------------

    val whereToStart: String
    val whereToStartBody: String
    val includedGames: String
    val orElse: String
    val defaultFeminine: String
    val defaultMasculine: String
    val useExistingTemplates: String
    val createFromScratch: String
    val createFromScratchHint: String
    val openSavedSession: String
    val noSavedSession: String
    val oneSavedSession: String
    val sourceIncludedGame: String
    val sourceExistingTemplates: String
    val sourceFromScratch: String
    fun savedSessions(count: Int): String
    fun templateSummary(level: Int, party: Int, opponents: Int): String
    fun templateOpponentLine(name: String, summary: String): String
    fun peopleAndCreatures(people: Int, creatures: Int): String

    // --- passaggio 2: partecipanti ----------------------------------------------

    val gameName: String
    val alliesLabel: String
    val opponentsLabel: String
    val nameTheEncounter: String
    val openCompendium: String
    val emptyCompendiumTitle: String
    val emptyCompendiumBody: String
    val emptyCompendiumHint: String
    val emptyCompendiumRequirement: String
    val baseParty: String
    val createMoreFromScratch: String
    fun participants(count: Int): String
    fun allies(count: Int): String
    fun opponents(count: Int): String
    fun charactersCount(count: Int): String
    fun creaturesCount(count: Int): String
    fun quantity(value: Int): String

    // --- passaggio 3: griglia ------------------------------------------------------

    val gridSize: String
    val squareScaleLabel: String
    val fewerColumns: String
    val moreColumns: String
    val fewerRows: String
    val moreRows: String
    val resetGrid: String
    fun gridDimensions(columns: Int, rows: Int): String
    fun columnsCount(count: Int): String
    fun rowsCount(count: Int): String
    fun scalePerSquare(distance: String): String
    fun decreaseScale(step: String): String
    fun increaseScale(step: String): String
    fun chosenScale(distance: String): String
    fun totalArea(width: String, height: String, columns: Int, rows: Int): String

    // --- passaggio 4: modalita' -------------------------------------------------------

    val howToStart: String
    val modeFight: String
    val modeFightHint: String
    val modeFull: String
    val modeFullHint: String
    val fightPlacementNote: String
    val fullPlacementNote: String

    // --- passaggio 5: la CPU avversaria -------------------------------------------------

    val howDangerous: String
    val difficultyBody: String
    val sandbox: String
    val sandboxHint: String
    val easyHint: String
    val mediumHint: String
    val sorryForYouHint: String
    fun singleFactionNote(reason: String): String

    // --- avanzamento e avvio --------------------------------------------------------------

    val nextGrid: String
    val nextMode: String
    val nextDifficulty: String
    val nextDifficultyHint: String
    val startGame: String
    fun startSummary(mode: String, opponent: String): String

    // --- rifiuti del view model -------------------------------------------------------------

    val cpuIdleNoAllies: String
    val cpuIdleNoOpponents: String
    val nameTheGame: String
    val pickAtLeastOneParticipant: String
    val invalidConfiguration: String
    val cannotStart: String
    fun sheetNoLongerAvailable(name: String): String
}
