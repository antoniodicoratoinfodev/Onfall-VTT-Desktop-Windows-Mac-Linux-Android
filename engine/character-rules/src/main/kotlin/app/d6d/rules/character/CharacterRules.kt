package app.d6d.rules.character

import app.d6d.domain.combat.DamageType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Identificatore aperto di caratteristica.
 *
 * Le sei costanti mantengono byte per byte i salvataggi legacy; un regolamento
 * può aggiungere `SANITY`, `user:stat:honor` o qualunque altro ID senza una
 * nuova build dell'app.
 */
@Serializable(with = AbilitySerializer::class)
data class Ability(val value: String) {
    init {
        require(value.isNotBlank()) { "Ability id cannot be blank" }
    }

    val name: String get() = value
    val italianLabel: String
        get() = standardAbilityItalianLabels[this] ?: value.substringAfterLast(':').humanizedRuleId()
    val abbreviation: String
        get() = standardAbbreviations[this]
            ?: value.substringAfterLast(':').filter(Char::isLetter).take(3).uppercase().ifBlank { "—" }
    val ordinal: Int get() = entries.indexOf(this).takeIf { it >= 0 } ?: Int.MAX_VALUE

    companion object {
        val STRENGTH = Ability("STRENGTH")
        val DEXTERITY = Ability("DEXTERITY")
        val CONSTITUTION = Ability("CONSTITUTION")
        val INTELLIGENCE = Ability("INTELLIGENCE")
        val WISDOM = Ability("WISDOM")
        val CHARISMA = Ability("CHARISMA")

        val entries: List<Ability> = listOf(
            STRENGTH,
            DEXTERITY,
            CONSTITUTION,
            INTELLIGENCE,
            WISDOM,
            CHARISMA,
        )

        fun of(raw: String): Ability {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) { "Ability id cannot be blank" }
            return entries.firstOrNull {
                it.value.equals(normalized, ignoreCase = true)
            } ?: Ability(normalized)
        }

        fun valueOf(raw: String): Ability = of(raw)
    }
}

object AbilitySerializer : KSerializer<Ability> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.rules.character.Ability", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Ability) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): Ability = Ability.of(decoder.decodeString())
}

private val standardAbilityItalianLabels = mapOf(
    Ability.STRENGTH to "Forza",
    Ability.DEXTERITY to "Destrezza",
    Ability.CONSTITUTION to "Costituzione",
    Ability.INTELLIGENCE to "Intelligenza",
    Ability.WISDOM to "Saggezza",
    Ability.CHARISMA to "Carisma",
)

private val standardAbbreviations = mapOf(
    Ability.STRENGTH to "FOR",
    Ability.DEXTERITY to "DES",
    Ability.CONSTITUTION to "COS",
    Ability.INTELLIGENCE to "INT",
    Ability.WISDOM to "SAG",
    Ability.CHARISMA to "CAR",
)

/** Identificatore aperto di skill; l'associazione dinamica vive nel content pack. */
@Serializable(with = SkillSerializer::class)
data class Skill(val value: String) {
    init {
        require(value.isNotBlank()) { "Skill id cannot be blank" }
    }

    val name: String get() = value
    val ability: Ability get() = standardSkillAbilities[this] ?: Ability.STRENGTH
    val italianLabel: String get() = standardSkillLabels[this] ?: value.substringAfterLast(':').humanizedRuleId()
    val ordinal: Int get() = entries.indexOf(this).takeIf { it >= 0 } ?: Int.MAX_VALUE

