package app.d6d.ui.i18n

/**
 * Il tavolo, in italiano.
 *
 * Sta in un file suo e non accanto all'interfaccia solo per la mole: [BattleStrings]
 * conta oltre duecento voci, e tenere qui una lingua e la' l'altra rende ciascuna
 * leggibile di seguito, come una pagina di traduzione.
 */
internal object BattleStringsIt : BattleStrings {

    // --- Rifiuti e avvisi del view model -------------------------------------

    override val tieHandlingLocked =
        "La gestione delle parità non si può cambiare durante il combattimento."
    override val combatantNotInTurn = "Il combattente scelto non appartiene al turno corrente."
    override val aimingAreaHint =
        "Stai mirando un'area: scegli un punto sulla mappa oppure annulla."
    override val enableEditToAddCombatants =
        "Attiva Modifica per aggiungere combattenti alla sessione."
    override val noValidTarget = "Nessun bersaglio valido."
    override val floatSaved = "Salvo"
    override val floatImmune = "0/Immune"

    override val attackResourceNotSaved =
        "Il consumo della risorsa dell'attacco non è stato salvato nella scheda."
    override val noActingCombatant = "Nessun attore di turno."
    override val inspectedSheetNotTurn =
        "Le capacità della scheda in esame non si possono usare perché non è il suo turno."
    override val abilityNotFound = "Capacità non trovata."
    override val druidSheetUnavailable = "La scheda originale del druido non è disponibile."
    override val wildShapeNeedsTwoLevels = "Forma Selvatica richiede almeno due livelli da Druido."
    override val wildShapeNotSaved =
        "Il consumo di Forma Selvatica non è stato salvato nella scheda."
    override val resourceNotSaved = "Il consumo della risorsa non è stato salvato nella scheda."
    override val turnChangedReselect = "Il turno è cambiato: seleziona di nuovo la capacità."
    override val invalidTarget = "Bersaglio non valido."
    override val healingCannotRaiseDead =
        "Una normale capacità di cura non può riportare in vita un bersaglio morto."
    override val healingOwnSideOnly = "Questa cura può bersagliare soltanto la propria squadra."
    override val healingSelfOnly = "Questa cura può essere usata soltanto su di sé."
    override val healingAllyOnly = "Questa cura richiede un alleato diverso da chi la usa."
    override val cannotTargetSelf = "L'attore non può essere il proprio bersaglio."
    override val targetAlreadyDown = "Il bersaglio è già sconfitto."
    override val healingNotSaved = "Il consumo della cura non è stato salvato nella scheda."
    override val noCreatureInArea = "Nessuna creatura nell'area."
    override val markSavesThenApply = "Segna chi supera il tiro salvezza, poi applica."
    override val areaResourceNotSaved =
        "Il consumo della risorsa dell'area non è stato salvato nella scheda."
    override val waitForCpuTurn = "Attendi che la CPU completi la parte nemica di questo turno."
    override fun creaturesNoticedParty(names: String) = "Vi hanno individuato: $names"
    override val dormantSuffix = ", non si è accorto del gruppo"
    override val combatantIsCpuControlled = "Questo combattente è controllato dalla CPU nemica."
    override val cpuControlsThisTurn = "La CPU sta controllando questo turno."
    override val noActiveActor = "Nessun attore attivo."
    override val configureGridFirst = "Configura prima la griglia."
    override val allTokensAlreadyPlaced = "Tutti i segnaposti sono già sulla mappa."
    override val editAfterCpuBatchNotUndoable =
        "La modifica successiva al batch CPU non può essere annullata."
    override val editAfterCpuBatchUndone =
        "Modifica successiva al batch CPU annullata; il batch resta nella cronologia."
    override val cpuBatchNotFullyUndoable = "Il batch CPU non può essere annullato completamente."
    override val nothingToUndo = "Niente da annullare."
    override val cpuBatchUndone =
        "Intero batch CPU annullato: l'automazione resta sospesa finché non scegli di riprenderla."
    override val correctionNotSaved = "La correzione non è stata salvata nella scheda."
    override val cpuTurnDoneResourcesNotSaved =
        "Il turno CPU è concluso, ma le risorse non sono state salvate nella scheda."
    override val resourceRestoreFailed = "ripristino risorse non riuscito"
    override val unknownError = "errore sconosciuto"

