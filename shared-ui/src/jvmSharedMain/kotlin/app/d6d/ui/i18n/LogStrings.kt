package app.d6d.ui.i18n

/**
 * Il registro degli eventi.
 *
 * Il motore tiene un log append-only con dettagli grezzi — chiavi, numeri, nomi di
 * enum — e non una riga di prosa. Le frasi sono tutte qui, una per tipo di evento,
 * perche' e' l'unico modo perche' due lingue possano raccontare lo stesso fatto
 * con la propria sintassi invece che con la stessa, riempita di parole diverse.
 *
 * Ogni funzione riceve gia' i pezzi risolti: nomi tradotti in nomi, distanze nella
 * misura giusta, tiri gia' scomposti. Qui non si calcola nulla — cosi' il registro
 * a schermo e quello salvato non possono divergere — si sceglie solo come dirlo.
 */
interface LogStrings {

    // --- apertura e chiusura dello scontro -----------------------------------

    val encounterCreated: String
    val encounterReady: String
    val encounterStarted: String
    val encounterPaused: String
    val encounterResumed: String
    val sidesDeclared: String
    val initiativeOrderSet: String
    val backgroundRemoved: String
    fun combatantAdded(actor: String): String
    fun partyDeclared(names: String): String
    fun initiativeOrder(order: String): String
    fun encounterResolved(outcome: String): String

    // --- turni e iniziativa ---------------------------------------------------

    val staticInitiativeSuffix: String
    val advantageInitiativeSuffix: String
    val disadvantageInitiativeSuffix: String
    fun initiativeSet(actor: String, total: String): String
    fun initiativeRolled(actor: String, roll: String): String
    fun abilityCheckRolled(actor: String, ability: String, roll: String): String
    fun roundStarted(round: String): String
    fun roundEnded(round: String): String
    fun turnStarted(actor: String): String
    fun turnEnded(actor: String): String
    fun actionSpent(actor: String, cost: String): String
    fun abilityActivated(actor: String, ability: String): String
    fun resourceSpent(
        actor: String,
        cost: String,
        resource: String,
        remaining: String,
        maximum: String,
    ): String
    fun actionGranted(actor: String): String
    fun movementSpent(actor: String, spent: String, remaining: String): String
    fun spellSlotSpent(actor: String): String

    // --- attacchi ----------------------------------------------------------------

    /** Coda che nomina la capacita' usata, quando c'e': « con «Colpo»». */
    fun withAbility(ability: String): String
    fun attackRolled(
        actor: String,
        target: String,
        ability: String,
        roll: String,
        armorClass: String,
    ): String
    fun attackMissed(actor: String, target: String, ability: String, rollAgainstAc: String): String
    fun attackHit(actor: String, target: String, ability: String, rollAgainstAc: String): String
    fun criticalHit(actor: String, target: String, ability: String, rollAgainstAc: String): String
    /** Coda condivisa dai tre eventi sopra: «: d20 18 + 5 = 23 contro CA 15». */
    fun rollAgainstArmorClass(roll: String, armorClass: String): String

    // --- aree e tiri salvezza ------------------------------------------------------

    val anArea: String
    val savePassedVerb: String
    val saveFailedVerb: String
    val decidedAtTheTable: String
    fun areaSpellCast(
        actor: String,
        ability: String,
        centre: String,
        radius: String,
        saveDc: String,
        targets: String,
    ): String
    fun savingThrowRolled(
        target: String,
        verb: String,
        save: String,
        against: String,
        roll: String,
        dc: String,
    ): String
    fun savingThrowDeclared(target: String, verb: String, save: String, against: String): String

    // --- danni e cure ---------------------------------------------------------------

    val immuneSuffix: String
    val resistantSuffix: String
    val vulnerableSuffix: String
    fun damageRolled(actor: String, ability: String, recipient: String, breakdown: String): String
    fun damageOnTarget(target: String): String
    fun damageDealt(
        actor: String,
        target: String,
        total: String,
        temporaryAbsorbed: Int,
        hitPointsLost: Int?,
        hitPointsAfter: String,
    ): String
    fun damageAdjusted(
        actor: String,
        target: String,
        type: String,
        raw: String,
        adjusted: String,
        adjustment: String,
    ): String
    fun zeroHitPoints(target: String, actor: String): String
    fun healed(target: String, restored: String, requested: String, after: String): String
    fun currentHitPointsSet(target: String, before: String, after: String, dead: Boolean): String
    fun temporaryHitPointsGranted(
        target: String,
        offered: String,
        retained: String,
        before: String,
    ): String

