package app.d6d.rules.character

import app.d6d.domain.combat.DamageType
import kotlinx.serialization.Serializable

/** Le sei caratteristiche usate dalle regole di creazione e avanzamento. */
@Serializable
enum class Ability(val italianLabel: String, val abbreviation: String) {
    STRENGTH("Forza", "FOR"),
    DEXTERITY("Destrezza", "DES"),
    CONSTITUTION("Costituzione", "COS"),
    INTELLIGENCE("Intelligenza", "INT"),
    WISDOM("Saggezza", "SAG"),
    CHARISMA("Carisma", "CAR"),
}

/** Le diciotto abilità della scheda SRD 5.2.1. */
@Serializable
enum class Skill(val ability: Ability, val italianLabel: String) {
    ATLETICA(Ability.STRENGTH, "Atletica"),

    ACROBAZIA(Ability.DEXTERITY, "Acrobazia"),
    FURTIVITA(Ability.DEXTERITY, "Furtività"),
    RAPIDITA_DI_MANO(Ability.DEXTERITY, "Rapidità di mano"),

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
        fun of(ability: Ability): List<Skill> = entries.filter { it.ability == ability }
    }
}

/** Le dodici classi presenti nel System Reference Document 5.2.1. */
@Serializable
enum class CharacterClassId(val contentId: String, val italianLabel: String) {
    BARBARIAN("barbaro", "Barbaro"),
    BARD("bardo", "Bardo"),
    CLERIC("chierico", "Chierico"),
    DRUID("druido", "Druido"),
    FIGHTER("guerriero", "Guerriero"),
    ROGUE("ladro", "Ladro"),
    WIZARD("mago", "Mago"),
    MONK("monaco", "Monaco"),
    PALADIN("paladino", "Paladino"),
    RANGER("ranger", "Ranger"),
    SORCERER("stregone", "Stregone"),
    WARLOCK("warlock", "Warlock"),
}

/** Tassonomia del Compendio e dei requisiti di scelta. */
@Serializable
enum class RuleElementKind(val italianLabel: String) {
    COMMON_ACTION("Azione comune"),
    CLASS_FEATURE("Privilegio di classe"),
    SUBCLASS_FEATURE("Privilegio di sottoclasse"),
    ORIGIN_FEAT("Talento Origini"),
    GENERAL_FEAT("Talento Generale"),
    FIGHTING_STYLE_FEAT("Talento Stile di combattimento"),
    EPIC_BOON_FEAT("Talento Dono epico"),
    CANTRIP("Trucchetto"),
    SPELL("Incantesimo"),
    METAMAGIC("Metamagia"),
    ELDRITCH_INVOCATION("Supplica occulta"),
    CLASS_OPTION("Opzione di classe"),
    CUSTOM("Personalizzata"),
}

@Serializable
enum class ChoiceKind(val italianLabel: String) {
    SKILL_PROFICIENCY("Competenza in abilità"),
    SKILL_OR_TOOL_PROFICIENCY("Competenza in abilità o strumenti"),
    TOOL_PROFICIENCY("Competenza in strumenti"),
    EXPERTISE("Maestria"),
    WEAPON_MASTERY("Padronanza d'arma"),
    SUBCLASS("Sottoclasse"),
    CLASS_OPTION("Opzione di classe"),
    FIGHTING_STYLE("Stile di combattimento"),
    METAMAGIC("Metamagia"),
    ELDRITCH_INVOCATION("Supplica occulta"),
    FEAT("Talento"),
    EPIC_BOON("Dono epico"),
    CANTRIP("Trucchetto"),
    PREPARED_SPELL("Incantesimo preparato"),
    MAGICAL_DISCOVERY("Scoperta magica"),
    ALWAYS_PREPARED_SPELL("Incantesimo sempre preparato"),
    SPELLBOOK_SPELL("Incantesimo nel libro"),
    LANGUAGE_PROFICIENCY("Lingua"),
    SPELLCASTING_ABILITY("Caratteristica da incantatore"),
    SPELL_LIST("Lista degli incantesimi"),
    STARTING_WEAPON("Arma iniziale"),
    FEATURE_TARGET("Bersaglio del privilegio"),
    ABILITY_SCORE_INCREASE("Aumento dei punteggi di caratteristica"),
}