    override fun cpuBatchUndoneResourcesOff(reason: String) =
        "Batch CPU annullato, ma le risorse della scheda non sono state riallineate: $reason"

    override fun wildShapeAssumed(druid: String, beast: String) =
        "Forma Selvatica: $druid assume la forma di $beast."

    override fun extraActionGranted(ability: String) =
        "«$ability» attivata: hai un'azione aggiuntiva, non utilizzabile per Magia."

    override fun healingApplied(ability: String, target: String, amount: Int) =
        "«$ability»: $target recupera $amount punti ferita."

    override fun aimAbility(ability: String) =
        "Mira «$ability»: clicca sulla mappa per centrare l'area. Esc per annullare."

    override fun abilityNeedsManualResolution(ability: String) =
        "«$ability» richiede una risoluzione manuale al tavolo."

    override fun spellSlotSuffix(level: Int) = " · slot di $level° livello"

    // --- Numeri che volano sopra i segnaposti --------------------------------

    override val concentrationLost = "Concentrazione persa"
    override val missed = "Mancato"
    override val dead = "Morto"
    override val stable = "Stabile"

    override fun deathSaves(successes: Int, failures: Int) = "Morte $successes/$failures"

    override fun exhaustionLevel(level: Int) = "Sfinimento $level"

    override fun temporaryHitPointsGained(amount: Int) = "+$amount PFT"

    override fun attackHitSentence(
        ability: String,
        critical: Boolean,
        target: String,
        damage: Int,
        hitPointsLeft: Int,
        roll: Int,
        armorClass: Int?,
    ) = buildString {
        append('«').append(ability).append("»: ")
        if (critical) append("colpo critico, ")
        append(target)
            .append(" subisce ")
            .append(damage)
            .append(" danni; ")
            .append(hitPointsLeft)
            .append(" PF rimasti")
        if (armorClass != null) append(" (").append(roll).append(" contro CA ").append(armorClass).append(')')
        append('.')
    }

    override fun attackMissSentence(
        ability: String,
        target: String,
        roll: Int,
        armorClass: Int?,
    ) = buildString {
        append('«').append(ability).append("»: ").append(target).append(" mancato")
        if (armorClass != null) append(" (").append(roll).append(" contro CA ").append(armorClass).append(')')
        append("; 0 danni.")
    }

    // --- Fascia dell'ordine dei turni ----------------------------------------

    override val turnOrderTitle = "ORDINE DEI TURNI"
    override val currentTurn = "Turno corrente"
    override val selectedTarget = "Bersaglio selezionato"
    override val inspectedSheet = "Scheda in esame"
    override val simultaneousTurn = "Turno simultaneo"
    override val allAtZeroTurnSkipped = "Tutti a zero punti ferita, turno saltato"
    override val nextTurn = "Turno successivo"
    override val turnSkipped = "turno saltato"
    override val makeCurrent = "rendi corrente"
    override val isCurrent = "corrente"
    override val togetherShort = "insieme"
    override val targetedShort = "bersaglio"
    override val zeroHitPointsTurnSkipped = "0 PF · turno saltato"
    override val showTurnOrderWithoutInitiative = "Mostra l'ordine dei turni senza iniziativa"
    override val showInitiativeValues = "Mostra anche i valori di iniziativa"
    override val hideTurnOrder = "Nascondi l'ordine dei turni"

    override fun turnOf(names: String) = "Turno di $names"
    override fun chooseAsTarget(name: String) = "Scegli $name come bersaglio"
    override fun makeTurnCurrent(name: String) = "Rendi corrente il turno di $name"
    override fun showAbilitiesOf(name: String) = "Mostra capacità e informazioni di $name"
    override fun select(name: String) = "Seleziona $name"
    override fun initiativeIs(value: String) = "Iniziativa $value"

