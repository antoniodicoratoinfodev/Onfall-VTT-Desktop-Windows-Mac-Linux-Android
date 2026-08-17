package app.d6d.ui.i18n

/** Il registro degli eventi, in inglese. */
internal object LogStringsEn : LogStrings {

    // --- apertura e chiusura dello scontro -----------------------------------

    override val encounterCreated = "Encounter created"
    override val encounterReady = "Encounter ready"
    override val encounterStarted = "The encounter begins"
    override val encounterPaused = "Encounter paused"
    override val encounterResumed = "Encounter resumed"
    override val sidesDeclared = "Sides declared"
    override val initiativeOrderSet = "Initiative order set"
    override val backgroundRemoved = "Background removed"

    override fun combatantAdded(actor: String) = "$actor joins the encounter"
    override fun partyDeclared(names: String) = "Party declared: $names"
    override fun initiativeOrder(order: String) = "Initiative order: $order"
    override fun encounterResolved(outcome: String) = "Encounter resolved: $outcome"

    // --- turni e iniziativa ---------------------------------------------------

    override val staticInitiativeSuffix = " (static score)"
    override val advantageInitiativeSuffix = " with Advantage: +5"
    override val disadvantageInitiativeSuffix = " with Disadvantage: −5"

    override fun initiativeSet(actor: String, total: String) = "$actor: initiative $total"
    override fun initiativeRolled(actor: String, roll: String) = "$actor rolls initiative: $roll"
    override fun abilityCheckRolled(actor: String, ability: String, roll: String) =
        "$actor makes a $ability check: $roll"
    override fun roundStarted(round: String) = "— Round $round —"
    override fun roundEnded(round: String) = "End of round $round"
    override fun turnStarted(actor: String) = "$actor's turn"
    override fun turnEnded(actor: String) = "$actor ends their turn"
    override fun actionSpent(actor: String, cost: String) = "$actor uses $cost"
    override fun abilityActivated(actor: String, ability: String) = "$actor activates “$ability”"
    override fun resourceSpent(
        actor: String,
        cost: String,
        resource: String,
        remaining: String,
        maximum: String,
    ) = "$actor spends $cost use of $resource; $remaining/$maximum left"
    override fun actionGranted(actor: String) =
        "$actor gains an extra action, which cannot be used for the Magic action"
    override fun movementSpent(actor: String, spent: String, remaining: String) =
        "$actor uses $spent of movement; $remaining left"
    override fun spellSlotSpent(actor: String) = "$actor spends a spell slot"

    // --- attacchi ----------------------------------------------------------------

    override fun withAbility(ability: String) = " with “$ability”"
    override fun attackRolled(
        actor: String,
        target: String,
        ability: String,
        roll: String,
        armorClass: String,
    ) = "$actor rolls to hit $target$ability: $roll vs AC $armorClass"
    override fun attackMissed(actor: String, target: String, ability: String, rollAgainstAc: String) =
        "$actor misses $target$ability$rollAgainstAc"
    override fun attackHit(actor: String, target: String, ability: String, rollAgainstAc: String) =
        "$actor hits $target$ability$rollAgainstAc"
    override fun criticalHit(actor: String, target: String, ability: String, rollAgainstAc: String) =
        "CRITICAL HIT by $actor on $target$ability$rollAgainstAc"
    override fun rollAgainstArmorClass(roll: String, armorClass: String) =
        ": $roll vs AC $armorClass"

    // --- aree e tiri salvezza ------------------------------------------------------

    override val anArea = "an area effect"
    override val savePassedVerb = "makes"
    override val saveFailedVerb = "fails"
    override val decidedAtTheTable = " (called at the table)"

    override fun areaSpellCast(
        actor: String,
        ability: String,
        centre: String,
        radius: String,
        saveDc: String,
        targets: String,
    ) = "$actor uses $ability centred on $centre — radius $radius, DC $saveDc, " +
        "$targets creatures caught"

    override fun savingThrowRolled(
        target: String,
        verb: String,
        save: String,
        against: String,
        roll: String,
        dc: String,
    ) = "$target $verb the $save saving throw against $against: $roll vs DC $dc"

