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
import app.d6d.domain.combat.SaveAbility
import kotlinx.serialization.Serializable

/** Competenza nelle armature, come i quattro rombi stampati sulla scheda. */
@Serializable
data class ArmorTraining(
    val light: Boolean = false,
    val medium: Boolean = false,
    val heavy: Boolean = false,
    val shields: Boolean = false,
)

/** Riga della tabella "Armi e trucchetti da combattimento". */
@Serializable
data class WeaponEntry(
    val name: String = "",
    val attackBonus: Int = 0,
    val diceCount: Int = 1,
    val diceSides: Int = 6,
    val damageModifier: Int = 0,
    val damageType: DamageType = DamageType.SLASHING,
    val rangeFeet: Int = 5,
    val note: String = "",
    /** Se vero l'arma/capacita' consuma l'azione bonus nel combattimento. */
    val bonusAction: Boolean = false,
    /**
     * Se maggiore di zero, la capacità è un incantesimo ad area: una sfera di questo
     * raggio (in piedi) centrata su un punto scelto sulla mappa. La Palla di Fuoco ne
     * è l'esempio: raggio 20 piedi.
     */
    val areaRadiusFeet: Int = 0,
    /** Caratteristica del tiro salvezza dell'area; null quando non c'è un TS. */
    val saveAbility: Ability? = null,
    /** Con il tiro salvezza superato la creatura subisce metà danni invece di nessuno. */
    val halfOnSave: Boolean = true,
) {
    /** Vero quando questa voce descrive un incantesimo ad area anziché un attacco. */
    val isArea: Boolean get() = areaRadiusFeet > 0
    /** Colonna "Danno e tipo" nella forma stampata sulla scheda. */
    val damageText: String
        get() = buildString {
            append(diceCount).append('d').append(diceSides)
            if (damageModifier != 0) append(formatModifier(damageModifier))
            append(' ').append(damageType.italianLabel)
        }
}

/** Slot di un singolo livello di incantesimo. */
@Serializable
data class SpellSlot(val level: Int, val total: Int = 0, val spent: Int = 0) {
    val remaining: Int get() = (total - spent).coerceAtLeast(0)
}

/** Riga della tabella "Trucchetti e incantesimi preparati". */
@Serializable
data class SpellEntry(
    val level: Int = 0,
    val name: String = "",
    val castingTime: String = "",
    val range: String = "",
    val concentration: Boolean = false,
    val ritual: Boolean = false,
    val materials: Boolean = false,
    val note: String = "",
)

/** Blocco da incantatore della seconda pagina. */
@Serializable
data class Spellcasting(
    val ability: Ability = Ability.INTELLIGENCE,
    val slots: List<SpellSlot> = (1..9).map { SpellSlot(it) },
    val spells: List<SpellEntry> = emptyList(),
)

/** Denari: rame, argento, elettro, oro e platino. */
@Serializable
data class Money(
    val copper: Int = 0,
    val silver: Int = 0,
    val electrum: Int = 0,
    val gold: Int = 0,
    val platinum: Int = 0,
)

/**
 * Scheda del personaggio 2024.
 *
 * Ricalca i campi della scheda ufficiale italiana. E' il modello di **redazione**:
 * piu' ricco della proiezione da combattimento che usa il motore, che ne viene
 * derivata con [toActorDefinition]. Molti campi (aspetto, storia, equipaggiamento,
 * denari) sono di sola registrazione e non hanno oggi effetto meccanico: restano
 * comunque nel modello perche' servono al tavolo.
 */