    // --- Intestazione e registro ---------------------------------------------

    override val eventLogTitle = "REGISTRO EVENTI"
    override val logNow = "ORA"
    override val eventLogHeading = "Registro eventi"
    override fun roundNumber(round: Int) = "Round $round"
    override fun eventCount(count: Int) = if (count == 1) "1 evento" else "$count eventi"
    override val dragDownToCollapse = "Trascina verso il basso per collassare"
    override val openArrow = "Apri ▸"
    override val collapseArrow = "Collassa ▾"
    override val unnamedEncounter = "Incontro senza nome"
    override val unsavedChanges = "Modifiche non salvate"
    override val sessionSaved = "Sessione salvata"
    override val editingActive = "Modifica attiva"
    override val tiesTogether = "Parità insieme"
    override val tiesSeparate = "Parità separate"
    override val tiesJoined = "Parità unite"
    override val tiesSplit = "Parità divise"

    override fun standingCount(standing: Int, total: Int) = "$standing/$total in piedi"
    override fun resolvedImmediately(text: String) = "Risolto subito · $text"
    override fun warning(text: String) = "Avviso · $text"

    // --- Targhe sulla mappa ---------------------------------------------------

    override val plateInspectedTarget = "In esame · bersaglio"
    override val plateInspecting = "In esame"
    override val plateSelectedTarget = "Bersaglio selezionato"
    override val plateSharedTurn = "Turno condiviso"
    override val plateActiveTurn = "Turno attivo"
    override val deadSentence = " Morto."
    override val defeatedSentence = " Sconfitto."
    override val bloodiedBadge = "INSANGUINATO"
    override val distanceUndetermined = "Distanza non determinata"

    override fun mapScaleDescription(square: String) =
        "Scala della mappa: una casella equivale a $square."
    override fun distanceToTargetSentence(distance: String) =
        " Distanza dal bersaglio: $distance."
    override val distanceToTargetUnknownSentence = " Distanza dal bersaglio non determinata."
    override fun oneSquareEquals(square: String) = "1 casella = $square"
    override fun distanceToTarget(distance: String) = "Distanza dal bersaglio: $distance"
    override fun hitPointsSentence(current: Int, max: Int) = "$current punti ferita su $max."
    override val selectedTargetSentence = " Bersaglio selezionato."
    override val activeTurnSentence = " Turno attivo."
    override val inspectedSentence = " Scheda in esame."
    override fun roleAndName(role: String, name: String) = "$role: $name"
    override fun hitPointsShort(current: Int, max: Int) = "$current/$max PF"
    override fun armorClassShort(value: Int) = "CA $value"

    // --- Barra dei comandi ----------------------------------------------------

    override val selfOrAllyShort = "sé/alleato"
    override val healingSelfShort = "sé"
    override val healingAllyShort = "alleato"
    override val abilityStatToHit = "Colpire"
    override val abilityStatAbility = "Car."
    override val abilityStatSave = "TS"
    override val abilityStatArea = "Area"
    override val abilityStatRange = "Gittata"
    override val abilityStatDamage = "Danno"
    override val abilityStatHealing = "Cura"
    override val abilityStatUses = "Usi"
    override val alwaysAvailableNoCost = "Vale sempre, senza spendere nulla nel turno."
    override val manualResolutionTapForRules = "Risoluzione manuale · tocca per le regole"
    override val aimingClickAgainToCancel = "IN MIRA · RICLICCA PER ANNULLARE"
    override val commandsExpand = "Comandi ▸"
    override val commandsCollapse = "Comandi ▾"
    override val whoActs = "Chi agisce:"
    override val cancelAim = "Annulla mira"
    override val noAbilityAvailable = "Nessuna capacità disponibile per questo combattente."
    override val cancelMove = "Annulla mossa"
    override val movementLeft = "Movimento residuo"
    override val notAvailable = "Non disponibile"
    override val resumeCpu = "Riprendi CPU"
    override val skipTurn = "Salta turno"
    override val endTurn = "Fine turno"
    override val inspectedZeroHitPointsTurnSkipped = "0 PF · il suo turno viene saltato."