    override fun savingThrowDeclared(target: String, verb: String, save: String, against: String) =
        "$target $verb the $save saving throw against $against$decidedAtTheTable"

    // --- danni e cure ---------------------------------------------------------------

    override val immuneSuffix = " · immune"
    override val resistantSuffix = " · resistant"
    override val vulnerableSuffix = " · vulnerable"

    override fun damageRolled(actor: String, ability: String, recipient: String, breakdown: String) =
        "$actor rolls damage$ability$recipient: $breakdown"
    override fun damageOnTarget(target: String) = " on $target"

    override fun damageDealt(
        actor: String,
        target: String,
        total: String,
        temporaryAbsorbed: Int,
        hitPointsLost: Int?,
        hitPointsAfter: String,
    ) = buildString {
        if (actor.isNotBlank()) {
            append(actor).append(" deals ").append(total).append(" damage to ").append(target)
        } else {
            append(target).append(" takes ").append(total).append(" damage")
        }
        if (temporaryAbsorbed > 0) {
            append(" (").append(temporaryAbsorbed).append(" absorbed by temporary HP)")
        }
        if (hitPointsLost != null) append("; HP lost ").append(hitPointsLost)
        append("; ").append(target).append(" is left at ").append(hitPointsAfter).append(" HP")
    }

    override fun damageAdjusted(
        actor: String,
        target: String,
        type: String,
        raw: String,
        adjusted: String,
        adjustment: String,
    ) = "$actor applies $type damage to $target: $raw → $adjusted$adjustment"

    override fun zeroHitPoints(target: String, actor: String) =
        "$target drops to 0 HP from $actor's damage"

    override fun healed(target: String, restored: String, requested: String, after: String) =
        "$target regains $restored HP ($requested requested, now $after HP)"

    override fun currentHitPointsSet(target: String, before: String, after: String, dead: Boolean) =
        "$target: current HP $before → $after" + if (dead) " — dead" else ""

    override fun temporaryHitPointsGranted(
        target: String,
        offered: String,
        retained: String,
        before: String,
    ) = "$target is offered $offered temporary HP; keeps $retained (was $before)"

    // --- condizioni e concentrazione --------------------------------------------------

    override val concentrationMaintained = "maintained"
    override val concentrationLost = "lost"

    override fun conditionApplied(
        target: String,
        condition: String,
        source: String,
        duration: String,
    ) = "$target becomes $condition$source$duration"

    override fun conditionSource(actor: String) = " from $actor"
    override fun conditionDuration(remaining: String, expiry: String) =
        " · $remaining left ($expiry)"
    override fun conditionRemoved(target: String, condition: String) =
        "$target is no longer $condition"
    override fun conditionExpired(target: String, condition: String) =
        "$condition expires on $target"
    override fun conditionImmune(actor: String, target: String, condition: String) =
        "$actor tries to make $target $condition, but the target is immune"

    override fun concentrationStarted(actor: String, ability: String) =
        "$actor starts concentrating$ability"
    override fun concentrationChecked(who: String, roll: String, dc: String, maintained: Boolean) =
        "$who tries to hold concentration: $roll vs DC $dc — " +
            if (maintained) concentrationMaintained else concentrationLost
    override fun concentrationEnded(actor: String, ability: String, reason: String) =
        "$actor loses concentration$ability$reason"

    // --- morte -------------------------------------------------------------------------

    override fun undoPerformed(revision: String) =
        "Command undone: restored the revision before $revision"

    override fun deathSaveFromDamage(
        who: String,
        failures: String,
        actor: String,
        totalFailures: String,
    ) = "$who takes $failures death save failures from $actor's damage; " +
        "$totalFailures failures in total"

    override fun deathSaveNatural20(who: String, roll: String) =
        "$who rolls a death save: $roll — regains 1 HP"

    override fun deathSaveRolled(who: String, roll: String, successes: String, failures: String) =
        "$who rolls a death save: $roll — $successes successes, $failures failures"