    companion object {
        val ATLETICA = Skill("ATLETICA")
        val ACROBAZIA = Skill("ACROBAZIA")
        val FURTIVITA = Skill("FURTIVITA")
        val RAPIDITA_DI_MANO = Skill("RAPIDITA_DI_MANO")
        val ARCANO = Skill("ARCANO")
        val INDAGARE = Skill("INDAGARE")
        val NATURA = Skill("NATURA")
        val RELIGIONE = Skill("RELIGIONE")
        val STORIA = Skill("STORIA")
        val ADDESTRARE_ANIMALI = Skill("ADDESTRARE_ANIMALI")
        val INTUIZIONE = Skill("INTUIZIONE")
        val MEDICINA = Skill("MEDICINA")
        val PERCEZIONE = Skill("PERCEZIONE")
        val SOPRAVVIVENZA = Skill("SOPRAVVIVENZA")
        val INGANNO = Skill("INGANNO")
        val INTIMIDIRE = Skill("INTIMIDIRE")
        val INTRATTENERE = Skill("INTRATTENERE")
        val PERSUASIONE = Skill("PERSUASIONE")

        val entries: List<Skill> = listOf(
            ATLETICA,
            ACROBAZIA,
            FURTIVITA,
            RAPIDITA_DI_MANO,
            ARCANO,
            INDAGARE,
            NATURA,
            RELIGIONE,
            STORIA,
            ADDESTRARE_ANIMALI,
            INTUIZIONE,
            MEDICINA,
            PERCEZIONE,
            SOPRAVVIVENZA,
            INGANNO,
            INTIMIDIRE,
            INTRATTENERE,
            PERSUASIONE,
        )

        fun of(raw: String): Skill {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) { "Skill id cannot be blank" }
            return entries.firstOrNull {
                it.value.equals(normalized, ignoreCase = true)
            } ?: Skill(normalized)
        }

        fun of(ability: Ability): List<Skill> = entries.filter { it.ability == ability }
        fun valueOf(raw: String): Skill = of(raw)
    }
}

object SkillSerializer : KSerializer<Skill> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.rules.character.Skill", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Skill) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): Skill = Skill.of(decoder.decodeString())
}

private val standardSkillAbilities = mapOf(
    Skill.ATLETICA to Ability.STRENGTH,
    Skill.ACROBAZIA to Ability.DEXTERITY,
    Skill.FURTIVITA to Ability.DEXTERITY,
    Skill.RAPIDITA_DI_MANO to Ability.DEXTERITY,
    Skill.ARCANO to Ability.INTELLIGENCE,
    Skill.INDAGARE to Ability.INTELLIGENCE,
    Skill.NATURA to Ability.INTELLIGENCE,
    Skill.RELIGIONE to Ability.INTELLIGENCE,
    Skill.STORIA to Ability.INTELLIGENCE,
    Skill.ADDESTRARE_ANIMALI to Ability.WISDOM,
    Skill.INTUIZIONE to Ability.WISDOM,
    Skill.MEDICINA to Ability.WISDOM,
    Skill.PERCEZIONE to Ability.WISDOM,
    Skill.SOPRAVVIVENZA to Ability.WISDOM,
    Skill.INGANNO to Ability.CHARISMA,
    Skill.INTIMIDIRE to Ability.CHARISMA,
    Skill.INTRATTENERE to Ability.CHARISMA,
    Skill.PERSUASIONE to Ability.CHARISMA,
)

private val standardSkillLabels = mapOf(
    Skill.ATLETICA to "Atletica",
    Skill.ACROBAZIA to "Acrobazia",
    Skill.FURTIVITA to "Furtività",
    Skill.RAPIDITA_DI_MANO to "Rapidità di mano",
    Skill.ARCANO to "Arcano",
    Skill.INDAGARE to "Indagare",
    Skill.NATURA to "Natura",
    Skill.RELIGIONE to "Religione",
    Skill.STORIA to "Storia",
    Skill.ADDESTRARE_ANIMALI to "Addestrare animali",
    Skill.INTUIZIONE to "Intuizione",
    Skill.MEDICINA to "Medicina",
    Skill.PERCEZIONE to "Percezione",
    Skill.SOPRAVVIVENZA to "Sopravvivenza",
    Skill.INGANNO to "Inganno",
    Skill.INTIMIDIRE to "Intimidire",
    Skill.INTRATTENERE to "Intrattenere",
    Skill.PERSUASIONE to "Persuasione",
)

private fun String.humanizedRuleId(): String =
    replace('-', ' ').replace('_', ' ').trim().lowercase().replaceFirstChar(Char::uppercase)