    override fun turnOfShort(name: String) = "Turno: $name"
    override fun inspectingShort(name: String) = "In esame: $name"
    override fun targetShort(name: String) = "Bersaglio: $name"
    override fun notTurnOf(name: String) = "Non è il turno di $name."
    override fun chooseTargetOf(ability: String) = "Scegli il bersaglio di «$ability» · "
    override val chooseTargetHint = "riclicca l'abilità o annulla per tornare all'ispezione."

    // --- Colonne dei combattenti ---------------------------------------------

    override val inTurnBadge = "IN TURNO"
    override val targetBadge = "BERSAGLIO"
    override val inspectedBadge = "IN ESAME"
    override val openSheetBadge = "SCHEDA ↗"
    override val spellSlotsCapitalized = "SLOT INCANTESIMO"
    override val spellSlots = "Slot incantesimo"
    override val pactSlotsCapitalized = "SLOT DEL PATTO"
    override val pactSlots = "Slot del Patto"
    override val classResourcesCapitalized = "RISORSE DI CLASSE"
    override val maxHitPointsAbbrev = "PF max"
    override val currentHitPointsAbbrev = "PF att."
    override val initiativeAbbrev = "Iniz."

    override fun combatantNamed(name: String) = "Combattente $name"
    override fun openFullSheetOf(name: String) = "Apri la scheda completa di $name"
    override fun slotsRemaining(kind: String, level: Int, remaining: Int, total: Int) =
        "$kind livello $level: $remaining rimanenti su $total"
    override fun classResourceRemaining(name: String, remaining: Int, total: Int) =
        "$name: $remaining rimanenti su $total"
    override val editResourcesHint = "TOCCA UNA RISORSA PER CORREGGERLA"
    override val resourceEditorEyebrow = "CORREZIONE AL TAVOLO"
    override val availableQuantity = "Disponibili"
    override val maximumQuantity = "Massimo"
    override val resourceQuantityHelp =
        "Imposta quanti usi restano e la capienza totale. La modifica aggiorna anche la scheda."
    override val turnResourceQuantityHelp =
        "Le risorse del turno valgono 0 se spese e 1 se disponibili."
    override val exhaustResource = "Esaurisci"
    override val restoreResource = "Ripristina"
    override val resourceCorrectionNotSaved = "La correzione della risorsa non è stata salvata"
    override fun editResourceFor(resource: String, combatant: String) =
        "$resource · $combatant"
    override fun decreaseQuantity(label: String) = "Riduci $label"
    override fun increaseQuantity(label: String) = "Aumenta $label"

    // --- Barre di stato -------------------------------------------------------

    override val hitPoints = "Punti ferita"
    override val actionAvailable = "Azione disponibile"
    override val actionSpent = "Azione spesa"
    override val bonusActionAvailable = "azione bonus disponibile"
    override val bonusActionSpent = "azione bonus spesa"
    override val reactionAvailable = "reazione disponibile"
    override val reactionSpent = "reazione spesa"
    override val bonusActionLabel = "Azione Bonus"

    override fun hitPointsOf(current: Int, max: Int) = "$current su $max"
    override fun plusTemporary(amount: Int) = ", più $amount temporanei"

    // --- Mappa -----------------------------------------------------------------

    override val chooseCreatureOrCancelAim =
        "Scegli una creatura come bersaglio, oppure annulla la mira."
    override val inspectingAnotherCombatant =
        "Stai consultando un altro combattente: seleziona quello di turno per muoverlo."
    override val noMapConfigured = "Nessuna mappa configurata"
    override val noMapBody = "Senza griglia l'incontro resta una simulazione astratta: " +
        "portate e distanze le dichiara il tavolo, non il motore."
    override val grid20x15 = "Griglia 20 × 15"
    override val grid40x30 = "Griglia 40 × 30"
    override val dragToMovePanel = "Trascina per spostare il pannello"
    override val savePassedHalf = "TS superato · metà"
    override val saveFailedFull = "TS fallito · pieno"
    override val turnActiveSuffix = ", turno attivo"
    override val targetSelectedSuffix = ", bersaglio selezionato"
    override val inspectedSuffix = ", scheda in esame"
    override val outOfCombatSuffix = ", fuori combattimento"

