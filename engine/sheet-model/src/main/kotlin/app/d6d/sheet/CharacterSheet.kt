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

/** Quanto il modificatore di Destrezza contribuisce a un calcolo della CA base. */
@Serializable
enum class ArmorClassDexterity(val italianLabel: String) {
    FULL("Destrezza completa"),
    MAX_TWO("Destrezza, massimo +2"),
    NONE("Senza Destrezza"),
    ;

    fun contribution(modifier: Int): Int = when (this) {
        FULL -> modifier
        MAX_TWO -> minOf(modifier, 2)
        NONE -> 0
    }
}

/**
 * Un solo metodo determina la CA base.
 *
 * Le armature non sono bonus sommati alla CA senza armatura: ciascuna sostituisce
 * il metodo base. Scudo, oggetti magici ed effetti sono invece aggiustamenti
 * separati applicati dopo avere scelto questo metodo.
 */
@Serializable
enum class ArmorClassMethod(
    val italianLabel: String,
    val baseValue: Int,
    val dexterity: ArmorClassDexterity,
) {
    /** Mantiene esattamente la CA delle schede create prima del calcolo guidato. */
    MANUAL_TOTAL("CA finale manuale", 0, ArmorClassDexterity.NONE),

    UNARMORED("Senza armatura", 10, ArmorClassDexterity.FULL),
    PADDED("Armatura imbottita", 11, ArmorClassDexterity.FULL),
    LEATHER("Armatura di cuoio", 11, ArmorClassDexterity.FULL),
    STUDDED_LEATHER("Cuoio borchiato", 12, ArmorClassDexterity.FULL),
    HIDE("Armatura di pelle", 12, ArmorClassDexterity.MAX_TWO),
    CHAIN_SHIRT("Giaco di maglia", 13, ArmorClassDexterity.MAX_TWO),
    SCALE_MAIL("Corazza a scaglie", 14, ArmorClassDexterity.MAX_TWO),
    BREASTPLATE("Corazza di piastre", 14, ArmorClassDexterity.MAX_TWO),
    HALF_PLATE("Mezza armatura", 15, ArmorClassDexterity.MAX_TWO),
    RING_MAIL("Corazza ad anelli", 14, ArmorClassDexterity.NONE),
    CHAIN_MAIL("Cotta di maglia", 16, ArmorClassDexterity.NONE),
    SPLINT("Corazza a strisce", 17, ArmorClassDexterity.NONE),
    PLATE("Armatura a piastre", 18, ArmorClassDexterity.NONE),
    MAGE_ARMOR("Armatura magica", 13, ArmorClassDexterity.FULL),

    /** Base scritta dall'utente, con una regola di Destrezza configurabile. */
    CUSTOM_BASE("Base personalizzata", 0, ArmorClassDexterity.NONE),
}

/** Bonus o penalita' applicato dopo il calcolo della CA base. */
@Serializable
data class ArmorClassAdjustment(
    val source: String = "",
    val value: Int = 0,
    val active: Boolean = true,
    /** Identita' stabile per conservare correttamente le bozze dei campi UI. */
    val id: String = "",
)

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
    /**
     * Valore conservato per retrocompatibilita'.
     *
     * In [ArmorClassMethod.MANUAL_TOTAL] e' la CA finale; con
     * [ArmorClassMethod.CUSTOM_BASE] e' invece il valore iniziale della formula.
     * I metodi predefiniti lo ignorano.
     */
    val armorClass: Int = 10,
    val armorClassMethod: ArmorClassMethod = ArmorClassMethod.MANUAL_TOTAL,
    val customArmorClassDexterity: ArmorClassDexterity = ArmorClassDexterity.NONE,
    val armorClassAdjustments: List<ArmorClassAdjustment> = emptyList(),
    /** Correzione eccezionale della CA finale, rimovibile senza perdere la formula. */
    val armorClassOverride: Int? = null,
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
    /** Identificatori delle capacità scelte dal catalogo del Compendio. */
    val abilityIds: List<String> = emptyList(),
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

    /** Risultato del solo metodo di CA scelto, prima di scudo e aggiustamenti. */
    val baseArmorClass: Int
        get() {
            if (armorClassMethod == ArmorClassMethod.MANUAL_TOTAL) return armorClass
            val base = if (armorClassMethod == ArmorClassMethod.CUSTOM_BASE) {
                armorClass
            } else {
                armorClassMethod.baseValue
            }
            val dexterityRule = if (armorClassMethod == ArmorClassMethod.CUSTOM_BASE) {
                customArmorClassDexterity
            } else {
                armorClassMethod.dexterity
            }
            return base + dexterityRule.contribution(modifier(Ability.DEXTERITY))
        }

    /** Bonus dello scudo; il regolamento lo concede solo a chi ne ha competenza. */
    val shieldArmorClassBonus: Int
        get() = if (
            armorClassMethod != ArmorClassMethod.MANUAL_TOTAL &&
            shieldEquipped &&
            armorTraining.shields
        ) {
            2
        } else {
            0
        }

    /** Somma di scudo e bonus o penalita' attivi; la CA manuale e' gia' finale. */
    val armorClassAdjustmentTotal: Int
        get() = if (armorClassMethod == ArmorClassMethod.MANUAL_TOTAL) {
            0
        } else {
            shieldArmorClassBonus +
                armorClassAdjustments.filter { it.active }.sumOf { it.value }
        }

    /** Totale prodotto dalla formula, prima di un eventuale override eccezionale. */
    val calculatedArmorClass: Int get() = baseArmorClass + armorClassAdjustmentTotal

    /** CA effettivamente trasferita al combattimento. */
    val effectiveArmorClass: Int get() = armorClassOverride ?: calculatedArmorClass

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
    fun toActorDefinition(
        rulesetVersion: String = "5.2.1",
        abilityCatalog: List<CatalogAbility> = emptyList(),
    ): ActorDefinition {
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
        val weaponIds = combatAbilities.mapTo(mutableSetOf()) { it.id() }
        val catalogAbilities = abilityIds
            .distinct()
            .mapNotNull { selectedId -> abilityCatalog.firstOrNull { it.id == selectedId } }
            .map { it.toDefinition(rulesetVersion) }
            .filterNot { it.id() in weaponIds }

        return ActorDefinition(
            id,
            "1.0.0",
            rulesetVersion,
            characterName.ifBlank { "Senza nome" },
            effectiveArmorClass,
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
            combatAbilities + catalogAbilities,
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
