package app.d6d.sheet

import app.d6d.domain.combat.CombatResourceState
import app.d6d.domain.combat.SpellSlotResourceId

const val SPELL_SLOT_RESOURCE_PREFIX = SpellSlotResourceId.STANDARD_PREFIX
const val PACT_SLOT_RESOURCE_PREFIX = SpellSlotResourceId.PACT_PREFIX

/** Livello dello slot fotografato come risorsa da combattimento, oppure null. */
fun CombatResourceState.spellSlotLevelOrNull(): Int? {
    return SpellSlotResourceId.parse(id()).orElse(null)?.level()
}

fun CombatResourceState.isPactSpellSlot(): Boolean =
    SpellSlotResourceId.parse(id()).orElse(null)?.kind() == SpellSlotResourceId.Kind.PACT

internal fun SpellSlot.toCombatResource(pact: Boolean = false): CombatResourceState {
    val resourceId = if (pact) {
        SpellSlotResourceId.pact(level)
    } else {
        SpellSlotResourceId.standard(level)
    }
    val label = if (pact) "Slot del patto" else "Slot incantesimo"
    return CombatResourceState(
        resourceId.id(),
        "$label di ${level}º livello",
        total,
        spent.coerceIn(0, total),
    )
}
