package app.d6d.sheet

import kotlinx.serialization.Serializable

/** Le sei caratteristiche, nell'ordine in cui compaiono sulla scheda. */
@Serializable
enum class Ability(val italianLabel: String, val abbreviation: String) {
    STRENGTH("Forza", "FOR"),
    DEXTERITY("Destrezza", "DES"),
    CONSTITUTION("Costituzione", "COS"),
    INTELLIGENCE("Intelligenza", "INT"),
    WISDOM("Saggezza", "SAG"),
    CHARISMA("Carisma", "CAR"),
}

/**
 * Le diciotto abilita' della scheda, ciascuna con la caratteristica che la governa.
 *
 * L'ordine rispetta quello stampato sulla scheda ufficiale italiana, cosi' che
 * l'interfaccia possa elencarle senza riordinarle.
 */
@Serializable
enum class Skill(val ability: Ability, val italianLabel: String) {
    ATLETICA(Ability.STRENGTH, "Atletica"),

    ACROBAZIA(Ability.DEXTERITY, "Acrobazia"),
    FURTIVITA(Ability.DEXTERITY, "Furtivita'"),
    RAPIDITA_DI_MANO(Ability.DEXTERITY, "Rapidita' di mano"),

    ARCANO(Ability.INTELLIGENCE, "Arcano"),
    INDAGARE(Ability.INTELLIGENCE, "Indagare"),
    NATURA(Ability.INTELLIGENCE, "Natura"),
    RELIGIONE(Ability.INTELLIGENCE, "Religione"),
    STORIA(Ability.INTELLIGENCE, "Storia"),

    ADDESTRARE_ANIMALI(Ability.WISDOM, "Addestrare animali"),
    INTUIZIONE(Ability.WISDOM, "Intuizione"),
    MEDICINA(Ability.WISDOM, "Medicina"),
    PERCEZIONE(Ability.WISDOM, "Percezione"),
    SOPRAVVIVENZA(Ability.WISDOM, "Sopravvivenza"),

    INGANNO(Ability.CHARISMA, "Inganno"),
    INTIMIDIRE(Ability.CHARISMA, "Intimidire"),
    INTRATTENERE(Ability.CHARISMA, "Intrattenere"),
    PERSUASIONE(Ability.CHARISMA, "Persuasione"),
    ;

    companion object {
        /** Abilita' governate da una caratteristica, nell'ordine di scheda. */
        fun of(ability: Ability): List<Skill> = entries.filter { it.ability == ability }
    }
}

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
