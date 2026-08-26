package app.d6d.ui.i18n

/** Il registro degli eventi, in italiano. */
internal object LogStringsIt : LogStrings {

    // --- apertura e chiusura dello scontro -----------------------------------

    override val encounterCreated = "Incontro creato"
    override val encounterReady = "Incontro pronto"
    override val encounterStarted = "L'incontro comincia"
    override val encounterPaused = "Incontro in pausa"
    override val encounterResumed = "Incontro ripreso"
    override val sidesDeclared = "Schieramenti dichiarati"
    override val initiativeOrderSet = "Ordine d'iniziativa fissato"
    override val backgroundRemoved = "Sfondo rimosso"

    override fun combatantAdded(actor: String) = "$actor entra nell'incontro"
    override fun partyDeclared(names: String) = "Squadra dichiarata: $names"
    override fun initiativeOrder(order: String) = "Ordine d'iniziativa: $order"
    override fun encounterResolved(outcome: String) = "Incontro risolto: $outcome"

    // --- turni e iniziativa ---------------------------------------------------

    override val staticInitiativeSuffix = " (punteggio statico)"
    override val advantageInitiativeSuffix = " con Vantaggio: +5"
    override val disadvantageInitiativeSuffix = " con Svantaggio: −5"

    override fun initiativeSet(actor: String, total: String) = "$actor: iniziativa $total"
    override fun initiativeRolled(actor: String, roll: String) = "$actor tira iniziativa: $roll"
    override fun abilityCheckRolled(actor: String, ability: String, roll: String) =
        "$actor effettua una prova di $ability: $roll"
    override fun roundStarted(round: String) = "— Round $round —"
    override fun roundEnded(round: String) = "Fine del round $round"
    override fun turnStarted(actor: String) = "Turno di $actor"
    override fun turnEnded(actor: String) = "$actor termina il turno"
    override fun actionSpent(actor: String, cost: String) = "$actor usa $cost"
    override fun abilityActivated(actor: String, ability: String) = "$actor attiva «$ability»"
    override fun resourceSpent(
        actor: String,
        cost: String,
        resource: String,
        remaining: String,
        maximum: String,
    ) = "$actor consuma $cost uso di $resource; ne restano $remaining/$maximum"
    override fun actionGranted(actor: String) =
        "$actor ottiene un'azione aggiuntiva, non utilizzabile per l'azione di Magia"
    override fun movementSpent(actor: String, spent: String, remaining: String) =
        "$actor usa $spent di movimento; ne restano $remaining"
    override fun spellSlotSpent(actor: String) = "$actor consuma uno slot"

    // --- attacchi ----------------------------------------------------------------

    override fun withAbility(ability: String) = " con «$ability»"
    override fun attackRolled(
        actor: String,
        target: String,
        ability: String,
        roll: String,
        armorClass: String,
    ) = "$actor tira per colpire $target$ability: $roll contro CA $armorClass"
    override fun attackMissed(actor: String, target: String, ability: String, rollAgainstAc: String) =
        "$actor manca $target$ability$rollAgainstAc"
    override fun attackHit(actor: String, target: String, ability: String, rollAgainstAc: String) =
        "$actor colpisce $target$ability$rollAgainstAc"
    override fun criticalHit(actor: String, target: String, ability: String, rollAgainstAc: String) =
        "COLPO CRITICO di $actor su $target$ability$rollAgainstAc"
    override fun rollAgainstArmorClass(roll: String, armorClass: String) =
        ": $roll contro CA $armorClass"

    // --- aree e tiri salvezza ------------------------------------------------------

    override val anArea = "un'area"
    override val savePassedVerb = "supera"
    override val saveFailedVerb = "fallisce"
    override val decidedAtTheTable = " (deciso al tavolo)"

    override fun areaSpellCast(
        actor: String,
        ability: String,
        centre: String,
        radius: String,
        saveDc: String,
        targets: String,
    ) = "$actor usa $ability al centro $centre — raggio $radius, CD $saveDc, " +
        "$targets creature coinvolte"

    override fun savingThrowRolled(
        target: String,
        verb: String,
        save: String,
        against: String,
        roll: String,
        dc: String,
    ) = "$target $verb il tiro salvezza su $save contro $against: $roll contro CD $dc"

    override fun savingThrowDeclared(target: String, verb: String, save: String, against: String) =
        "$target $verb il tiro salvezza su $save contro $against$decidedAtTheTable"

