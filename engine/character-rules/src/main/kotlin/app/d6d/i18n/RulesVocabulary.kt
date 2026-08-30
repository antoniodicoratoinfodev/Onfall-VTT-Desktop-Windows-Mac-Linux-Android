package app.d6d.i18n

import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.EffectCondition
import app.d6d.rules.character.EffectTarget
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.Skill
import app.d6d.rules.character.WeaponCategory
import app.d6d.rules.character.WeaponProperty
import app.d6d.rules.character.WeaponReach

/**
 * Vocabolario inglese delle regole di creazione e avanzamento.
 *
 * Le voci italiane restano dove sono, come proprieta' costanti degli enum: non
 * sono solo etichette da mostrare, sono anche la chiave con cui i contenuti SRD
 * in italiano vengono ritrovati (vedi `GuidedCharacterService`, che ne ricava uno
 * slug). Spostarle romperebbe l'aggancio ai contenuti; l'inglese si aggiunge qui
 * accanto, in un file solo, dove una revisione della traduzione si legge tutta
 * di seguito invece che sparsa fra le dichiarazioni.
 *
 * I termini sono quelli del System Reference Document 5.2.1 in lingua originale:
 * un tavolo che gioca in inglese deve ritrovare le stesse parole del manuale.
 */

// --- Caratteristiche ---------------------------------------------------------

val Ability.englishLabel: String
    get() = when (this) {
        Ability.STRENGTH -> "Strength"
        Ability.DEXTERITY -> "Dexterity"
        Ability.CONSTITUTION -> "Constitution"
        Ability.INTELLIGENCE -> "Intelligence"
        Ability.WISDOM -> "Wisdom"
        Ability.CHARISMA -> "Charisma"
        else -> italianLabel
    }

/**
 * Sigla inglese della caratteristica.
 *
 * Non deriva dall'etichetta: l'inglese abbrevia Strength in STR e Dexterity in
 * DEX, che non sono le prime tre lettere in nessuna regola meccanica.
 */
val Ability.englishAbbreviation: String
    get() = when (this) {
        Ability.STRENGTH -> "STR"
        Ability.DEXTERITY -> "DEX"
        Ability.CONSTITUTION -> "CON"
        Ability.INTELLIGENCE -> "INT"
        Ability.WISDOM -> "WIS"
        Ability.CHARISMA -> "CHA"
        else -> abbreviation
    }

fun Ability.label(language: AppLanguage): String = language.pick(italianLabel, englishLabel)

fun Ability.abbreviationIn(language: AppLanguage): String =
    language.pick(abbreviation, englishAbbreviation)

// --- Abilita' ----------------------------------------------------------------

val Skill.englishLabel: String
    get() = when (this) {
        Skill.ATLETICA -> "Athletics"
        Skill.ACROBAZIA -> "Acrobatics"
        Skill.FURTIVITA -> "Stealth"
        Skill.RAPIDITA_DI_MANO -> "Sleight of Hand"
        Skill.ARCANO -> "Arcana"
        Skill.INDAGARE -> "Investigation"
        Skill.NATURA -> "Nature"
        Skill.RELIGIONE -> "Religion"
        Skill.STORIA -> "History"
        Skill.ADDESTRARE_ANIMALI -> "Animal Handling"
        Skill.INTUIZIONE -> "Insight"
        Skill.MEDICINA -> "Medicine"
        Skill.PERCEZIONE -> "Perception"
        Skill.SOPRAVVIVENZA -> "Survival"
        Skill.INGANNO -> "Deception"
        Skill.INTIMIDIRE -> "Intimidation"
        Skill.INTRATTENERE -> "Performance"
        Skill.PERSUASIONE -> "Persuasion"
        else -> italianLabel
    }

fun Skill.label(language: AppLanguage): String = language.pick(italianLabel, englishLabel)

// --- Classi ------------------------------------------------------------------

val CharacterClassId.englishLabel: String
    get() = when (this) {
        CharacterClassId.BARBARIAN -> "Barbarian"
        CharacterClassId.BARD -> "Bard"
        CharacterClassId.CLERIC -> "Cleric"
        CharacterClassId.DRUID -> "Druid"
        CharacterClassId.FIGHTER -> "Fighter"
        CharacterClassId.ROGUE -> "Rogue"
        CharacterClassId.WIZARD -> "Wizard"
        CharacterClassId.MONK -> "Monk"
        CharacterClassId.PALADIN -> "Paladin"
        CharacterClassId.RANGER -> "Ranger"
        CharacterClassId.SORCERER -> "Sorcerer"
        CharacterClassId.WARLOCK -> "Warlock"
        else -> contentId.replace('-', ' ').replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
    }

fun CharacterClassId.label(language: AppLanguage): String =
    language.pick(italianLabel, englishLabel)

// --- Tassonomia del Compendio ------------------------------------------------

val RuleElementKind.englishLabel: String
    get() = when (this) {
        RuleElementKind.COMMON_ACTION -> "Common action"
        RuleElementKind.CLASS_FEATURE -> "Class feature"
        RuleElementKind.SUBCLASS_FEATURE -> "Subclass feature"
        RuleElementKind.ORIGIN_FEAT -> "Origin feat"
        RuleElementKind.GENERAL_FEAT -> "General feat"
        RuleElementKind.FIGHTING_STYLE_FEAT -> "Fighting style feat"
        RuleElementKind.EPIC_BOON_FEAT -> "Epic boon feat"
        RuleElementKind.CANTRIP -> "Cantrip"
        RuleElementKind.SPELL -> "Spell"
        RuleElementKind.METAMAGIC -> "Metamagic"
        RuleElementKind.ELDRITCH_INVOCATION -> "Eldritch invocation"
        RuleElementKind.CLASS_OPTION -> "Class option"
        RuleElementKind.CUSTOM -> "Custom"
    }

