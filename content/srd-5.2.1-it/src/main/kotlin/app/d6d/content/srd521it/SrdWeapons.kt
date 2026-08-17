package app.d6d.content.srd521it

import app.d6d.domain.combat.DamageType
import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.WeaponCategory
import app.d6d.rules.character.WeaponDefinition
import app.d6d.rules.character.WeaponProperty
import app.d6d.rules.character.WeaponReach
import app.d6d.rules.character.WeaponTrainingGrant
import app.d6d.i18n.inlineLabel
import app.d6d.i18n.label
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.sheet.i18n.distanceValue

private const val WEAPON_PREFIX = "srd521-it:weapon"

private fun weapon(
    slug: String,
    name: String,
    category: WeaponCategory,
    reach: WeaponReach,
    diceCount: Int,
    diceSides: Int,
    damageType: DamageType,
    mastery: String,
    properties: Set<WeaponProperty> = emptySet(),
    normalRangeFeet: Int = 0,
    longRangeFeet: Int = 0,
    versatileDiceSides: Int = 0,
    fixedDamage: Int = 0,
) = WeaponDefinition(
    id = "$WEAPON_PREFIX:$slug",
    name = name,
    category = category,
    reach = reach,
    diceCount = diceCount,
    diceSides = diceSides,
    damageType = damageType,
    mastery = mastery,
    properties = properties,
    normalRangeFeet = normalRangeFeet,
    longRangeFeet = longRangeFeet,
    versatileDiceSides = versatileDiceSides,
    fixedDamage = fixedDamage,
)

/** Colonna "Danni" come stampata sulla tabella, senza il modificatore. */
fun WeaponDefinition.damageText(language: AppLanguage): String =
    "${if (fixedDamage > 0) fixedDamage else "${diceCount}d$diceSides"} ${damageType.inlineLabel(language)}"

/**
 * Riga leggibile usata dai selettori: danno, gittata, Padronanza e proprieta'.
 *
 * La gittata non si traduce, si converte: metri per l'edizione italiana, piedi
 * per quella inglese, con il motore che continua a contare in piedi.
 */
fun WeaponDefinition.summary(language: AppLanguage): String =
    buildString {
        append(damageText(language))
        when {
            reach == WeaponReach.RANGED || WeaponProperty.THROWN in properties ->
                append(SrdWords.of(language).rangePrefix)
                    .append(distanceValue(normalRangeFeet, language))
                    .append('/')
                    .append(distanceLabel(longRangeFeet, language))
            WeaponProperty.REACH in properties -> append(SrdWords.of(language).reachSuffix)
        }
        append(SrdWords.of(language).masteryPrefix).append(mastery)
        if (properties.isNotEmpty()) {
            append(" · ").append(properties.joinToString { it.label(language) })
        }
    }

/**
 * Tabella Armi dello SRD 5.2.1: dieci semplici da mischia, quattro semplici a
 * distanza, diciotto da guerra da mischia e sei da guerra a distanza.
 *
 * Il PDF italiano stampa le gittate in metri, e in metri le mostra anche questa
 * tabella. In memoria restano piedi come nel resto del motore, con la
 * conversione ufficiale (1,5 m = 5 piedi).
 */
object SrdWeapons {