@Serializable
enum class RecoveryPeriod(val italianLabel: String) {
    SHORT_REST("Riposo breve"),
    LONG_REST("Riposo lungo"),
    SHORT_OR_LONG_REST("Riposo breve o lungo"),
    TURN("Inizio del turno"),
    MANUAL("Manuale"),
}

@Serializable
enum class SpellcastingKind {
    NONE,
    STANDARD,
    HALF_CASTER,
    SPELLBOOK,
    PACT_MAGIC,
}

@Serializable
enum class ResourceFormula {
    TABLE,
    CLASS_LEVEL,
    CLASS_LEVEL_TIMES_MULTIPLIER,
    ABILITY_MODIFIER,
    PROFICIENCY_BONUS,
    FIXED,
}

/** Le due categorie d'arma dello SRD; l'addestramento di classe parte da qui. */
@Serializable
enum class WeaponCategory(val italianLabel: String) {
    SIMPLE("Semplice"),
    MARTIAL("Da guerra"),
}

@Serializable
enum class WeaponReach(val italianLabel: String) {
    MELEE("Mischia"),
    RANGED("A distanza"),
}

/** Proprietà d'arma citate dai requisiti di classe e dalle regole di attacco. */
@Serializable
enum class WeaponProperty(val italianLabel: String) {
    FINESSE("Accurata"),
    LIGHT("Leggera"),
    THROWN("Lancio"),
    VERSATILE("Versatile"),
    TWO_HANDED("Due mani"),
    HEAVY("Pesante"),
    REACH("Portata"),
    AMMUNITION("Munizioni"),
    LOADING("Ricarica"),
}

/**
 * Armi che la classe sa impugnare.
 *
 * Il monaco e il ladro non ottengono tutte le armi da guerra ma solo quelle con
 * certe proprietà, quindi il filtro fa parte del privilegio e non di una nota.
 */
@Serializable
data class WeaponTrainingGrant(
    val categories: Set<WeaponCategory> = emptySet(),
    /** Vuoto = tutte le armi da guerra; altrimenti bastano una di queste proprietà. */
    val martialPropertyFilter: Set<WeaponProperty> = emptySet(),
) {
    val trainsAnyWeapon: Boolean get() = categories.isNotEmpty()

    fun allows(category: WeaponCategory, properties: Set<WeaponProperty>): Boolean = when {
        category !in categories -> false
        category != WeaponCategory.MARTIAL -> true
        martialPropertyFilter.isEmpty() -> true
        else -> properties.any { it in martialPropertyFilter }
    }
}

/**
 * Una riga della tabella Armi, senza nulla di specifico di un content pack.
 *
 * Le gittate sono in piedi come nel resto del motore; per la mischia contano
 * solo tramite [attackRangeFeet].
 */
@Serializable
data class WeaponDefinition(
    val id: String,
    val name: String,
    val category: WeaponCategory,
    val reach: WeaponReach,
    val diceCount: Int,
    val diceSides: Int,
    val damageType: DamageType,
    val mastery: String,
    val properties: Set<WeaponProperty> = emptySet(),
    /** Gittata normale delle armi da lancio o con munizioni; 0 per la sola mischia. */
    val normalRangeFeet: Int = 0,
    val longRangeFeet: Int = 0,
    /** Dado usato impugnandola a due mani; 0 quando non è versatile. */
    val versatileDiceSides: Int = 0,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(mastery.isNotBlank())
        require(diceCount >= 1)
        require(diceSides >= 1)
        require(normalRangeFeet >= 0)
        require(longRangeFeet >= normalRangeFeet)
        require(versatileDiceSides == 0 || WeaponProperty.VERSATILE in properties)
        require(reach == WeaponReach.MELEE || normalRangeFeet > 0)
    }

    /** Distanza a cui si può attaccare: le armi con portata arrivano a 3 metri. */
    val attackRangeFeet: Int
        get() = when {
            reach == WeaponReach.RANGED -> normalRangeFeet
            WeaponProperty.REACH in properties -> 10
            else -> 5
        }

    /** Vero quando è la Destrezza a poter reggere l'attacco al posto della Forza. */
    val usesDexterity: Boolean
        get() = reach == WeaponReach.RANGED || WeaponProperty.FINESSE in properties
}

