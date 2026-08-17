package app.d6d.sheet.i18n

import app.d6d.i18n.AppLanguage
import app.d6d.i18n.inlineLabel
import app.d6d.i18n.pick
import app.d6d.sheet.ArmorCategory
import app.d6d.sheet.ArmorClassDexterity
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.ArmorSpecialRule
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.CatalogDamage
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.Proficiency
import app.d6d.sheet.WeaponEntry
import app.d6d.sheet.formatModifier

/**
 * Vocabolario della scheda e dello stat block nelle due lingue.
 *
 * Come per il vocabolario del combattimento, le voci italiane restano dove sono —
 * sono proprieta' costanti degli enum e in qualche punto servono anche a ritrovare
 * i contenuti — e l'inglese si aggiunge qui accanto.
 *
 * Insieme alle etichette vivono qui anche le poche righe *composte* che la scheda
 * stampa: la colonna «Danno e tipo», il sottotitolo di una creatura. Nel motore ne
 * esistono le versioni italiane, ferme, usate dai test e dal content pack; queste
 * prendono la lingua come parametro perche' sono l'unica forma che arriva
 * davvero sotto gli occhi di chi gioca.
 */

// --- Competenza e taglia -----------------------------------------------------

fun Proficiency.label(language: AppLanguage): String = language.pick(
    italian = italianLabel,
    english = when (this) {
        Proficiency.NONE -> "None"
        Proficiency.PROFICIENT -> "Proficient"
        Proficiency.EXPERTISE -> "Expertise"
    },
)

fun CreatureSize.label(language: AppLanguage): String = language.pick(
    italian = italianLabel,
    english = when (this) {
        CreatureSize.TINY -> "Tiny"
        CreatureSize.SMALL -> "Small"
        CreatureSize.MEDIUM -> "Medium"
        CreatureSize.LARGE -> "Large"
        CreatureSize.HUGE -> "Huge"
        CreatureSize.GARGANTUAN -> "Gargantuan"
    },
)

// --- Armatura ----------------------------------------------------------------

fun ArmorClassDexterity.label(language: AppLanguage): String = language.pick(
    italian = italianLabel,
    english = when (this) {
        ArmorClassDexterity.FULL -> "Full Dexterity"
        ArmorClassDexterity.MAX_TWO -> "Dexterity, max +2"
        ArmorClassDexterity.NONE -> "No Dexterity"
    },
)

fun ArmorCategory.label(language: AppLanguage): String = language.pick(
    italian = italianLabel,
    english = when (this) {
        ArmorCategory.LIGHT -> "light armor"
        ArmorCategory.MEDIUM -> "medium armor"
        ArmorCategory.HEAVY -> "heavy armor"
    },
)

fun ArmorSpecialRule.label(language: AppLanguage): String = language.pick(
    italian = italianLabel,
    english = when (this) {
        ArmorSpecialRule.STANDARD -> "Ordinary armor"
        ArmorSpecialRule.MITHRAL -> "Mithral armor"
        ArmorSpecialRule.ELVEN_CHAIN -> "Elven chain"
    },
)

fun ArmorClassMethod.label(language: AppLanguage): String = language.pick(
    italian = italianLabel,
    english = when (this) {
        ArmorClassMethod.MANUAL_TOTAL -> "Manual final AC"
        ArmorClassMethod.UNARMORED -> "Unarmored"
        ArmorClassMethod.BARBARIAN_UNARMORED -> "Unarmored Defense (Barbarian)"
        ArmorClassMethod.MONK_UNARMORED -> "Unarmored Defense (Monk)"
        ArmorClassMethod.DRACONIC_RESILIENCE -> "Draconic Resilience"
        ArmorClassMethod.PADDED -> "Padded armor"
        ArmorClassMethod.LEATHER -> "Leather armor"
        ArmorClassMethod.STUDDED_LEATHER -> "Studded leather"
        ArmorClassMethod.HIDE -> "Hide armor"
        ArmorClassMethod.CHAIN_SHIRT -> "Chain shirt"
        ArmorClassMethod.SCALE_MAIL -> "Scale mail"
        ArmorClassMethod.BREASTPLATE -> "Breastplate"
        ArmorClassMethod.HALF_PLATE -> "Half plate"
        ArmorClassMethod.RING_MAIL -> "Ring mail"
        ArmorClassMethod.CHAIN_MAIL -> "Chain mail"
        ArmorClassMethod.SPLINT -> "Splint armor"
        ArmorClassMethod.PLATE -> "Plate armor"
        ArmorClassMethod.MAGE_ARMOR -> "Mage Armor"
        ArmorClassMethod.CUSTOM_BASE -> "Custom base"
    },
)

// --- Righe composte ----------------------------------------------------------

/** «1d6+2 tagliente» / «1d6+2 slashing». */
fun CatalogDamage.text(language: AppLanguage): String = buildString {
    append(diceCount).append('d').append(diceSides)
    if (modifier != 0) append(formatModifier(modifier))
    append(' ').append(type.inlineLabel(language))
}