@Serializable
data class CharacterSheet(
    val id: String = "pg-nuovo",

    // --- intestazione ---
    val characterName: String = "",
    val background: String = "",
    val className: String = "",
    val subclass: String = "",
    val species: String = "",
    val level: Int = 1,
    val experiencePoints: Int = 0,

    // --- difesa, vita e morte ---
    val armorClass: Int = 10,
    val shieldEquipped: Boolean = false,
    val currentHitPoints: Int = 8,
    val maxHitPoints: Int = 8,
    val temporaryHitPoints: Int = 0,
    val hitDiceMax: Int = 1,
    val hitDiceSpent: Int = 0,
    val hitDieSides: Int = 8,
    val deathSaveSuccesses: Int = 0,
    val deathSaveFailures: Int = 0,

    // --- caratteristiche, tiri salvezza e abilita' ---
    val abilityScores: Map<Ability, Int> = Ability.entries.associateWith { 10 },
    val saveProficiencies: Map<Ability, Proficiency> = emptyMap(),
    val skillProficiencies: Map<Skill, Proficiency> = emptyMap(),

    val heroicInspiration: Boolean = false,

    // --- riga superiore ---
    val speedFeet: Int = 30,
    val size: CreatureSize = CreatureSize.MEDIUM,

    // --- addestramento e competenze ---
    val armorTraining: ArmorTraining = ArmorTraining(),
    val weaponProficiencies: String = "",
    val toolProficiencies: String = "",

    // --- blocchi di testo ---
    val weapons: List<WeaponEntry> = emptyList(),
    val classFeatures: String = "",
    val speciesTraits: String = "",
    val feats: String = "",

    // --- seconda pagina ---
    val spellcasting: Spellcasting? = null,
    val appearance: String = "",
    val backstory: String = "",
    val alignment: String = "",
    val languages: String = "",
    val equipment: String = "",
    val attunements: List<String> = listOf("", "", ""),
    val money: Money = Money(),
) {

    val proficiencyBonus: Int get() = proficiencyBonusForLevel(level)

    fun score(ability: Ability): Int = abilityScores[ability] ?: 10

    fun modifier(ability: Ability): Int = abilityModifier(score(ability))

    /** Tiro salvezza: modificatore piu' il bonus di competenza se competente. */
    fun saveBonus(ability: Ability): Int =
        modifier(ability) + proficiencyBonus * (saveProficiencies[ability] ?: Proficiency.NONE).multiplier

    /** Prova di abilita': la Maestria raddoppia il bonus di competenza. */
    fun skillBonus(skill: Skill): Int =
        modifier(skill.ability) + proficiencyBonus * (skillProficiencies[skill] ?: Proficiency.NONE).multiplier

    /** L'iniziativa e' una prova di Destrezza. */
    val initiativeModifier: Int get() = modifier(Ability.DEXTERITY)

    /** Punteggio statico d'iniziativa: 10 piu' tutti i modificatori. */
    val initiativeScore: Int get() = 10 + initiativeModifier

    val passivePerception: Int get() = 10 + skillBonus(Skill.PERCEZIONE)

    val hitDiceRemaining: Int get() = (hitDiceMax - hitDiceSpent).coerceAtLeast(0)

    val spellSaveDc: Int?
        get() = spellcasting?.let { 8 + proficiencyBonus + modifier(it.ability) }

    val spellAttackBonus: Int?
        get() = spellcasting?.let { proficiencyBonus + modifier(it.ability) }

    /** Un personaggio a 0 PF non e' semplicemente "morto": entra nei tiri contro morte. */
    val unconscious: Boolean get() = currentHitPoints <= 0 && deathSaveFailures < 3

    val dead: Boolean get() = deathSaveFailures >= 3

    /**
     * Proiezione da combattimento consumata dal motore.
     *
     * Trasferisce solo cio' che il motore sa applicare oggi. Le armi diventano
     * capacita' con tiro per colpire; i campi narrativi restano fuori perche' il
     * motore non deve conoscere testi.
     */
    fun toActorDefinition(rulesetVersion: String = "5.2.1"): ActorDefinition {
        val combatAbilities = weapons
            .filter { it.name.isNotBlank() }
            .mapIndexed { index, weapon ->
                val damage = listOf(
                    DamageFormula.dice(
                        weapon.damageType,
                        weapon.diceCount.coerceAtLeast(1),
                        weapon.diceSides.coerceAtLeast(2),
                        weapon.damageModifier,
                    ),
                )
                val cost = if (weapon.bonusAction) ActivationCost.BONUS_ACTION else ActivationCost.ACTION
                if (weapon.isArea) {
                    // Incantesimo ad area: risoluzione con tiro salvezza, non con tiro
                    // per colpire. Il motore lo riconosce dal raggio.
                    AbilityDefinition.builder("${id}-arma-$index", weapon.name)
                        .version("1.0.0")
                        .source("content-user-private")
                        .rulesetVersion(rulesetVersion)
                        .activationCost(cost)
                        .resolutionMethod(ResolutionMethod.SAVING_THROW)
                        .rangeFeet(weapon.rangeFeet)
                        .damage(damage)
                        .areaRadiusFeet(weapon.areaRadiusFeet)
                        .saveAbility(weapon.saveAbility?.let { SaveAbility.valueOf(it.name) })
                        .halfOnSave(weapon.halfOnSave)
                        .automationStatus(AutomationStatus.AUTOMATED)
                        .rulesText(weapon.note)
                        .build()
                } else {
                    AbilityDefinition(
                        "${id}-arma-$index",
                        "1.0.0",
                        "content-user-private",
                        rulesetVersion,
                        weapon.name,
                        cost,
                        ResolutionMethod.ATTACK_ROLL,
                        weapon.attackBonus,
                        weapon.rangeFeet,
                        1,
                        damage,
                        AutomationStatus.AUTOMATED,
                        weapon.note,
                    )
                }
            }

        return ActorDefinition(
            id,
            "1.0.0",
            rulesetVersion,
            characterName.ifBlank { "Senza nome" },
            armorClass,
            maxHitPoints.coerceAtLeast(1),
            currentHitPoints.coerceIn(0, maxHitPoints.coerceAtLeast(1)),
            temporaryHitPoints.coerceAtLeast(0),
            speedFeet,
            initiativeModifier,
            initiativeScore,
            saveBonus(Ability.CONSTITUTION),
            emptySet<DamageType>(),
            emptySet<DamageType>(),
            emptySet<DamageType>(),
            emptySet<ConditionType>(),
            combatAbilities,
            savingThrowBonusMap(::saveBonus),
            spellSaveDc ?: 0,
        )
    }
}

/** Nome italiano dei tipi di danno, usato anche dalla scheda. */
val DamageType.italianLabel: String
    get() = when (this) {
        DamageType.ACID -> "acido"
        DamageType.BLUDGEONING -> "contundente"
        DamageType.COLD -> "freddo"
        DamageType.FIRE -> "fuoco"
        DamageType.FORCE -> "forza"
        DamageType.LIGHTNING -> "fulmine"
        DamageType.NECROTIC -> "necrotico"
        DamageType.PIERCING -> "perforante"
        DamageType.POISON -> "veleno"
        DamageType.PSYCHIC -> "psichico"
        DamageType.RADIANT -> "radiante"
        DamageType.SLASHING -> "tagliente"
        DamageType.THUNDER -> "tuono"
        DamageType.UNTYPED -> "non tipizzato"
    }
