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
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.EffectCondition
import app.d6d.rules.character.EffectTarget
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.RuleEffect
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

/** Categoria regolamentare dell'armatura realmente indossata. */
@Serializable
enum class ArmorCategory(
    val italianLabel: String,
    val donMinutes: Int,
    val doffMinutes: Int,
) {
    LIGHT("armature leggere", donMinutes = 1, doffMinutes = 1),
    MEDIUM("armature medie", donMinutes = 5, doffMinutes = 1),
    HEAVY("armature pesanti", donMinutes = 10, doffMinutes = 5),
}

/**
 * Eccezioni dell'equipaggiamento magico SRD applicate all'armatura indossata.
 *
 * Il giaco elfico può essere una cotta o un giaco di maglia: con una CA manuale
 * l'utente dichiara esplicitamente la categoria, mentre con i metodi guidati la
 * compatibilità viene ricavata dall'armatura scelta.
 */
@Serializable
enum class ArmorSpecialRule(val italianLabel: String) {
    STANDARD("Armatura normale"),
    MITHRAL("Armatura in mithral"),
    ELVEN_CHAIN("Giaco di maglia elfico"),
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
    val secondaryAbility: Ability? = null,
    val armorCategory: ArmorCategory? = null,
    val minimumStrength: Int = 0,
    val stealthDisadvantage: Boolean = false,
) {
    /** Mantiene esattamente la CA delle schede create prima del calcolo guidato. */
    MANUAL_TOTAL("CA finale manuale", 0, ArmorClassDexterity.NONE),

    UNARMORED("Senza armatura", 10, ArmorClassDexterity.FULL),
    BARBARIAN_UNARMORED(
        "Difesa senza armatura (Barbaro)",
        10,
        ArmorClassDexterity.FULL,
        Ability.CONSTITUTION,
    ),
    MONK_UNARMORED(
        "Difesa senza armatura (Monaco)",
        10,
        ArmorClassDexterity.FULL,
        Ability.WISDOM,
    ),
    DRACONIC_RESILIENCE(
        "Resilienza draconica",
        10,
        ArmorClassDexterity.FULL,
        Ability.CHARISMA,
    ),
    PADDED(
        "Armatura imbottita",
        11,
        ArmorClassDexterity.FULL,
        armorCategory = ArmorCategory.LIGHT,
        stealthDisadvantage = true,
    ),
    LEATHER(
        "Armatura di cuoio",
        11,
        ArmorClassDexterity.FULL,
        armorCategory = ArmorCategory.LIGHT,
    ),
    STUDDED_LEATHER(
        "Cuoio borchiato",
        12,
        ArmorClassDexterity.FULL,
        armorCategory = ArmorCategory.LIGHT,
    ),
    HIDE(
        "Armatura di pelle",
        12,
        ArmorClassDexterity.MAX_TWO,
        armorCategory = ArmorCategory.MEDIUM,
    ),
    CHAIN_SHIRT(
        "Giaco di maglia",
        13,
        ArmorClassDexterity.MAX_TWO,
        armorCategory = ArmorCategory.MEDIUM,
    ),
    SCALE_MAIL(
        "Corazza a scaglie",
        14,
        ArmorClassDexterity.MAX_TWO,
        armorCategory = ArmorCategory.MEDIUM,
        stealthDisadvantage = true,
    ),
    BREASTPLATE(
        "Corazza di piastre",
        14,
        ArmorClassDexterity.MAX_TWO,
        armorCategory = ArmorCategory.MEDIUM,
    ),
    HALF_PLATE(
        "Mezza armatura",
        15,
        ArmorClassDexterity.MAX_TWO,
        armorCategory = ArmorCategory.MEDIUM,
        stealthDisadvantage = true,
    ),
    RING_MAIL(
        "Corazza ad anelli",
        14,
        ArmorClassDexterity.NONE,
        armorCategory = ArmorCategory.HEAVY,
        stealthDisadvantage = true,
    ),
    CHAIN_MAIL(
        "Cotta di maglia",
        16,
        ArmorClassDexterity.NONE,
        armorCategory = ArmorCategory.HEAVY,
        minimumStrength = 13,
        stealthDisadvantage = true,
    ),
    SPLINT(
        "Corazza a strisce",
        17,
        ArmorClassDexterity.NONE,
        armorCategory = ArmorCategory.HEAVY,
        minimumStrength = 15,
        stealthDisadvantage = true,
    ),
    PLATE(
        "Armatura a piastre",
        18,
        ArmorClassDexterity.NONE,
        armorCategory = ArmorCategory.HEAVY,
        minimumStrength = 15,
        stealthDisadvantage = true,
    ),
    MAGE_ARMOR("Armatura magica", 13, ArmorClassDexterity.FULL),

    /** Base scritta dall'utente, con una regola di Destrezza configurabile. */
    CUSTOM_BASE("Base personalizzata", 0, ArmorClassDexterity.NONE),
    ;

    /**
     * Vero quando il metodo descrive un'armatura davvero indossata.
     *
     * Le difese senza armatura e l'Armatura magica non contano: privilegi come lo
     * Stile Difesa chiedono un'armatura addosso, non una CA alta comunque ottenuta.
     */
    val isWornArmor: Boolean get() = armorCategory != null

    val isHeavyArmor: Boolean get() = armorCategory == ArmorCategory.HEAVY
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
) {
    fun includes(category: ArmorCategory): Boolean = when (category) {
        ArmorCategory.LIGHT -> light
        ArmorCategory.MEDIUM -> medium
        ArmorCategory.HEAVY -> heavy
    }
}

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
    /** Caratteristica effettivamente usata dal tiro per colpire. */
    val attackAbility: Ability? = null,
    /** Esplicito anche per capacità private e incantesimi non presenti nel Compendio. */
    val spellOrCantrip: Boolean = false,
    /**
     * Una vecchia riga non offriva metadati sufficienti a distinguere un'arma da
     * un trucchetto d'attacco. Finché non viene riclassificata, non può aggirare
     * le limitazioni dell'armatura indossata senza competenza.
     */
    val legacyClassificationRequired: Boolean = false,
) {
    /** Vero quando questa voce descrive un incantesimo ad area anziché un attacco. */
    val isArea: Boolean get() = areaRadiusFeet > 0
    /** Le vecchie righe ad area erano già definite dall'interfaccia come incantesimi. */
    val isSpellOrCantrip: Boolean get() = spellOrCantrip || isArea
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

/** Riserva di Dadi Vita per una taglia di dado, necessaria per la multiclasse. */
@Serializable
data class HitDicePool(val dieSides: Int, val total: Int, val spent: Int = 0) {
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
    val abilitiesByClass: Map<app.d6d.rules.character.CharacterClassId, Ability> = emptyMap(),
    val slots: List<SpellSlot> = (1..9).map { SpellSlot(it) },
    /** Gli slot della Magia del patto non si sommano agli slot Incantesimi. */
    val pactSlots: SpellSlot? = null,
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
    /**
     * Progressione guidata e versionata. Vuota per le schede create prima del
     * content pack SRD, che continuano a funzionare in modalità manuale.
     */
    val progression: CharacterProgression = CharacterProgression(),

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
    /**
     * Armatura realmente indossata quando la formula non lo può dire da sola.
     *
     * Serve soprattutto alle CA manuali e personalizzate: il totale resta libero,
     * ma competenza, Forza, Furtività e tempi di equipaggiamento non vengono persi.
     */
    val manualArmorCategory: ArmorCategory? = null,
    /** Dettagli dell'armatura quando il metodo di CA è libero e non li può derivare. */
    val manualArmorMinimumStrength: Int = 0,
    val manualArmorStealthDisadvantage: Boolean = false,
    val armorSpecialRule: ArmorSpecialRule = ArmorSpecialRule.STANDARD,
    val armorClassAdjustments: List<ArmorClassAdjustment> = emptyList(),
    /** Correzione eccezionale della CA finale, rimovibile senza perdere la formula. */
    val armorClassOverride: Int? = null,
    val shieldEquipped: Boolean = false,
    /** Resistenze del personaggio trasferite allo snapshot di combattimento. */
    val damageResistances: Set<DamageType> = emptySet(),
    /** Tipo attivo e sostituibile di Resilienza immonda, separato dalle resistenze permanenti. */
    val fiendishResilienceDamageType: DamageType? = null,
    val currentHitPoints: Int = 8,
    val maxHitPoints: Int = 8,
    val temporaryHitPoints: Int = 0,
    val hitDiceMax: Int = 1,
    val hitDiceSpent: Int = 0,
    val hitDieSides: Int = 8,
    val hitDicePools: List<HitDicePool> = emptyList(),
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
    /**
     * Talenti e privilegi della progressione che il tavolo ha escluso manualmente.
     *
     * La progressione e la sua cronologia restano intatte: questo overlay consente
     * di correggere o personalizzare la scheda senza riscrivere retroattivamente i
     * passaggi di livello. Le aggiunte manuali sono normali riferimenti in
     * [abilityIds], così continuano a usare la fonte unica del Compendio.
     */
    val excludedTraitIds: Set<String> = emptySet(),
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

    /** Livello totale SRD; sulle schede legacy conserva il vecchio campo manuale. */
    val effectiveLevel: Int
        get() = progression.totalLevel.takeIf { it > 0 } ?: level.coerceIn(1, 20)

    /**
     * Riferimenti strutturati attivi dopo le personalizzazioni della scheda.
     *
     * Include anche i collegamenti manuali del Compendio; i controlli che cercano
     * un privilegio per ID possono quindi rispettare sia la progressione guidata
     * sia le correzioni fatte direttamente dalla scheda.
     */
    val activeRuleElementIds: List<String>
        get() = (progression.selectedFeatureIds + progression.featIds + abilityIds)
            .distinct()
            .filterNot { it in excludedTraitIds }

    val proficiencyBonus: Int get() = proficiencyBonusForLevel(effectiveLevel)

    /** Livello massimo già raggiungibile con i PE annotati. */
    val experienceEligibleLevel: Int
        get() = ExperienceProgression.levelForExperience(experiencePoints)

    /** Il passaggio di livello è sempre una scelta esplicita, mai automatico. */
    val canLevelUp: Boolean
        get() = progression.configured &&
            effectiveLevel < 20 &&
            experienceEligibleLevel > effectiveLevel

    val nextLevelExperienceThreshold: Int?
        get() = ExperienceProgression.nextThreshold(effectiveLevel)

    val experienceToNextLevel: Int?
        get() = nextLevelExperienceThreshold?.let { (it - experiencePoints).coerceAtLeast(0) }

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
            return base +
                dexterityRule.contribution(modifier(Ability.DEXTERITY)) +
                (armorClassMethod.secondaryAbility?.let(::modifier) ?: 0)
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

    /**
     * Effetti dei privilegi che valgono per come e' equipaggiato il personaggio.
     *
     * Il pacchetto dice quanto e a quale condizione; qui si guarda soltanto se la
     * condizione e' soddisfatta. Con la CA finale scritta a mano non si applica
     * nulla: quel numero e' una dichiarazione dell'utente e va rispettata.
     */
    fun activeEffects(target: EffectTarget): List<RuleEffect> =
        progression.effects.filter { it.target == target && satisfies(it.condition) }

    private fun satisfies(condition: EffectCondition): Boolean = when (condition) {
        EffectCondition.ALWAYS -> true
        EffectCondition.WEARING_ARMOR -> wornArmorCategory != null
        EffectCondition.NOT_WEARING_HEAVY_ARMOR -> wornArmorCategory != ArmorCategory.HEAVY
        EffectCondition.UNARMORED_WITHOUT_SHIELD -> wornArmorCategory == null && !shieldEquipped
    }

    /** Bonus alla CA che arrivano dai privilegi, per esempio lo Stile Difesa. */
    val armorClassEffectBonus: Int
        get() = if (armorClassMethod == ArmorClassMethod.MANUAL_TOTAL) {
            0
        } else {
            activeEffects(EffectTarget.ARMOR_CLASS).sumOf { it.amount }
        }

    /** Velocita' effettiva: quella base piu' i privilegi che la aumentano. */
    val effectiveSpeedFeet: Int
        get() = (
            speedFeet +
                activeEffects(EffectTarget.SPEED_FEET).sumOf { it.amount } -
                armorSpeedPenaltyFeet
            ).coerceAtLeast(0)

    /** Bonus intrinseco del giaco elfico; nella CA manuale è già compreso nel totale. */
    val armorSpecialArmorClassBonus: Int
        get() = if (
            effectiveArmorSpecialRule == ArmorSpecialRule.ELVEN_CHAIN &&
            armorClassMethod != ArmorClassMethod.MANUAL_TOTAL
        ) {
            1
        } else {
            0
        }

    /** Somma di scudo e bonus o penalita' attivi; la CA manuale e' gia' finale. */
    val armorClassAdjustmentTotal: Int
        get() = if (armorClassMethod == ArmorClassMethod.MANUAL_TOTAL) {
            0
        } else {
            shieldArmorClassBonus +
                armorSpecialArmorClassBonus +
                armorClassAdjustments.filter { it.active }.sumOf { it.value } +
                armorClassEffectBonus
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

    /** Categoria effettiva: guidata dal metodo o dichiarata per le CA libere. */
    val wornArmorCategory: ArmorCategory?
        get() = when (armorClassMethod) {
            ArmorClassMethod.MANUAL_TOTAL,
            ArmorClassMethod.CUSTOM_BASE -> manualArmorCategory
            else -> armorClassMethod.armorCategory
        }

    /**
     * Applica una variante soltanto dove può esistere secondo lo SRD.
     *
     * Per le formule libere basta la categoria dichiarata: il nome dell'armatura
     * non è strutturato e la scelta esplicita dell'utente resta autorevole.
     */
    val effectiveArmorSpecialRule: ArmorSpecialRule
        get() = when (armorSpecialRule) {
            ArmorSpecialRule.STANDARD -> ArmorSpecialRule.STANDARD
            ArmorSpecialRule.MITHRAL -> if (
                wornArmorCategory in setOf(ArmorCategory.MEDIUM, ArmorCategory.HEAVY) &&
                armorClassMethod != ArmorClassMethod.HIDE
            ) {
                ArmorSpecialRule.MITHRAL
            } else {
                ArmorSpecialRule.STANDARD
            }
            ArmorSpecialRule.ELVEN_CHAIN -> if (
                armorClassMethod in setOf(ArmorClassMethod.CHAIN_SHIRT, ArmorClassMethod.CHAIN_MAIL) ||
                (
                    armorClassMethod in setOf(
                        ArmorClassMethod.MANUAL_TOTAL,
                        ArmorClassMethod.CUSTOM_BASE,
                    ) &&
                        wornArmorCategory in setOf(ArmorCategory.MEDIUM, ArmorCategory.HEAVY)
                    )
            ) {
                ArmorSpecialRule.ELVEN_CHAIN
            } else {
                ArmorSpecialRule.STANDARD
            }
        }

    /** Requisito dopo le eccezioni: il mithral lo elimina. */
    val effectiveArmorMinimumStrength: Int
        get() = if (
            wornArmorCategory != ArmorCategory.HEAVY ||
            effectiveArmorSpecialRule == ArmorSpecialRule.MITHRAL
        ) {
            0
        } else {
            when (armorClassMethod) {
                ArmorClassMethod.MANUAL_TOTAL,
                ArmorClassMethod.CUSTOM_BASE -> manualArmorMinimumStrength.coerceAtLeast(0)
                else -> armorClassMethod.minimumStrength
            }
        }

    /** Svantaggio intrinseco dopo le eccezioni: il mithral lo elimina. */
    val armorStealthDisadvantage: Boolean
        get() = wornArmorCategory != null &&
            when (armorClassMethod) {
                ArmorClassMethod.MANUAL_TOTAL,
                ArmorClassMethod.CUSTOM_BASE -> manualArmorStealthDisadvantage
                else -> armorClassMethod.stealthDisadvantage
            } &&
            effectiveArmorSpecialRule != ArmorSpecialRule.MITHRAL

    /** Vero se l'armatura scelta richiede un addestramento che il personaggio non possiede. */
    val wearingArmorWithoutTraining: Boolean
        get() = wornArmorCategory?.let { category ->
            effectiveArmorSpecialRule != ArmorSpecialRule.ELVEN_CHAIN &&
                !armorTraining.includes(category)
        } ?: false

    /** La penalità regolamentare coinvolge prove, attacchi e TS basati su Forza o Destrezza. */
    val strengthDexterityD20Disadvantage: Boolean get() = wearingArmorWithoutTraining

    /** Un'armatura rumorosa impone svantaggio a Furtività anche quando si è addestrati. */
    fun hasDisadvantageOnSkill(skill: Skill): Boolean =
        (strengthDexterityD20Disadvantage && skill.ability in STRENGTH_OR_DEXTERITY) ||
            (armorStealthDisadvantage && skill == Skill.FURTIVITA)

    fun hasDisadvantageOnSave(ability: Ability): Boolean =
        strengthDexterityD20Disadvantage && ability in STRENGTH_OR_DEXTERITY

    /** Senza addestramento nell'armatura indossata il lancio è vietato, trucchetti compresi. */
    val spellcastingBlockedByArmor: Boolean get() = wearingArmorWithoutTraining

    /** Requisito di Forza non soddisfatto dall'armatura pesante attualmente indossata. */
    val armorStrengthRequirementNotMet: Boolean
        get() = effectiveArmorMinimumStrength > 0 &&
            score(Ability.STRENGTH) < effectiveArmorMinimumStrength

    /** Lo SRD riduce la velocità di 3 metri, equivalenti a 10 piedi sulla griglia. */
    val armorSpeedPenaltyFeet: Int get() = if (armorStrengthRequirementNotMet) 10 else 0

    /** Minuti SRD necessari a indossare o togliere l'armatura corrente. */
    val armorDonMinutes: Int get() = wornArmorCategory?.donMinutes ?: 0
    val armorDoffMinutes: Int get() = wornArmorCategory?.doffMinutes ?: 0

    val hitDiceRemaining: Int
        get() = if (hitDicePools.isEmpty()) {
            (hitDiceMax - hitDiceSpent).coerceAtLeast(0)
        } else {
            hitDicePools.sumOf { it.remaining }
        }

    val spellSaveDc: Int?
        get() = spellcasting?.let { 8 + proficiencyBonus + modifier(it.ability) }

    val spellAttackBonus: Int?
        get() = spellcasting?.let { proficiencyBonus + modifier(it.ability) }

    /**
     * Numero di attacchi nella singola Azione Attacco. I privilegi omonimi non
     * si sommano in multiclasse; il Guerriero è l'eccezione che sale a tre/quattro.
     */
    val attacksPerAction: Int
        get() {
            val fighterLevel = progression.levelIn(app.d6d.rules.character.CharacterClassId.FIGHTER)
            val fighterThreeExtra = "srd521-it:feature:guerriero:tre-attacchi-extra"
            val fighterTwoExtra = "srd521-it:feature:guerriero:due-attacchi-extra"
            if (
                fighterThreeExtra in activeRuleElementIds ||
                fighterLevel >= 20 && fighterThreeExtra !in excludedTraitIds
            ) {
                return 4
            }
            if (
                fighterTwoExtra in activeRuleElementIds ||
                fighterLevel >= 11 && fighterTwoExtra !in excludedTraitIds ||
                activeRuleElementIds.any {
                    it.endsWith(":lama-divoratrice") || it.endsWith(":devouring-blade")
                }
            ) {
                return 3
            }
            val extraAttackClasses = listOf(
                app.d6d.rules.character.CharacterClassId.FIGHTER,
                app.d6d.rules.character.CharacterClassId.BARBARIAN,
                app.d6d.rules.character.CharacterClassId.MONK,
                app.d6d.rules.character.CharacterClassId.PALADIN,
                app.d6d.rules.character.CharacterClassId.RANGER,
            )
            val hasExtraAttack = extraAttackClasses.any { classId ->
                val featureId = "srd521-it:feature:${classId.contentId}:attacco-extra"
                featureId in activeRuleElementIds ||
                    progression.levelIn(classId) >= 5 && featureId !in excludedTraitIds
            } || activeRuleElementIds.any {
                it.endsWith(":lama-assetata") || it.endsWith(":thirsting-blade")
            }
            return if (hasExtraAttack) 2 else 1
        }

    /**
     * Bonus al tiro per colpire che i privilegi aggiungono a una certa arma.
     *
     * La distinzione fra mischia e distanza segue la portata della riga: oltre i
     * 5 piedi si sta tirando, quindi un giavellotto lanciato beneficia dello
     * Stile Tiro come qualunque altro attacco a distanza.
     */
    fun attackEffectBonus(weapon: WeaponEntry): Int {
        val target = if (weapon.rangeFeet > MELEE_REACH_FEET) {
            EffectTarget.RANGED_ATTACK
        } else {
            EffectTarget.MELEE_ATTACK
        }
        return activeEffects(target).sumOf { it.amount }
    }

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
        val combatAbilities = weapons.mapIndexedNotNull { index, weapon ->
            if (
                weapon.name.isBlank() ||
                (
                    spellcastingBlockedByArmor &&
                        (weapon.isSpellOrCantrip || weapon.legacyClassificationRequired)
                    )
            ) {
                return@mapIndexedNotNull null
            }
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
                    .spellOrCantrip(weapon.isSpellOrCantrip)
                    .rangeFeet(weapon.rangeFeet)
                    .damage(damage)
                    .areaRadiusFeet(weapon.areaRadiusFeet)
                    .saveAbility(weapon.saveAbility?.let { SaveAbility.valueOf(it.name) })
                    .halfOnSave(weapon.halfOnSave)
                    .automationStatus(AutomationStatus.AUTOMATED)
                    .rulesText(weapon.note)
                    .build()
            } else {
                val attackAbility = weapon.attackAbility
                    ?: spellcasting?.ability.takeIf { weapon.isSpellOrCantrip }
                AbilityDefinition.builder("${id}-arma-$index", weapon.name)
                    .version("1.0.0")
                    .source("content-user-private")
                    .rulesetVersion(rulesetVersion)
                    .activationCost(cost)
                    .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                    .attackBonus(weapon.attackBonus + attackEffectBonus(weapon))
                    .attackAbility(attackAbility?.let { SaveAbility.valueOf(it.name) })
                    .spellOrCantrip(weapon.isSpellOrCantrip)
                    .rangeFeet(weapon.rangeFeet)
                    .damage(damage)
                    .automationStatus(AutomationStatus.AUTOMATED)
                    .rulesText(weapon.note)
                    .build()
            }
        }
        val weaponIds = combatAbilities.mapTo(mutableSetOf()) { it.id() }
        val catalogAbilities = abilityIds
            .distinct()
            .mapNotNull { selectedId -> abilityCatalog.firstOrNull { it.id == selectedId } }
            .filterNot { spellcastingBlockedByArmor && it.isSpellOrCantrip }
            .map { ability ->
                val resolved = if (
                    ability.isSpellOrCantrip &&
                    ability.attackAbility == null &&
                    ability.resolutionMethod == ResolutionMethod.ATTACK_ROLL
                ) {
                    ability.copy(attackAbility = spellcasting?.ability)
                } else {
                    ability
                }
                resolved.toDefinition(rulesetVersion)
            }
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
            effectiveSpeedFeet,
            initiativeModifier,
            initiativeScore,
            saveBonus(Ability.CONSTITUTION),
            damageResistances + listOfNotNull(fiendishResilienceDamageType),
            emptySet<DamageType>(),
            emptySet<DamageType>(),
            emptySet<ConditionType>(),
            combatAbilities + catalogAbilities,
            savingThrowBonusMap(::saveBonus),
            spellSaveDc ?: 0,
            attacksPerAction,
            strengthDexterityD20Disadvantage,
        )
    }

    private companion object {
        /** Oltre questa portata la riga descrive un attacco a distanza, non in mischia. */
        const val MELEE_REACH_FEET = 5
        val STRENGTH_OR_DEXTERITY = setOf(Ability.STRENGTH, Ability.DEXTERITY)
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