/** Riga «Danno e tipo» di una capacita' del Compendio. */
fun CatalogAbility.damageText(language: AppLanguage): String = if (!dealsDamage) {
    language.pick("Nessun danno", "No damage")
} else {
    buildList {
        add(
            buildString {
                append(diceCount).append('d').append(diceSides)
                if (damageModifier != 0) append(formatModifier(damageModifier))
                append(' ').append(damageType.inlineLabel(language))
            },
        )
        addAll(additionalDamage.map { it.text(language) })
    }.joinToString(" + ")
}

/** Colonna «Danno e tipo» di una riga d'attacco della scheda. */
fun WeaponEntry.damageText(language: AppLanguage): String = buildString {
    if (fixedDamage > 0) append(fixedDamage) else append(diceCount).append('d').append(diceSides)
    if (damageModifier != 0) append(formatModifier(damageModifier))
    append(' ').append(damageType.inlineLabel(language))
}

/**
 * Intestazione di uno stat block: «Media Umanoide (goblinoide), Neutrale Malvagio».
 *
 * Solo la taglia si traduce. Tipo, sottotipi e allineamento sono testo libero
 * scritto da chi ha redatto la creatura: tradurli automaticamente vorrebbe dire
 * indovinare, e indovinare male su una scheda altrui.
 */
fun MonsterStatBlock.subtitle(language: AppLanguage): String = buildString {
    append(size.label(language))
    if (type.isNotBlank()) append(' ').append(type)
    if (tags.isNotBlank()) append(" (").append(tags).append(')')
    if (alignment.isNotBlank()) append(", ").append(alignment)
}

/**
 * Traduce gli errori di validazione prodotti dal modello storico della scheda.
 *
 * Le eccezioni restano prive di stato globale e continuano a conservare il testo
 * italiano per compatibilità; il confine di presentazione le rende nella lingua
 * corrente. Un messaggio del sistema o di un plug-in sconosciuto passa invece
 * intatto: tentare di tradurlo per somiglianza rischierebbe di alterarne il senso.
 */
fun localizedSheetError(detail: String, language: AppLanguage): String {
    if (language == AppLanguage.ITALIAN || detail.isBlank()) return detail
    ENGLISH_SHEET_ERRORS[detail]?.let { return it }
    val schema = UNSUPPORTED_SCHEMA.matchEntire(detail)
    if (schema != null) {
        val (stored, supported) = schema.destructured
        return "The file uses schema $stored, but this version of the app supports up to " +
            "schema $supported. Update the app before saving it."
    }
    return detail
}

private val UNSUPPORTED_SCHEMA = Regex(
    """Il file usa lo schema (\d+), ma questa versione dell'app supporta fino allo schema (\d+)\. """ +
        """Aggiorna l'app prima di salvarlo\.""",
)

private val ENGLISH_SHEET_ERRORS = mapOf(
    "L'identificatore dell'abilità non può essere vuoto." to "The ability ID can't be blank.",
    "Il nome dell'abilità non può essere vuoto." to "The ability name can't be blank.",
    "La gittata non può essere negativa." to "The range can't be negative.",
    "Il numero di bersagli deve essere positivo." to "The number of targets must be positive.",
    "Il raggio dell'area non può essere negativo." to "The area radius can't be negative.",
    "La pagina sorgente non può essere negativa." to "The source page can't be negative.",
    "Il livello dell'incantesimo deve essere 0-9." to "The spell level must be between 0 and 9.",
    "Il costo in risorse non può essere negativo." to "The resource cost can't be negative.",
    "Una capacità con effetto automatico non può essere un tratto passivo." to
        "An ability with an automatic effect can't be a passive trait.",
    "Un attacco deve indicare il danno." to "An attack must specify its damage.",
    "Il livello slot base deve essere compreso tra 1 e 9." to
        "The base slot level must be between 1 and 9.",
    "I dadi aggiuntivi per livello devono essere positivi." to
        "The additional dice per level must be positive.",
    "I livelli di classe non possono essere negativi." to "Class levels can't be negative.",
    "Una cura deve indicare dadi oppure un importo fisso." to
        "Healing must specify either dice or a fixed amount.",
    "Una cura fissa deve essere positiva." to "A fixed healing amount must be positive.",
    "Una cura basata sul livello deve indicare la classe." to
        "Level-based healing must specify the class.",
    "La classe si indica soltanto per un bonus basato sul livello." to
        "A class can be specified only for a level-based bonus.",
    "Un bonus dinamico richiede una formula di dadi." to
        "A dynamic bonus requires a dice formula.",
    "Lo scaling per livello slot richiede una formula di dadi." to
        "Scaling by slot level requires a dice formula.",
)

/**
 * Come si chiama una scheda a cui nessuno ha dato un nome.
 *
 * Sta qui, e non nel fascicolo dell'interfaccia, perche' non e' solo testo da
 * mostrare: `ActorCatalogEntry` esige che il nome nella voce di catalogo e
 * quello nella definizione dell'attore coincidano, e i due nascono in strati
 * diversi. Con due ripieghi separati bastava tradurne uno perche' salvare una
 * scheda senza nome riuscisse e la rigenerazione del catalogo fallisse.
 */
fun unnamedActor(language: AppLanguage): String = language.pick(
    italian = "Senza nome",
    english = "Unnamed",
)

/** Gemello di [unnamedActor] per le creature. */
fun unnamedCreature(language: AppLanguage): String = language.pick(
    italian = "Creatura senza nome",
    english = "Unnamed creature",
)
