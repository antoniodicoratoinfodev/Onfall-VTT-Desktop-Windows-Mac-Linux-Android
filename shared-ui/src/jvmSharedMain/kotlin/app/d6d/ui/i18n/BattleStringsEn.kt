package app.d6d.ui.i18n

/**
 * Il tavolo, in inglese.
 *
 * I termini di gioco seguono il System Reference Document in lingua originale —
 * *saving throw*, *hit points*, *ability check* — perche' chi gioca in inglese
 * confronta quello che legge qui con il manuale che ha aperto accanto.
 */
internal object BattleStringsEn : BattleStrings {

    // --- Rifiuti e avvisi del view model -------------------------------------

    override val tieHandlingLocked = "Tie handling cannot be changed during combat."
    override val combatantNotInTurn = "The chosen combatant is not part of the current turn."
    override val aimingAreaHint = "You are aiming an area: pick a point on the map, or cancel."
    override val enableEditToAddCombatants = "Turn on Edit to add combatants to the session."
    override val noValidTarget = "No valid target."
    override val floatSaved = "Saved"
    override val floatImmune = "0/Immune"

    override val attackResourceNotSaved =
        "The resource this attack spent was not saved to the sheet."
    override val noActingCombatant = "No combatant is acting."
    override val inspectedSheetNotTurn =
        "The abilities on the sheet you are inspecting cannot be used because it is not its turn."
    override val abilityNotFound = "Ability not found."
    override val druidSheetUnavailable = "The druid's original sheet is unavailable."
    override val wildShapeNeedsTwoLevels = "Wild Shape requires at least two Druid levels."
    override val wildShapeNotSaved = "The Wild Shape use was not saved to the sheet."
    override val resourceNotSaved = "The resource spent was not saved to the sheet."
    override val turnChangedReselect = "The turn has changed: pick the ability again."
    override val invalidTarget = "Invalid target."
    override val healingCannotRaiseDead =
        "Ordinary healing cannot bring a dead target back to life."
    override val healingOwnSideOnly = "This healing can only target your own side."
    override val healingSelfOnly = "This healing can only be used on yourself."
    override val healingAllyOnly = "This healing needs an ally other than the healer."
    override val cannotTargetSelf = "A combatant cannot target itself."
    override val targetAlreadyDown = "The target is already down."
    override val healingNotSaved = "The healing use was not saved to the sheet."
    override val noCreatureInArea = "No creature in the area."
    override val markSavesThenApply = "Mark who makes the saving throw, then apply."
    override val areaResourceNotSaved =
        "The resource this area effect spent was not saved to the sheet."
    override val waitForCpuTurn = "Wait for the CPU to finish the enemy part of this turn."
    override val combatantIsCpuControlled = "This combatant is controlled by the enemy CPU."
    override val cpuControlsThisTurn = "The CPU is running this turn."
    override val noActiveActor = "No active combatant."
    override val configureGridFirst = "Set up the grid first."
    override val allTokensAlreadyPlaced = "Every token is already on the map."
    override val editAfterCpuBatchNotUndoable =
        "The edit made after the CPU batch cannot be undone."
    override val editAfterCpuBatchUndone =
        "The edit after the CPU batch was undone; the batch stays in the history."
    override val cpuBatchNotFullyUndoable = "The CPU batch cannot be undone completely."
    override val nothingToUndo = "Nothing to undo."
    override val cpuBatchUndone =
        "The whole CPU batch was undone: automation stays paused until you resume it."
    override val correctionNotSaved = "The correction was not saved to the sheet."
    override val cpuTurnDoneResourcesNotSaved =
        "The CPU turn is over, but its resources were not saved to the sheet."
    override val resourceRestoreFailed = "resource restore failed"
    override val unknownError = "unknown error"

    override fun cpuBatchUndoneResourcesOff(reason: String) =
        "CPU batch undone, but the sheet's resources were not realigned: $reason"

    override fun wildShapeAssumed(druid: String, beast: String) =
        "Wild Shape: $druid takes the form of $beast."

    override fun extraActionGranted(ability: String) =
        "“$ability” activated: you have an extra action, which cannot be used for Magic."

