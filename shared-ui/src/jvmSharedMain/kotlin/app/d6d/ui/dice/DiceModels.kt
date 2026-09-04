package app.d6d.ui.dice

import app.d6d.domain.combat.CombatEvent
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.combat.EventType

/** Se i tiri del motore restano nel registro o passano anche dal vassoio animato. */
enum class DiceRollVisibility {
    HIDDEN,
    VISIBLE,
}

/** Quanto spazio occupa la presentazione visiva di un lancio. */
enum class DiceRollPresentation {
    /** Il vassoio compatto storico, lasciato invariato. */
    STANDARD,

    /** Un dado protagonista in un pannello centrale, con il resto del tavolo ancora visibile. */
    FOREGROUND,
}

/** Materiale visivo applicato a ogni poliedro del vassoio. */
enum class DiceSkinId {
    RUNIC_OBSIDIAN,
    DRAGONFORGE,
    MOON_IVORY,
}

/** Un tiro collegato puo' entrare nel motore; uno libero resta soltanto sul tavolo. */
enum class DiceLinkMode {
    LINKED,
    UNLINKED,
}

/** Motivo semantico del tiro, privo di testo per poter cambiare lingua a vassoio aperto. */
enum class DiceRollPurpose {
    ATTACK,
    DAMAGE,
    SAVING_THROW,
    HEALING,
    CONCENTRATION,
    DEATH_SAVE,
    ABILITY_CHECK,
    INITIATIVE,
    FREE,
}

/** Formula scelta nel vassoio. Il modificatore e' applicato solo ai tiri liberi. */
data class DicePoolSpec(
    val count: Int = 1,
    val sides: Int = 20,
    val modifier: Int = 0,
    val mode: D20Mode = D20Mode.NORMAL,
) {
    init {
        require(count in 1..MAX_FREE_DICE) { "Dice count must be between 1 and $MAX_FREE_DICE" }
        require(sides in SUPPORTED_DICE) { "Unsupported die: d$sides" }
    }

    val notation: String
        get() = buildString {
            append(count).append('d').append(sides)
            if (modifier > 0) append('+').append(modifier)
            if (modifier < 0) append(modifier)
        }

    companion object {
        val SUPPORTED_DICE = listOf(4, 6, 8, 10, 12, 20, 100)
        const val MAX_FREE_DICE = 100
    }
}

/** Un gruppo logico mostrato insieme: per esempio il d20 dell'attacco o 2d8 danni. */
data class PresentedDiceRoll(
    val purpose: DiceRollPurpose,
    val actorId: String = "",
    val targetId: String = "",
    val abilityName: String = "",
    val sides: Int,
    val values: List<Int>,
    val modifier: Int = 0,
    val total: Int,
    val mode: D20Mode = D20Mode.NORMAL,
    val selectedValue: Int? = null,
    /** Nei tiri liberi con vantaggio/svantaggio distingue il pool usato da quello scartato. */
    val kept: Boolean = true,
) {
    init {
        require(sides >= 2)
        require(values.isNotEmpty())
        require(values.all { it in 1..sides })
    }

    val notation: String
        get() = "${values.size}d$sides" + when {
            modifier > 0 -> "+$modifier"
            modifier < 0 -> modifier.toString()
            else -> ""
        }
}

/**
 * Dice effettivamente conservato dopo una scelta con vantaggio o svantaggio.
 * In caso di parita' viene scelto deterministicamente il primo, come fa il motore.
 */
internal fun PresentedDiceRoll.keepsDieAt(index: Int): Boolean {
    if (!kept || index !in values.indices) return false
    if (mode == D20Mode.NORMAL || selectedValue == null) return true
    val selectedIndex = values.indexOfFirst { it == selectedValue }
    return selectedIndex < 0 || index == selectedIndex
}

/** Stato transitorio di un'azione che non ha ancora toccato la sessione viva. */
data class PendingLinkedRoll(
    val id: Long,
    val baseRevision: Long,
    val baseRandomState: Long,
    val rolls: List<PresentedDiceRoll>,
    val started: Boolean = false,
)

/** Ultimo lancio del vassoio, collegato oppure libero. */
data class DiceTrayResult(
    val id: Long,
    val linkMode: DiceLinkMode,
    val rolls: List<PresentedDiceRoll>,
)

/** Estrae dal registro solo gli eventi che rappresentano un lancio realmente avvenuto. */
fun presentedRollsFromEvents(events: List<CombatEvent>): List<PresentedDiceRoll> =
    events.mapNotNull(::presentedRollFromEvent)

private fun presentedRollFromEvent(event: CombatEvent): PresentedDiceRoll? {
    val purpose = when (event.type()) {
        EventType.ATTACK_ROLLED -> DiceRollPurpose.ATTACK
        EventType.DAMAGE_ROLLED -> DiceRollPurpose.DAMAGE
        EventType.SAVING_THROW_ROLLED -> DiceRollPurpose.SAVING_THROW
        EventType.HEALED -> DiceRollPurpose.HEALING
        EventType.CONCENTRATION_CHECKED -> DiceRollPurpose.CONCENTRATION
        EventType.DEATH_SAVE_ROLLED -> DiceRollPurpose.DEATH_SAVE
        EventType.ABILITY_CHECK_ROLLED -> DiceRollPurpose.ABILITY_CHECK
        EventType.INITIATIVE_ROLLED -> DiceRollPurpose.INITIATIVE
        else -> return null
    }
    val details = event.details()
    val values = details["dice"].parseDiceValues()
    if (values.isEmpty()) return null
    val sides = when (purpose) {
        DiceRollPurpose.ATTACK,
        DiceRollPurpose.SAVING_THROW,
        DiceRollPurpose.CONCENTRATION,
        DiceRollPurpose.DEATH_SAVE,
        DiceRollPurpose.ABILITY_CHECK,
        DiceRollPurpose.INITIATIVE,
        -> 20

        else -> details["formula"].orEmpty().diceSidesOrNull()
            ?: values.maxOrNull()?.coerceAtLeast(2)
            ?: return null
    }
    return PresentedDiceRoll(
        purpose = purpose,
        actorId = event.actorId(),
        targetId = event.targetId(),
        abilityName = details["abilityName"].orEmpty(),
        sides = sides,
        values = values,
        modifier = details["modifier"]?.toIntOrNull() ?: 0,
        total = details["total"]?.toIntOrNull()
            ?: details["requested"]?.toIntOrNull()
            ?: details["amount"]?.toIntOrNull()
            ?: (values.sum() + (details["modifier"]?.toIntOrNull() ?: 0)),
        mode = details["mode"]
            ?.let { runCatching { D20Mode.valueOf(it) }.getOrNull() }
            ?: D20Mode.NORMAL,
        selectedValue = details["natural"]?.toIntOrNull(),
    )
}

private fun String?.parseDiceValues(): List<Int> = this
    ?.removePrefix("[")
    ?.removeSuffix("]")
    ?.split(',')
    ?.mapNotNull { it.trim().toIntOrNull() }
    .orEmpty()

private fun String.diceSidesOrNull(): Int? {
    val d = indexOf('d', ignoreCase = true)
    if (d < 0) return null
    return substring(d + 1).takeWhile(Char::isDigit).toIntOrNull()
}