    private val italian: List<WeaponDefinition> = listOf(
        // --- Armi da mischia semplici ---
        weapon(
            "ascia", "Ascia", WeaponCategory.SIMPLE, WeaponReach.MELEE,
            1, 6, DamageType.SLASHING, mastery = "Vessazione",
            properties = setOf(WeaponProperty.THROWN, WeaponProperty.LIGHT),
            normalRangeFeet = 20, longRangeFeet = 60,
        ),
        weapon(
            "bastone-ferrato", "Bastone ferrato", WeaponCategory.SIMPLE, WeaponReach.MELEE,
            1, 6, DamageType.BLUDGEONING, mastery = "Rovesciamento",
            properties = setOf(WeaponProperty.VERSATILE), versatileDiceSides = 8,
        ),
        weapon(
            "falcetto", "Falcetto", WeaponCategory.SIMPLE, WeaponReach.MELEE,
            1, 4, DamageType.SLASHING, mastery = "Graffio",
            properties = setOf(WeaponProperty.LIGHT),
        ),
        weapon(
            "giavellotto", "Giavellotto", WeaponCategory.SIMPLE, WeaponReach.MELEE,
            1, 6, DamageType.PIERCING, mastery = "Lentezza",
            properties = setOf(WeaponProperty.THROWN),
            normalRangeFeet = 30, longRangeFeet = 120,
        ),
        weapon(
            "lancia", "Lancia", WeaponCategory.SIMPLE, WeaponReach.MELEE,
            1, 6, DamageType.PIERCING, mastery = "Fiaccare",
            properties = setOf(WeaponProperty.THROWN, WeaponProperty.VERSATILE),
            normalRangeFeet = 20, longRangeFeet = 60, versatileDiceSides = 8,
        ),
        weapon(
            "martello-leggero", "Martello leggero", WeaponCategory.SIMPLE, WeaponReach.MELEE,
            1, 4, DamageType.BLUDGEONING, mastery = "Graffio",
            properties = setOf(WeaponProperty.THROWN, WeaponProperty.LIGHT),
            normalRangeFeet = 20, longRangeFeet = 60,
        ),
        weapon(
            "mazza", "Mazza", WeaponCategory.SIMPLE, WeaponReach.MELEE,
            1, 6, DamageType.BLUDGEONING, mastery = "Fiaccare",
        ),
        weapon(
            "pugnale", "Pugnale", WeaponCategory.SIMPLE, WeaponReach.MELEE,
            1, 4, DamageType.PIERCING, mastery = "Graffio",
            properties = setOf(
                WeaponProperty.FINESSE,
                WeaponProperty.THROWN,
                WeaponProperty.LIGHT,
            ),
            normalRangeFeet = 20, longRangeFeet = 60,
        ),
        weapon(
            "randello-pesante", "Randello pesante", WeaponCategory.SIMPLE, WeaponReach.MELEE,
            1, 8, DamageType.BLUDGEONING, mastery = "Spinta",
            properties = setOf(WeaponProperty.TWO_HANDED),
        ),
        weapon(
            "randello", "Randello", WeaponCategory.SIMPLE, WeaponReach.MELEE,
            1, 4, DamageType.BLUDGEONING, mastery = "Lentezza",
            properties = setOf(WeaponProperty.LIGHT),
        ),

        // --- Armi a distanza semplici ---
        weapon(
            "arco-corto", "Arco corto", WeaponCategory.SIMPLE, WeaponReach.RANGED,
            1, 6, DamageType.PIERCING, mastery = "Vessazione",
            properties = setOf(WeaponProperty.TWO_HANDED, WeaponProperty.AMMUNITION),
            normalRangeFeet = 80, longRangeFeet = 320,
        ),
        weapon(
            "balestra-leggera", "Balestra leggera", WeaponCategory.SIMPLE, WeaponReach.RANGED,
            1, 8, DamageType.PIERCING, mastery = "Lentezza",
            properties = setOf(
                WeaponProperty.TWO_HANDED,
                WeaponProperty.AMMUNITION,
                WeaponProperty.LOADING,
            ),
            normalRangeFeet = 80, longRangeFeet = 320,
        ),
        weapon(
            "dardo", "Dardo", WeaponCategory.SIMPLE, WeaponReach.RANGED,
            1, 4, DamageType.PIERCING, mastery = "Vessazione",
            properties = setOf(WeaponProperty.FINESSE, WeaponProperty.THROWN),
            normalRangeFeet = 20, longRangeFeet = 60,
        ),
        weapon(
            "fionda", "Fionda", WeaponCategory.SIMPLE, WeaponReach.RANGED,
            1, 4, DamageType.BLUDGEONING, mastery = "Lentezza",
            properties = setOf(WeaponProperty.AMMUNITION),
            normalRangeFeet = 30, longRangeFeet = 120,
        ),

        // --- Armi da mischia da guerra ---
        weapon(
            "alabarda", "Alabarda", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 10, DamageType.SLASHING, mastery = "Doppio fendente",
            properties = setOf(
                WeaponProperty.TWO_HANDED,
                WeaponProperty.HEAVY,
                WeaponProperty.REACH,
            ),
        ),
        weapon(
            "ascia-bipenne", "Ascia bipenne", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 12, DamageType.SLASHING, mastery = "Doppio fendente",
            properties = setOf(WeaponProperty.TWO_HANDED, WeaponProperty.HEAVY),
        ),
        weapon(
            "ascia-da-battaglia", "Ascia da battaglia", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 8, DamageType.SLASHING, mastery = "Rovesciamento",
            properties = setOf(WeaponProperty.VERSATILE), versatileDiceSides = 10,
        ),
        weapon(
            "falcione", "Falcione", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 10, DamageType.SLASHING, mastery = "Colpo di striscio",
            properties = setOf(
                WeaponProperty.TWO_HANDED,
                WeaponProperty.HEAVY,
                WeaponProperty.REACH,
            ),
        ),
        weapon(
            "frusta", "Frusta", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 4, DamageType.SLASHING, mastery = "Lentezza",
            properties = setOf(WeaponProperty.FINESSE, WeaponProperty.REACH),
        ),
        weapon(
            "lancia-da-cavaliere", "Lancia da cavaliere", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 10, DamageType.PIERCING, mastery = "Rovesciamento",
            properties = setOf(
                WeaponProperty.TWO_HANDED,
                WeaponProperty.HEAVY,
                WeaponProperty.REACH,
            ),
        ),
        weapon(
            "maglio", "Maglio", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            2, 6, DamageType.BLUDGEONING, mastery = "Rovesciamento",
            properties = setOf(WeaponProperty.TWO_HANDED, WeaponProperty.HEAVY),
        ),
        weapon(
            "martello-da-guerra", "Martello da guerra", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 8, DamageType.BLUDGEONING, mastery = "Spinta",
            properties = setOf(WeaponProperty.VERSATILE), versatileDiceSides = 10,
        ),
        weapon(
            "mazza-chiodata", "Mazza chiodata", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 8, DamageType.PIERCING, mastery = "Fiaccare",
        ),
        weapon(
            "mazzafrusto", "Mazzafrusto", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 8, DamageType.BLUDGEONING, mastery = "Fiaccare",
        ),
        weapon(
            "picca", "Picca", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 10, DamageType.PIERCING, mastery = "Spinta",
            properties = setOf(
                WeaponProperty.TWO_HANDED,
                WeaponProperty.HEAVY,
                WeaponProperty.REACH,
            ),
        ),
        weapon(
            "piccone-da-guerra", "Piccone da guerra", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 8, DamageType.PIERCING, mastery = "Fiaccare",
            properties = setOf(WeaponProperty.VERSATILE), versatileDiceSides = 10,
        ),
        weapon(
            "scimitarra", "Scimitarra", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 6, DamageType.SLASHING, mastery = "Graffio",
            properties = setOf(WeaponProperty.FINESSE, WeaponProperty.LIGHT),
        ),
        weapon(
            "spada-corta", "Spada corta", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 6, DamageType.PIERCING, mastery = "Vessazione",
            properties = setOf(WeaponProperty.FINESSE, WeaponProperty.LIGHT),
        ),
        weapon(
            "spada-lunga", "Spada lunga", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 8, DamageType.SLASHING, mastery = "Fiaccare",
            properties = setOf(WeaponProperty.VERSATILE), versatileDiceSides = 10,
        ),
        weapon(
            "spadone", "Spadone", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            2, 6, DamageType.SLASHING, mastery = "Colpo di striscio",
            properties = setOf(WeaponProperty.TWO_HANDED, WeaponProperty.HEAVY),
        ),
        weapon(
            "stocco", "Stocco", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 8, DamageType.PIERCING, mastery = "Vessazione",
            properties = setOf(WeaponProperty.FINESSE),
        ),
        weapon(
            "tridente", "Tridente", WeaponCategory.MARTIAL, WeaponReach.MELEE,
            1, 8, DamageType.PIERCING, mastery = "Rovesciamento",
            properties = setOf(WeaponProperty.THROWN, WeaponProperty.VERSATILE),
            normalRangeFeet = 20, longRangeFeet = 60, versatileDiceSides = 10,
        ),

        // --- Armi a distanza da guerra ---
        weapon(
            "arco-lungo", "Arco lungo", WeaponCategory.MARTIAL, WeaponReach.RANGED,
            1, 8, DamageType.PIERCING, mastery = "Lentezza",
            properties = setOf(
                WeaponProperty.TWO_HANDED,
                WeaponProperty.AMMUNITION,
                WeaponProperty.HEAVY,
            ),
            normalRangeFeet = 150, longRangeFeet = 600,
        ),
        weapon(
            "balestra-a-mano", "Balestra a mano", WeaponCategory.MARTIAL, WeaponReach.RANGED,
            1, 6, DamageType.PIERCING, mastery = "Vessazione",
            properties = setOf(
                WeaponProperty.LIGHT,
                WeaponProperty.AMMUNITION,
                WeaponProperty.LOADING,
            ),
            normalRangeFeet = 30, longRangeFeet = 120,
        ),
        weapon(
            "balestra-pesante", "Balestra pesante", WeaponCategory.MARTIAL, WeaponReach.RANGED,
            1, 10, DamageType.PIERCING, mastery = "Spinta",
            properties = setOf(
                WeaponProperty.TWO_HANDED,
                WeaponProperty.AMMUNITION,
                WeaponProperty.HEAVY,
                WeaponProperty.LOADING,
            ),
            normalRangeFeet = 100, longRangeFeet = 400,
        ),
        // Lo SRD assegna alla cerbottana un danno fisso di 1, che non raddoppia col critico.
        weapon(
            "cerbottana", "Cerbottana", WeaponCategory.MARTIAL, WeaponReach.RANGED,
            1, 1, DamageType.PIERCING, mastery = "Vessazione",
            properties = setOf(WeaponProperty.AMMUNITION, WeaponProperty.LOADING),
            normalRangeFeet = 25, longRangeFeet = 100,
            fixedDamage = 1,
        ),
        weapon(
            "moschetto", "Moschetto", WeaponCategory.MARTIAL, WeaponReach.RANGED,
            1, 12, DamageType.PIERCING, mastery = "Lentezza",
            properties = setOf(
                WeaponProperty.TWO_HANDED,
                WeaponProperty.AMMUNITION,
                WeaponProperty.LOADING,
            ),
            normalRangeFeet = 40, longRangeFeet = 120,
        ),
        weapon(
            "pistola", "Pistola", WeaponCategory.MARTIAL, WeaponReach.RANGED,
            1, 10, DamageType.PIERCING, mastery = "Vessazione",
            properties = setOf(WeaponProperty.AMMUNITION, WeaponProperty.LOADING),
            normalRangeFeet = 30, longRangeFeet = 90,
        ),
    )