    override fun healingApplied(ability: String, target: String, amount: Int) =
        "“$ability”: $target regains $amount hit points."

    override fun aimAbility(ability: String) =
        "Aiming “$ability”: click the map to centre the area. Esc to cancel."

    override fun abilityNeedsManualResolution(ability: String) =
        "“$ability” has to be resolved by hand at the table."

    override fun spellSlotSuffix(level: Int) = " · level $level slot"

    // --- Numeri che volano sopra i segnaposti --------------------------------

    override val concentrationLost = "Concentration lost"
    override val missed = "Missed"
    override val dead = "Dead"
    override val stable = "Stable"

    override fun deathSaves(successes: Int, failures: Int) = "Death $successes/$failures"

    override fun exhaustionLevel(level: Int) = "Exhaustion $level"

    override fun temporaryHitPointsGained(amount: Int) = "+$amount THP"

    override fun attackHitSentence(
        ability: String,
        critical: Boolean,
        target: String,
        damage: Int,
        hitPointsLeft: Int,
        roll: Int,
        armorClass: Int?,
    ) = buildString {
        append('“').append(ability).append("”: ")
        if (critical) append("critical hit, ")
        append(target)
            .append(" takes ")
            .append(damage)
            .append(" damage; ")
            .append(hitPointsLeft)
            .append(" HP left")
        if (armorClass != null) append(" (").append(roll).append(" vs AC ").append(armorClass).append(')')
        append('.')
    }

    override fun attackMissSentence(
        ability: String,
        target: String,
        roll: Int,
        armorClass: Int?,
    ) = buildString {
        append('“').append(ability).append("”: ").append(target).append(" missed")
        if (armorClass != null) append(" (").append(roll).append(" vs AC ").append(armorClass).append(')')
        append("; 0 damage.")
    }

    // --- Fascia dell'ordine dei turni ----------------------------------------

    override val turnOrderTitle = "TURN ORDER"
    override val currentTurn = "Current turn"
    override val selectedTarget = "Selected target"
    override val inspectedSheet = "Sheet being inspected"
    override val simultaneousTurn = "Simultaneous turn"
    override val allAtZeroTurnSkipped = "All at zero hit points, turn skipped"
    override val nextTurn = "Next turn"
    override val turnSkipped = "turn skipped"
    override val makeCurrent = "make current"
    override val isCurrent = "current"
    override val togetherShort = "together"
    override val targetedShort = "targeted"
    override val zeroHitPointsTurnSkipped = "0 HP · turn skipped"
    override val showTurnOrderWithoutInitiative = "Show the turn order without initiative"
    override val showInitiativeValues = "Show initiative values too"
    override val hideTurnOrder = "Hide the turn order"

    override fun turnOf(names: String) = "$names's turn"
    override fun chooseAsTarget(name: String) = "Target $name"
    override fun makeTurnCurrent(name: String) = "Make $name's turn the current one"
    override fun showAbilitiesOf(name: String) = "Show $name's abilities and details"
    override fun select(name: String) = "Select $name"
    override fun initiativeIs(value: String) = "Initiative $value"

    // --- Intestazione e registro ---------------------------------------------

    override val eventLogTitle = "EVENT LOG"
    override val logNow = "NOW"
    override val eventLogHeading = "Event log"
    override fun roundNumber(round: Int) = "Round $round"
    override fun eventCount(count: Int) = if (count == 1) "1 event" else "$count events"
    override val dragDownToCollapse = "Drag down to collapse"
    override val openArrow = "Open ▸"
    override val collapseArrow = "Collapse ▾"
    override val unnamedEncounter = "Unnamed encounter"
    override val unsavedChanges = "Unsaved changes"
    override val sessionSaved = "Session saved"
    override val editingActive = "Editing on"
    override val tiesTogether = "Ties together"
    override val tiesSeparate = "Ties separate"
    override val tiesJoined = "Ties joined"
    override val tiesSplit = "Ties split"

    override fun standingCount(standing: Int, total: Int) = "$standing/$total standing"
    override fun resolvedImmediately(text: String) = "Resolved · $text"
    override fun warning(text: String) = "Warning · $text"

