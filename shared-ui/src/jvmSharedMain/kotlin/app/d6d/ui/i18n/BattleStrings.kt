package app.d6d.ui.i18n

/**
 * Il tavolo: mappa tattica, barra dei comandi, colonne dei combattenti, finestre
 * degli strumenti, e i rifiuti che il view model oppone a un comando impossibile.
 *
 * E' il fascicolo piu' grande dell'applicazione perche' e' la schermata dove si
 * passa il tempo. Segue l'ordine in cui le cose stanno sullo schermo — fascia dei
 * turni, mappa, comandi, colonne — cosi' cercare una frase somiglia a guardarla.
 */
interface BattleStrings {

    // --- Rifiuti e avvisi del view model -------------------------------------

    val tieHandlingLocked: String
    val combatantNotInTurn: String
    val aimingAreaHint: String
    val enableEditToAddCombatants: String
    val noValidTarget: String
    /** Testo che sale sopra il bersaglio quando supera il tiro salvezza. */
    val floatSaved: String

    /** Idem, quando il tipo di danno non lo scalfisce. */
    val floatImmune: String

    val attackResourceNotSaved: String
    val noActingCombatant: String
    val inspectedSheetReadOnly: String
    val abilityNotFound: String
    val druidSheetUnavailable: String
    val wildShapeNeedsTwoLevels: String
    val wildShapeNotSaved: String
    val resourceNotSaved: String
    val turnChangedReselect: String
    val invalidTarget: String
    val healingCannotRaiseDead: String
    val healingOwnSideOnly: String
    val healingSelfOnly: String
    val healingAllyOnly: String
    val cannotTargetSelf: String
    val targetAlreadyDown: String
    val healingNotSaved: String
    val noCreatureInArea: String
    val markSavesThenApply: String
    val areaResourceNotSaved: String
    val waitForCpuTurn: String
    val combatantIsCpuControlled: String
    val cpuControlsThisTurn: String
    val noActiveActor: String
    val configureGridFirst: String
    val allTokensAlreadyPlaced: String
    val editAfterCpuBatchNotUndoable: String
    val editAfterCpuBatchUndone: String
    val cpuBatchNotFullyUndoable: String
    val nothingToUndo: String
    val cpuBatchUndone: String
    val correctionNotSaved: String
    val cpuTurnDoneResourcesNotSaved: String
    val resourceRestoreFailed: String
    val unknownError: String

    fun cpuBatchUndoneResourcesOff(reason: String): String
    fun wildShapeAssumed(druid: String, beast: String): String
    fun extraActionGranted(ability: String): String
    fun healingApplied(ability: String, target: String, amount: Int): String
    fun aimAbility(ability: String): String
    fun abilityNeedsManualResolution(ability: String): String
    fun spellSlotSuffix(level: Int): String

    // --- Numeri che volano sopra i segnaposti --------------------------------

    val concentrationLost: String
    val missed: String
    val dead: String
    val stable: String
    fun deathSaves(successes: Int, failures: Int): String
    fun exhaustionLevel(level: Int): String
    fun temporaryHitPointsGained(amount: Int): String

    /**
     * L'esito di un attacco, in una frase sola.
     *
     * Costruita qui e non a pezzi altrove: l'italiano e l'inglese non mettono
     * nello stesso punto ne' il soggetto ne' il tiro fra parentesi, e incollare
     * frammenti tradotti uno per uno produce frasi che nessuna delle due lingua
     * scriverebbe mai. [armorClass] manca quando la CA del bersaglio non e' nota.
     */
    fun attackHitSentence(
        ability: String,
        critical: Boolean,
        target: String,
        damage: Int,
        hitPointsLeft: Int,
        roll: Int,
        armorClass: Int?,
    ): String

    fun attackMissSentence(
        ability: String,
        target: String,
        roll: Int,
        armorClass: Int?,
    ): String

    // --- Fascia dell'ordine dei turni ----------------------------------------

    val turnOrderTitle: String
    val currentTurn: String
    val selectedTarget: String
    val inspectedSheet: String
    val simultaneousTurn: String
    val allAtZeroTurnSkipped: String
    val nextTurn: String
    val turnSkipped: String
    val makeCurrent: String
    val isCurrent: String
    val togetherShort: String
    val targetedShort: String
    val zeroHitPointsTurnSkipped: String
    val showTurnOrderWithoutInitiative: String
    val showInitiativeValues: String
    val hideTurnOrder: String
    fun turnOf(names: String): String
    fun chooseAsTarget(name: String): String
    fun makeTurnCurrent(name: String): String
    fun showAbilitiesOf(name: String): String
    fun select(name: String): String
    fun initiativeIs(value: String): String

    // --- Intestazione e registro ---------------------------------------------

