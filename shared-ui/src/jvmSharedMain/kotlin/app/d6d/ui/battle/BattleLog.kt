package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.CombatEvent
import app.d6d.domain.combat.EventType
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.state.latest
import app.d6d.ui.theme.Palette

/**
 * Registro degli eventi.
 *
 * Il motore tiene un log append-only: qui viene solo tradotto in italiano. Resta
 * la fonte autorevole di cio' che e' successo, sopra qualsiasi effetto visivo.
 */
@Composable
fun BattleLog(viewModel: BattleViewModel, modifier: Modifier = Modifier, entries: Int = 40) {
    val recent = viewModel.events.latest(entries)
    val listState = rememberLazyListState()

    // Il registro deve mostrare sempre l'ultimo aggiornamento: a ogni nuovo evento
    // torna in cima, anche se il tavolo stava scorrendo indietro nella cronologia.
    LaunchedEffect(viewModel.events.size) {
        if (recent.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Abyss)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Eyebrow("Registro eventi")
            Text(
                text = "${viewModel.events.size} eventi",
                color = Palette.TextFaint,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        // L'ultimo evento resta comunque in evidenza, staccato dallo scorrimento.
        recent.firstOrNull()?.let { latest ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Gold.copy(alpha = 0.09f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ORA",
                    color = Palette.Gold,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = latest.describeInItalian(viewModel),
                    color = Palette.Text,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(recent) { event ->
                LogLine(event, viewModel)
            }
        }
    }
}

@Composable
private fun LogLine(event: CombatEvent, viewModel: BattleViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = "R${event.round()}",
            color = Palette.TextFaint,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = event.describeInItalian(viewModel),
            color = event.type().tint,
            fontWeight = if (event.type() == EventType.CRITICAL_HIT) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val EventType.tint: Color
    get() = when (this) {
        EventType.CRITICAL_HIT -> Palette.Crit
        EventType.ATTACK_HIT, EventType.DAMAGE_APPLIED -> Palette.Text
        EventType.ATTACK_MISSED -> Palette.TextMuted
        EventType.HEALED, EventType.TEMPORARY_HIT_POINTS_GRANTED -> Palette.Heal
        EventType.ZERO_HIT_POINTS -> Palette.Critical
        EventType.CONDITION_APPLIED, EventType.CONDITION_EXPIRED,
        EventType.CONDITION_REMOVED, EventType.CONDITION_IMMUNE -> Palette.Bloodied
        EventType.CONCENTRATION_STARTED, EventType.CONCENTRATION_CHECKED,
        EventType.CONCENTRATION_ENDED -> Palette.Temporary
        EventType.ROUND_STARTED, EventType.TURN_STARTED -> Palette.Gold
        EventType.DEATH_SAVE_ROLLED -> Palette.Bloodied
        EventType.STABILIZED, EventType.KNOCKED_OUT -> Palette.Heal
        EventType.DIED -> Palette.Critical
        EventType.EXHAUSTION_CHANGED -> Palette.Enemy
        EventType.COMBATANT_EDITED -> Palette.Party
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

    return when (type()) {
        EventType.ENCOUNTER_CREATED -> "Incontro creato"
        EventType.COMBATANT_ADDED -> "$actor entra nell'incontro"
        EventType.PARTY_SET -> "Schieramenti dichiarati"
        EventType.ENCOUNTER_READY -> "Incontro pronto"
        EventType.INITIATIVE_SET -> "$actor iniziativa ${detail("total")}"
        EventType.INITIATIVE_ROLLED -> "$actor tira iniziativa ${detail("total")}"
        EventType.INITIATIVE_ORDER_SET -> "Ordine d'iniziativa fissato"
        EventType.ENCOUNTER_STARTED -> "L'incontro comincia"
        EventType.ROUND_STARTED -> "— Round ${detail("round")} —"
        EventType.ROUND_ENDED -> "Fine del round"
        EventType.TURN_STARTED -> "Turno di $actor"
        EventType.TURN_ENDED -> "$actor termina il turno"
        EventType.ACTION_SPENT -> "$actor spende ${detail("cost")}"
        EventType.MOVEMENT_SPENT -> "$actor si muove di ${detail("feet")} piedi"
        EventType.SPELL_SLOT_SPENT -> "$actor consuma uno slot"
        EventType.ATTACK_ROLLED ->
            "$actor attacca $target — tiro ${detail("total")} contro CA ${detail("armorClass")}"
        EventType.ATTACK_MISSED -> "$actor manca $target"
        EventType.ATTACK_HIT -> "$actor colpisce $target"
        EventType.CRITICAL_HIT -> "COLPO CRITICO di $actor su $target"
        EventType.DAMAGE_ROLLED -> "Danno tirato: ${detail("total")}"
        EventType.DAMAGE_APPLIED ->
            "$target subisce ${detail("adjusted")} danni (PF ${detail("hitPointsAfter")})"
        EventType.ZERO_HIT_POINTS -> "$target cade a 0 PF"
        EventType.HEALED -> "$target recupera ${detail("amount")} PF"
        EventType.TEMPORARY_HIT_POINTS_GRANTED -> "$target riceve ${detail("amount")} PF temporanei"
        EventType.CONDITION_APPLIED -> "$target ora e' ${detail("type")}"
        EventType.CONDITION_REMOVED -> "$target non e' piu' ${detail("type")}"
        EventType.CONDITION_EXPIRED -> "Su $target scade ${detail("type")}"
        EventType.CONDITION_IMMUNE -> "$target e' immune a ${detail("type")}"
        EventType.CONCENTRATION_STARTED -> "$actor inizia a concentrarsi"
        EventType.CONCENTRATION_CHECKED ->
            "Concentrazione di $target: CD ${detail("dc")}, " +
                if (detail("maintained") == "true") "mantenuta" else "persa"
        EventType.CONCENTRATION_ENDED -> "$actor perde la concentrazione"
        EventType.ENCOUNTER_PAUSED -> "Incontro in pausa"
        EventType.ENCOUNTER_RESUMED -> "Incontro ripreso"
        EventType.ENCOUNTER_RESOLVED -> "Incontro risolto: ${detail("outcome")}"
        // L'annullamento resta scritto nel registro invece di sparire: il log e'
        // append-only, quindi la cronologia mostra anche i ripensamenti del tavolo.
        EventType.UNDO_PERFORMED -> "↶ Comando annullato"

        EventType.DEATH_SAVE_ROLLED -> when (detail("outcome")) {
            "natural20" -> "$actor: 20 naturale, recupera 1 PF"
            else -> {
                val who = actor.ifBlank { target }
                "$who — tiro contro morte: ${detail("successes").ifBlank { "0" }} successi, " +
                    "${detail("failures").ifBlank { detail("totalFailures") }} fallimenti"
            }
        }

        EventType.STABILIZED -> "${actor.ifBlank { target }} e' stabilizzato"
        EventType.KNOCKED_OUT -> "$target messo fuori combattimento a 1 PF"
        EventType.DIED -> "${actor.ifBlank { target }} muore (${detail("cause")})"
        EventType.EXHAUSTION_CHANGED ->
            "$actor Exhaustion ${detail("before")} → ${detail("after")} " +
                "(${detail("d20Penalty")} ai D20, ${detail("speedPenaltyFeet")} piedi)"

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
                Triple("Velocita'", detail("previousSpeedFeet"), detail("speedFeet")),
                Triple("Iniziativa", detail("previousInitiativeModifier"), detail("initiativeModifier")),
                Triple("TS Cos", detail("previousConstitutionSaveBonus"), detail("constitutionSaveBonus")),
            ).filter { (_, before, after) -> before != after && after.isNotBlank() }
                .joinToString(", ") { (label, before, after) -> "$label $before→$after" }

            buildString {
                append("✎ Scheda corretta: ").append(rename)
                if (changes.isNotEmpty()) append(" — ").append(changes)
                append(" [rev. ").append(detail("version")).append(']')
            }
        }

        EventType.MAP_CONFIGURED -> buildString {
            append("▦ Mappa ").append(detail("columns")).append('×').append(detail("rows"))
            append(", ").append(detail("feetPerSquare")).append(" piedi per casella")
            val dropped = detail("droppedPlacements").toIntOrNull() ?: 0
            if (dropped > 0) append(" — $dropped segnaposti fuori bordo rimossi")
        }

        EventType.MAP_BACKGROUND_SET ->
            if (detail("image").isBlank()) "▦ Sfondo rimosso" else "▦ Sfondo: ${detail("image")}"

        EventType.COMBATANT_PLACED -> "$actor collocato in ${detail("position")}"

        EventType.COMBATANT_MOVED ->
            "$actor si sposta ${detail("from")} → ${detail("to")} " +
                "(${detail("feet")} piedi, ne restano ${detail("remainingFeet")})"

        EventType.COMBATANT_REMOVED_FROM_MAP -> "$actor tolto dalla mappa"
    }
}