    // --- Targhe sulla mappa ---------------------------------------------------

    override val plateInspectedTarget = "Inspecting · target"
    override val plateInspecting = "Inspecting"
    override val plateSelectedTarget = "Selected target"
    override val plateSharedTurn = "Shared turn"
    override val plateActiveTurn = "Active turn"
    override val deadSentence = " Dead."
    override val defeatedSentence = " Defeated."
    override val bloodiedBadge = "BLOODIED"
    override val distanceUndetermined = "Distance unknown"

    override fun mapScaleDescription(square: String) = "Map scale: one square is $square."
    override fun distanceToTargetSentence(distance: String) = " Distance to target: $distance."
    override val distanceToTargetUnknownSentence = " Distance to target unknown."
    override fun oneSquareEquals(square: String) = "1 square = $square"
    override fun distanceToTarget(distance: String) = "Distance to target: $distance"
    override fun hitPointsSentence(current: Int, max: Int) = "$current hit points out of $max."
    override val selectedTargetSentence = " Selected target."
    override val activeTurnSentence = " Active turn."
    override val inspectedSentence = " Sheet being inspected."
    override fun roleAndName(role: String, name: String) = "$role: $name"
    override fun hitPointsShort(current: Int, max: Int) = "$current/$max HP"
    override fun armorClassShort(value: Int) = "AC $value"

    // --- Barra dei comandi ----------------------------------------------------

    override val selfOrAllyShort = "self/ally"
    override val healingSelfShort = "self"
    override val healingAllyShort = "ally"
    override val abilityStatToHit = "To hit"
    override val abilityStatAbility = "Abil."
    override val abilityStatSave = "Save"
    override val abilityStatArea = "Area"
    override val abilityStatRange = "Range"
    override val abilityStatDamage = "Damage"
    override val abilityStatHealing = "Healing"
    override val abilityStatUses = "Uses"
    override val alwaysAvailableNoCost = "Always available, and costs nothing on your turn."
    override val manualResolutionTapForRules = "Resolve by hand · tap for the rules"
    override val aimingClickAgainToCancel = "AIMING · CLICK AGAIN TO CANCEL"
    override val commandsExpand = "Commands ▸"
    override val commandsCollapse = "Commands ▾"
    override val whoActs = "Acting:"
    override val cancelAim = "Cancel aim"
    override val noAbilityAvailable = "No ability available for this combatant."
    override val cancelMove = "Cancel move"
    override val movementLeft = "Movement left"
    override val notAvailable = "Not available"
    override val resumeCpu = "Resume CPU"
    override val skipTurn = "Skip turn"
    override val endTurn = "End turn"
    override val inspectedZeroHitPointsTurnSkipped = "0 HP · its turn is skipped."

    override fun turnOfShort(name: String) = "Turn: $name"
    override fun inspectingShort(name: String) = "Inspecting: $name"
    override fun targetShort(name: String) = "Target: $name"
    override fun notTurnOf(name: String) = "It is not $name's turn."
    override fun chooseTargetOf(ability: String) = "Choose a target for “$ability” · "
    override val chooseTargetHint = "click the ability again, or cancel, to go back to inspecting."

    // --- Colonne dei combattenti ---------------------------------------------

    override val inTurnBadge = "ACTING"
    override val targetBadge = "TARGET"
    override val inspectedBadge = "INSPECTING"
    override val openSheetBadge = "SHEET ↗"
    override val spellSlotsCapitalized = "SPELL SLOTS"
    override val spellSlots = "Spell slots"
    override val pactSlotsCapitalized = "PACT SLOTS"
    override val pactSlots = "Pact slots"
    override val maxHitPointsAbbrev = "Max HP"
    override val currentHitPointsAbbrev = "Cur. HP"
    override val initiativeAbbrev = "Init."

    override fun combatantNamed(name: String) = "Combatant $name"
    override fun openFullSheetOf(name: String) = "Open $name's full sheet"
    override fun slotsRemaining(kind: String, level: Int, remaining: Int, total: Int) =
        "$kind level $level: $remaining of $total remaining"

    // --- Barre di stato -------------------------------------------------------

