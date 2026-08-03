package app.d6d.sheet

import app.d6d.domain.combat.CombatResourceState

const val SPELL_SLOT_RESOURCE_PREFIX = "app.d6d:spell-slot:"
const val PACT_SLOT_RESOURCE_PREFIX = "app.d6d:pact-slot:"

/** Livello dello slot fotografato come risorsa da combattimento, oppure null. */
fun CombatResourceState.spellSlotLevelOrNull(): Int? {
    val suffix = when {
        id().startsWith(SPELL_SLOT_RESOURCE_PREFIX) -> id().removePrefix(SPELL_SLOT_RESOURCE_PREFIX)
        id().startsWith(PACT_SLOT_RESOURCE_PREFIX) -> id().removePrefix(PACT_SLOT_RESOURCE_PREFIX)
        else -> return null
    }
    return suffix.toIntOrNull()?.takeIf { it in 1..9 }
}

fun CombatResourceState.isPactSpellSlot(): Boolean = id().startsWith(PACT_SLOT_RESOURCE_PREFIX)

internal fun SpellSlot.toCombatResource(pact: Boolean = false): CombatResourceState {
    val prefix = if (pact) PACT_SLOT_RESOURCE_PREFIX else SPELL_SLOT_RESOURCE_PREFIX
    val label = if (pact) "Slot del patto" else "Slot incantesimo"
    return CombatResourceState(
        "$prefix$level",
        "$label di ${level}º livello",
        total,
        spent.coerceIn(0, total),
    )
}
