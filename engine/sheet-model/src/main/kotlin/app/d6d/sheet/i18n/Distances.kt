package app.d6d.sheet.i18n

import app.d6d.i18n.AppLanguage
import app.d6d.i18n.pick
import app.d6d.sheet.feetFromMetres
import app.d6d.sheet.metresFromFeet
import app.d6d.sheet.MonsterSpeeds

/**
 * Le distanze cambiano unita' con la lingua, non solo parola.
 *
 * Tradurre «9 m» in «9 m» sarebbe una traduzione a meta': il regolamento italiano
 * misura in metri, quello inglese in piedi, e un tavolo che gioca in inglese legge
 * «30 feet» su ogni manuale che ha in mano. Convertirlo in metri lo costringerebbe
 * a fare i conti a mente per confrontare una gittata con la propria tabella.
 *
 * Il motore resta l'unico metro di misura interno e conta sempre in piedi interi:
 * qui si converte solo cio' che va sullo schermo, e solo all'ultimo momento.
 */

/** Il valore nudo, senza unita': `"1,5"` in italiano, `"5"` in inglese. */
fun distanceValue(feet: Int, language: AppLanguage): String = when (language) {
    AppLanguage.ITALIAN -> metresFromFeet(feet)
    // Il segno meno tipografico tiene allineate le due lingue: il motore non
    // produce mai distanze negative, ma un campo modificabile puo' passarne una.
    AppLanguage.ENGLISH -> if (feet < 0) "−${-feet}" else "$feet"
}

/**
 * Unita' di misura da sola, per l'etichetta di un campo.
 *
 * `ft` e non `ft.`: la sigla finisce dentro le frasi, e un punto suo si
 * sommerebbe a quello del periodo — «area di 20 ft..». I manuali scrivono
 * «5 ft.» in tabella e «5 feet» in prosa; qui la stessa stringa deve servire a
 * entrambe, e senza punto funziona in tutte e due.
 */
fun distanceUnit(language: AppLanguage): String = when (language) {
    AppLanguage.ITALIAN -> "m"
    AppLanguage.ENGLISH -> "ft"
}

/** Distanza pronta da mostrare, unita' compresa: `"1,5 m"` / `"5 ft"`. */
fun distanceLabel(feet: Int, language: AppLanguage): String =
    "${distanceValue(feet, language)} ${distanceUnit(language)}"

/** Riga delle velocita' di uno stat block, completa e localizzata. */
fun MonsterSpeeds.label(language: AppLanguage): String = buildString {
    append(distanceLabel(walk, language))
    if (fly > 0) {
        append(", ").append(language.pick("Volo", "Fly")).append(' ')
            .append(distanceLabel(fly, language))
        if (hover) append(language.pick(" (fluttua)", " (hover)"))
    }
    if (swim > 0) append(", ${language.pick("Nuoto", "Swim")} ${distanceLabel(swim, language)}")
    if (climb > 0) append(", ${language.pick("Scalata", "Climb")} ${distanceLabel(climb, language)}")
    if (burrow > 0) append(", ${language.pick("Scavo", "Burrow")} ${distanceLabel(burrow, language)}")
}

/**
 * Riporta ai piedi del motore una misura scritta da chi gioca.
 *
 * In inglese il giro e' l'identita': si scrivono piedi e piedi restano. In
 * italiano si passa dalla conversione del regolamento, che e' esatta su tutte le
 * misure che il regolamento nomina (1,5 → 5 → 1,5).
 */
fun feetFromDistance(value: Double, language: AppLanguage): Int = when (language) {
    AppLanguage.ITALIAN -> feetFromMetres(value)
    AppLanguage.ENGLISH -> Math.round(value).toInt()
}

/** Legge una misura scritta a mano, con la virgola o con il punto. */
fun parseDistance(text: String): Double? = text
    .trim()
    .replace('−', '-')
    .replace(',', '.')
    .toDoubleOrNull()

/**
 * Converte le distanze citate dentro un testo regolamentare.
 *
 * Riconosce entrambe le scritture — «30 piedi» e «30 ft.» — perche' il testo puo'
 * arrivare da un content pack italiano o inglese, e la lingua di chi legge non
 * dice quella di chi ha scritto. Le gittate doppie restano doppie: `30/120 piedi`
 * diventa `9/36 m` oppure `30/120 ft`
 *
 * Sostituisce invece di affiancare: una riga che dicesse «Gittata 30/120 piedi
 * (9/36 m)» costringerebbe a leggere due volte la stessa misura.
 */
fun String.withLocalizedDistances(language: AppLanguage): String {
    val distance = Regex(
        """(-?\d+(?:\s*/\s*-?\d+)*)\s*(?:piedi|feet|ft\.?)""",
        RegexOption.IGNORE_CASE,
    )
    return replace(distance) { match ->
        // Un numero piu' grande di quanto un Int contenga non e' una gittata: e'
        // testo che qualcuno ha scritto nella descrizione di una capacita'. Non
        // deve far fallire il disegno della scheda, e nemmeno essere convertito
        // in qualcosa che non significa niente: si lascia dov'e'.
        val numbers = match.groupValues[1].split(Regex("""\s*/\s*"""))
        val converted = numbers.map { it.toIntOrNull() }
        if (converted.any { it == null }) {
            match.value
        } else {
            converted.joinToString("/") { distanceValue(it!!, language) } +
                " ${distanceUnit(language)}"
        }
    }
}