    override val hitPoints = "Hit points"
    override val actionAvailable = "Action available"
    override val actionSpent = "Action spent"
    override val bonusActionAvailable = "bonus action available"
    override val bonusActionSpent = "bonus action spent"
    override val reactionAvailable = "reaction available"
    override val reactionSpent = "reaction spent"
    override val bonusActionLabel = "Bonus Action"

    override fun hitPointsOf(current: Int, max: Int) = "$current out of $max"
    override fun plusTemporary(amount: Int) = ", plus $amount temporary"

    // --- Mappa -----------------------------------------------------------------

    override val chooseCreatureOrCancelAim = "Pick a creature as the target, or cancel the aim."
    override val inspectingAnotherCombatant =
        "You are inspecting another combatant: select the one whose turn it is to move it."
    override val noMapConfigured = "No map set up"
    override val noMapBody = "Without a grid the encounter stays an abstract simulation: " +
        "reach and distance are called by the table, not the engine."
    override val grid20x15 = "20 × 15 grid"
    override val grid40x30 = "40 × 30 grid"
    override val dragToMovePanel = "Drag to move the panel"
    override val savePassedHalf = "Save made · half"
    override val saveFailedFull = "Save failed · full"
    override val turnActiveSuffix = ", active turn"
    override val targetSelectedSuffix = ", selected target"
    override val inspectedSuffix = ", sheet being inspected"
    override val outOfCombatSuffix = ", out of the fight"

    override fun hitPointsLongSentence(current: Int, max: Int) = "$current out of $max hit points"
    override fun aimingAt(name: String) = "Aiming · $name"
    override fun areaAndRange(area: String, range: String) = "Area $area · range $range"
    override val clickMapToCentre = " · click the map to centre it"
    override fun savingThrowsHeader(saveDc: Int) =
        "Saving throws · DC $saveDc · tap a name to flip its outcome"
    override fun countInArea(count: Int) = "$count in the area"

    // --- Comandi della mappa ---------------------------------------------------

    override val mapCaps = "MAP"
    override val squad = "Party"
    override val enemies = "Enemies"
    override val tools = "Tools"
    override val mapLabel = "Map"
    override val logLabel = "Log"
    override val resolvedByTable = "Called by the table"
    override val gridBrightness = "Grid brightness"
    override val gridVisible = "Grid visible"
    override val gridHidden = "Grid hidden"
    override val hideOptions = "Hide options"
    override val mapOptions = "Map options"
    override val dragImageHint = "Drag the image to move it · corners: keep proportions · " +
        "edges: stretch freely."
    override val fewerColumns = "− columns"
    override val moreColumns = "+ columns"
    override val fewerRows = "− rows"
    override val moreRows = "+ rows"
    override val chooseBackground = "Choose background"
    override val fromMapArchive = "From the map archive"
    override val mapEditing = "Map editing"
    override val removeBackground = "Remove background"
    override val fitAndCentre = "Fit and centre"
    override val placeAll = "Place all"

    override fun gridSummary(columns: Int, rows: Int, square: String) =
        "$columns × $rows · $square/square"
    override fun zoomLevel(value: Int) = "Zoom $value"
    override fun perSquare(distance: String) = "$distance/square"

    // --- Finestra degli strumenti ----------------------------------------------

    override val toolsSuspendedDuringCpu =
        "Tools are paused while the CPU resolves the enemy part of the turn."
    override val tableToolsTitle = "Table tools"
    override val tableToolsSubtitle =
        "Checks, damage, healing and conditions for the chosen combatant."
    override val affectedCombatant = "Combatant affected"
    override val unconscious = "Unconscious"
    override val abilityCheck = "Ability check"
    override val abilityCheckHint = "Pick the ability and type the check modifier. Exhaustion " +
        "and armor penalties are applied by the engine."
    override val rollCheck = "Roll check"
    override val amount = "Amount"
    override val applyDamage = "Apply damage"
    override val temporaryHitPoints = "Temporary HP"
    override val damageTypeLabel = "Damage type"
    override val roundsManualHint = "Rounds (0 = manual)"
    override val addCondition = "Add condition"
    override val clickConditionToRemove = "Click a condition to remove it"
    override val deathAndExhaustion = "Death and exhaustion"
    override val deathSaveRoll = "Death saving throw"