@Serializable
data class ArmorTrainingGrant(
    val light: Boolean = false,
    val medium: Boolean = false,
    val heavy: Boolean = false,
    val shields: Boolean = false,
)

@Serializable
data class ChoiceDefinition(
    val id: String,
    val title: String,
    val kind: ChoiceKind,
    val count: Int,
    /** Opzioni finite. Vuoto quando la scelta usa [poolId]. */
    val optionIds: List<String> = emptyList(),
    /** Pool dinamico, per esempio `spells:bardo:cantrip` o `feats:general`. */
    val poolId: String? = null,
    val allowDuplicates: Boolean = false,
    val description: String = "",
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(count >= 0)
        require(optionIds.isNotEmpty() || !poolId.isNullOrBlank() || count == 0)
    }
}

@Serializable
data class ResourceMaximum(
    val resourceId: String,
    val maximum: Int,
    val dieSides: Int = 0,
)

/**
 * Statistica su cui un privilegio incide con un numero.
 *
 * Volutamente poche voci: qui entra solo cio' che il tavolo puo' davvero
 * applicare da solo, senza chiedere una decisione a chi gioca. Un privilegio che
 * dipende dalla situazione — l'Ira accesa, un Attacco furtivo dichiarato — non ha
 * un bersaglio qui e resta testo finche' non ci sara' un interruttore al tavolo.
 */
@Serializable
enum class EffectTarget(val italianLabel: String) {
    ARMOR_CLASS("Classe Armatura"),
    SPEED_FEET("Velocità"),
    MELEE_ATTACK("Tiri per colpire in mischia"),
    RANGED_ATTACK("Tiri per colpire a distanza"),
}

/** Quando un effetto vale davvero. */
@Serializable
enum class EffectCondition(val italianLabel: String) {
    ALWAYS(""),
    WEARING_ARMOR("con un'armatura indossata"),
    NOT_WEARING_HEAVY_ARMOR("senza armatura pesante"),
    UNARMORED_WITHOUT_SHIELD("senza armatura né scudo"),
}

/**
 * Effetto numerico di un privilegio o di un talento.
 *
 * E' dato del pacchetto, non codice: aggiungere lo Stile Difesa non richiede di
 * toccare il calcolo della CA, e chi legge la scheda puo' risalire dal numero al
 * privilegio che lo produce.
 */
@Serializable
data class RuleEffect(
    val target: EffectTarget,
    val amount: Int,
    val condition: EffectCondition = EffectCondition.ALWAYS,
    /** Nome da mostrare accanto al numero: e' la risposta a "da dove viene?". */
    val source: String = "",
    /**
     * Effetti progressivi dello stesso gruppo non si sommano: vale il maggiore.
     * Il Movimento senza armatura del monaco passa da +10 a +30 salendo di
     * livello, non arriva a +100.
     */
    val group: String = "",
) {
    init {
        require(source.isNotBlank()) { "Un effetto deve dire da dove viene." }
    }
}

/** Incantesimi concessi automaticamente da un privilegio o da una sottoclasse. */
@Serializable
data class SpellGrant(
    val spellIds: List<String>,
    /** Opzione/sottoclasse che deve risultare selezionata; nullo = concessione incondizionata. */
    val requiredOptionId: String? = null,
) {
    init {
        require(spellIds.isNotEmpty())
        require(spellIds.all { it.isNotBlank() })
        require(requiredOptionId == null || requiredOptionId.isNotBlank())
    }
}