    val eventLogTitle: String
    /** Etichetta della riga piu' recente del registro. */
    val logNow: String
    val eventLogHeading: String
    fun roundNumber(round: Int): String
    fun eventCount(count: Int): String
    val dragDownToCollapse: String
    val openArrow: String
    val collapseArrow: String
    val unnamedEncounter: String
    val unsavedChanges: String
    val sessionSaved: String
    val editingActive: String
    val tiesTogether: String
    val tiesSeparate: String
    val tiesJoined: String
    val tiesSplit: String
    fun standingCount(standing: Int, total: Int): String
    fun resolvedImmediately(text: String): String
    fun warning(text: String): String

    // --- Targhe sulla mappa ---------------------------------------------------

    val plateInspectedTarget: String
    val plateInspectedReadOnly: String
    val plateSelectedTarget: String
    val plateSharedTurn: String
    val plateActiveTurn: String
    val deadSentence: String
    val defeatedSentence: String
    val bloodiedBadge: String
    val distanceUndetermined: String
    fun mapScaleDescription(square: String): String
    fun distanceToTargetSentence(distance: String): String
    val distanceToTargetUnknownSentence: String
    fun oneSquareEquals(square: String): String
    fun distanceToTarget(distance: String): String
    fun hitPointsSentence(current: Int, max: Int): String
    val selectedTargetSentence: String
    val activeTurnSentence: String
    val inspectedReadOnlySentence: String
    fun roleAndName(role: String, name: String): String
    fun hitPointsShort(current: Int, max: Int): String
    fun armorClassShort(value: Int): String

    // --- Barra dei comandi ----------------------------------------------------

    val selfOrAllyShort: String
    val healingSelfShort: String
    val healingAllyShort: String
    val abilityStatToHit: String
    val abilityStatAbility: String
    val abilityStatSave: String
    val abilityStatArea: String
    val abilityStatRange: String
    val abilityStatDamage: String
    val abilityStatHealing: String
    val abilityStatUses: String
    val alwaysAvailableNoCost: String
    val manualResolutionTapForRules: String
    val aimingClickAgainToCancel: String
    val commandsExpand: String
    val commandsCollapse: String
    val whoActs: String
    val cancelAim: String
    val noAbilityAvailable: String
    val cancelMove: String
    val movementLeft: String
    val notAvailable: String
    val resumeCpu: String
    val skipTurn: String
    val endTurn: String
    val readOnlyZeroHitPoints: String
    fun turnOfShort(name: String): String
    fun inspectingShort(name: String): String
    fun targetShort(name: String): String
    fun readOnlyNotTurnOf(name: String): String
    fun chooseTargetOf(ability: String): String
    val chooseTargetHint: String

    // --- Colonne dei combattenti ---------------------------------------------

    val inTurnBadge: String
    val targetBadge: String
    val inspectedBadge: String
    val openSheetBadge: String
    val spellSlotsCapitalized: String
    val spellSlots: String
    val pactSlotsCapitalized: String
    val pactSlots: String
    val maxHitPointsAbbrev: String
    val currentHitPointsAbbrev: String
    val initiativeAbbrev: String
    fun combatantNamed(name: String): String
    fun openFullSheetOf(name: String): String
    fun slotsRemaining(kind: String, level: Int, remaining: Int, total: Int): String

    // --- Barre di stato -------------------------------------------------------

    val hitPoints: String
    val actionAvailable: String
    val actionSpent: String
    val bonusActionAvailable: String
    val bonusActionSpent: String
    val reactionAvailable: String
    val reactionSpent: String
    val bonusActionLabel: String
    fun hitPointsOf(current: Int, max: Int): String
    fun plusTemporary(amount: Int): String

    // --- Mappa -----------------------------------------------------------------

    val chooseCreatureOrCancelAim: String
    val inspectingAnotherCombatant: String
    val noMapConfigured: String
    val noMapBody: String
    val grid20x15: String
    val grid40x30: String
    val dragToMovePanel: String
    val savePassedHalf: String
    val saveFailedFull: String
    val turnActiveSuffix: String
    val targetSelectedSuffix: String
    val inspectedSuffix: String
    val outOfCombatSuffix: String
    fun hitPointsLongSentence(current: Int, max: Int): String
    fun aimingAt(name: String): String
    fun areaAndRange(area: String, range: String): String
    val clickMapToCentre: String
    fun savingThrowsHeader(saveDc: Int): String
    fun countInArea(count: Int): String

    // --- Comandi della mappa ---------------------------------------------------

    val mapCaps: String
    val squad: String
    val enemies: String
    val tools: String
    val mapLabel: String
    val logLabel: String
    val resolvedByTable: String
    val gridBrightness: String
    val gridVisible: String
    val gridHidden: String
    val hideOptions: String
    val mapOptions: String
    val dragImageHint: String
    val fewerColumns: String
    val moreColumns: String
    val fewerRows: String
    val moreRows: String
    val chooseBackground: String
    val fromMapArchive: String
    val mapEditing: String
    val removeBackground: String
    val fitAndCentre: String
    val placeAll: String
    fun gridSummary(columns: Int, rows: Int, square: String): String
    fun zoomLevel(value: Int): String
    /** «1,5 m/casella»: la misura di una casella, offerta come scelta. */
    fun perSquare(distance: String): String

