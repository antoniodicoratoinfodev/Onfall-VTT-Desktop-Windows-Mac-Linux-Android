package app.d6d.ui.i18n

/** La procedura di nuova partita, in inglese. */
internal object EncounterStringsEn : EncounterStrings {

    override fun step(current: Int, total: Int, description: String) =
        "$current of $total · $description"
    override val stepSource = "Start from a bundled game, from your templates, or from scratch."
    override val stepParticipants = "Pick characters, mobs, how many, and which side."
    override val stepGrid = "Set the size and the scale of the grid."
    override val stepMode = "Choose the experience you want to begin with."
    override val stepDifficulty = "Choose the opposition: no CPU, or how ruthless it will be."

    override val whereToStart = "Where do you want to start?"
    override val whereToStartBody = "Characters can join more than one session: each game gets " +
        "its own copy of HP, conditions, turns and position. Starting from scratch does not " +
        "delete your templates."
    override val includedGames = "Bundled games"
    override val orElse = "Or else"
    override val defaultFeminine = "Default"
    override val defaultMasculine = "Default"
    override val useExistingTemplates = "Use templates you already made"
    override val createFromScratch = "Create characters and mobs from scratch"
    override val createFromScratchHint = "Opens the Compendium to create new sheets"
    override val openSavedSession = "Open a saved session"
    override val noSavedSession = "No saved session"
    override val oneSavedSession = "1 saved session"
    override val sourceIncludedGame = "Bundled game"
    override val sourceExistingTemplates = "Existing templates"
    override val sourceFromScratch = "From scratch"
    override fun savedSessions(count: Int) = "$count saved sessions"
    override fun templateSummary(level: Int, party: Int, opponents: Int) =
        "Level $level · $party characters · $opponents opponents"
    override fun templateOpponentLine(name: String, summary: String) = "“$name” — $summary"
    override fun peopleAndCreatures(people: Int, creatures: Int) =
        "$people characters · $creatures mobs"

    override val gameName = "Game name"
    override val alliesLabel = "Allies"
    override val opponentsLabel = "Opponents"
    override val nameTheEncounter = "Give the encounter a name."
    override val openCompendium = "Open the Compendium"
    override val emptyCompendiumTitle = "The Compendium is empty."
    override val emptyCompendiumBody = "Create the cast of your game."
    override val emptyCompendiumHint =
        "Create and save characters and mobs; back here you will see only the new templates."
    override val emptyCompendiumRequirement = "Create and save at least one sheet or stat block."
    override val baseParty = "Base party"
    override val createMoreFromScratch = "Create more from scratch"
    override fun participants(count: Int) = "$count taking part"
    override fun allies(count: Int) = "$count allies"
    override fun opponents(count: Int) = "$count opponents"
    override fun charactersCount(count: Int) = "Characters ($count)"
    override fun creaturesCount(count: Int) = "Mobs and creatures ($count)"
    override fun quantity(value: Int) = "Quantity $value"

    override val gridSize = "Grid size"
    override val squareScaleLabel = "How much ground each square covers"
    override val fewerColumns = "− columns"
    override val moreColumns = "+ columns"
    override val fewerRows = "− rows"
    override val moreRows = "+ rows"
    override val resetGrid = "Reset to 20 × 15"
    override fun gridDimensions(columns: Int, rows: Int) = "$columns × $rows"
    override fun columnsCount(count: Int) = "$count columns"
    override fun rowsCount(count: Int) = "$count rows"
    override fun scalePerSquare(distance: String) = "$distance / square"
    override fun decreaseScale(step: String) = "− $step"
    override fun increaseScale(step: String) = "+ $step"
    override fun chosenScale(distance: String) = "Chosen scale: $distance"
    override fun totalArea(width: String, height: String, columns: Int, rows: Int) =
        "Total area: $width × $height · $columns × $rows squares."

    override val howToStart = "How do you want to begin?"
    override val modeFight = "Fight mode"
    override val modeFightHint = "Opens the tactical map and places allies and enemies close " +
        "together, ready to fight."
    override val modeFull = "Roleplay & Fight & Exploration"
    override val modeFullHint = "Opens the normal screen with the grid ready, leaving token " +
        "placement to you."
    override val fightPlacementNote = "Tokens will be placed in the middle in two facing lines. " +
        "You can drag them around in Edit."
    override val fullPlacementNote = "The grid will be ready but empty: you are free to set up " +
        "exploration and narrative scenes."

    override val howDangerous = "How dangerous should the enemies be?"
    override val difficultyBody = "Medium is the normal level. Difficulty changes what the CPU " +
        "decides, not the creatures' statistics nor the rules of combat. On Sandbox the CPU " +
        "stays off and you command the opposition too."
    override val sandbox = "Sandbox"
    override val sandboxHint = "No CPU: you move the opposition and make it act, just like the " +
        "allies. Handy for refereeing by hand, trying out a scene, or setting up an encounter. " +
        "The rules stay the engine's: only who picks the enemy moves changes."
    override val easyHint = "Compared to Medium it keeps its choices simple: it attacks the " +
        "nearest target and heals only in emergencies, with the smallest slot that will do and " +
        "no focus fire or flanking. Far less efficient than “Sorry for you!”."
    override val mediumHint = "The normal level: it coordinates attacks and healing and looks " +
        "for good positions. Compared to Easy it plays as a team and upcasts a heal just enough " +
        "to get out of danger; compared to “Sorry for you!” it presses the priority target less " +
        "and accepts more cautious choices."
    override val sorryForYouHint = "Compared to normal it focuses fire on vulnerable targets, " +
        "flanks, avoids friendly fire and spends higher slots to get its side safe at once. " +
        "The most aggressive and coordinated CPU there is."
    override fun singleFactionNote(reason: String) =
        "$reason You can still start this one-sided session."

    override val nextGrid = "Next · Grid"
    override val nextMode = "Next · Mode"
    override val nextDifficulty = "Next · Difficulty"
    override val nextDifficultyHint = "Then choose whether, and how tactical, the enemy CPU is"
    override val startGame = "Start game"
    override fun startSummary(mode: String, opponent: String) = "$mode · $opponent"

    override val cpuIdleNoAllies =
        "CPU idle: add at least one ally for the opposition to face."
    override val cpuIdleNoOpponents = "CPU idle: you have not picked any opponent for it to run."
    override val nameTheGame = "Give the game a name."
    override val pickAtLeastOneParticipant = "Pick at least one participant."
    override val invalidConfiguration = "The encounter setup is not valid."
    override val cannotStart = "The encounter could not be started."
    override fun sheetNoLongerAvailable(name: String) = "The sheet “$name” is no longer available."
}
