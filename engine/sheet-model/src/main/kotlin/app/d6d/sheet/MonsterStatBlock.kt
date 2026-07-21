@file:UseSerializers(DamageTypeSerializer::class, ConditionTypeSerializer::class)

package app.d6d.sheet

import kotlinx.serialization.UseSerializers
import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageFormula
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.ResolutionMethod
import kotlinx.serialization.Serializable

/** Velocita' del mostro, con le cinque forme di movimento piu' la fluttuazione. */
@Serializable
data class MonsterSpeeds(
    val walk: Int = 30,
    val fly: Int = 0,
    val swim: Int = 0,
    val climb: Int = 0,
    val burrow: Int = 0,
    val hover: Boolean = false,
) {
    /** Riga "Velocita'" come appare nello stat block. */
    val text: String
        get() = buildString {
            append("${walk} ft.")
            if (fly > 0) append(", Volo $fly ft.").also { if (hover) append(" (fluttua)") }
            if (swim > 0) append(", Nuoto $swim ft.")
            if (climb > 0) append(", Scalata $climb ft.")
            if (burrow > 0) append(", Scavo $burrow ft.")
        }
}

/**
 * Voce di una sezione operativa dello stat block.
 *
 * Se [attack] e' valorizzata, la voce e' anche un attacco eseguibile dal motore;
 * altrimenti resta testo che il DM applica a mano. Il documento chiede che una
 * capacita' non automatizzata sia uno stato dichiarato, non un silenzio.
 */
@Serializable
data class StatBlockEntry(
    val name: String = "",
    val text: String = "",
    val attack: WeaponEntry? = null,
) {
    val automated: Boolean get() = attack != null
}

/**
 * Stat block del mostro nel formato 2024/2025.
 *
 * E' la versione ridotta della scheda: un mostro non ha background, talenti,
 * denari o Dadi Vita spendibili, ma ha Grado di Sfida, XP, sezioni operative
 * separate e XP alternativi in tana.
 */