/** Metadati versionati di una caratteristica aperta consumati dalla scheda. */
@Serializable
data class CharacterStatDefinition(
    val id: Ability,
    val name: String,
    val abbreviation: String,
    val defaultScore: Int = 10,
    val minimumScore: Int = 1,
    val maximumScore: Int = 30,
    /** Limite ordinario degli aumenti; può differire dal massimo assoluto. */
    val advancementMaximum: Int = maximumScore,
    /** Formula generica; `${score}` è il valore inserito nella scheda. */
    val modifierFormula: String = "floor((\${score} - 10) / 2)",
    /** ID della RuleEntity: permette alle formule di referenziare la caratteristica senza dedurla dal nome. */
    val ruleEntityId: String = id.value,
    val rounding: CharacterStatRounding = CharacterStatRounding.NONE,
) {
    init {
        require(name.isNotBlank())
        require(abbreviation.isNotBlank())
        require(minimumScore <= defaultScore)
        require(defaultScore <= maximumScore)
        require(advancementMaximum in minimumScore..maximumScore)
        require(modifierFormula.isNotBlank())
        require(ruleEntityId.isNotBlank())
    }
}

@Serializable
enum class CharacterStatRounding { NONE, FLOOR, CEILING, HALF_UP }

/** Associazione versionata fra una skill aperta e la sua caratteristica. */
@Serializable
data class CharacterSkillDefinition(
    val id: Skill,
    val name: String,
    val statId: Ability,
    val formula: String = "",
    val trainedBonusFormula: String = "\${proficiency}",
    val ruleEntityId: String = id.value,
) {
    init {
        require(name.isNotBlank())
        require(trainedBonusFormula.isNotBlank())
        require(ruleEntityId.isNotBlank())
    }
}

private fun standardCharacterStats(): List<CharacterStatDefinition> = Ability.entries.map { ability ->
    CharacterStatDefinition(ability, ability.italianLabel, ability.abbreviation, advancementMaximum = 20)
}

private fun standardCharacterSkills(): List<CharacterSkillDefinition> = Skill.entries.map { skill ->
    CharacterSkillDefinition(skill, skill.italianLabel, skill.ability)
}

/**
 * Identificatore aperto di classe.
 *
 * Le dodici costanti conservano esattamente i nomi serializzati dal vecchio enum,
 * quindi le schede esistenti continuano a leggere `FIGHTER`, `WIZARD` e simili.
 * Un regolamento può però coniare un ID namespaced nuovo senza ricompilare l'app,
 * per esempio `user:campaign:class:chronomancer`.
 */
@Serializable(with = CharacterClassIdSerializer::class)
data class CharacterClassId(val value: String) {
    init {
        require(value.isNotBlank()) { "Character class id cannot be blank" }
    }

    /** Nome compatibile con l'API del vecchio enum. */
    val name: String get() = value

    /** Slug usato dai contenuti e dai pool di scelta. */
    val contentId: String
        get() = standardContentIds[this] ?: value.substringAfterLast(':').lowercase()

    /** Etichetta legacy; le revisioni usano invece il testo localizzato della RuleEntity. */
    val italianLabel: String
        get() = standardItalianLabels[this] ?: contentId.humanizedClassId()

    /** Ordine stabile delle classi incluse; le classi esterne seguono in ordine lessicografico. */
    val ordinal: Int get() = entries.indexOf(this).takeIf { it >= 0 } ?: Int.MAX_VALUE

    companion object {
        val BARBARIAN = CharacterClassId("BARBARIAN")
        val BARD = CharacterClassId("BARD")
        val CLERIC = CharacterClassId("CLERIC")
        val DRUID = CharacterClassId("DRUID")
        val FIGHTER = CharacterClassId("FIGHTER")
        val ROGUE = CharacterClassId("ROGUE")
        val WIZARD = CharacterClassId("WIZARD")
        val MONK = CharacterClassId("MONK")
        val PALADIN = CharacterClassId("PALADIN")
        val RANGER = CharacterClassId("RANGER")
        val SORCERER = CharacterClassId("SORCERER")
        val WARLOCK = CharacterClassId("WARLOCK")

        /** Compatibilità sorgente con `Enum.entries`: contiene soltanto lo SRD incluso. */
        val entries: List<CharacterClassId> = listOf(
            BARBARIAN,
            BARD,
            CLERIC,
            DRUID,
            FIGHTER,
            ROGUE,
            WIZARD,
            MONK,
            PALADIN,
            RANGER,
            SORCERER,
            WARLOCK,
        )

        /** Accetta nome enum legacy, slug SRD o un nuovo ID namespaced. */
        fun of(raw: String): CharacterClassId {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) { "Character class id cannot be blank" }
            return entries.firstOrNull {
                it.value.equals(normalized, ignoreCase = true) ||
                    it.contentId.equals(normalized, ignoreCase = true)
            } ?: CharacterClassId(normalized)
        }
    }
}

