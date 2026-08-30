package app.d6d.i18n

import app.d6d.domain.campaign.ActorKind
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.HealingTarget
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.domain.combat.SaveAbility

/**
 * Vocabolario del combattimento nelle due lingue.
 *
 * Il modello di dominio e' in Java e non conosce alcuna lingua: i suoi enum sono
 * nomi in inglese *tecnico*, buoni per un `when` e per il registro del motore, non
 * per essere mostrati. Qui vengono resi in parole da leggere, e questo e' l'unico
 * posto dove farlo — prima le stesse quindici condizioni erano tradotte in tre
 * file diversi, e bastava aggiungerne una per lasciarne indietro due.
 *
 * Ogni voce ha due forme, e la differenza non e' cosmetica:
 *
 *  - [label] e' la forma da vetrina: apre in maiuscolo perche' sta da sola, in una
 *    pillola, in un elenco, in un'intestazione di colonna.
 *  - [inlineLabel] e' la forma da frase: minuscola, perche' finisce dentro un
 *    periodo gia' cominciato («subisce 7 danni da fuoco»).
 *
 * Vive nel modulo delle regole e non nell'interfaccia perche' anche la scheda e lo
 * stat block compongono testo — la colonna «Danno e tipo», il sottotitolo di una
 * creatura — e devono poterlo fare senza dipendere da Compose.
 */

// --- Tipi di danno -----------------------------------------------------------

fun DamageType.label(language: AppLanguage): String = language.pick(
    italian = when (this) {
        DamageType.ACID -> "Acido"
        DamageType.BLUDGEONING -> "Contundente"
        DamageType.COLD -> "Freddo"
        DamageType.FIRE -> "Fuoco"
        DamageType.FORCE -> "Forza"
        DamageType.LIGHTNING -> "Fulmine"
        DamageType.NECROTIC -> "Necrotico"
        DamageType.PIERCING -> "Perforante"
        DamageType.POISON -> "Veleno"
        DamageType.PSYCHIC -> "Psichico"
        DamageType.RADIANT -> "Radioso"
        DamageType.SLASHING -> "Tagliente"
        DamageType.THUNDER -> "Tuono"
        DamageType.UNTYPED -> "Non tipizzato"
        else -> name().openRuleLabel()
    },
    english = when (this) {
        DamageType.ACID -> "Acid"
        DamageType.BLUDGEONING -> "Bludgeoning"
        DamageType.COLD -> "Cold"
        DamageType.FIRE -> "Fire"
        DamageType.FORCE -> "Force"
        DamageType.LIGHTNING -> "Lightning"
        DamageType.NECROTIC -> "Necrotic"
        DamageType.PIERCING -> "Piercing"
        DamageType.POISON -> "Poison"
        DamageType.PSYCHIC -> "Psychic"
        DamageType.RADIANT -> "Radiant"
        DamageType.SLASHING -> "Slashing"
        DamageType.THUNDER -> "Thunder"
        DamageType.UNTYPED -> "Untyped"
        else -> name().openRuleLabel()
    },
)

/**
 * Forma da mettere dentro una frase.
 *
 * Non e' `label(language).lowercase()` per un motivo: «Non tipizzato» diventa
 * «non tipizzato» e va bene, ma la regola non regge in ogni lingua che potremmo
 * aggiungere, e una minuscola forzata su un nome proprio sarebbe un errore. La
 * forma giusta si dichiara, non si calcola.
 */
fun DamageType.inlineLabel(language: AppLanguage): String = language.pick(
    italian = when (this) {
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
        DamageType.RADIANT -> "radioso"
        DamageType.SLASHING -> "tagliente"
        DamageType.THUNDER -> "tuono"
        DamageType.UNTYPED -> "non tipizzato"
        else -> name().openRuleLabel().lowercase()
    },
    english = when (this) {
        DamageType.ACID -> "acid"
        DamageType.BLUDGEONING -> "bludgeoning"
        DamageType.COLD -> "cold"
        DamageType.FIRE -> "fire"
        DamageType.FORCE -> "force"
        DamageType.LIGHTNING -> "lightning"
        DamageType.NECROTIC -> "necrotic"
        DamageType.PIERCING -> "piercing"
        DamageType.POISON -> "poison"
        DamageType.PSYCHIC -> "psychic"
        DamageType.RADIANT -> "radiant"
        DamageType.SLASHING -> "slashing"
        DamageType.THUNDER -> "thunder"
        DamageType.UNTYPED -> "untyped"
        else -> name().openRuleLabel().lowercase()
    },
)

// --- Condizioni --------------------------------------------------------------