fun RuleElementKind.label(language: AppLanguage): String =
    language.pick(italianLabel, englishLabel)

val ChoiceKind.englishLabel: String
    get() = when (this) {
        ChoiceKind.SKILL_PROFICIENCY -> "Skill proficiency"
        ChoiceKind.SKILL_OR_TOOL_PROFICIENCY -> "Skill or tool proficiency"
        ChoiceKind.TOOL_PROFICIENCY -> "Tool proficiency"
        ChoiceKind.EXPERTISE -> "Expertise"
        ChoiceKind.WEAPON_MASTERY -> "Weapon mastery"
        ChoiceKind.SUBCLASS -> "Subclass"
        ChoiceKind.CLASS_OPTION -> "Class option"
        ChoiceKind.FIGHTING_STYLE -> "Fighting style"
        ChoiceKind.METAMAGIC -> "Metamagic"
        ChoiceKind.ELDRITCH_INVOCATION -> "Eldritch invocation"
        ChoiceKind.FEAT -> "Feat"
        ChoiceKind.EPIC_BOON -> "Epic boon"
        ChoiceKind.CANTRIP -> "Cantrip"
        ChoiceKind.PREPARED_SPELL -> "Prepared spell"
        ChoiceKind.MAGICAL_DISCOVERY -> "Magical discovery"
        ChoiceKind.ALWAYS_PREPARED_SPELL -> "Always-prepared spell"
        ChoiceKind.SPELLBOOK_SPELL -> "Spellbook spell"
        ChoiceKind.LANGUAGE_PROFICIENCY -> "Language"
        ChoiceKind.SPELLCASTING_ABILITY -> "Spellcasting ability"
        ChoiceKind.SPELL_LIST -> "Spell list"
        ChoiceKind.STARTING_WEAPON -> "Starting weapon"
        ChoiceKind.FEATURE_TARGET -> "Feature target"
        ChoiceKind.ABILITY_SCORE_INCREASE -> "Ability score increase"
        ChoiceKind.BACKGROUND -> "Background"
        ChoiceKind.STARTING_EQUIPMENT -> "Starting equipment"
    }

fun ChoiceKind.label(language: AppLanguage): String = language.pick(italianLabel, englishLabel)

val RecoveryPeriod.englishLabel: String
    get() = when (this) {
        RecoveryPeriod.SHORT_REST -> "Short rest"
        RecoveryPeriod.LONG_REST -> "Long rest"
        RecoveryPeriod.SHORT_OR_LONG_REST -> "Short or long rest"
        RecoveryPeriod.TURN -> "Start of turn"
        RecoveryPeriod.MANUAL -> "Manual"
    }

fun RecoveryPeriod.label(language: AppLanguage): String =
    language.pick(italianLabel, englishLabel)

// --- Armi --------------------------------------------------------------------

val WeaponCategory.englishLabel: String
    get() = when (this) {
        WeaponCategory.SIMPLE -> "Simple"
        WeaponCategory.MARTIAL -> "Martial"
    }

fun WeaponCategory.label(language: AppLanguage): String =
    language.pick(italianLabel, englishLabel)

val WeaponReach.englishLabel: String
    get() = when (this) {
        WeaponReach.MELEE -> "Melee"
        WeaponReach.RANGED -> "Ranged"
    }

fun WeaponReach.label(language: AppLanguage): String = language.pick(italianLabel, englishLabel)

val WeaponProperty.englishLabel: String
    get() = when (this) {
        WeaponProperty.FINESSE -> "Finesse"
        WeaponProperty.LIGHT -> "Light"
        WeaponProperty.THROWN -> "Thrown"
        WeaponProperty.VERSATILE -> "Versatile"
        WeaponProperty.TWO_HANDED -> "Two-Handed"
        WeaponProperty.HEAVY -> "Heavy"
        WeaponProperty.REACH -> "Reach"
        WeaponProperty.AMMUNITION -> "Ammunition"
        WeaponProperty.LOADING -> "Loading"
    }

fun WeaponProperty.label(language: AppLanguage): String =
    language.pick(italianLabel, englishLabel)

// --- Effetti di privilegi e talenti ------------------------------------------

val EffectTarget.englishLabel: String
    get() = when (this) {
        EffectTarget.ARMOR_CLASS -> "Armor Class"
        EffectTarget.SPEED_FEET -> "Speed"
        EffectTarget.MELEE_ATTACK -> "melee attack rolls"
        EffectTarget.RANGED_ATTACK -> "ranged attack rolls"
    }

fun EffectTarget.label(language: AppLanguage): String = language.pick(italianLabel, englishLabel)

val EffectCondition.englishLabel: String
    get() = when (this) {
        EffectCondition.ALWAYS -> ""
        EffectCondition.WEARING_ARMOR -> "while wearing armor"
        EffectCondition.NOT_WEARING_HEAVY_ARMOR -> "while not in heavy armor"
        EffectCondition.UNARMORED_WITHOUT_SHIELD -> "while unarmored and without a shield"
    }

fun EffectCondition.label(language: AppLanguage): String =
    language.pick(italianLabel, englishLabel)