    override fun stabilized(who: String) = "$who is stabilized"
    override fun knockedOut(target: String) = "$target is knocked out at 1 HP"
    override fun died(who: String, cause: String, byActor: String) = "$who dies ($cause)$byActor"
    override fun killedBy(actor: String) = " from $actor's action"

    override fun exhaustionChanged(
        actor: String,
        before: String,
        after: String,
        d20Penalty: String,
        speedPenalty: String,
    ) = "$actor: exhaustion $before → $after ($d20Penalty to D20 rolls, $speedPenalty)"

    // --- correzioni e trasformazioni -------------------------------------------------

    override val statArmorClass = "AC"
    override val statMaxHitPoints = "Max HP"
    override val statSpeed = "Speed"
    override val statInitiative = "Initiative"
    override val statConstitutionSave = "CON save"

    override fun combatantEdited(rename: String, changes: String, version: String) = buildString {
        append("Sheet corrected: ").append(rename)
        if (changes.isNotEmpty()) append(" — ").append(changes)
        append(" [rev. ").append(version).append(']')
    }

    override fun combatantTransformed(
        actor: String,
        previousName: String,
        name: String,
        temporaryHitPoints: String,
    ) = "$actor uses Wild Shape: $previousName becomes $name; " +
        "$temporaryHitPoints temporary HP"

    // --- mappa ---------------------------------------------------------------------------

    override fun mapConfigured(columns: String, rows: String, perSquare: String, dropped: Int) =
        buildString {
            append("Map ").append(columns).append('×').append(rows)
            append(", ").append(perSquare).append(" per square")
            if (dropped > 0) append(" — $dropped tokens removed for falling outside")
        }

    override fun backgroundSet(image: String) = "Background: $image"
    override fun combatantPlaced(actor: String, position: String) = "$actor placed at $position"
    override fun combatantMoved(
        actor: String,
        from: String,
        to: String,
        feet: String,
        remaining: String,
    ) = "$actor moves $from → $to ($feet, $remaining left)"
    override fun combatantRemovedFromMap(actor: String) = "$actor taken off the map"

    // --- scomposizione dei tiri ------------------------------------------------------------

    override val enteredManually = " · entered by hand"
    override fun dieWithAdvantage(rolled: String, chosen: String) =
        "d20 $rolled (Advantage, took $chosen)"
    override fun dieWithDisadvantage(rolled: String, chosen: String) =
        "d20 $rolled (Disadvantage, took $chosen)"
    override fun plainDie(natural: String) = "d20 $natural"
    override fun manualDamage(amount: String) = "value entered $amount"
    override fun fixedDamage(amount: String) = "fixed damage $amount"
    override fun diceDamage(dice: String) = "dice $dice"

    // --- vocabolario dei dettagli grezzi -----------------------------------------------------

    override val costAction = "their action"
    override val costBonusAction = "their bonus action"
    override val costReaction = "their reaction"
    override val costLegendaryAction = "a legendary action"
    override val costFree = "a free action"
    override val abilityUnspecified = "unspecified ability"

    override val expiryStartOfTargetTurn = "start of the target's turn"
    override val expiryEndOfTargetTurn = "end of the target's turn"
    override val expiryStartOfSourceTurn = "start of the source's turn"
    override val expiryEndOfSourceTurn = "end of the source's turn"
    override val expiryConcentration = "concentration"
    override val expiryManual = "removed by hand"
    override val expiryUnspecified = "unspecified expiry"

    override val reasonZeroHitPoints = "0 HP"
    override val reasonFailedSave = "failed saving throw"
    override val reasonReplaced = "replaced by another concentration"
    override val reasonManual = "broken by hand"
    override val reasonManualHitPointEdit = "HP set by hand"
    override val reasonUnspecified = "unspecified reason"

    override val causeThreeSuccesses = "three successes"
    override val causeDeathSaves = "death saving throws"
    override val causeMassiveDamage = "massive damage"
    override val causeExhaustion = "exhaustion"
    override val causeManualStabilization = "stabilized by hand"
    override val causeManualHitPointEdit = "HP set by hand"
    override val causeUnspecified = "unspecified cause"
}