    /**
     * La tabella nella lingua richiesta.
     *
     * L'edizione inglese non e' una seconda tabella: e' la stessa, con nome e
     * Padronanza sostituiti. Tenerla derivata invece che duplicata vuol dire
     * che una correzione ai danni o alla gittata vale per entrambe, e che
     * un'arma aggiunta senza la sua resa inglese non compila piu' — la
     * `getValue` qui sotto solleva, invece di lasciar passare un nome italiano
     * in mezzo a una scheda inglese.
     */
    fun all(language: AppLanguage = AppLanguage.ITALIAN): List<WeaponDefinition> = when (language) {
        AppLanguage.ITALIAN -> italian
        AppLanguage.ENGLISH -> english
    }

    private val english: List<WeaponDefinition> by lazy {
        italian.map { weapon ->
            val slug = weapon.id.substringAfterLast(':')
            weapon.copy(
                name = ENGLISH_WEAPON_NAMES.getValue(slug),
                mastery = ENGLISH_MASTERY.getValue(weapon.mastery),
            )
        }
    }

    private val indexed: Map<AppLanguage, Map<String, WeaponDefinition>> by lazy {
        AppLanguage.entries.associateWith { language -> all(language).associateBy { it.id } }
    }

    fun byId(id: String, language: AppLanguage = AppLanguage.ITALIAN): WeaponDefinition? =
        indexed.getValue(language)[id]