fun ConditionType.label(language: AppLanguage): String = language.pick(
    italian = when (this) {
        ConditionType.BLINDED -> "Accecato"
        ConditionType.CHARMED -> "Affascinato"
        ConditionType.DEAFENED -> "Assordato"
        ConditionType.EXHAUSTION -> "Sfinimento"
        ConditionType.FRIGHTENED -> "Spaventato"
        ConditionType.GRAPPLED -> "Afferrato"
        ConditionType.INCAPACITATED -> "Incapacitato"
        ConditionType.INVISIBLE -> "Invisibile"
        ConditionType.PARALYZED -> "Paralizzato"
        ConditionType.PETRIFIED -> "Pietrificato"
        ConditionType.POISONED -> "Avvelenato"
        ConditionType.PRONE -> "Prono"
        ConditionType.RESTRAINED -> "Trattenuto"
        ConditionType.STUNNED -> "Stordito"
        ConditionType.UNCONSCIOUS -> "Privo di sensi"
        ConditionType.CUSTOM -> "Personalizzata"
        else -> name().openRuleLabel()
    },
    english = when (this) {
        ConditionType.BLINDED -> "Blinded"
        ConditionType.CHARMED -> "Charmed"
        ConditionType.DEAFENED -> "Deafened"
        ConditionType.EXHAUSTION -> "Exhaustion"
        ConditionType.FRIGHTENED -> "Frightened"
        ConditionType.GRAPPLED -> "Grappled"
        ConditionType.INCAPACITATED -> "Incapacitated"
        ConditionType.INVISIBLE -> "Invisible"
        ConditionType.PARALYZED -> "Paralyzed"
        ConditionType.PETRIFIED -> "Petrified"
        ConditionType.POISONED -> "Poisoned"
        ConditionType.PRONE -> "Prone"
        ConditionType.RESTRAINED -> "Restrained"
        ConditionType.STUNNED -> "Stunned"
        ConditionType.UNCONSCIOUS -> "Unconscious"
        ConditionType.CUSTOM -> "Custom"
        else -> name().openRuleLabel()
    },
)

fun ConditionType.inlineLabel(language: AppLanguage): String = label(language).lowercase()

private fun String.openRuleLabel(): String =
    substringAfterLast(':').replace('-', ' ').replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)

// --- Caratteristiche dei tiri salvezza ---------------------------------------

fun SaveAbility.label(language: AppLanguage): String = language.pick(
    italian = when (this) {
        SaveAbility.STRENGTH -> "Forza"
        SaveAbility.DEXTERITY -> "Destrezza"
        SaveAbility.CONSTITUTION -> "Costituzione"
        SaveAbility.INTELLIGENCE -> "Intelligenza"
        SaveAbility.WISDOM -> "Saggezza"
        SaveAbility.CHARISMA -> "Carisma"
    },
    english = when (this) {
        SaveAbility.STRENGTH -> "Strength"
        SaveAbility.DEXTERITY -> "Dexterity"
        SaveAbility.CONSTITUTION -> "Constitution"
        SaveAbility.INTELLIGENCE -> "Intelligence"
        SaveAbility.WISDOM -> "Wisdom"
        SaveAbility.CHARISMA -> "Charisma"
    },
)

fun SaveAbility.abbreviation(language: AppLanguage): String = language.pick(
    italian = when (this) {
        SaveAbility.STRENGTH -> "FOR"
        SaveAbility.DEXTERITY -> "DES"
        SaveAbility.CONSTITUTION -> "COS"
        SaveAbility.INTELLIGENCE -> "INT"
        SaveAbility.WISDOM -> "SAG"
        SaveAbility.CHARISMA -> "CAR"
    },
    english = when (this) {
        SaveAbility.STRENGTH -> "STR"
        SaveAbility.DEXTERITY -> "DEX"
        SaveAbility.CONSTITUTION -> "CON"
        SaveAbility.INTELLIGENCE -> "INT"
        SaveAbility.WISDOM -> "WIS"
        SaveAbility.CHARISMA -> "CHA"
    },
)

// --- Costo di attivazione ----------------------------------------------------

fun ActivationCost.label(language: AppLanguage): String = language.pick(
    italian = when (this) {
        ActivationCost.ACTION -> "Azione"
        ActivationCost.BONUS_ACTION -> "Azione bonus"
        ActivationCost.REACTION -> "Reazione"
        ActivationCost.LEGENDARY_ACTION -> "Azione leggendaria"
        ActivationCost.NONE -> "Nessun costo"
    },
    english = when (this) {
        ActivationCost.ACTION -> "Action"
        ActivationCost.BONUS_ACTION -> "Bonus action"
        ActivationCost.REACTION -> "Reaction"
        ActivationCost.LEGENDARY_ACTION -> "Legendary action"
        ActivationCost.NONE -> "No cost"
    },
)

/** Forma breve per la barra dei comandi, dove lo spazio e' contato. */
fun ActivationCost.shortLabel(language: AppLanguage): String = language.pick(
    italian = when (this) {
        ActivationCost.ACTION -> "Azione"
        ActivationCost.BONUS_ACTION -> "Bonus"
        ActivationCost.REACTION -> "Reazione"
        ActivationCost.LEGENDARY_ACTION -> "Leggendaria"
        ActivationCost.NONE -> "Libera"
    },
    english = when (this) {
        ActivationCost.ACTION -> "Action"
        ActivationCost.BONUS_ACTION -> "Bonus"
        ActivationCost.REACTION -> "Reaction"
        ActivationCost.LEGENDARY_ACTION -> "Legendary"
        ActivationCost.NONE -> "Free"
    },
)

