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
import app.d6d.sheet.italianLabel
import app.d6d.sheet.metresLabel
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.PanelScrollbar
import app.d6d.ui.components.italianLabel
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
                    Eyebrow("Registro eventi")
                    // Il round corrente vive qui, accanto all'etichetta: il registro
                    // e' il posto naturale per "a che punto siamo" della battaglia.
                    Chip(text = "Round ${viewModel.round}", color = Palette.Gold)
                }
                Text(
                    text = "${viewModel.events.size} eventi",
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
                text = "Round ${event.round()}",
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
            text = if (latest) "ORA" else "R${event.round()}",
            color = if (latest) Palette.Gold else Palette.TextFaint,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = event.describeInItalian(viewModel),
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
 * cosi' il registro a schermo e quello salvato non possono divergere.
 */
internal fun CombatEvent.describeInItalian(viewModel: BattleViewModel): String {
    val actor = actorId().takeIf { it.isNotBlank() }?.let { viewModel.name(it) } ?: ""
    val target = targetId().takeIf { it.isNotBlank() }?.let { viewModel.name(it) } ?: ""
    val detail: (String) -> String = { key -> details()[key].orEmpty() }
    val abilityName = detail("abilityName").ifBlank {
        val abilityId = detail("abilityId")
        viewModel.abilities(actorId()).firstOrNull { it.id() == abilityId }?.name().orEmpty()
            .ifBlank { abilityId }
    }
    val ability = abilityName.takeIf { it.isNotBlank() }?.let { " con «$it»" }.orEmpty()
    val d20 = details().d20Breakdown()

    return when (type()) {
        EventType.ENCOUNTER_CREATED -> "Incontro creato"
        EventType.COMBATANT_ADDED -> "$actor entra nell'incontro"
        EventType.PARTY_SET -> {
            val party = detail("combatantIds")
                .split(',')
                .filter { it.isNotBlank() }
                .joinToString(", ") { viewModel.name(it) }
            if (party.isBlank()) "Schieramenti dichiarati" else "Squadra dichiarata: $party"
        }
        EventType.ENCOUNTER_READY -> "Incontro pronto"
        EventType.INITIATIVE_SET -> buildString {
            append(actor).append(": iniziativa ").append(detail("total"))
            if (detail("static") == "true") append(" (punteggio statico)")
            when (detail("mode")) {
                "ADVANTAGE" -> append(" con Vantaggio: +5")
                "DISADVANTAGE" -> append(" con Svantaggio: −5")
            }
        }
        EventType.INITIATIVE_ROLLED -> "$actor tira iniziativa: $d20"
        EventType.ABILITY_CHECK_ROLLED ->
            "$actor effettua una prova di ${detail("ability").saveAbilityInItalian()}: $d20"
        EventType.INITIATIVE_ORDER_SET -> {
            val order = detail("order")
                .split(',')
                .filter { it.isNotBlank() }
                .joinToString(" → ") { viewModel.name(it) }
            if (order.isBlank()) "Ordine d'iniziativa fissato" else "Ordine d'iniziativa: $order"
        }
        EventType.ENCOUNTER_STARTED -> "L'incontro comincia"
        EventType.ROUND_STARTED -> "— Round ${detail("round")} —"
        EventType.ROUND_ENDED -> "Fine del round ${detail("round").ifBlank { round().toString() }}"
        EventType.TURN_STARTED -> "Turno di $actor"
        EventType.TURN_ENDED -> "$actor termina il turno"
        EventType.ACTION_SPENT -> "$actor usa ${detail("cost").activationCostInItalian()}"
        EventType.ABILITY_ACTIVATED -> "$actor attiva «${abilityName.ifBlank { detail("abilityId") }}»"
        EventType.RESOURCE_SPENT ->
            "$actor consuma ${detail("cost")} uso di ${detail("resourceName")}; " +
                "ne restano ${detail("remaining")}/${detail("maximum")}"
        EventType.ACTION_GRANTED ->
            "$actor ottiene un'azione aggiuntiva, non utilizzabile per l'azione di Magia"
        EventType.MOVEMENT_SPENT ->
            "$actor usa ${detail("feet").asDistance()} di movimento; " +
                "ne restano ${detail("remaining").asDistance()}"
        EventType.SPELL_SLOT_SPENT -> "$actor consuma uno slot"
        EventType.ATTACK_ROLLED ->
            "$actor tira per colpire $target$ability: $d20 contro CA ${detail("armorClass")}"
        EventType.ATTACK_MISSED -> buildString {
            append(actor).append(" manca ").append(target).append(ability)
            if (d20.isNotBlank()) append(": ").append(d20).append(" contro CA ").append(detail("armorClass"))
        }
        EventType.ATTACK_HIT -> buildString {
            append(actor).append(" colpisce ").append(target).append(ability)
            if (d20.isNotBlank()) append(": ").append(d20).append(" contro CA ").append(detail("armorClass"))
        }
        EventType.CRITICAL_HIT -> buildString {
            append("COLPO CRITICO di ").append(actor).append(" su ").append(target).append(ability)
            if (d20.isNotBlank()) append(": ").append(d20).append(" contro CA ").append(detail("armorClass"))
        }
        EventType.AREA_SPELL_CAST ->
            "$actor usa ${abilityName.ifBlank { "un'area" }} al centro ${detail("center")} — " +
                "raggio ${detail("radiusFeet").asDistance()}, CD ${detail("saveDc")}, " +
                "${detail("targets")} creature coinvolte"
        EventType.SAVING_THROW_ROLLED -> {
            val esito = if (detail("saved") == "true") "supera" else "fallisce"
            val save = detail("save").saveAbilityInItalian()
            if (d20.isNotBlank()) {
                "$target $esito il tiro salvezza su $save contro ${abilityName.ifBlank { actor }}: " +
                    "$d20 contro CD ${detail("dc")}"
            } else {
                "$target $esito il tiro salvezza su $save contro " +
                    "${abilityName.ifBlank { actor }} (deciso al tavolo)"
            }
        }
        EventType.DAMAGE_ROLLED -> {
            val damageType = detail("type").damageTypeInItalian()
            val recipient = target.takeIf { it.isNotBlank() }?.let { " su $it" }.orEmpty()
            "$actor determina i danni${ability}$recipient: ${details().damageBreakdown()} $damageType"
        }
        EventType.DAMAGE_APPLIED -> if (detail("hitPointsAfter").isNotBlank()) {
            val total = detail("totalAdjusted").ifBlank { detail("adjusted") }
            buildString {
                if (actor.isNotBlank()) append(actor).append(" infligge a ") else append(target).append(" subisce ")
                if (actor.isNotBlank()) append(target).append(' ')
                append(total).append(" danni")
                val temporary = detail("temporaryAbsorbed").toIntOrNull() ?: 0
                val lost = detail("hitPointsLost").toIntOrNull()
                if (temporary > 0) append(" (").append(temporary).append(" assorbiti dai PF temporanei)")
                if (lost != null) append("; PF persi ").append(lost)
                append("; ").append(target).append(" resta a ").append(detail("hitPointsAfter")).append(" PF")
            }
        } else {
            val type = detail("type").damageTypeInItalian()
            val adjustment = when {
                detail("immune") == "true" -> " · immune"
                detail("resistant") == "true" -> " · resistente"
                detail("vulnerable") == "true" -> " · vulnerabile"
                else -> ""
            }
            "$actor applica a $target il danno $type: ${detail("raw")} → ${detail("adjusted")}$adjustment"
        }
        EventType.ZERO_HIT_POINTS -> "$target cade a 0 PF per il danno di $actor"
        EventType.HEALED ->
            "$target recupera ${detail("restored")} PF " +
                "(richiesti ${detail("requested")}, ora ${detail("hitPointsAfter")} PF)"
        EventType.CURRENT_HIT_POINTS_SET ->
            "$target: PF attuali ${detail("before")} → ${detail("after")}" +
                if (detail("zeroMeansDead") == "true") " — morto" else ""
        EventType.TEMPORARY_HIT_POINTS_GRANTED ->
            "$target riceve ${detail("offered")} PF temporanei; " +
                "ne conserva ${detail("retained")} (prima ${detail("before")})"
        EventType.CONDITION_APPLIED -> {
            val condition = detail("condition").ifBlank { detail("type") }.conditionInItalian()
            val source = actor.takeIf { it.isNotBlank() }?.let { " da $it" }.orEmpty()
            val duration = detail("remaining").takeIf { it.isNotBlank() }?.let {
                " · durata residua $it (${detail("expiry").conditionExpiryInItalian()})"
            }.orEmpty()
            "$target diventa $condition$source$duration"
        }
        EventType.CONDITION_REMOVED -> {
            val condition = detail("condition").ifBlank { detail("type") }.conditionInItalian()
            "$target non è più $condition"
        }
        EventType.CONDITION_EXPIRED -> {
            val condition = detail("condition").ifBlank { detail("type") }.conditionInItalian()
            "Su $target scade: $condition"
        }
        EventType.CONDITION_IMMUNE -> {
            val condition = detail("condition").ifBlank { detail("type") }.conditionInItalian()
            "$actor tenta di applicare $condition a $target, ma il bersaglio è immune"
        }
        EventType.CONCENTRATION_STARTED ->
            "$actor inizia a concentrarsi${ability}"
        EventType.CONCENTRATION_CHECKED -> {
            val who = actor.ifBlank { target }
            val dc = detail("difficultyClass").ifBlank { detail("dc") }
            "$who prova a mantenere la concentrazione: $d20 contro CD $dc — " +
                if (detail("maintained") == "true") "mantenuta" else "persa"
        }
        EventType.CONCENTRATION_ENDED -> buildString {
            append(actor).append(" perde la concentrazione").append(ability)
            detail("reason").takeIf { it.isNotBlank() }?.let { append(" (").append(it.reasonInItalian()).append(')') }
        }
        EventType.ENCOUNTER_PAUSED -> "Incontro in pausa"
        EventType.ENCOUNTER_RESUMED -> "Incontro ripreso"
        EventType.ENCOUNTER_RESOLVED -> "Incontro risolto: ${detail("outcome")}"
        // L'annullamento resta scritto nel registro invece di sparire: il log e'
        // append-only, quindi la cronologia mostra anche i ripensamenti del tavolo.
        EventType.UNDO_PERFORMED ->
            "Comando annullato: ripristinata la revisione precedente alla ${detail("revertedRevision")}"

        EventType.DEATH_SAVE_ROLLED -> {
            val who = target.ifBlank { actor }
            when {
                detail("source") == "damage" -> {
                    "$who subisce ${detail("failures")} fallimenti contro morte per il danno di $actor; " +
                        "fallimenti totali ${detail("totalFailures")}"
                }
                detail("outcome") == "natural20" -> "$who tira contro morte: $d20 — recupera 1 PF"
                else -> {
                    "$who tira contro morte: $d20 — " +
                        "${detail("successes").ifBlank { "0" }} successi, " +
                        "${detail("failures").ifBlank { detail("totalFailures") }} fallimenti"
                }
            }
        }

        EventType.STABILIZED -> "${actor.ifBlank { target }} è stabilizzato"
        EventType.KNOCKED_OUT -> "$target messo fuori combattimento a 1 PF"
        EventType.DIED -> buildString {
            val who = target.ifBlank { actor }
            append(who).append(" muore (").append(detail("cause").causeInItalian()).append(')')
            if (actor.isNotBlank() && actor != who) append(" per l'azione di ").append(actor)
        }
        EventType.EXHAUSTION_CHANGED ->
            "$actor: sfinimento ${detail("before")} → ${detail("after")} " +
                "(${detail("d20Penalty")} ai D20, ${detail("speedPenaltyFeet").asDistance()})"

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
            val changes = listOf(
                Triple("CA", detail("previousArmorClass"), detail("armorClass")),
                Triple("PF max", detail("previousMaxHitPoints"), detail("maxHitPoints")),
                Triple("Velocità", detail("previousSpeedFeet"), detail("speedFeet")),
                Triple("Iniziativa", detail("previousInitiativeModifier"), detail("initiativeModifier")),
                Triple("TS Cos", detail("previousConstitutionSaveBonus"), detail("constitutionSaveBonus")),
            ).filter { (_, before, after) -> before != after && after.isNotBlank() }
                .joinToString(", ") { (label, before, after) ->
                    if (label == "Velocità") "$label ${before.asDistance()}→${after.asDistance()}" else "$label $before→$after"
                }

            buildString {
                append("Scheda corretta: ").append(rename)
                if (changes.isNotEmpty()) append(" — ").append(changes)
                append(" [rev. ").append(detail("version")).append(']')
            }
        }
        EventType.COMBATANT_TRANSFORMED ->
            "$actor usa Forma Selvatica: ${detail("previousName")} diventa ${detail("name")}; " +
                "${detail("temporaryHitPoints")} PF temporanei"

        EventType.MAP_CONFIGURED -> buildString {
            append("Mappa ").append(detail("columns")).append('×').append(detail("rows"))
            append(", ").append(detail("feetPerSquare").asDistance()).append(" per casella")
            val dropped = detail("droppedPlacements").toIntOrNull() ?: 0
            if (dropped > 0) append(" — $dropped segnaposti fuori bordo rimossi")
        }

        EventType.MAP_BACKGROUND_SET ->
            if (detail("image").isBlank()) "Sfondo rimosso" else "Sfondo: ${detail("image")}"

        EventType.COMBATANT_PLACED -> "$actor collocato in ${detail("position")}"

        EventType.COMBATANT_MOVED ->
            "$actor si sposta ${detail("from")} → ${detail("to")} " +
                "(${detail("feet").asDistance()}, ne restano ${detail("remainingFeet").asDistance()})"

        EventType.COMBATANT_REMOVED_FROM_MAP -> "$actor tolto dalla mappa"
    }
}

