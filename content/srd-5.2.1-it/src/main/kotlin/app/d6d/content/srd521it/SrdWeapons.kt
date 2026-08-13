package app.d6d.content.srd521it

import app.d6d.domain.combat.DamageType
import app.d6d.rules.character.WeaponCategory
import app.d6d.rules.character.WeaponDefinition
import app.d6d.rules.character.WeaponProperty
import app.d6d.rules.character.WeaponReach
import app.d6d.rules.character.WeaponTrainingGrant
import app.d6d.sheet.italianLabel
import app.d6d.sheet.metresFromFeet
import app.d6d.sheet.metresLabel

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
val WeaponDefinition.damageText: String
    get() = "${if (fixedDamage > 0) fixedDamage else "${diceCount}d$diceSides"} ${damageType.italianLabel}"

/** Riga leggibile usata dai selettori: danno, gittata, Padronanza e proprietà. */
val WeaponDefinition.summary: String
    get() = buildString {
        append(damageText)
        when {
            reach == WeaponReach.RANGED ->
                append(" · gittata ")
                    .append(metresFromFeet(normalRangeFeet))
                    .append('/')
                    .append(metresLabel(longRangeFeet))
            WeaponProperty.REACH in properties -> append(" · portata")
        }
        append(" · Padronanza: ").append(mastery)
        if (properties.isNotEmpty()) {
            append(" · ").append(properties.joinToString { it.italianLabel })
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

    val all: List<WeaponDefinition> = listOf(
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

    private val indexed: Map<String, WeaponDefinition> = all.associateBy { it.id }

    fun byId(id: String): WeaponDefinition? = indexed[id]

    /** Le armi che la classe sa impugnare, nell'ordine della tabella dello SRD. */
    fun trainedBy(grant: WeaponTrainingGrant): List<WeaponDefinition> =
        all.filter { grant.allows(it.category, it.properties) }
}