    // --- danni e cure ---------------------------------------------------------------

    override val immuneSuffix = " · immune"
    override val resistantSuffix = " · resistente"
    override val vulnerableSuffix = " · vulnerabile"

    override fun damageRolled(actor: String, ability: String, recipient: String, breakdown: String) =
        "$actor determina i danni$ability$recipient: $breakdown"
    override fun damageOnTarget(target: String) = " su $target"

    override fun damageDealt(
        actor: String,
        target: String,
        total: String,
        temporaryAbsorbed: Int,
        hitPointsLost: Int?,
        hitPointsAfter: String,
    ) = buildString {
        if (actor.isNotBlank()) {
            append(actor).append(" infligge a ").append(target).append(' ')
        } else {
            append(target).append(" subisce ")
        }
        append(total).append(" danni")
        if (temporaryAbsorbed > 0) {
            append(" (").append(temporaryAbsorbed).append(" assorbiti dai PF temporanei)")
        }
        if (hitPointsLost != null) append("; PF persi ").append(hitPointsLost)
        append("; ").append(target).append(" resta a ").append(hitPointsAfter).append(" PF")
    }

    override fun damageAdjusted(
        actor: String,
        target: String,
        type: String,
        raw: String,
        adjusted: String,
        adjustment: String,
    ) = "$actor applica a $target il danno $type: $raw → $adjusted$adjustment"

    override fun zeroHitPoints(target: String, actor: String) =
        "$target cade a 0 PF per il danno di $actor"

    override fun healed(target: String, restored: String, requested: String, after: String) =
        "$target recupera $restored PF (richiesti $requested, ora $after PF)"

    override fun currentHitPointsSet(target: String, before: String, after: String, dead: Boolean) =
        "$target: PF attuali $before → $after" + if (dead) " — morto" else ""

    override fun temporaryHitPointsGranted(
        target: String,
        offered: String,
        retained: String,
        before: String,
    ) = "$target riceve $offered PF temporanei; ne conserva $retained (prima $before)"

    // --- condizioni e concentrazione --------------------------------------------------

    override val concentrationMaintained = "mantenuta"
    override val concentrationLost = "persa"

    override fun conditionApplied(
        target: String,
        condition: String,
        source: String,
        duration: String,
    ) = "$target diventa $condition$source$duration"

    override fun conditionSource(actor: String) = " da $actor"
    override fun conditionDuration(remaining: String, expiry: String) =
        " · durata residua $remaining ($expiry)"
    override fun conditionRemoved(target: String, condition: String) = "$target non è più $condition"
    override fun conditionExpired(target: String, condition: String) = "Su $target scade: $condition"
    override fun conditionImmune(actor: String, target: String, condition: String) =
        "$actor tenta di applicare $condition a $target, ma il bersaglio è immune"

    override fun concentrationStarted(actor: String, ability: String) =
        "$actor inizia a concentrarsi$ability"
    override fun concentrationChecked(who: String, roll: String, dc: String, maintained: Boolean) =
        "$who prova a mantenere la concentrazione: $roll contro CD $dc — " +
            if (maintained) concentrationMaintained else concentrationLost
    override fun concentrationEnded(actor: String, ability: String, reason: String) =
        "$actor perde la concentrazione$ability$reason"

    // --- morte -------------------------------------------------------------------------

    override fun undoPerformed(revision: String) =
        "Comando annullato: ripristinata la revisione precedente alla $revision"

    override fun deathSaveFromDamage(
        who: String,
        failures: String,
        actor: String,
        totalFailures: String,
    ) = "$who subisce $failures fallimenti contro morte per il danno di $actor; " +
        "fallimenti totali $totalFailures"

    override fun deathSaveNatural20(who: String, roll: String) =
        "$who tira contro morte: $roll — recupera 1 PF"

    override fun deathSaveRolled(who: String, roll: String, successes: String, failures: String) =
        "$who tira contro morte: $roll — $successes successi, $failures fallimenti"

    override fun stabilized(who: String) = "$who è stabilizzato"
    override fun knockedOut(target: String) = "$target messo fuori combattimento a 1 PF"
    override fun died(who: String, cause: String, byActor: String) = "$who muore ($cause)$byActor"
    override fun killedBy(actor: String) = " per l'azione di $actor"