    // --- Finestra degli strumenti ----------------------------------------------

    val toolsSuspendedDuringCpu: String
    val tableToolsTitle: String
    val tableToolsSubtitle: String
    val affectedCombatant: String
    val unconscious: String
    val abilityCheck: String
    val abilityCheckHint: String
    val rollCheck: String
    val amount: String
    val applyDamage: String
    val temporaryHitPoints: String
    val damageTypeLabel: String
    val roundsManualHint: String
    val addCondition: String
    val clickConditionToRemove: String
    val deathAndExhaustion: String
    val deathSaveRoll: String
    fun rollModeFromBar(mode: String): String
    fun temporaryHitPointsOf(amount: Int): String
    fun removeCondition(condition: String): String

    // --- Finestra del Grimorio --------------------------------------------------

    fun addTo(destination: String): String
    val pickFromGrimoireOrCreate: String
    val addCharacter: String
    val addCreature: String
    val grimoireEmpty: String
    fun charactersCount(count: Int): String
    fun creaturesCount(count: Int): String
    fun sheetUnavailable(name: String): String

    // --- Finestra degli oggetti --------------------------------------------------

    fun inventoryOf(character: String): String
    val selectCharacterForItems: String
    val noItemsYet: String
    val hoverItemHint: String
    val noDescriptionListed: String
    val noEffectListed: String

    // --- Turno della CPU ----------------------------------------------------------

    val cpuCouldNotFinishTurn: String
    val cpuSuspendedTableChangedBeforeEdit: String
    val cpuSuspendedTableChangedDuringPlayback: String
    val cpuSuspendedTableChangedDuringCommit: String
    val cpuSafetyLimitReached: String
    val cpuDifficultyEasy: String
    val cpuDifficultyMedium: String
    val cpuDifficultySorryForYou: String

    /** Riepilogo del turno nemico: «CPU Medio · bersaglio: Grix · 2 attacchi». */
    fun cpuSummary(
        difficulty: String,
        focus: String?,
        attacks: Int,
        heals: Int,
        moves: Int,
        encounterResolved: Boolean,
    ): String

    fun cpuMoves(actor: String): String
    fun cpuFlanks(actor: String): String
    fun cpuUsesAbility(actor: String): String
    fun cpuResourcesNotRealigned(detail: String): String
    fun cpuTurnUndone(reason: String, suffix: String): String
    fun cpuSuspendedKeepingChanges(reason: String): String
    fun cpuResourcesOutOfSync(detail: String): String
    fun cpuMisses(actor: String, target: String): String
    fun cpuHits(actor: String, target: String): String
    fun cpuHitsForDamage(actor: String, target: String, amount: Int): String
    fun cpuConcentratesOn(actor: String, target: String, amount: Int): String
    fun cpuHitsSeveral(actor: String, count: Int): String

    /** [raising] distingue il rialzare un caduto dal curare chi e' ancora in piedi. */
    fun cpuHeals(actor: String, target: String, amount: Int, raising: Boolean): String
}

/**
 * L'inventario da combattimento.
 *
 * Le voci d'esempio sono contenuto scritto da noi — nessuna proviene da un
 * manuale commerciale — quindi si traducono come il resto dell'interfaccia.
 * Etichette dell'inventario reale conservato nelle schede dei personaggi.
 */
interface ItemStrings {
    val title: String
    val inventoryCaps: String
    val descriptionCaps: String
    val effectsCaps: String

    val categoryPotions: String
    val categoryWeapons: String
    val categoryArmor: String
    val categoryScrolls: String
    val categoryMisc: String
}

internal object ItemStringsIt : ItemStrings {
    override val title = "Oggetti"
    override val inventoryCaps = "Inventario"
    override val descriptionCaps = "Descrizione"
    override val effectsCaps = "Effetti"

    override val categoryPotions = "Pozioni"
    override val categoryWeapons = "Armi"
    override val categoryArmor = "Armature"
    override val categoryScrolls = "Pergamene"
    override val categoryMisc = "Varie"
}

internal object ItemStringsEn : ItemStrings {
    override val title = "Items"
    override val inventoryCaps = "Inventory"
    override val descriptionCaps = "Description"
    override val effectsCaps = "Effects"

    override val categoryPotions = "Potions"
    override val categoryWeapons = "Weapons"
    override val categoryArmor = "Armor"
    override val categoryScrolls = "Scrolls"
    override val categoryMisc = "Misc"
}
