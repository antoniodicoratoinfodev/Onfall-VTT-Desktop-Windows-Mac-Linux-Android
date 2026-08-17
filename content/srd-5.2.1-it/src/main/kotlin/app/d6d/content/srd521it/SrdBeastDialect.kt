package app.d6d.content.srd521it

import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.SaveAbility
import app.d6d.i18n.AppLanguage

/**
 * Come si legge una scheda delle statistiche nelle due edizioni dell'SRD.
 *
 * Le schede sono testo, non dati: per proiettare una forma bestiale in un
 * attore giocabile bisogna rileggere quel testo. Le due edizioni scrivono le
 * stesse grandezze con parole diverse — `CA`/`AC`, `PF`/`HP`, `Colpito:`/`Hit:`
 * — e, soprattutto, mettono le distanze in unita' diverse: metri in italiano,
 * piedi in inglese. Il motore conta sempre in piedi, quindi la conversione sta
 * qui e non altrove.
 *
 * Una differenza merita attenzione perche' non e' solo lessicale: il tipo di
 * danno **precede** la parola «damage» in inglese e **segue** «danni» in
 * italiano. I due modelli lo catturano nello stesso gruppo per numero, cosi'
 * il codice che li usa non deve sapere in quale lingua sta leggendo.
 */
internal class BeastDialect private constructor(
    val header: Regex,
    val hitPoints: Regex,
    /** Cattura il valore di velocita' nell'unita' dell'edizione. */
    val speed: Regex,
    /** Da quel valore ai piedi che conta il motore. */
    val speedToFeet: (String) -> Int,
    val actionsHeading: String,
    val bonusActionsHeading: String,
    val multiattack: String,
    val attack: Regex,
    val savingThrows: Regex,
    private val saveLabels: Map<String, SaveAbility>,
    val resistancesLabel: String,
    val vulnerabilitiesLabel: String,
    val damageImmunitiesLabel: String,
    private val damageWords: Map<DamageType, String>,
    /** «GS 1/4 · Velocità 12 m» oppure «CR 1/4 · Speed 40 ft.». */
    val summary: (challengeRating: String, speed: String) -> String,
    val wildShapePrerequisite: (level: Int) -> String,
) {
    fun saveAbility(label: String): SaveAbility = saveLabels.getValue(label)

    /**
     * L'italiano concorda il tipo di danno col numero — «danno perforante» ma
     * «danni perforanti» — quindi il confronto ignora la vocale finale.
     */
    fun damageType(label: String): DamageType {
        val wanted = label.lowercase().trimEnd('i', 'e', 'o', 'a')
        return damageWords.entries
            .firstOrNull { (_, word) -> word.isNotEmpty() && word.trimEnd('i', 'e', 'o', 'a') == wanted }
            ?.key
            ?: error("Tipo di danno non supportato: $label")
    }

    /** I tipi di danno nominati in una riga di resistenze o immunita'. */
    fun damageTypesIn(line: String): Set<DamageType> {
        val lowered = line.lowercase()
        return damageWords.entries
            .filterTo(mutableSetOf()) { (_, word) -> word.isNotEmpty() && word in lowered }
            .mapTo(mutableSetOf()) { it.key }
    }

    companion object {
        fun of(language: AppLanguage): BeastDialect = when (language) {
            AppLanguage.ITALIAN -> ITALIAN
            AppLanguage.ENGLISH -> ENGLISH
        }

        private val ITALIAN = BeastDialect(
            header = Regex("(?m)^CA (\\d+) Iniziativa ([+-]?\\d+) \\((\\d+)\\)"),
            hitPoints = Regex("(?m)^PF (\\d+)"),
            speed = Regex("(?m)^Velocità ([\\d,]+) m"),
            speedToFeet = { (it.replace(',', '.').toDouble() / 1.5 * 5).toInt() },
            actionsHeading = "\nAzioni\n",
            bonusActionsHeading = "\nAzioni bonus\n",
            multiattack = "Multiattacco.",
            attack = Regex(
                "([A-ZÀ-Ü][^.]*)\\. Tiro per colpire (in mischia|a distanza): ([+-]?\\d+)" +
                    "(?: \\([^)]*\\))?, (?:portata|gittata) ([\\d,]+)(?:/[\\d,]+)? m\\.?" +
                    "\\s*Colpito: (\\d+)(?: \\((\\d+)d(\\d+)(?:\\s*([+-])?\\s*(\\d+))?\\))?" +
                    " dann[oi] (?:da )?([a-zàèéìòù]+)",
            ),
            savingThrows = Regex("(For|Des|Cos|Int|Sag|Car) \\d+ [+-]?\\d+ ([+-]?\\d+)"),
            saveLabels = mapOf(
                "For" to SaveAbility.STRENGTH,
                "Des" to SaveAbility.DEXTERITY,
                "Cos" to SaveAbility.CONSTITUTION,
                "Int" to SaveAbility.INTELLIGENCE,
                "Sag" to SaveAbility.WISDOM,
                "Car" to SaveAbility.CHARISMA,
            ),
            resistancesLabel = "Resistenze",
            vulnerabilitiesLabel = "Vulnerabilità",
            damageImmunitiesLabel = "Immunità ai danni",
            damageWords = mapOf(
                DamageType.ACID to "acido",
                DamageType.BLUDGEONING to "contundente",
                DamageType.COLD to "freddo",
                DamageType.FIRE to "fuoco",
                DamageType.FORCE to "forza",
                DamageType.LIGHTNING to "fulmine",
                DamageType.NECROTIC to "necrotico",
                DamageType.PIERCING to "perforante",
                DamageType.POISON to "veleno",
                DamageType.PSYCHIC to "psichico",
                DamageType.RADIANT to "radioso",
                DamageType.SLASHING to "tagliente",
                DamageType.THUNDER to "tuono",
            ),
            summary = { rating, speed -> "GS $rating · Velocità $speed" },
            wildShapePrerequisite = { level -> "Forma selvatica · Druido di ${level}º livello" },
        )

        private val ENGLISH = BeastDialect(
            header = Regex("(?m)^AC (\\d+) Initiative ([+-]?\\d+) \\((\\d+)\\)"),
            hitPoints = Regex("(?m)^HP (\\d+)"),
            speed = Regex("(?m)^Speed (\\d+) (?:ft|feet)"),
            speedToFeet = { it.toInt() },
            actionsHeading = "\nActions\n",
            bonusActionsHeading = "\nBonus Actions\n",
            multiattack = "Multiattack.",
            attack = Regex(
                "([A-Z][^.]*)\\. (Melee|Ranged) Attack Roll: ([+-]?\\d+)" +
                    // «ft.» oppure «feet»: l'SRD inglese usa entrambe, e
                    // accettarne una sola lasciava Aquila e Ratto gigante senza
                    // nessun attacco in combattimento.
                    "(?: \\([^)]*\\))?, (?:reach|range) (\\d+)(?:/\\d+)? (?:ft|feet)\\.?" +
                    "\\s*Hit: (\\d+)(?: \\((\\d+)d(\\d+)(?:\\s*([+-])?\\s*(\\d+))?\\))?" +
                    " ([A-Za-z]+) damage",
            ),
            savingThrows = Regex("(Str|Dex|Con|Int|Wis|Cha) \\d+ [+-−]?\\d+ ([+-−]?\\d+)"),
            saveLabels = mapOf(
                "Str" to SaveAbility.STRENGTH,
                "Dex" to SaveAbility.DEXTERITY,
                "Con" to SaveAbility.CONSTITUTION,
                "Int" to SaveAbility.INTELLIGENCE,
                "Wis" to SaveAbility.WISDOM,
                "Cha" to SaveAbility.CHARISMA,
            ),
            resistancesLabel = "Resistances",
            vulnerabilitiesLabel = "Vulnerabilities",
            damageImmunitiesLabel = "Immunities",
            damageWords = mapOf(
                DamageType.ACID to "acid",
                DamageType.BLUDGEONING to "bludgeoning",
                DamageType.COLD to "cold",
                DamageType.FIRE to "fire",
                DamageType.FORCE to "force",
                DamageType.LIGHTNING to "lightning",
                DamageType.NECROTIC to "necrotic",
                DamageType.PIERCING to "piercing",
                DamageType.POISON to "poison",
                DamageType.PSYCHIC to "psychic",
                DamageType.RADIANT to "radiant",
                DamageType.SLASHING to "slashing",
                DamageType.THUNDER to "thunder",
            ),
            summary = { rating, speed -> "CR $rating · Speed $speed" },
            wildShapePrerequisite = { level -> "Wild Shape · Level $level+ Druid" },
        )
    }
}