@Serializable
data class ClassLevelDefinition(
    val level: Int,
    val featureIds: List<String> = emptyList(),
    val choices: List<ChoiceDefinition> = emptyList(),
    val cantripsKnown: Int = 0,
    val preparedSpellLimit: Int = 0,
    /** Pool alternativo per i nuovi incantesimi preparati ottenuti a questo livello. */
    val preparedSpellPoolId: String? = null,
    val spellbookAdditions: Int = 0,
    /** Indice zero = slot di 1º livello, fino all'indice otto. */
    val spellSlots: List<Int> = emptyList(),
    val pactSlotLevel: Int = 0,
    val pactSlotCount: Int = 0,
    val spellGrants: List<SpellGrant> = emptyList(),
    val resourceMaximums: List<ResourceMaximum> = emptyList(),
    /** Competenze nei tiri salvezza concesse esattamente a questo livello. */
    val savingThrowProficiencyGrants: Set<Ability> = emptySet(),
    /** Lingue concesse esattamente a questo livello. */
    val languageProficiencyGrants: List<String> = emptyList(),
    /**
     * Effetti numerici che il livello porta con se'. Restano validi ai livelli
     * successivi; quelli progressivi si dichiarano a ogni scalino con lo stesso
     * gruppo, cosi' il piu' alto sostituisce il precedente.
     */
    val effects: List<RuleEffect> = emptyList(),
) {
    init {
        require(level in 1..20)
        require(cantripsKnown >= 0)
        require(preparedSpellLimit >= 0)
        require(preparedSpellPoolId == null || preparedSpellPoolId.isNotBlank())
        require(spellbookAdditions >= 0)
        require(spellSlots.size <= 9)
        require(spellSlots.all { it >= 0 })
        require(pactSlotLevel in 0..5)
        require(pactSlotCount in 0..4)
        require((pactSlotLevel == 0) == (pactSlotCount == 0))
        require(languageProficiencyGrants.all { it.isNotBlank() })
    }
}

@Serializable
data class ResourceDefinition(
    val id: String,
    val name: String,
    val recovery: RecoveryPeriod,
    val formula: ResourceFormula = ResourceFormula.TABLE,
    val multiplier: Int = 1,
    val ability: Ability? = null,
    val minimum: Int = 0,
    /** Utilizzi recuperati con un riposo breve; 0 usa il recupero completo indicato da [recovery]. */
    val shortRestRecovery: Int = 0,
    /** Dal livello indicato il recupero passa da lungo a breve o lungo (per esempio il Bardo al 5º). */
    val fullShortRestRecoveryFromLevel: Int = 0,
    val description: String = "",
    /** Massimo usato da [ResourceFormula.FIXED]. */
    val fixedMaximum: Int = 1,
    /** Livello minimo nella classe che concede la risorsa. */
    val availableFromClassLevel: Int = 1,
    /** Opzione, sottoclasse o supplica che deve risultare selezionata. */
    val requiredOptionId: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(fixedMaximum >= 0)
        require(availableFromClassLevel in 1..20)
        require(requiredOptionId == null || requiredOptionId.isNotBlank())
        require(multiplier >= 1)
        require(minimum >= 0)
        require(shortRestRecovery >= 0)
        require(fullShortRestRecoveryFromLevel in 0..20)
        require(formula != ResourceFormula.ABILITY_MODIFIER || ability != null)
    }
}

@Serializable
data class ClassDefinition(
    val id: CharacterClassId,
    val name: String,
    val primaryAbilities: Set<Ability>,
    /**
     * Ogni insieme è un requisito alternativo (OR); tutti gli insiemi devono
     * essere soddisfatti (AND). Il guerriero usa `{FOR, DES}`, il ranger
     * `{DES}, {SAG}`.
     */
    val multiclassPrerequisiteGroups: List<Set<Ability>> =
        primaryAbilities.map { setOf(it) },
    val hitDieSides: Int,
    val fixedHitPointsPerLevel: Int,
    val savingThrowProficiencies: Set<Ability>,
    val skillChoice: ChoiceDefinition,
    val weaponTraining: String,
    /** Versione selezionabile di [weaponTraining], usata per proporre le armi iniziali. */
    val weaponTrainingGrant: WeaponTrainingGrant = WeaponTrainingGrant(),
    val armorTraining: ArmorTrainingGrant = ArmorTrainingGrant(),
    val toolChoice: ChoiceDefinition? = null,
    /**
     * Armi di partenza, proposte solo al primo livello del personaggio: entrando
     * in una classe per multiclasse non si ricomincia dall'equipaggiamento.
     */
    val startingWeaponChoice: ChoiceDefinition? = null,
    /** Scelte concesse entrando nella classe dopo il 1º livello totale. */
    val multiclassSkillChoice: ChoiceDefinition? = null,
    val multiclassToolChoice: ChoiceDefinition? = null,
    val startingEquipment: String = "",
    val multiclassWeaponTraining: String = "",
    val multiclassArmorTraining: ArmorTrainingGrant = ArmorTrainingGrant(),
    val subclassIds: List<String>,
    val spellcastingAbility: Ability? = null,
    val spellcastingKind: SpellcastingKind = SpellcastingKind.NONE,
    val levels: List<ClassLevelDefinition>,
    val resources: List<ResourceDefinition> = emptyList(),
) {
    init {
        require(name.isNotBlank())
        require(primaryAbilities.isNotEmpty())
        require(multiclassPrerequisiteGroups.isNotEmpty())
        require(multiclassPrerequisiteGroups.all { it.isNotEmpty() })
        require(hitDieSides in setOf(6, 8, 10, 12))
        require(fixedHitPointsPerLevel == hitDieSides / 2 + 1)
        require(levels.map { it.level } == (1..20).toList())
        require(subclassIds.isNotEmpty())
        require(spellcastingKind == SpellcastingKind.NONE || spellcastingAbility != null)
    }

    fun level(level: Int): ClassLevelDefinition = levels.first { it.level == level }
}

