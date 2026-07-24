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
import app.d6d.sheet.feetWithMetres
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

    // Segue gli eventi nuovi soltanto quando il tavolo era gia' in cima. Chi sta
    // consultando la cronologia non viene riportato via dal punto che sta leggendo.
    LaunchedEffect(viewModel.events.size) {
        if (recent.isNotEmpty() && listState.firstVisibleItemIndex == 0) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Abyss)
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
        EventType.ACTION_SPENT -> "$actor usa ${detail("cost").activationCostInItalian()}"
        EventType.MOVEMENT_SPENT -> "$actor si muove di ${detail("feet").asFeet()}"
        EventType.SPELL_SLOT_SPENT -> "$actor consuma uno slot"
        EventType.ATTACK_ROLLED ->
            "$actor attacca $target — tiro ${detail("total")} contro CA ${detail("armorClass")}"
        EventType.ATTACK_MISSED -> "$actor manca $target"
        EventType.ATTACK_HIT -> "$actor colpisce $target"
        EventType.CRITICAL_HIT -> "COLPO CRITICO di $actor su $target"
        EventType.AREA_SPELL_CAST ->
            "$actor scatena un'area — raggio ${detail("radiusFeet").asFeet()}, CD ${detail("saveDc")}, " +
                "${detail("targets")} creature nel raggio"
        EventType.SAVING_THROW_ROLLED -> {
            val esito = if (detail("saved") == "true") "supera" else "fallisce"
            val tiro = detail("total")
            if (tiro.isNotBlank()) "$target $esito il tiro salvezza ($tiro contro CD ${detail("dc")})"
            else "$target $esito il tiro salvezza (deciso al tavolo)"
        }
        EventType.DAMAGE_ROLLED -> "Danno tirato: ${detail("total")}"
        EventType.DAMAGE_APPLIED -> if (detail("hitPointsAfter").isNotBlank()) {
            val total = detail("totalAdjusted").ifBlank { detail("adjusted") }
            "$target subisce $total danni (PF ${detail("hitPointsAfter")})"
        } else {
            val type = detail("type").damageTypeInItalian()
            val adjustment = when {
                detail("immune") == "true" -> " · immune"
                detail("resistant") == "true" -> " · resistente"
                detail("vulnerable") == "true" -> " · vulnerabile"
                else -> ""
            }
            "Danno $type: ${detail("raw")} → ${detail("adjusted")}$adjustment"
        }
        EventType.ZERO_HIT_POINTS -> "$target cade a 0 PF"
        EventType.HEALED -> "$target recupera ${detail("restored")} PF"
        EventType.TEMPORARY_HIT_POINTS_GRANTED ->
            "$target ha ${detail("retained")} PF temporanei (offerti ${detail("offered")})"
        EventType.CONDITION_APPLIED -> "$target ora è ${detail("type").conditionInItalian()}"
        EventType.CONDITION_REMOVED -> "$target non è più ${detail("type").conditionInItalian()}"
        EventType.CONDITION_EXPIRED -> "Su $target scade: ${detail("type").conditionInItalian()}"
        EventType.CONDITION_IMMUNE -> "$target è immune a: ${detail("type").conditionInItalian()}"
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
        EventType.UNDO_PERFORMED -> "Comando annullato"

        EventType.DEATH_SAVE_ROLLED -> when (detail("outcome")) {
            "natural20" -> "$actor: 20 naturale, recupera 1 PF"
            else -> {
                val who = actor.ifBlank { target }
                "$who — tiro contro morte: ${detail("successes").ifBlank { "0" }} successi, " +
                    "${detail("failures").ifBlank { detail("totalFailures") }} fallimenti"
            }
        }

        EventType.STABILIZED -> "${actor.ifBlank { target }} è stabilizzato"
        EventType.KNOCKED_OUT -> "$target messo fuori combattimento a 1 PF"
        EventType.DIED -> "${target.ifBlank { actor }} muore (${detail("cause").causeInItalian()})"
        EventType.EXHAUSTION_CHANGED ->
            "$actor: sfinimento ${detail("before")} → ${detail("after")} " +
                "(${detail("d20Penalty")} ai D20, ${detail("speedPenaltyFeet").asFeet()})"

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
                    if (label == "Velocità") "$label ${before.asFeet()}→${after.asFeet()}" else "$label $before→$after"
                }

            buildString {
                append("Scheda corretta: ").append(rename)
                if (changes.isNotEmpty()) append(" — ").append(changes)
                append(" [rev. ").append(detail("version")).append(']')
            }
        }

        EventType.MAP_CONFIGURED -> buildString {
            append("Mappa ").append(detail("columns")).append('×').append(detail("rows"))
            append(", ").append(detail("feetPerSquare").asFeet()).append(" per casella")
            val dropped = detail("droppedPlacements").toIntOrNull() ?: 0
            if (dropped > 0) append(" — $dropped segnaposti fuori bordo rimossi")
        }

        EventType.MAP_BACKGROUND_SET ->
            if (detail("image").isBlank()) "Sfondo rimosso" else "Sfondo: ${detail("image")}"

        EventType.COMBATANT_PLACED -> "$actor collocato in ${detail("position")}"

        EventType.COMBATANT_MOVED ->
            "$actor si sposta ${detail("from")} → ${detail("to")} " +
                "(${detail("feet").asFeet()}, ne restano ${detail("remainingFeet").asFeet()})"

        EventType.COMBATANT_REMOVED_FROM_MAP -> "$actor tolto dalla mappa"
    }
}

private fun String.asFeet(): String = toIntOrNull()?.let(::feetWithMetres) ?: "$this piedi"

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

private fun String.causeInItalian(): String = when (this) {
    "three successes" -> "tre successi"
    "three failures", "death saves" -> "tiri contro morte"
    "massive damage" -> "danno massiccio"
    "exhaustion" -> "sfinimento"
    "manual" -> "stabilizzazione manuale"
    else -> ifBlank { "causa non specificata" }
}
