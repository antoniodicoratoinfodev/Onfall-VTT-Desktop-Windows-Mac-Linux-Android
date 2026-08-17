package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage

/**
 * Il testo che il pacchetto SRD scrive di suo, non quello che legge dal PDF.
 *
 * Quasi tutta la prosa del pacchetto arriva dai JSON estratti, ed e' gia' nella
 * lingua giusta perche' viene da due PDF diversi. Resta pero' il testo che
 * questo modulo compone da se': i titoli delle scelte guidate, i nomi delle
 * sottoclassi, le etichette degli strumenti, le descrizioni delle opzioni che
 * l'SRD lascia implicite. Quello sta qui.
 *
 * E' un'interfaccia e non una mappa per la stessa ragione del fascicolo
 * dell'interfaccia utente: una voce dimenticata non compila, invece di apparire
 * vuota a chi gioca.
 *
 * Attenzione a cosa **non** entra qui: gli slug che compongono gli
 * identificativi. Quelli restano italiani in entrambe le lingue, perche' sono
 * chiavi salvate nelle schede — vedi [SrdIdentity].
 */
internal interface SrdWords {
    val language: AppLanguage

    // Scelte guidate
    fun startingWeaponsTitle(count: Int): String
    val startingEquipmentTitle: String
    fun startingEquipmentFor(className: String): String
    val toolProficiencyTitle: String
    val toolProficiency: String

    // Sottoclassi e opzioni composte
    fun subclassDescription(className: String): String
    val subclassFeaturesPrefix: String
    fun mysticArcanum(level: Int): String
    fun landChoice(resistance: String): String
    fun elementalAffinity(damage: String): String
    fun displayName(slug: String): String
    /** «Trucchetto · Invocazione» oppure «Level 3 · Evocation». */
    fun spellLevelTag(level: Int, school: String): String

    /** «Armi da guerra» concesse da un privilegio. */
    val martialWeapons: String

    /**
     * Il costo dichiarato dentro la descrizione di un privilegio.
     *
     * Sta qui e non in una costante perche' le due edizioni scrivono «Costo: 2»
     * e «Cost: 2»: con il solo modello italiano, ogni privilegio inglese
     * risulterebbe gratuito.
     */
    val statedCost: Regex

    // Riga di riepilogo di un'arma
    val rangePrefix: String
    val reachSuffix: String
    val masteryPrefix: String

    // Parole di dominio usate nelle descrizioni composte
    val acid: String
    val cold: String
    val fire: String
    val lightning: String
    val poison: String

    companion object {
        fun of(language: AppLanguage): SrdWords = when (language) {
            AppLanguage.ITALIAN -> SrdWordsIt
            AppLanguage.ENGLISH -> SrdWordsEn
        }
    }
}

private object SrdWordsIt : SrdWords {
    override val language = AppLanguage.ITALIAN

    override fun startingWeaponsTitle(count: Int) =
        "Scegli $count armi iniziali fra quelle della classe"
    override val startingEquipmentTitle = "Scegli l'equipaggiamento iniziale"
    override fun startingEquipmentFor(className: String) =
        "$className: scegli la dotazione iniziale"
    override val toolProficiencyTitle = "Scegli uno strumento"
    override val toolProficiency = "Competenza negli strumenti"

    override fun subclassDescription(className: String) = "Sottoclasse SRD del $className."
    override val subclassFeaturesPrefix = " Privilegi: "
    override fun mysticArcanum(level: Int) = "Arcanum mistico (${level}º)"
    override fun landChoice(resistance: String) =
        "Tipo di terra del Circolo della Terra. Conferisce la relativa lista di " +
            "incantesimi del Circolo e, dall'Interdizione della Natura, resistenza ai " +
            "danni da $resistance."
    override fun elementalAffinity(damage: String) =
        "Tipo di danno scelto per Affinità elementale: $damage."
    override fun displayName(slug: String) = ITALIAN_SUBCLASSES[slug] ?: slug.toDisplayName()
    override fun spellLevelTag(level: Int, school: String) =
        if (level == 0) "Trucchetto · $school" else "${level}º · $school"

