package app.d6d.sheet

/**
 * Equivalente metrico esatto di un numero intero di piedi, con la virgola italiana.
 *
 * Un piede internazionale equivale esattamente a 0,3048 metri. Si eliminano solo
 * gli zeri finali, senza arrotondare una distanza a un valore meno preciso.
 */
fun metresFromFeet(feet: Int): String {
    val tenThousandths = feet.toLong() * 3048L
    val sign = if (tenThousandths < 0) "−" else ""
    val absolute = kotlin.math.abs(tenThousandths)
    val whole = absolute / 10_000L
    val fraction = (absolute % 10_000L).toString().padStart(4, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole,$fraction"
}

/** Testo compatto che conserva i piedi e aggiunge il loro equivalente metrico. */
fun feetWithMetres(feet: Int, feetUnit: String = "piedi"): String =
    "$feet $feetUnit (${metresFromFeet(feet)} m)"

/** Aggiunge i metri ai testi regolamentari che contengono piedi o ft., incluse le gittate 30/120. */
fun String.withMetricFeet(): String {
    val distance = Regex("""(-?\d+(?:\s*/\s*-?\d+)*)\s*(piedi|ft\.?)""", RegexOption.IGNORE_CASE)
    return replace(distance) { match ->
        val metric = match.groupValues[1]
            .split(Regex("\\s*/\\s*"))
            .joinToString("/") { metresFromFeet(it.toInt()) }
        "${match.value} ($metric m)"
    }
}