    override fun rollModeFromBar(mode: String) = "Mode from the bar: $mode"
    override fun temporaryHitPointsOf(amount: Int) = "$amount temporary HP"
    override fun removeCondition(condition: String) = "Remove $condition"

    // --- Finestra del Grimorio --------------------------------------------------

    override fun addTo(destination: String) = "Add to $destination"
    override val pickFromGrimoireOrCreate =
        "Pick an entry from the Grimoire, or create a new one in the Compendium."
    override val addCharacter = "+ Character"
    override val addCreature = "+ Creature"
    override val grimoireEmpty = "The Grimoire is empty: create a character or a creature first."

    override fun charactersCount(count: Int) = "Characters ($count)"
    override fun creaturesCount(count: Int) = "Creatures ($count)"
    override fun sheetUnavailable(name: String) = "The sheet “$name” is unavailable."

    // --- Finestra degli oggetti --------------------------------------------------

    override fun inventoryOf(character: String) = "$character’s inventory."
    override val selectCharacterForItems = "Inspect a character to open their inventory."
    override val noItemsYet = "Nothing here yet."
    override val hoverItemHint =
        "Hover an item — or click it — to read its description and effects here."
    override val noDescriptionListed = "No description provided."
    override val noEffectListed = "No effect listed."

    // --- Turno della CPU ----------------------------------------------------------

    override val cpuCouldNotFinishTurn = "The CPU could not finish the turn."
    override val cpuSuspendedTableChangedBeforeEdit =
        "CPU turn paused: the table changed before Edit was entered."
    override val cpuSuspendedTableChangedDuringPlayback =
        "CPU turn paused: the table state changed during playback."
    override val cpuSuspendedTableChangedDuringCommit =
        "CPU turn paused: the table state changed while committing."
    override val cpuSafetyLimitReached =
        "The CPU hit its safety limit: the turn is left to the table."
    override val cpuDifficultyEasy = "Easy"
    override val cpuDifficultyMedium = "Medium"
    override val cpuDifficultySorryForYou = "Sorry for you!"

    override fun cpuSummary(
        difficulty: String,
        focus: String?,
        attacks: Int,
        heals: Int,
        moves: Int,
        encounterResolved: Boolean,
    ) = buildString {
        append("CPU ").append(difficulty)
        if (focus != null) append(" · target: ").append(focus)
        append(" · ").append(attacks).append(if (attacks == 1) " attack" else " attacks")
        if (heals > 0) append(" · ").append(heals).append(if (heals == 1) " heal" else " heals")
        if (moves > 0) append(" · ").append(moves).append(if (moves == 1) " move" else " moves")
        if (encounterResolved) append(" · encounter resolved")
    }

    override fun cpuMoves(actor: String) = "$actor moves"
    override fun cpuFlanks(actor: String) = "$actor flanks the target"
    override fun cpuUsesAbility(actor: String) = "$actor uses an ability"

    override fun cpuResourcesNotRealigned(detail: String) = " Resources not realigned: $detail"
    override fun cpuTurnUndone(reason: String, suffix: String) = "CPU turn undone: $reason$suffix"
    override fun cpuSuspendedKeepingChanges(reason: String) =
        "CPU turn paused without removing the external changes: $reason"
    override fun cpuResourcesOutOfSync(detail: String) = " CPU resources out of sync: $detail"

    override fun cpuMisses(actor: String, target: String) = "$actor misses $target"
    override fun cpuHits(actor: String, target: String) = "$actor hits $target"
    override fun cpuHitsForDamage(actor: String, target: String, amount: Int) =
        "$actor hits $target · -$amount"
    override fun cpuConcentratesOn(actor: String, target: String, amount: Int) =
        "$actor focuses on $target · -$amount"
    override fun cpuHitsSeveral(actor: String, count: Int) = "$actor hits $count targets"
    override fun cpuHeals(actor: String, target: String, amount: Int, raising: Boolean) =
        "$actor ${if (raising) "raises" else "heals"} $target · +$amount"
}