    // --- condizioni e concentrazione --------------------------------------------------

    val concentrationMaintained: String
    val concentrationLost: String
    fun conditionApplied(target: String, condition: String, source: String, duration: String): String
    fun conditionSource(actor: String): String
    fun conditionDuration(remaining: String, expiry: String): String
    fun conditionRemoved(target: String, condition: String): String
    fun conditionExpired(target: String, condition: String): String
    fun conditionImmune(actor: String, target: String, condition: String): String
    fun concentrationStarted(actor: String, ability: String): String
    fun concentrationChecked(who: String, roll: String, dc: String, maintained: Boolean): String
    fun concentrationEnded(actor: String, ability: String, reason: String): String

    // --- morte -------------------------------------------------------------------------

    fun undoPerformed(revision: String): String
    fun deathSaveFromDamage(
        who: String,
        failures: String,
        actor: String,
        totalFailures: String,
    ): String
    fun deathSaveNatural20(who: String, roll: String): String
    fun deathSaveRolled(who: String, roll: String, successes: String, failures: String): String
    fun stabilized(who: String): String
    fun knockedOut(target: String): String
    fun died(who: String, cause: String, byActor: String): String
    fun killedBy(actor: String): String
    fun exhaustionChanged(
        actor: String,
        before: String,
        after: String,
        d20Penalty: String,
        speedPenalty: String,
    ): String

    // --- correzioni e trasformazioni -------------------------------------------------

    val statArmorClass: String
    val statMaxHitPoints: String
    val statSpeed: String
    val statInitiative: String
    val statConstitutionSave: String
    fun combatResourceSet(
        actor: String,
        resource: String,
        previousRemaining: String,
        remaining: String,
        previousMaximum: String,
        maximum: String,
    ): String
    fun turnResourceSet(actor: String, resource: String, before: String, after: String): String
    fun combatantEdited(rename: String, changes: String, version: String): String
    fun combatantTransformed(
        actor: String,
        previousName: String,
        name: String,
        temporaryHitPoints: String,
    ): String

    // --- mappa ---------------------------------------------------------------------------

    fun mapConfigured(columns: String, rows: String, perSquare: String, dropped: Int): String
    fun backgroundSet(image: String): String
    fun combatantPlaced(actor: String, position: String): String
    fun combatantMoved(actor: String, from: String, to: String, feet: String, remaining: String): String
    fun combatantRemovedFromMap(actor: String): String

    // --- scomposizione dei tiri ------------------------------------------------------------

    val enteredManually: String
    fun dieWithAdvantage(rolled: String, chosen: String): String
    fun dieWithDisadvantage(rolled: String, chosen: String): String
    fun plainDie(natural: String): String
    fun manualDamage(amount: String): String
    fun fixedDamage(amount: String): String
    fun diceDamage(dice: String): String

    // --- vocabolario dei dettagli grezzi -----------------------------------------------------

    val costAction: String
    val costBonusAction: String
    val costReaction: String
    val costLegendaryAction: String
    val costFree: String
    val abilityUnspecified: String

    val expiryStartOfTargetTurn: String
    val expiryEndOfTargetTurn: String
    val expiryStartOfSourceTurn: String
    val expiryEndOfSourceTurn: String
    val expiryConcentration: String
    val expiryManual: String
    val expiryUnspecified: String

    val reasonZeroHitPoints: String
    val reasonFailedSave: String
    val reasonReplaced: String
    val reasonManual: String
    val reasonManualHitPointEdit: String
    val reasonUnspecified: String

    val causeThreeSuccesses: String
    val causeDeathSaves: String
    val causeMassiveDamage: String
    val causeExhaustion: String
    val causeManualStabilization: String
    val causeManualHitPointEdit: String
    val causeUnspecified: String
}