    override fun hitPointsLongSentence(current: Int, max: Int) = "$current su $max punti ferita"
    override fun aimingAt(name: String) = "Mira · $name"
    override fun areaAndRange(area: String, range: String) = "Area $area · gittata $range"
    override val clickMapToCentre = " · clicca sulla mappa per centrare"
    override fun savingThrowsHeader(saveDc: Int) =
        "Tiri salvezza · CD $saveDc · tocca un nome per cambiarne l'esito"
    override fun countInArea(count: Int) = "$count nell'area"

    // --- Comandi della mappa ---------------------------------------------------

    override val mapCaps = "MAPPA"
    override val squad = "Squadra"
    override val enemies = "Nemici"
    override val tools = "Strumenti"
    override val mapLabel = "Mappa"
    override val logLabel = "Registro"
    override val resolvedByTable = "Concluso dal tavolo"
    override val gridBrightness = "Luminosità griglia"
    override val gridVisible = "Griglia visibile"
    override val gridHidden = "Griglia nascosta"
    override val hideOptions = "Nascondi opzioni"
    override val mapOptions = "Opzioni mappa"
    override val dragImageHint = "Trascina l'immagine per spostarla · angoli: " +
        "proporzioni bloccate · lati: stretching libero."
    override val fewerColumns = "− colonne"
    override val moreColumns = "+ colonne"
    override val fewerRows = "− righe"
    override val moreRows = "+ righe"
    override val chooseBackground = "Scegli sfondo"
    override val fromMapArchive = "Dall'archivio mappe"
    override val mapEditing = "Editing mappa"
    override val removeBackground = "Togli sfondo"
    override val fitAndCentre = "Adatta e centra"
    override val placeAll = "Disponi tutti"

    override fun gridSummary(columns: Int, rows: Int, square: String) =
        "$columns × $rows · $square/casella"
    override fun zoomLevel(value: Int) = "Zoom $value"
    override fun perSquare(distance: String) = "$distance/casella"

    // --- Finestra degli strumenti ----------------------------------------------

    override val toolsSuspendedDuringCpu =
        "Strumenti sospesi mentre la CPU risolve la parte nemica del turno."
    override val tableToolsTitle = "Strumenti del tavolo"
    override val tableToolsSubtitle = "Prove, danni, cure e condizioni del combattente scelto."
    override val affectedCombatant = "Combattente interessato"
    override val unconscious = "Privo di sensi"
    override val abilityCheck = "Prova di caratteristica"
    override val abilityCheckHint = "Scegli la caratteristica e inserisci il modificatore " +
        "della prova. Sfinimento e penalità dell'armatura vengono applicati dal motore."
    override val rollCheck = "Tira prova"
    override val amount = "Quantità"
    override val applyDamage = "Applica danno"
    override val temporaryHitPoints = "PF temporanei"
    override val damageTypeLabel = "Tipo del danno"
    override val roundsManualHint = "Round (0 = manuale)"
    override val addCondition = "Aggiungi condizione"
    override val clickConditionToRemove = "Clicca una condizione per rimuoverla"
    override val deathAndExhaustion = "Morte e sfinimento"
    override val deathSaveRoll = "Tiro contro morte"

    override fun rollModeFromBar(mode: String) = "Modalità dalla barra: $mode"
    override fun temporaryHitPointsOf(amount: Int) = "$amount PF temporanei"
    override fun removeCondition(condition: String) = "Rimuovi $condition"

    // --- Finestra del Grimorio --------------------------------------------------