    /** Le armi che la classe sa impugnare, nell'ordine della tabella dello SRD. */
    fun trainedBy(
        grant: WeaponTrainingGrant,
        language: AppLanguage = AppLanguage.ITALIAN,
    ): List<WeaponDefinition> =
        all(language).filter { grant.allows(it.category, it.properties) }
}

// Chiave: lo slug dell'identificativo, che resta italiano in entrambe le edizioni.
private val ENGLISH_WEAPON_NAMES = mapOf(
    "ascia" to "Handaxe",
    "bastone-ferrato" to "Quarterstaff",
    "falcetto" to "Sickle",
    "giavellotto" to "Javelin",
    "lancia" to "Spear",
    "martello-leggero" to "Light Hammer",
    "mazza" to "Mace",
    "pugnale" to "Dagger",
    "randello-pesante" to "Greatclub",
    "randello" to "Club",
    "arco-corto" to "Shortbow",
    "balestra-leggera" to "Light Crossbow",
    "dardo" to "Dart",
    "fionda" to "Sling",
    "alabarda" to "Halberd",
    "ascia-bipenne" to "Greataxe",
    "ascia-da-battaglia" to "Battleaxe",
    "falcione" to "Glaive",
    "frusta" to "Whip",
    "lancia-da-cavaliere" to "Lance",
    "maglio" to "Maul",
    "martello-da-guerra" to "Warhammer",
    "mazza-chiodata" to "Morningstar",
    "mazzafrusto" to "Flail",
    "picca" to "Pike",
    "piccone-da-guerra" to "War Pick",
    "scimitarra" to "Scimitar",
    "spada-corta" to "Shortsword",
    "spada-lunga" to "Longsword",
    "spadone" to "Greatsword",
    "stocco" to "Rapier",
    "tridente" to "Trident",
    "arco-lungo" to "Longbow",
    "balestra-a-mano" to "Hand Crossbow",
    "balestra-pesante" to "Heavy Crossbow",
    "cerbottana" to "Blowgun",
    "moschetto" to "Musket",
    "pistola" to "Pistol",
)

// Le otto proprieta' di Padronanza. Verificate contro l'arma che le porta:
// il Falcetto ha Graffio e in inglese ha Nick, il Bastone ferrato Rovesciamento
// e Topple — una coppia sbagliata qui si vedrebbe sulla tabella delle armi.
private val ENGLISH_MASTERY = mapOf(
    "Colpo di striscio" to "Graze",
    "Doppio fendente" to "Cleave",
    "Fiaccare" to "Sap",
    "Graffio" to "Nick",
    "Lentezza" to "Slow",
    "Rovesciamento" to "Topple",
    "Spinta" to "Push",
    "Vessazione" to "Vex",
)
