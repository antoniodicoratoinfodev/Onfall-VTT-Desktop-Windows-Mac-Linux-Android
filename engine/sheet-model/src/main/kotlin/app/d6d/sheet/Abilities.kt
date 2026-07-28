package app.d6d.sheet

import app.d6d.rules.character.Ability as RulesAbility
import app.d6d.rules.character.Skill as RulesSkill
import kotlinx.serialization.Serializable

/** Alias compatibili: le regole condivise sono ora la fonte unica di caratteristiche e abilità. */
typealias Ability = RulesAbility
typealias Skill = RulesSkill

/** Grado di competenza in un tiro salvezza o in un'abilita'. */
@Serializable
enum class Proficiency(val italianLabel: String, val multiplier: Int) {
    NONE("Nessuna", 0),
    PROFICIENT("Competente", 1),
    EXPERTISE("Maestria", 2),
}

/** Taglie delle creature. */
@Serializable
enum class CreatureSize(val italianLabel: String) {
    TINY("Minuscola"),
    SMALL("Piccola"),
    MEDIUM("Media"),
    LARGE("Grande"),
    HUGE("Enorme"),
    GARGANTUAN("Mastodontica"),
}

/**
 * Modificatore di caratteristica.
 *
 * La divisione e' arrotondata **per difetto**, non troncata verso lo zero: un
 * punteggio di 7 da' −2, non −1. E' la regola generale del documento, e con la
 * divisione intera di Kotlin sarebbe sbagliata sui punteggi sotto 10.
 */
fun abilityModifier(score: Int): Int = Math.floorDiv(score - 10, 2)

/** Bonus di competenza derivato dal livello totale del personaggio. */
fun proficiencyBonusForLevel(level: Int): Int = when {
    level >= 17 -> 6
    level >= 13 -> 5
    level >= 9 -> 4
    level >= 5 -> 3
    else -> 2
}

/** Formatta un modificatore col segno esplicito, come e' stampato sulla scheda. */
fun formatModifier(value: Int): String = if (value >= 0) "+$value" else "$value"