// --- Modo del d20 ------------------------------------------------------------

fun D20Mode.label(language: AppLanguage): String = language.pick(
    italian = when (this) {
        D20Mode.NORMAL -> "Normale"
        D20Mode.ADVANTAGE -> "Vantaggio"
        D20Mode.DISADVANTAGE -> "Svantaggio"
    },
    english = when (this) {
        D20Mode.NORMAL -> "Normal"
        D20Mode.ADVANTAGE -> "Advantage"
        D20Mode.DISADVANTAGE -> "Disadvantage"
    },
)

// --- Metodo di risoluzione e automazione -------------------------------------

fun ResolutionMethod.label(language: AppLanguage): String = language.pick(
    italian = when (this) {
        ResolutionMethod.ATTACK_ROLL -> "Tiro per colpire"
        ResolutionMethod.SAVING_THROW -> "Tiro salvezza"
        ResolutionMethod.ABILITY_CHECK -> "Prova di caratteristica"
        ResolutionMethod.AUTOMATIC -> "Automatico"
        ResolutionMethod.MANUAL -> "Manuale"
    },
    english = when (this) {
        ResolutionMethod.ATTACK_ROLL -> "Attack roll"
        ResolutionMethod.SAVING_THROW -> "Saving throw"
        ResolutionMethod.ABILITY_CHECK -> "Ability check"
        ResolutionMethod.AUTOMATIC -> "Automatic"
        ResolutionMethod.MANUAL -> "Manual"
    },
)

fun AutomationStatus.label(language: AppLanguage): String = language.pick(
    italian = when (this) {
        AutomationStatus.AUTOMATED -> "Automatica"
        AutomationStatus.ASSISTED -> "Assistita"
        AutomationStatus.MANUAL_REQUIRED -> "Da risolvere a mano"
    },
    english = when (this) {
        AutomationStatus.AUTOMATED -> "Automated"
        AutomationStatus.ASSISTED -> "Assisted"
        AutomationStatus.MANUAL_REQUIRED -> "Resolve by hand"
    },
)

// --- Bersaglio delle cure ----------------------------------------------------

fun HealingTarget.label(language: AppLanguage): String = language.pick(
    italian = when (this) {
        HealingTarget.SELF -> "Solo se stesso"
        HealingTarget.ALLY -> "Solo un alleato"
        HealingTarget.SELF_OR_ALLY -> "Se stesso o un alleato"
    },
    english = when (this) {
        HealingTarget.SELF -> "Self only"
        HealingTarget.ALLY -> "An ally only"
        HealingTarget.SELF_OR_ALLY -> "Self or an ally"
    },
)

// --- Tipo di attore ----------------------------------------------------------

fun ActorKind.label(language: AppLanguage): String = language.pick(
    italian = when (this) {
        ActorKind.PLAYER_CHARACTER -> "Personaggio"
        ActorKind.NON_PLAYER_CHARACTER -> "PNG"
        ActorKind.CREATURE -> "Creatura"
    },
    english = when (this) {
        ActorKind.PLAYER_CHARACTER -> "Character"
        ActorKind.NON_PLAYER_CHARACTER -> "NPC"
        ActorKind.CREATURE -> "Creature"
    },
)

// --- Stato dello scontro -----------------------------------------------------

fun CombatStatus.label(language: AppLanguage): String = language.pick(
    italian = when (this) {
        CombatStatus.DRAFT -> "Bozza"
        CombatStatus.READY -> "Pronto"
        CombatStatus.ACTIVE -> "In corso"
        CombatStatus.PAUSED -> "In pausa"
        CombatStatus.RESOLVED -> "Concluso"
    },
    english = when (this) {
        CombatStatus.DRAFT -> "Draft"
        CombatStatus.READY -> "Ready"
        CombatStatus.ACTIVE -> "Active"
        CombatStatus.PAUSED -> "Paused"
        CombatStatus.RESOLVED -> "Resolved"
    },
)

/** Forma da frase: «Il comando richiede uno scontro *in corso*». */
fun CombatStatus.inlineLabel(language: AppLanguage): String = language.pick(
    italian = when (this) {
        CombatStatus.DRAFT -> "in preparazione"
        CombatStatus.READY -> "pronto"
        CombatStatus.ACTIVE -> "in corso"
        CombatStatus.PAUSED -> "in pausa"
        CombatStatus.RESOLVED -> "concluso"
    },
    english = when (this) {
        CombatStatus.DRAFT -> "in preparation"
        CombatStatus.READY -> "ready"
        CombatStatus.ACTIVE -> "active"
        CombatStatus.PAUSED -> "paused"
        CombatStatus.RESOLVED -> "resolved"
    },
)
