package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.CombatEvent
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.EventType
import app.d6d.domain.combat.SaveAbility
import app.d6d.i18n.AppLanguage
import app.d6d.i18n.inlineLabel
import app.d6d.i18n.label
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.sheet.i18n.distanceUnit
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.PanelScrollbar
import app.d6d.ui.i18n.LogStrings
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.strings
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.Palette

/**
 * Registro degli eventi.
 *
 * Il motore tiene un log append-only: qui viene solo tradotto in italiano. Resta
 * la fonte autorevole di cio' che e' successo, sopra qualsiasi effetto visivo.
 */
@Composable
fun BattleLog(
    viewModel: BattleViewModel,
    modifier: Modifier = Modifier,
    entries: Int = Int.MAX_VALUE,
    showHeader: Boolean = true,
) {
    val recent = viewModel.events.asReversed().let { events ->
        if (entries == Int.MAX_VALUE) events else events.take(entries.coerceAtLeast(0))
    }
    val listState = rememberLazyListState()

    // L'evento piu' recente vive all'indice zero. Ogni nuova interazione riporta
    // sempre il registro in cima: durante il combattimento l'ultima cosa successa
    // deve restare visibile anche se poco prima si stava leggendo la cronologia.
    // La generazione copre anche il caricamento di una sessione diversa che abbia,
    // per coincidenza, lo stesso numero e la stessa sequenza finale di eventi.
    LaunchedEffect(viewModel.sessionGeneration, viewModel.events.lastOrNull()?.sequence()) {
        if (recent.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Abyss.copy(alpha = 0.88f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showHeader) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Eyebrow(strings.battle.eventLogHeading)
                    // Il round corrente vive qui, accanto all'etichetta: il registro
                    // e' il posto naturale per "a che punto siamo" della battaglia.
                    Chip(text = strings.battle.roundNumber(viewModel.round), color = Palette.Gold)
                }
                Text(
                    text = strings.battle.eventCount(viewModel.events.size),
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(recent, key = { _, event -> event.sequence() }) { index, event ->
                    LogLine(event, viewModel, latest = index == 0)
                }
            }
            PanelScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun LogLine(event: CombatEvent, viewModel: BattleViewModel, latest: Boolean) {
    // L'inizio di un round e' un capitolo della cronaca: riga ornamentale
    // centrata invece di una voce qualsiasi dell'elenco.
    if (event.type() == EventType.ROUND_STARTED) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoldenRule(Modifier.weight(1f), alpha = 0.3f)
            Text(
                text = strings.battle.roundNumber(event.round()),
                color = Palette.Gold,
                style = MaterialTheme.typography.labelSmall,
            )
            GoldenRule(Modifier.weight(1f), alpha = 0.3f)
        }
        return
    }

    val critical = event.type() == EventType.CRITICAL_HIT
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    latest -> Palette.Gold.copy(alpha = 0.09f)
                    critical -> Palette.Crit.copy(alpha = 0.07f)
                    else -> Color.Transparent
                },
            )
            // I critici portano una barretta dorata sul margine, come una nota
            // segnata a lato del testo.
            .drawBehind {
                if (critical) {
                    drawRect(color = Palette.Crit, size = Size(2.dp.toPx(), size.height))
                }
            }
            .padding(horizontal = 5.dp, vertical = if (latest) 3.dp else 1.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = if (latest) strings.battle.logNow else "R${event.round()}",
            color = if (latest) Palette.Gold else Palette.TextFaint,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = event.describe(viewModel, strings),
            color = event.type().tint,
            fontWeight = when {
                latest || critical || event.type() == EventType.DIED -> FontWeight.Bold
                else -> FontWeight.Normal
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

private val EventType.tint: Color
    get() = when (this) {
        EventType.CRITICAL_HIT -> Palette.Crit
        EventType.ATTACK_HIT, EventType.DAMAGE_APPLIED -> Palette.Text
        EventType.ATTACK_MISSED -> Palette.TextMuted
        EventType.HEALED, EventType.TEMPORARY_HIT_POINTS_GRANTED -> Palette.Heal
        EventType.CURRENT_HIT_POINTS_SET -> Palette.Party
        EventType.ZERO_HIT_POINTS -> Palette.Critical
        EventType.CONDITION_APPLIED, EventType.CONDITION_EXPIRED,
        EventType.CONDITION_REMOVED, EventType.CONDITION_IMMUNE -> Palette.Bloodied
        EventType.CONCENTRATION_STARTED, EventType.CONCENTRATION_CHECKED,
        EventType.CONCENTRATION_ENDED -> Palette.Temporary
        EventType.ROUND_STARTED, EventType.TURN_STARTED, EventType.ACTION_GRANTED -> Palette.Gold
        EventType.ABILITY_ACTIVATED, EventType.RESOURCE_SPENT -> Palette.Party
        EventType.ABILITY_CHECK_ROLLED -> Palette.Party
        EventType.DEATH_SAVE_ROLLED -> Palette.Bloodied
        EventType.STABILIZED, EventType.KNOCKED_OUT -> Palette.Heal
        EventType.DIED -> Palette.Critical
        EventType.EXHAUSTION_CHANGED -> Palette.Enemy
        EventType.COMBATANT_EDITED,
        EventType.COMBAT_RESOURCE_SET,
        EventType.TURN_RESOURCE_SET,
        EventType.COMBATANT_TRANSFORMED,
        -> Palette.Party
        EventType.COMBATANT_MOVED, EventType.COMBATANT_PLACED -> Palette.Party
        EventType.MAP_CONFIGURED, EventType.MAP_BACKGROUND_SET -> Palette.Gold
        else -> Palette.TextMuted
    }

/**
 * Traduce un evento del motore in una riga leggibile.
 *
 * I dettagli restano quelli registrati dal motore: qui non si ricalcola nulla,
 * cosi' il registro a schermo e quello salvato non possono divergere. Le parole
 * arrivano tutte da [LogStrings]; questa funzione decide solo quali pezzi passare.
 */
internal fun CombatEvent.describe(viewModel: BattleViewModel, strings: Strings): String {
    val words = strings.log
    val language = strings.language
    val actor = actorId().takeIf { it.isNotBlank() }?.let { viewModel.name(it) } ?: ""
    val target = targetId().takeIf { it.isNotBlank() }?.let { viewModel.name(it) } ?: ""
    val detail: (String) -> String = { key -> details()[key].orEmpty() }
    val abilityName = detail("abilityName").ifBlank {
        val abilityId = detail("abilityId")
        viewModel.abilities(actorId()).firstOrNull { it.id() == abilityId }?.name().orEmpty()
            .ifBlank { abilityId }
    }
    val ability = abilityName.takeIf { it.isNotBlank() }?.let { words.withAbility(it) }.orEmpty()
    val d20 = details().d20Breakdown(words)
    val againstAc = if (d20.isBlank()) "" else words.rollAgainstArmorClass(d20, detail("armorClass"))

    return when (type()) {
        EventType.ENCOUNTER_CREATED -> words.encounterCreated
        EventType.COMBATANT_ADDED -> words.combatantAdded(actor)
        EventType.PARTY_SET -> {
            val party = detail("combatantIds")
                .split(',')
                .filter { it.isNotBlank() }
                .joinToString(", ") { viewModel.name(it) }
            if (party.isBlank()) words.sidesDeclared else words.partyDeclared(party)
        }
        EventType.ENCOUNTER_READY -> words.encounterReady
        EventType.INITIATIVE_SET -> buildString {
            append(words.initiativeSet(actor, detail("total")))
            if (detail("static") == "true") append(words.staticInitiativeSuffix)
            when (detail("mode")) {
                "ADVANTAGE" -> append(words.advantageInitiativeSuffix)
                "DISADVANTAGE" -> append(words.disadvantageInitiativeSuffix)
            }
        }
        EventType.INITIATIVE_ROLLED -> words.initiativeRolled(actor, d20)
        EventType.ABILITY_CHECK_ROLLED ->
            words.abilityCheckRolled(actor, detail("ability").asSaveAbility(strings), d20)
        EventType.INITIATIVE_ORDER_SET -> {
            val order = detail("order")
                .split(',')
                .filter { it.isNotBlank() }
                .joinToString(" → ") { viewModel.name(it) }
            if (order.isBlank()) words.initiativeOrderSet else words.initiativeOrder(order)
        }
        EventType.ENCOUNTER_STARTED -> words.encounterStarted
        EventType.ROUND_STARTED -> words.roundStarted(detail("round"))
        EventType.ROUND_ENDED -> words.roundEnded(detail("round").ifBlank { round().toString() })
        EventType.TURN_STARTED -> words.turnStarted(actor)
        EventType.TURN_ENDED -> words.turnEnded(actor)
        EventType.ACTION_SPENT -> words.actionSpent(actor, detail("cost").asActivationCost(words))
        EventType.ABILITY_ACTIVATED ->
            words.abilityActivated(actor, abilityName.ifBlank { detail("abilityId") })
        EventType.RESOURCE_SPENT -> words.resourceSpent(
            actor = actor,
            cost = detail("cost"),
            resource = detail("resourceName"),
            remaining = detail("remaining"),
            maximum = detail("maximum"),
        )
        EventType.ACTION_GRANTED -> words.actionGranted(actor)
        EventType.MOVEMENT_SPENT -> words.movementSpent(
            actor = actor,
            spent = detail("feet").asDistance(language),
            remaining = detail("remaining").asDistance(language),
        )
        EventType.SPELL_SLOT_SPENT -> words.spellSlotSpent(actor)
        EventType.ATTACK_ROLLED ->
            words.attackRolled(actor, target, ability, d20, detail("armorClass"))
        EventType.ATTACK_MISSED -> words.attackMissed(actor, target, ability, againstAc)
        EventType.ATTACK_HIT -> words.attackHit(actor, target, ability, againstAc)
        EventType.CRITICAL_HIT -> words.criticalHit(actor, target, ability, againstAc)
        EventType.AREA_SPELL_CAST -> words.areaSpellCast(
            actor = actor,
            ability = abilityName.ifBlank { words.anArea },
            centre = detail("center"),
            radius = detail("radiusFeet").asDistance(language),
            saveDc = detail("saveDc"),
            targets = detail("targets"),
        )
        EventType.SAVING_THROW_ROLLED -> {
            val verb = if (detail("saved") == "true") words.savePassedVerb else words.saveFailedVerb
            val save = detail("save").asSaveAbility(strings)
            val against = abilityName.ifBlank { actor }
            if (d20.isNotBlank()) {
                words.savingThrowRolled(target, verb, save, against, d20, detail("dc"))
            } else {
                words.savingThrowDeclared(target, verb, save, against)
            }
        }
        EventType.DAMAGE_ROLLED -> words.damageRolled(
            actor = actor,
            ability = ability,
            recipient = target.takeIf { it.isNotBlank() }?.let { words.damageOnTarget(it) }.orEmpty(),
            breakdown = "${details().damageBreakdown(words)} ${detail("type").asDamageType(language)}",
        )
        EventType.DAMAGE_APPLIED -> if (detail("hitPointsAfter").isNotBlank()) {
            words.damageDealt(
                actor = actor,
                target = target,
                total = detail("totalAdjusted").ifBlank { detail("adjusted") },
                temporaryAbsorbed = detail("temporaryAbsorbed").toIntOrNull() ?: 0,
                hitPointsLost = detail("hitPointsLost").toIntOrNull(),
                hitPointsAfter = detail("hitPointsAfter"),
            )
        } else {
            words.damageAdjusted(
                actor = actor,
                target = target,
                type = detail("type").asDamageType(language),
                raw = detail("raw"),
                adjusted = detail("adjusted"),
                adjustment = when {
                    detail("immune") == "true" -> words.immuneSuffix
                    detail("resistant") == "true" -> words.resistantSuffix
                    detail("vulnerable") == "true" -> words.vulnerableSuffix
                    else -> ""
                },
            )
        }
        EventType.ZERO_HIT_POINTS -> words.zeroHitPoints(target, actor)
        EventType.HEALED -> words.healed(
            target = target,
            restored = detail("restored"),
            requested = detail("requested"),
            after = detail("hitPointsAfter"),
        )
        EventType.CURRENT_HIT_POINTS_SET -> words.currentHitPointsSet(
            target = target,
            before = detail("before"),
            after = detail("after"),
            dead = detail("zeroMeansDead") == "true",
        )
        EventType.TEMPORARY_HIT_POINTS_GRANTED -> words.temporaryHitPointsGranted(
            target = target,
            offered = detail("offered"),
            retained = detail("retained"),
            before = detail("before"),
        )
        EventType.CONDITION_APPLIED -> words.conditionApplied(
            target = target,
            condition = detail("condition").ifBlank { detail("type") }.asCondition(language),
            source = actor.takeIf { it.isNotBlank() }?.let { words.conditionSource(it) }.orEmpty(),
            duration = detail("remaining").takeIf { it.isNotBlank() }?.let {
                words.conditionDuration(it, detail("expiry").asConditionExpiry(words))
            }.orEmpty(),
        )
        EventType.CONDITION_REMOVED -> words.conditionRemoved(
            target,
            detail("condition").ifBlank { detail("type") }.asCondition(language),
        )
        EventType.CONDITION_EXPIRED -> words.conditionExpired(
            target,
            detail("condition").ifBlank { detail("type") }.asCondition(language),
        )
        EventType.CONDITION_IMMUNE -> words.conditionImmune(
            actor,
            target,
            detail("condition").ifBlank { detail("type") }.asCondition(language),
        )
        EventType.CONCENTRATION_STARTED -> words.concentrationStarted(actor, ability)
        EventType.CONCENTRATION_CHECKED -> words.concentrationChecked(
            who = actor.ifBlank { target },
            roll = d20,
            dc = detail("difficultyClass").ifBlank { detail("dc") },
            maintained = detail("maintained") == "true",
        )
        EventType.CONCENTRATION_ENDED -> words.concentrationEnded(
            actor = actor,
            ability = ability,
            reason = detail("reason").takeIf { it.isNotBlank() }
                ?.let { " (${it.asConcentrationReason(words)})" }
                .orEmpty(),
        )
        EventType.ENCOUNTER_PAUSED -> words.encounterPaused
        EventType.ENCOUNTER_RESUMED -> words.encounterResumed
        EventType.ENCOUNTER_RESOLVED -> words.encounterResolved(detail("outcome"))
        // L'annullamento resta scritto nel registro invece di sparire: il log e'
        // append-only, quindi la cronologia mostra anche i ripensamenti del tavolo.
        EventType.UNDO_PERFORMED -> words.undoPerformed(detail("revertedRevision"))

        EventType.DEATH_SAVE_ROLLED -> {
            val who = target.ifBlank { actor }
            when {
                detail("source") == "damage" -> words.deathSaveFromDamage(
                    who = who,
                    failures = detail("failures"),
                    actor = actor,
                    totalFailures = detail("totalFailures"),
                )
                detail("outcome") == "natural20" -> words.deathSaveNatural20(who, d20)
                else -> words.deathSaveRolled(
                    who = who,
                    roll = d20,
                    successes = detail("successes").ifBlank { "0" },
                    failures = detail("failures").ifBlank { detail("totalFailures") },
                )
            }
        }

        EventType.STABILIZED -> words.stabilized(actor.ifBlank { target })
        EventType.KNOCKED_OUT -> words.knockedOut(target)
        EventType.DIED -> {
            val who = target.ifBlank { actor }
            words.died(
                who = who,
                cause = detail("cause").asDeathCause(words),
                byActor = if (actor.isNotBlank() && actor != who) words.killedBy(actor) else "",
            )
        }
        EventType.EXHAUSTION_CHANGED -> words.exhaustionChanged(
            actor = actor,
            before = detail("before"),
            after = detail("after"),
            d20Penalty = detail("d20Penalty"),
            speedPenalty = detail("speedPenaltyFeet").asDistance(language),
        )

        EventType.COMBAT_RESOURCE_SET -> words.combatResourceSet(
            actor = actor,
            resource = detail("resourceName").ifBlank { detail("resourceId") },
            previousRemaining = detail("previousRemaining"),
            remaining = detail("remaining"),
            previousMaximum = detail("previousMaximum"),
            maximum = detail("maximum"),
        )
        EventType.TURN_RESOURCE_SET -> words.turnResourceSet(
            actor = actor,
            resource = when (detail("resource")) {
                "ACTION" -> strings.glossary.action
                "BONUS_ACTION" -> strings.battle.bonusActionLabel
                "REACTION" -> strings.glossary.reaction
                else -> detail("resource")
            },
            before = detail("before"),
            after = detail("after"),
        )

        EventType.COMBATANT_EDITED -> {
            val previousName = detail("previousName")
            val now = detail("name")
            val rename = if (previousName != now && previousName.isNotBlank()) {
                "$previousName → $now"
            } else {
                now
            }
            // Vengono elencate solo le statistiche cambiate davvero: il registro
            // deve dire cosa e' stato corretto, non ripetere tutta la scheda.
            val speedLabel = words.statSpeed
            val changes = listOf(
                Triple(words.statArmorClass, detail("previousArmorClass"), detail("armorClass")),
                Triple(words.statMaxHitPoints, detail("previousMaxHitPoints"), detail("maxHitPoints")),
                Triple(speedLabel, detail("previousSpeedFeet"), detail("speedFeet")),
                Triple(
                    words.statInitiative,
                    detail("previousInitiativeModifier"),
                    detail("initiativeModifier"),
                ),
                Triple(
                    words.statConstitutionSave,
                    detail("previousConstitutionSaveBonus"),
                    detail("constitutionSaveBonus"),
                ),
            ).filter { (_, before, after) -> before != after && after.isNotBlank() }
                .joinToString(", ") { (label, before, after) ->
                    if (label == speedLabel) {
                        "$label ${before.asDistance(language)}→${after.asDistance(language)}"
                    } else {
                        "$label $before→$after"
                    }
                }

            words.combatantEdited(rename, changes, detail("version"))
        }
        EventType.COMBATANT_TRANSFORMED -> words.combatantTransformed(
            actor = actor,
            previousName = detail("previousName"),
            name = detail("name"),
            temporaryHitPoints = detail("temporaryHitPoints"),
        )

        EventType.MAP_CONFIGURED -> words.mapConfigured(
            columns = detail("columns"),
            rows = detail("rows"),
            perSquare = detail("feetPerSquare").asDistance(language),
            dropped = detail("droppedPlacements").toIntOrNull() ?: 0,
        )

        EventType.MAP_BACKGROUND_SET ->
            if (detail("image").isBlank()) words.backgroundRemoved else words.backgroundSet(detail("image"))

        EventType.COMBATANT_PLACED -> words.combatantPlaced(actor, detail("position"))

        EventType.COMBATANT_MOVED -> words.combatantMoved(
            actor = actor,
            from = detail("from"),
            to = detail("to"),
            feet = detail("feet").asDistance(language),
            remaining = detail("remainingFeet").asDistance(language),
        )

        EventType.COMBATANT_REMOVED_FROM_MAP -> words.combatantRemovedFromMap(actor)
    }
}

/**
 * Il registro riceve i piedi del motore e li mostra nella misura della lingua.
 *
 * Un valore che non e' un numero passa cosi' com'e' con l'unita' appesa: un
 * dettaglio malformato non deve far sparire la riga dal registro.
 */
private fun String.asDistance(language: AppLanguage): String =
    toIntOrNull()?.let { distanceLabel(it, language) } ?: "$this ${distanceUnit(language)}"

/** Formula completa di un tiro d20, inclusi entrambi i dadi di Vantaggio/Svantaggio. */
private fun Map<String, String>.d20Breakdown(words: LogStrings): String {
    val natural = this["natural"].orEmpty()
    val total = this["total"].orEmpty()
    if (natural.isBlank()) return total

    val rolled = this["dice"].orEmpty()
    val die = when (this["mode"]) {
        "ADVANTAGE" -> words.dieWithAdvantage(rolled, natural)
        "DISADVANTAGE" -> words.dieWithDisadvantage(rolled, natural)
        else -> words.plainDie(natural)
    }
    val modifier = this["modifier"].orEmpty().modifierOperation()
    val source = if (this["source"] == "MANUAL") words.enteredManually else ""
    return buildString {
        append(die)
        if (modifier.isNotBlank()) append(' ').append(modifier)
        if (total.isNotBlank()) append(" = ").append(total)
        append(source)
    }
}

/** Valori effettivi dei dadi di danno, formula originale, modificatore e totale. */
private fun Map<String, String>.damageBreakdown(words: LogStrings): String {
    val amount = this["amount"].orEmpty().ifBlank { this["total"].orEmpty() }
    val formula = this["formula"].orEmpty()
    val prefix = formula.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
    return when (this["source"]?.lowercase()) {
        "manual" -> prefix + words.manualDamage(amount)
        "fixed" -> prefix + words.fixedDamage(amount)
        else -> {
            val modifier = this["modifier"].orEmpty().modifierOperation()
            buildString {
                append(prefix).append(words.diceDamage(this@damageBreakdown["dice"].orEmpty()))
                if (modifier.isNotBlank()) append(' ').append(modifier)
                append(" = ").append(amount)
            }
        }
    }
}

private fun String.modifierOperation(): String {
    val value = toIntOrNull() ?: return takeIf { it.isNotBlank() }?.let { "+ $it" }.orEmpty()
    return if (value < 0) "− ${-value}" else "+ $value"
}

private fun String.asActivationCost(words: LogStrings): String = runCatching {
    when (ActivationCost.valueOf(this)) {
        ActivationCost.ACTION -> words.costAction
        ActivationCost.BONUS_ACTION -> words.costBonusAction
        ActivationCost.REACTION -> words.costReaction
        ActivationCost.LEGENDARY_ACTION -> words.costLegendaryAction
        ActivationCost.NONE -> words.costFree
    }
}.getOrDefault(lowercase())

private fun String.asCondition(language: AppLanguage): String = runCatching {
    ConditionType.valueOf(this).inlineLabel(language)
}.getOrDefault(lowercase())

private fun String.asDamageType(language: AppLanguage): String = runCatching {
    DamageType.valueOf(this).inlineLabel(language)
}.getOrDefault(lowercase())

private fun String.asSaveAbility(strings: Strings): String = runCatching {
    SaveAbility.valueOf(this).label(strings.language)
}.getOrElse { lowercase().ifBlank { strings.log.abilityUnspecified } }

private fun String.asConditionExpiry(words: LogStrings): String = when (this) {
    "START_OF_TARGET_TURN" -> words.expiryStartOfTargetTurn
    "END_OF_TARGET_TURN" -> words.expiryEndOfTargetTurn
    "START_OF_SOURCE_TURN" -> words.expiryStartOfSourceTurn
    "END_OF_SOURCE_TURN" -> words.expiryEndOfSourceTurn
    "CONCENTRATION" -> words.expiryConcentration
    "MANUAL" -> words.expiryManual
    else -> lowercase().ifBlank { words.expiryUnspecified }
}

private fun String.asConcentrationReason(words: LogStrings): String = when (this) {
    "zero hit points" -> words.reasonZeroHitPoints
    "failed save" -> words.reasonFailedSave
    "replaced" -> words.reasonReplaced
    "manual" -> words.reasonManual
    "manual current hit points edit" -> words.reasonManualHitPointEdit
    else -> ifBlank { words.reasonUnspecified }
}

private fun String.asDeathCause(words: LogStrings): String = when (this) {
    "three successes" -> words.causeThreeSuccesses
    "three failures", "death saves" -> words.causeDeathSaves
    "massive damage" -> words.causeMassiveDamage
    "exhaustion" -> words.causeExhaustion
    "manual" -> words.causeManualStabilization
    "manual current hit points edit" -> words.causeManualHitPointEdit
    else -> ifBlank { words.causeUnspecified }
}