object CharacterClassIdSerializer : KSerializer<CharacterClassId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.rules.character.CharacterClassId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CharacterClassId) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): CharacterClassId = CharacterClassId.of(decoder.decodeString())
}

private val standardContentIds = mapOf(
    CharacterClassId.BARBARIAN to "barbaro",
    CharacterClassId.BARD to "bardo",
    CharacterClassId.CLERIC to "chierico",
    CharacterClassId.DRUID to "druido",
    CharacterClassId.FIGHTER to "guerriero",
    CharacterClassId.ROGUE to "ladro",
    CharacterClassId.WIZARD to "mago",
    CharacterClassId.MONK to "monaco",
    CharacterClassId.PALADIN to "paladino",
    CharacterClassId.RANGER to "ranger",
    CharacterClassId.SORCERER to "stregone",
    CharacterClassId.WARLOCK to "warlock",
)

private val standardItalianLabels = mapOf(
    CharacterClassId.BARBARIAN to "Barbaro",
    CharacterClassId.BARD to "Bardo",
    CharacterClassId.CLERIC to "Chierico",
    CharacterClassId.DRUID to "Druido",
    CharacterClassId.FIGHTER to "Guerriero",
    CharacterClassId.ROGUE to "Ladro",
    CharacterClassId.WIZARD to "Mago",
    CharacterClassId.MONK to "Monaco",
    CharacterClassId.PALADIN to "Paladino",
    CharacterClassId.RANGER to "Ranger",
    CharacterClassId.SORCERER to "Stregone",
    CharacterClassId.WARLOCK to "Warlock",
)

private fun String.humanizedClassId(): String =
    replace('-', ' ').replace('_', ' ').trim().replaceFirstChar { it.uppercase() }

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
    REPLACEMENT_TARGET("Opzione da sostituire"),
    ABILITY_SCORE_INCREASE("Aumento dei punteggi di caratteristica"),
    BACKGROUND("Background"),
    STARTING_EQUIPMENT("Equipaggiamento iniziale"),
}