@Serializable
data class MonsterStatBlock(
    val id: String = "mostro-nuovo",
    val name: String = "",

    // --- intestazione ---
    val size: CreatureSize = CreatureSize.MEDIUM,
    val type: String = "",
    val tags: String = "",
    val alignment: String = "",

    // --- difesa e iniziativa ---
    val armorClass: Int = 12,
    val initiativeModifier: Int = 0,
    /**
     * Punteggio statico, tenuto come campo distinto dal modificatore: lo stat
     * block aggiornato li riporta entrambi e non vanno ricavati l'uno dall'altro.
     */
    val initiativeScore: Int = 10,

    // --- punti ferita ---
    val averageHitPoints: Int = 10,
    val hitDiceCount: Int = 2,
    val hitDiceSides: Int = 8,
    val hitDiceModifier: Int = 0,

    val speeds: MonsterSpeeds = MonsterSpeeds(),

    // --- caratteristiche ---
    val abilityScores: Map<Ability, Int> = Ability.entries.associateWith { 10 },
    val saveProficiencies: Map<Ability, Proficiency> = emptyMap(),
    val skillProficiencies: Map<Skill, Proficiency> = emptyMap(),

    // --- difese tipizzate ---
    val resistances: Set<DamageType> = emptySet(),
    val vulnerabilities: Set<DamageType> = emptySet(),
    val damageImmunities: Set<DamageType> = emptySet(),
    val conditionImmunities: Set<ConditionType> = emptySet(),

    /** Oggetti significativi recuperabili: non e' tutto cio' che il mostro indossa. */
    val gear: String = "",
    val senses: String = "",
    val languages: String = "",

    // --- sfida ---
    val challengeRating: String = "1",
    val baseXp: Long = 200,
    /** XP alternativi quando il mostro combatte nella propria tana. */
    val lairXp: Long? = null,
    val proficiencyBonus: Int = 2,

    // --- sezioni operative ---
    val traits: List<StatBlockEntry> = emptyList(),
    val actions: List<StatBlockEntry> = emptyList(),
    val bonusActions: List<StatBlockEntry> = emptyList(),
    val reactions: List<StatBlockEntry> = emptyList(),
    val legendaryActions: List<StatBlockEntry> = emptyList(),

    // --- metadati di biblioteca, separati dallo stato di combattimento ---
    val habitat: String = "",
    val treasureTheme: String = "",
) {

    fun score(ability: Ability): Int = abilityScores[ability] ?: 10

    fun modifier(ability: Ability): Int = abilityModifier(score(ability))

    fun saveBonus(ability: Ability): Int =
        modifier(ability) + proficiencyBonus * (saveProficiencies[ability] ?: Proficiency.NONE).multiplier

    fun skillBonus(skill: Skill): Int =
        modifier(skill.ability) + proficiencyBonus * (skillProficiencies[skill] ?: Proficiency.NONE).multiplier

    val passivePerception: Int get() = 10 + skillBonus(Skill.PERCEZIONE)

    /** Riga dei PF come stampata: "45 (7d8 + 14)". */
    val hitPointsText: String
        get() = buildString {
            append(averageHitPoints).append(" (").append(hitDiceCount).append('d').append(hitDiceSides)
            if (hitDiceModifier != 0) append(' ').append(formatModifier(hitDiceModifier).replace("+", "+ ").replace("-", "- "))
            append(')')
        }

    /** Intestazione: "Media Umanoide (goblinoide), Neutrale Malvagio". */
    val subtitle: String
        get() = buildString {
            append(size.italianLabel)
            if (type.isNotBlank()) append(' ').append(type)
            if (tags.isNotBlank()) append(" (").append(tags).append(')')
            if (alignment.isNotBlank()) append(", ").append(alignment)
        }

    /** XP applicabili nel contesto scelto: in tana vale il valore alternativo, se esiste. */
    fun xpFor(inLair: Boolean): Long = if (inLair) lairXp ?: baseXp else baseXp

    /**
     * Proiezione da combattimento.
     *
     * Solo le voci con dati d'attacco strutturati diventano capacita' eseguibili:
     * le altre restano testo per il DM e vengono marcate come da risolvere a mano,
     * cosi' il motore non le ignora in silenzio.
     */
    fun toActorDefinition(rulesetVersion: String = "5.2.1"): ActorDefinition {
        val sections = listOf(
            actions to ActivationCost.ACTION,
            bonusActions to ActivationCost.BONUS_ACTION,
            reactions to ActivationCost.REACTION,
            legendaryActions to ActivationCost.LEGENDARY_ACTION,
        )

        val abilities = sections.flatMap { (entries, cost) ->
            entries.filter { it.name.isNotBlank() }.mapIndexed { index, entry ->
                val attack = entry.attack
                AbilityDefinition(
                    "$id-${cost.name.lowercase()}-$index",
                    "1.0.0",
                    "content-user-private",
                    rulesetVersion,
                    entry.name,
                    cost,
                    if (attack != null) ResolutionMethod.ATTACK_ROLL else ResolutionMethod.MANUAL,
                    attack?.attackBonus ?: 0,
                    attack?.rangeFeet ?: 5,
                    1,
                    if (attack != null) {
                        listOf(
                            DamageFormula.dice(
                                attack.damageType,
                                attack.diceCount.coerceAtLeast(1),
                                attack.diceSides.coerceAtLeast(2),
                                attack.damageModifier,
                            ),
                        )
                    } else {
                        emptyList()
                    },
                    if (attack != null) AutomationStatus.AUTOMATED else AutomationStatus.MANUAL_REQUIRED,
                    entry.text,
                )
            }
        }

        return ActorDefinition(
            id,
            "1.0.0",
            rulesetVersion,
            name.ifBlank { "Creatura senza nome" },
            armorClass,
            averageHitPoints.coerceAtLeast(1),
            averageHitPoints.coerceAtLeast(1),
            0,
            speeds.walk,
            initiativeModifier,
            initiativeScore,
            saveBonus(Ability.CONSTITUTION),
            resistances,
            vulnerabilities,
            damageImmunities,
            conditionImmunities,
            abilities,
        )
    }
}

/**
 * Bonus di competenza suggerito dal Grado di Sfida.
 *
 * E' solo un suggerimento per l'editor: lo stat block riporta il proprio valore,
 * che resta modificabile.
 */
fun suggestedProficiencyBonus(challengeRating: Double): Int = when {
    challengeRating >= 29 -> 9
    challengeRating >= 25 -> 8
    challengeRating >= 21 -> 7
    challengeRating >= 17 -> 6
    challengeRating >= 13 -> 5
    challengeRating >= 9 -> 4
    challengeRating >= 5 -> 3
    else -> 2
}
