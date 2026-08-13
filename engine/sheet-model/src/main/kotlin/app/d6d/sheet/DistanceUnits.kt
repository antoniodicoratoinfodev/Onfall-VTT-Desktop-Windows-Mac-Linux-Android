package app.d6d.sheet

/**
 * Distanze in metri, con i valori del regolamento italiano.
 *
 * Il motore misura in piedi interi — la griglia vive di `feetPerSquare`, e 1,5
 * non e' un intero — ma nessuno al tavolo li vede: tutto cio' che arriva sullo
 * schermo passa da qui e parla in metri.
 *
 * La conversione non e' quella esatta del piede internazionale (0,3048 m) bensi'
 * quella del regolamento: **5 piedi = 1,5 metri**, cioe' tre metri ogni dieci
 * piedi. E' la stessa che il content pack SRD usa gia' nei propri testi — 6 m
 * per un raggio di 20 piedi, 18 m per una scurovisione di 60 — e adottarla qui
 * fa combaciare le due meta' dell'applicazione. La conversione esatta dava
 * invece 1,524 e 18,288: numeri giusti in fisica e sbagliati a un tavolo di
 * gioco, dove nessuna tabella li nomina.
 */
private const val METRE_TENTHS_PER_FOOT = 3L

/**
 * Valore metrico senza unita': `"1,5"`, `"9"`, `"45"`.
 *
 * La virgola e' quella italiana e i decimali nulli non si scrivono, cosi' 30
 * piedi restano `"9"` e non `"9,0"`.
 */
fun metresFromFeet(feet: Int): String {
    val tenths = feet.toLong() * METRE_TENTHS_PER_FOOT
    val sign = if (tenths < 0) "−" else ""
    val absolute = kotlin.math.abs(tenths)
    val whole = absolute / 10L
    val fraction = absolute % 10L
    return if (fraction == 0L) "$sign$whole" else "$sign$whole,$fraction"
}

/** Distanza pronta da mostrare, unita' compresa: `"1,5 m"`. */
fun metresLabel(feet: Int): String = "${metresFromFeet(feet)} m"

/**
 * Riporta ai piedi del motore una misura scritta in metri.
 *
 * Si arrotonda al piede piu' vicino: ogni misura che il regolamento nomina e'
 * multipla di 1,5 m, quindi il giro di andata e ritorno e' esatto per tutte
 * (1,5 → 5 → 1,5). Un valore inventato a mano puo' invece spostarsi di poco,
 * che e' preferibile a rifiutarlo.
 */
fun feetFromMetres(metres: Double): Int =
    Math.round(metres * 10.0 / METRE_TENTHS_PER_FOOT).toInt()

/** Legge una misura scritta dall'utente, con la virgola o con il punto. */
fun parseMetres(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()

/**
 * Converte in metri le distanze che un testo regolamentare esprime in piedi.
 *
 * Sostituisce invece di affiancare: una scheda che dicesse «Gittata 30/120 piedi
 * (9/36 m)» costringerebbe a leggere due volte la stessa misura. Le gittate
 * doppie restano tali, `30/120 piedi` diventa `9/36 m`. Un testo gia' scritto in
 * metri non viene toccato, perche' nulla vi corrisponde.
 */
fun String.withMetricDistances(): String {
    val distance = Regex("""(-?\d+(?:\s*/\s*-?\d+)*)\s*(?:piedi|ft\.?)""", RegexOption.IGNORE_CASE)
    return replace(distance) { match ->
        val metric = match.groupValues[1]
            .split(Regex("\\s*/\\s*"))
            .joinToString("/") { metresFromFeet(it.toInt()) }
        "$metric m"
    }
}