/** Il registro riceve i piedi del motore e li mostra in metri come tutto il resto. */
private fun String.asDistance(): String = toIntOrNull()?.let(::metresLabel) ?: "$this m"

/** Formula completa di un tiro d20, inclusi entrambi i dadi di Vantaggio/Svantaggio. */
private fun Map<String, String>.d20Breakdown(): String {
    val natural = this["natural"].orEmpty()
    val total = this["total"].orEmpty()
    if (natural.isBlank()) return total

    val rolled = this["dice"].orEmpty()
    val die = when (this["mode"]) {
        "ADVANTAGE" -> "d20 $rolled (Vantaggio, scelto $natural)"
        "DISADVANTAGE" -> "d20 $rolled (Svantaggio, scelto $natural)"
        else -> "d20 $natural"
    }
    val modifier = this["modifier"].orEmpty().modifierOperation()
    val source = if (this["source"] == "MANUAL") " · inserito manualmente" else ""
    return buildString {
        append(die)
        if (modifier.isNotBlank()) append(' ').append(modifier)
        if (total.isNotBlank()) append(" = ").append(total)
        append(source)
    }
}

/** Valori effettivi dei dadi di danno, formula originale, modificatore e totale. */
private fun Map<String, String>.damageBreakdown(): String {
    val amount = this["amount"].orEmpty().ifBlank { this["total"].orEmpty() }
    val formula = this["formula"].orEmpty()
    val prefix = formula.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
    return when (this["source"]?.lowercase()) {
        "manual" -> "${prefix}valore inserito $amount"
        "fixed" -> "${prefix}danno fisso $amount"
        else -> {
            val dice = this["dice"].orEmpty()
            val modifier = this["modifier"].orEmpty().modifierOperation()
            buildString {
                append(prefix).append("dadi ").append(dice)
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

private fun String.activationCostInItalian(): String = runCatching {
    when (ActivationCost.valueOf(this)) {
        ActivationCost.ACTION -> "l'azione"
        ActivationCost.BONUS_ACTION -> "l'azione bonus"
        ActivationCost.REACTION -> "la reazione"
        ActivationCost.LEGENDARY_ACTION -> "un'azione leggendaria"
        ActivationCost.NONE -> "un'azione gratuita"
    }
}.getOrDefault(lowercase())

private fun String.conditionInItalian(): String = runCatching {
    ConditionType.valueOf(this).italianLabel.lowercase()
}.getOrDefault(lowercase())

private fun String.damageTypeInItalian(): String = runCatching {
    DamageType.valueOf(this).italianLabel.lowercase()
}.getOrDefault(lowercase())

private fun String.saveAbilityInItalian(): String = when (this) {
    "STRENGTH" -> "Forza"
    "DEXTERITY" -> "Destrezza"
    "CONSTITUTION" -> "Costituzione"
    "INTELLIGENCE" -> "Intelligenza"
    "WISDOM" -> "Saggezza"
    "CHARISMA" -> "Carisma"
    else -> lowercase().ifBlank { "caratteristica non indicata" }
}

private fun String.conditionExpiryInItalian(): String = when (this) {
    "START_OF_TARGET_TURN" -> "inizio turno del bersaglio"
    "END_OF_TARGET_TURN" -> "fine turno del bersaglio"
    "START_OF_SOURCE_TURN" -> "inizio turno della fonte"
    "END_OF_SOURCE_TURN" -> "fine turno della fonte"
    "CONCENTRATION" -> "concentrazione"
    "MANUAL" -> "rimozione manuale"
    else -> lowercase().ifBlank { "scadenza non indicata" }
}

private fun String.reasonInItalian(): String = when (this) {
    "zero hit points" -> "0 PF"
    "failed save" -> "tiro salvezza fallito"
    "replaced" -> "sostituita da un'altra concentrazione"
    "manual" -> "interrotta manualmente"
    "manual current hit points edit" -> "PF impostati manualmente"
    else -> ifBlank { "motivo non indicato" }
}

private fun String.causeInItalian(): String = when (this) {
    "three successes" -> "tre successi"
    "three failures", "death saves" -> "tiri contro morte"
    "massive damage" -> "danno massiccio"
    "exhaustion" -> "sfinimento"
    "manual" -> "stabilizzazione manuale"
    "manual current hit points edit" -> "PF impostati manualmente"
    else -> ifBlank { "causa non specificata" }
}