/** Momento in cui una scelta acquisita può essere sostituita. */
@Serializable
enum class ChoiceReplacementWindow {
    NEVER,
    CLASS_LEVEL_UP,
    SHORT_OR_LONG_REST,
    LONG_REST,
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

/**
 * Formula di competenza fotografata dal regolamento scelto.
 *
 * È intenzionalmente una prima primitiva semplice: sostituisce i due calcoli SRD
 * duplicati senza introdurre ancora l'interprete completo delle formule.
 */
@Serializable
data class ProficiencyProgressionDefinition(
    val base: Int = 2,
    val levelsPerIncrease: Int = 4,
    val maximum: Int = 6,
) {
    init {
        require(levelsPerIncrease > 0)
        require(maximum >= base)
    }

    fun bonus(level: Int): Int =
        minOf(maximum, base + (level.coerceAtLeast(1) - 1) / levelsPerIncrease)
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
    @Serializable(with = CharacterDamageTypeSerializer::class)
    val damageType: DamageType,
    val mastery: String,
    val properties: Set<WeaponProperty> = emptySet(),
    /** Gittata normale delle armi da lancio o con munizioni; 0 per la sola mischia. */
    val normalRangeFeet: Int = 0,
    val longRangeFeet: Int = 0,
    /** Dado usato impugnandola a due mani; 0 quando non è versatile. */
    val versatileDiceSides: Int = 0,
    /** Danno base fisso; se positivo sostituisce i dadi (per esempio la cerbottana). */
    val fixedDamage: Int = 0,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(mastery.isNotBlank())
        require(diceCount >= 1)
        require(diceSides >= 1)
        require(fixedDamage >= 0)
        require(normalRangeFeet >= 0)
        require(longRangeFeet >= normalRangeFeet)
        require(versatileDiceSides == 0 || WeaponProperty.VERSATILE in properties)
        require(fixedDamage == 0 || versatileDiceSides == 0)
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

object CharacterDamageTypeSerializer : KSerializer<DamageType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.domain.combat.DamageType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DamageType) = encoder.encodeString(value.name())
    override fun deserialize(decoder: Decoder): DamageType = DamageType.of(decoder.decodeString())
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
    /** Minimo selezionabile; coincide con [count] salvo le scelte facoltative. */
    val minimumCount: Int = count,
    /** Opzioni finite. Vuoto quando la scelta usa [poolId]. */
    val optionIds: List<String> = emptyList(),
    /** Pool dinamico, per esempio `spells:bardo:cantrip` o `feats:general`. */
    val poolId: String? = null,
    val allowDuplicates: Boolean = false,
    val description: String = "",
    /** Opzione o sottoclasse che deve essere attiva perché questa scelta compaia. */
    val requiredOptionId: String? = null,
    /** Quando una delle opzioni già scelte può essere sostituita. */
    val replacementWindow: ChoiceReplacementWindow = ChoiceReplacementWindow.NEVER,
    /** Scelta acquisita che questo controllo sta sostituendo. */
    val replacesChoiceId: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(count >= 0)
        require(minimumCount in 0..count)
        require(optionIds.isNotEmpty() || !poolId.isNullOrBlank() || count == 0)
        require(requiredOptionId == null || requiredOptionId.isNotBlank())
        require(replacesChoiceId == null || replacesChoiceId.isNotBlank())
    }
}

/** Armature presenti nelle dotazioni iniziali SRD e applicabili senza dipendenze dalla UI. */
@Serializable
enum class StartingArmor {
    LEATHER,
    STUDDED_LEATHER,
    CHAIN_SHIRT,
    CHAIN_MAIL,
}

/** Una delle alternative complete offerte da classe o background alla creazione. */
@Serializable
data class EquipmentPackageDefinition(
    val id: String,
    val name: String,
    val description: String,
    val weaponIds: List<String> = emptyList(),
    val itemNames: List<String> = emptyList(),
    val armor: StartingArmor? = null,
    val shield: Boolean = false,
    val goldPieces: Int = 0,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(description.isNotBlank())
        require(weaponIds.all { it.isNotBlank() })
        require(itemNames.all { it.isNotBlank() })
        require(goldPieces >= 0)
    }
}

/** Background SRD strutturato: aumenti, talento, competenze e dotazione. */
@Serializable
data class BackgroundDefinition(
    val id: String,
    val name: String,
    val abilityOptions: Set<Ability>,
    val featId: String,
    val skillProficiencies: Set<Skill>,
    val toolChoice: ChoiceDefinition,
    val equipmentChoice: ChoiceDefinition,
    /** Lista imposta dal background quando il talento è Iniziato alla magia. */
    val magicInitiateListId: String? = null,
    val description: String = "",
    val sourcePage: Int = 0,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(abilityOptions.size == 3)
        require(featId.isNotBlank())
        require(skillProficiencies.size == 2)
        require(magicInitiateListId == null || magicInitiateListId.isNotBlank())
        require(sourcePage >= 0)
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
        require(level >= 1)
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
        require(availableFromClassLevel >= 1)
        require(requiredOptionId == null || requiredOptionId.isNotBlank())
        require(multiplier >= 1)
        require(minimum >= 0)
        require(shortRestRecovery >= 0)
        require(fullShortRestRecoveryFromLevel >= 0)
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
    /** Alternativa strutturata alla vecchia descrizione testuale della dotazione. */
    val startingEquipmentChoice: ChoiceDefinition? = null,
    /** Scelte concesse entrando nella classe dopo il 1º livello totale. */
    val multiclassSkillChoice: ChoiceDefinition? = null,
    val multiclassToolChoice: ChoiceDefinition? = null,
    val startingEquipment: String = "",
    val multiclassWeaponTraining: String = "",
    val multiclassArmorTraining: ArmorTrainingGrant = ArmorTrainingGrant(),
    val subclassIds: List<String>,
    /** Livello della classe al quale viene effettuata la scelta della sottoclasse. */
    val subclassLevel: Int = 3,
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
        require(hitDieSides >= 2)
        require(fixedHitPointsPerLevel >= 1)
        require(levels.isNotEmpty())
        require(levels.map { it.level } == (1..levels.size).toList())
        require(subclassLevel >= 1)
        require(subclassIds.isEmpty() || subclassLevel <= levels.size)
        require(subclassIds.all { it.isNotBlank() })
        require(subclassIds.distinct().size == subclassIds.size)
        require(spellcastingKind == SpellcastingKind.NONE || spellcastingAbility != null)
    }

    fun level(level: Int): ClassLevelDefinition = levels.first { it.level == level }

    val maximumLevel: Int get() = levels.last().level
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
    /** Opzione o sottoclasse necessaria perché il privilegio sia attivo. */
    val requiredOptionId: String? = null,
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
        require(requiredOptionId == null || requiredOptionId.isNotBlank())
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
    /** Binding esatto del regolamento che ha prodotto questa proiezione. */
    val rulesetProjectId: String = "",
    val rulesetRevisionId: String = "",
    val rulesetCanonicalHash: String = "",
    val rulesetRuntimeHash: String = "",
    val runtimeSemanticsVersion: String = "",
)