    override val martialWeapons = "Armi da guerra"
    override val statedCost = Regex("""Costo:\s*(\d+)""", RegexOption.IGNORE_CASE)

    override val rangePrefix = " · gittata "
    override val reachSuffix = " · portata"
    override val masteryPrefix = " · Padronanza: "

    override val acid = "acido"
    override val cold = "freddo"
    override val fire = "fuoco"
    override val lightning = "fulmine"
    override val poison = "veleno"
}

private object SrdWordsEn : SrdWords {
    override val language = AppLanguage.ENGLISH

    override fun startingWeaponsTitle(count: Int) =
        "Choose $count starting weapons from your class list"
    override val startingEquipmentTitle = "Choose your starting equipment"
    override fun startingEquipmentFor(className: String) =
        "$className: choose your starting equipment"
    override val toolProficiencyTitle = "Choose a tool"
    override val toolProficiency = "Tool Proficiency"

    override fun subclassDescription(className: String) = "SRD $className subclass."
    override val subclassFeaturesPrefix = " Features: "
    override fun mysticArcanum(level: Int) = "Mystic Arcanum (level $level)"
    override fun landChoice(resistance: String) =
        "Land type for the Circle of the Land. It grants the matching Circle spell " +
            "list and, from Nature's Ward onward, resistance to $resistance damage."
    override fun elementalAffinity(damage: String) =
        "Damage type chosen for Elemental Affinity: $damage."
    override fun displayName(slug: String) = ENGLISH_SUBCLASSES[slug] ?: slug.toDisplayName()
    override fun spellLevelTag(level: Int, school: String) =
        if (level == 0) "Cantrip · $school" else "Level $level · $school"

    override val martialWeapons = "Martial weapons"
    override val statedCost = Regex("""Cost:\s*(\d+)""", RegexOption.IGNORE_CASE)

    override val rangePrefix = " · range "
    override val reachSuffix = " · reach"
    override val masteryPrefix = " · Mastery: "

    override val acid = "acid"
    override val cold = "cold"
    override val fire = "fire"
    override val lightning = "lightning"
    override val poison = "poison"
}

// Le chiavi sono gli slug canonici, cioe' italiani, anche nella tavola inglese:
// e' l'identificativo a cercare il nome, non il contrario.
private val ITALIAN_SUBCLASSES = mapOf(
    "cammino-del-berserker" to "Cammino del berserker",
    "collegio-della-sapienza" to "Collegio della Sapienza",
    "dominio-della-vita" to "Dominio della Vita",
    "circolo-della-terra" to "Circolo della Terra",
    "campione" to "Campione",
    "furfante" to "Furfante",
    "invocatore" to "Invocatore",
    "guerriero-della-mano-aperta" to "Guerriero della Mano Aperta",
    "giuramento-di-devozione" to "Giuramento di devozione",
    "cacciatore" to "Cacciatore",
    "stregoneria-draconica" to "Stregoneria draconica",
    "patrono-immondo" to "Patrono immondo",
)

private val ENGLISH_SUBCLASSES = mapOf(
    "cammino-del-berserker" to "Path of the Berserker",
    "collegio-della-sapienza" to "College of Lore",
    "dominio-della-vita" to "Life Domain",
    "circolo-della-terra" to "Circle of the Land",
    "campione" to "Champion",
    "furfante" to "Thief",
    "invocatore" to "Evoker",
    "guerriero-della-mano-aperta" to "Warrior of the Open Hand",
    "giuramento-di-devozione" to "Oath of Devotion",
    "cacciatore" to "Hunter",
    "stregoneria-draconica" to "Draconic Sorcery",
    "patrono-immondo" to "Fiend Patron",
)

private fun String.toDisplayName(): String =
    replace('-', ' ').replaceFirstChar { it.uppercase() }