    override fun exhaustionChanged(
        actor: String,
        before: String,
        after: String,
        d20Penalty: String,
        speedPenalty: String,
    ) = "$actor: sfinimento $before → $after ($d20Penalty ai D20, $speedPenalty)"

    // --- correzioni e trasformazioni -------------------------------------------------

    override val statArmorClass = "CA"
    override val statMaxHitPoints = "PF max"
    override val statSpeed = "Velocità"
    override val statInitiative = "Iniziativa"
    override val statConstitutionSave = "TS Cos"

    override fun combatResourceSet(
        actor: String,
        resource: String,
        previousRemaining: String,
        remaining: String,
        previousMaximum: String,
        maximum: String,
    ) = "$actor corregge $resource: disponibili $previousRemaining→$remaining, " +
        "massimo $previousMaximum→$maximum"

    override fun turnResourceSet(actor: String, resource: String, before: String, after: String) =
        "$actor corregge $resource: $before→$after"

    override fun combatantEdited(rename: String, changes: String, version: String) = buildString {
        append("Scheda corretta: ").append(rename)
        if (changes.isNotEmpty()) append(" — ").append(changes)
        append(" [rev. ").append(version).append(']')
    }

    override fun combatantTransformed(
        actor: String,
        previousName: String,
        name: String,
        temporaryHitPoints: String,
    ) = "$actor usa Forma Selvatica: $previousName diventa $name; " +
        "$temporaryHitPoints PF temporanei"

    // --- mappa ---------------------------------------------------------------------------

    override fun mapConfigured(columns: String, rows: String, perSquare: String, dropped: Int) =
        buildString {
            append("Mappa ").append(columns).append('×').append(rows)
            append(", ").append(perSquare).append(" per casella")
            if (dropped > 0) append(" — $dropped segnaposti fuori bordo rimossi")
        }

    override fun backgroundSet(image: String) = "Sfondo: $image"
    override fun combatantPlaced(actor: String, position: String) = "$actor collocato in $position"
    override fun combatantMoved(
        actor: String,
        from: String,
        to: String,
        feet: String,
        remaining: String,
    ) = "$actor si sposta $from → $to ($feet, ne restano $remaining)"
    override fun combatantRemovedFromMap(actor: String) = "$actor tolto dalla mappa"

    // --- scomposizione dei tiri ------------------------------------------------------------

    override val enteredManually = " · inserito manualmente"
    override fun dieWithAdvantage(rolled: String, chosen: String) =
        "d20 $rolled (Vantaggio, scelto $chosen)"
    override fun dieWithDisadvantage(rolled: String, chosen: String) =
        "d20 $rolled (Svantaggio, scelto $chosen)"
    override fun plainDie(natural: String) = "d20 $natural"
    override fun manualDamage(amount: String) = "valore inserito $amount"
    override fun fixedDamage(amount: String) = "danno fisso $amount"
    override fun diceDamage(dice: String) = "dadi $dice"

    // --- vocabolario dei dettagli grezzi -----------------------------------------------------

    override val costAction = "l'azione"
    override val costBonusAction = "l'azione bonus"
    override val costReaction = "la reazione"
    override val costLegendaryAction = "un'azione leggendaria"
    override val costFree = "un'azione gratuita"
    override val abilityUnspecified = "caratteristica non indicata"

    override val expiryStartOfTargetTurn = "inizio turno del bersaglio"
    override val expiryEndOfTargetTurn = "fine turno del bersaglio"
    override val expiryStartOfSourceTurn = "inizio turno della fonte"
    override val expiryEndOfSourceTurn = "fine turno della fonte"
    override val expiryConcentration = "concentrazione"
    override val expiryManual = "rimozione manuale"
    override val expiryUnspecified = "scadenza non indicata"

    override val reasonZeroHitPoints = "0 PF"
    override val reasonFailedSave = "tiro salvezza fallito"
    override val reasonReplaced = "sostituita da un'altra concentrazione"
    override val reasonManual = "interrotta manualmente"
    override val reasonManualHitPointEdit = "PF impostati manualmente"
    override val reasonUnspecified = "motivo non indicato"

    override val causeThreeSuccesses = "tre successi"
    override val causeDeathSaves = "tiri contro morte"
    override val causeMassiveDamage = "danno massiccio"
    override val causeExhaustion = "sfinimento"
    override val causeManualStabilization = "stabilizzazione manuale"
    override val causeManualHitPointEdit = "PF impostati manualmente"
    override val causeUnspecified = "causa non specificata"
}