@Serializable
data class RulesContentPack(
    val manifest: ContentPackManifest,
    val classes: List<ClassDefinition>,
    val elements: List<RuleElementDefinition>,
    /** Tabella Armi del pacchetto: alimenta le armi iniziali e le loro capacità. */
    val weapons: List<WeaponDefinition> = emptyList(),
    val backgrounds: List<BackgroundDefinition> = emptyList(),
    val equipmentPackages: List<EquipmentPackageDefinition> = emptyList(),
    val proficiencyProgression: ProficiencyProgressionDefinition = ProficiencyProgressionDefinition(),
    val maximumCharacterLevel: Int = 20,
    val enforceExperienceThresholds: Boolean = true,
    /** Caratteristiche e skill aperte della revisione, non un elenco globale dell'app. */
    val stats: List<CharacterStatDefinition> = standardCharacterStats(),
    val skills: List<CharacterSkillDefinition> = standardCharacterSkills(),
    /**
     * Soglie cumulative. Le costruzioni legacy ereditano la curva standard;
     * passare esplicitamente una lista vuota abilita l'avanzamento manuale/milestone.
     */
    val experienceThresholds: List<Int> = ExperienceProgression.thresholds,
) {
    init {
        require(classes.map { it.id }.distinct().size == classes.size)
        require(elements.map { it.id }.distinct().size == elements.size)
        require(weapons.map { it.id }.distinct().size == weapons.size)
        require(backgrounds.map { it.id }.distinct().size == backgrounds.size)
        require(equipmentPackages.map { it.id }.distinct().size == equipmentPackages.size)
        require(stats.map { it.id }.distinct().size == stats.size)
        require(skills.map { it.id }.distinct().size == skills.size)
        require(skills.all { skill -> stats.any { it.id == skill.statId } })
        require(experienceThresholds.zipWithNext().all { (first, second) -> second > first })
        require(experienceThresholds.firstOrNull()?.let { it >= 0 } != false)
        require(maximumCharacterLevel >= 1)
        require(classes.all { it.maximumLevel <= maximumCharacterLevel })
    }

    fun classDefinition(id: CharacterClassId): ClassDefinition =
        classes.first { it.id == id }

    fun element(id: String): RuleElementDefinition? =
        elements.firstOrNull { it.id == id }

    fun weapon(id: String): WeaponDefinition? =
        weapons.firstOrNull { it.id == id }

    fun background(id: String): BackgroundDefinition? =
        backgrounds.firstOrNull { it.id == id }

    fun equipmentPackage(id: String): EquipmentPackageDefinition? =
        equipmentPackages.firstOrNull { it.id == id }

    fun stat(id: Ability): CharacterStatDefinition? = stats.firstOrNull { it.id == id }

    fun skill(id: Skill): CharacterSkillDefinition? = skills.firstOrNull { it.id == id }
}