@Serializable
data class ClassEligibility(
    val classId: CharacterClassId,
    val minimumLevel: Int = 1,
)

@Serializable
data class SpellDetails(
    val level: Int,
    val school: String,
    val castingTime: String,
    val range: String,
    val components: String,
    val duration: String,
    val ritual: Boolean = false,
    val concentration: Boolean = false,
) {
    init {
        require(level in 0..9)
        require(school.isNotBlank())
    }
}

@Serializable
data class RuleElementDefinition(
    val id: String,
    val name: String,
    val kind: RuleElementKind,
    val description: String,
    val classEligibility: List<ClassEligibility> = emptyList(),
    val spell: SpellDetails? = null,
    val prerequisite: String = "",
    val sourcePage: Int = 0,
    val activation: String = "",
    val resourceId: String? = null,
    val resourceCost: Int = 0,
    val armorTrainingGrant: ArmorTrainingGrant? = null,
    val weaponTrainingGrant: String = "",
    /** Incantesimi resi sempre preparati quando questo elemento è attivo. */
    val grantedSpellIds: List<String> = emptyList(),
    /**
     * Effetti numerici dell'elemento, quando sceglierlo cambia una statistica.
     * Vuoto per tutto cio' che il tavolo applica a mano.
     */
    val effects: List<RuleEffect> = emptyList(),
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(description.isNotBlank())
        require(sourcePage >= 0)
        require(resourceCost >= 0)
        require(grantedSpellIds.all { it.isNotBlank() })
        require((kind == RuleElementKind.CANTRIP || kind == RuleElementKind.SPELL) == (spell != null))
        require(spell?.level != 0 || kind != RuleElementKind.SPELL)
        require(spell?.level == 0 || kind != RuleElementKind.CANTRIP)
    }
}

@Serializable
data class ContentPackManifest(
    val id: String,
    val version: String,
    val rulesetVersion: String,
    val locale: String,
    val title: String,
    val sourceUrl: String,
    val license: String,
    val attribution: String,
)

@Serializable
data class RulesContentPack(
    val manifest: ContentPackManifest,
    val classes: List<ClassDefinition>,
    val elements: List<RuleElementDefinition>,
    /** Tabella Armi del pacchetto: alimenta le armi iniziali e le loro capacità. */
    val weapons: List<WeaponDefinition> = emptyList(),
) {
    init {
        require(classes.map { it.id }.distinct().size == classes.size)
        require(elements.map { it.id }.distinct().size == elements.size)
        require(weapons.map { it.id }.distinct().size == weapons.size)
    }

    fun classDefinition(id: CharacterClassId): ClassDefinition =
        classes.first { it.id == id }

    fun element(id: String): RuleElementDefinition? =
        elements.firstOrNull { it.id == id }

    fun weapon(id: String): WeaponDefinition? =
        weapons.firstOrNull { it.id == id }
}