    override fun addTo(destination: String) = "Aggiungi a $destination"
    override val pickFromGrimoireOrCreate =
        "Scegli una voce dal Grimorio o creane una nuova nel Compendio."
    override val addCharacter = "+ Personaggio"
    override val addCreature = "+ Creatura"
    override val grimoireEmpty = "Il Grimorio è vuoto: crea prima un personaggio o una creatura."

    override fun charactersCount(count: Int) = "Personaggi ($count)"
    override fun creaturesCount(count: Int) = "Creature ($count)"
    override fun sheetUnavailable(name: String) = "La scheda «$name» non è disponibile."

    // --- Finestra degli oggetti --------------------------------------------------

    override fun inventoryOf(character: String) = "Inventario di $character."
    override val selectCharacterForItems = "Ispeziona un PG per aprire il suo inventario."
    override val noItemsYet = "Nessun oggetto qui ancora."
    override val hoverItemHint =
        "Passa il mouse su un oggetto — o cliccalo — per leggerne qui descrizione ed effetti."
    override val noDescriptionListed = "Nessuna descrizione indicata."
    override val noEffectListed = "Nessun effetto indicato."

    // --- Turno della CPU ----------------------------------------------------------

    override val cpuCouldNotFinishTurn = "La CPU non ha potuto completare il turno."
    override val cpuSuspendedTableChangedBeforeEdit =
        "Turno CPU sospeso: il tavolo è cambiato prima dell'ingresso in Modifica."
    override val cpuSuspendedTableChangedDuringPlayback =
        "Turno CPU sospeso: lo stato del tavolo è cambiato durante la riproduzione."
    override val cpuSuspendedTableChangedDuringCommit =
        "Turno CPU sospeso: lo stato del tavolo è cambiato durante il consolidamento."
    override val cpuSafetyLimitReached =
        "La CPU ha raggiunto il limite di sicurezza: il turno resta disponibile al tavolo."
    override val cpuDifficultyEasy = "Facile"
    override val cpuDifficultyMedium = "Medio"
    override val cpuDifficultySorryForYou = "Mi dispiace per te!"

    override fun cpuSummary(
        difficulty: String,
        focus: String?,
        attacks: Int,
        heals: Int,
        moves: Int,
        encounterResolved: Boolean,
    ) = buildString {
        append("CPU ").append(difficulty)
        if (focus != null) append(" · bersaglio: ").append(focus)
        append(" · ").append(attacks).append(if (attacks == 1) " attacco" else " attacchi")
        if (heals > 0) append(" · ").append(heals).append(if (heals == 1) " cura" else " cure")
        if (moves > 0) append(" · ").append(moves).append(if (moves == 1) " movimento" else " movimenti")
        if (encounterResolved) append(" · scontro concluso")
    }

    override fun cpuMoves(actor: String) = "$actor si sposta"
    override fun cpuFlanks(actor: String) = "$actor accerchia il bersaglio"
    override fun cpuUsesAbility(actor: String) = "$actor usa una capacità"

    override fun cpuResourcesNotRealigned(detail: String) = " Risorse non riallineate: $detail"
    override fun cpuTurnUndone(reason: String, suffix: String) =
        "Turno CPU annullato: $reason$suffix"
    override fun cpuSuspendedKeepingChanges(reason: String) =
        "Turno CPU sospeso senza rimuovere le modifiche esterne: $reason"
    override fun cpuResourcesOutOfSync(detail: String) =
        " Risorse CPU non sincronizzate: $detail"

    override fun cpuMisses(actor: String, target: String) = "$actor manca $target"
    override fun cpuHits(actor: String, target: String) = "$actor colpisce $target"
    override fun cpuHitsForDamage(actor: String, target: String, amount: Int) =
        "$actor colpisce $target · -$amount"
    override fun cpuConcentratesOn(actor: String, target: String, amount: Int) =
        "$actor si concentra su $target · -$amount"
    override fun cpuHitsSeveral(actor: String, count: Int) = "$actor colpisce $count bersagli"
    override fun cpuHeals(actor: String, target: String, amount: Int, raising: Boolean) =
        "$actor ${if (raising) "rialza" else "cura"} $target · +$amount"
}
