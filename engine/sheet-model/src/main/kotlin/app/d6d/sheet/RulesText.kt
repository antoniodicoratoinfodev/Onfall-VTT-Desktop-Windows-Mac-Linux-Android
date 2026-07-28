package app.d6d.sheet

/**
 * Ricompone le righe spezzate dalla formattazione del sorgente.
 *
 * Le descrizioni arrivano da testi impaginati a colonne: una frase può essere
 * interrotta a metà da un a capo che non significa nulla, e reso com'è produce
 * un paragrafo sfrangiato. Una riga che riprende in minuscolo (o dopo una
 * virgola) è la continuazione di quella precedente e viene ricucita; quando la
 * riga nuova comincia per maiuscola resta un capoverso, perché lì l'a capo
 * separava davvero due blocchi.
 */
fun String.reflowRulesText(): String {
    val lines = trim().lines().map { it.trim() }
    val flowed = StringBuilder()
    lines.forEach { line ->
        if (line.isEmpty()) {
            if (flowed.isNotEmpty()) flowed.append("\n\n")
            return@forEach
        }
        val continuesSentence = flowed.isNotEmpty() &&
            !flowed.endsWith("\n\n") &&
            (line.first().isLowerCase() || flowed.trimEnd().lastOrNull() == ',')
        when {
            flowed.isEmpty() || flowed.endsWith("\n\n") -> flowed.append(line)
            continuesSentence -> flowed.append(' ').append(line)
            else -> flowed.append('\n').append(line)
        }
    }
    return flowed.toString().replace(Regex("\n{3,}"), "\n\n").trim()
}

/**
 * Estratto da mostrare dove c'è spazio per una riga o due, non per una pagina.
 *
 * Serve a rispondere a «cosa fa?» in un colpo d'occhio, quindi scarta due cose
 * che nel manuale hanno senso e in un riquadro no: gli attacchi che annunciano
 * un elenco senza dire nulla ("Il personaggio ottiene i seguenti benefici") e i
 * rimandi ad altre pagine ("Consulta il capitolo…"). Il resto viene tenuto in
 * ordine finché entra nel limite. Il testo integrale resta nel Compendio.
 */
fun String.rulesTextLead(maxCharacters: Int = 300): String {
    // Dopo la ricucitura ogni a capo rimasto separa davvero due blocchi, che
    // arrivi da una riga vuota (testi scritti a mano) o da un solo ritorno a
    // capo (testi estratti dal PDF).
    val paragraphs = reflowRulesText()
        .split(Regex("\n+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (paragraphs.isEmpty()) return ""

    val body = paragraphs.dropWhile { it.announcesAList() }.ifEmpty { paragraphs }
    val sentences = body.first()
        .splitIntoSentences()
        .map { it.withoutCrossReferences() }
        .filter { it.isNotEmpty() }
    val meaningful = sentences.filterNot { it.pointsElsewhere() }.ifEmpty { sentences }

    // Ci si ferma alla prima frase che non entra: saltarla per agganciare quella
    // dopo produrrebbe un salto logico, non un riassunto.
    val lead = StringBuilder(meaningful.first())
    for (sentence in meaningful.drop(1)) {
        if (lead.length + 1 + sentence.length > maxCharacters) break
        lead.append(' ').append(sentence)
    }
    val text = lead.toString()
    if (text.length <= maxCharacters) return text

    // Una prima frase già più lunga del limite: si taglia all'ultima parola intera.
    return text.take(maxCharacters).substringBeforeLast(' ').trimEnd(',', ';') + "…"
}

private fun String.splitIntoSentences(): List<String> =
    Regex("(?<=[.!?])\\s+").split(this).map { it.trim() }.filter { it.isNotEmpty() }

/** "Il personaggio ottiene i seguenti benefici." e simili: annunciano, non spiegano. */
private fun String.announcesAList(): Boolean {
    val normalized = lowercase()
    return endsWith(":") ||
        normalized.endsWith("i seguenti benefici.") ||
        normalized.endsWith("le seguenti opzioni.")
}

/**
 * Toglie gli incisi che rimandano al manuale — «(vedi capitolo "Talenti")» —
 * lasciando intatta la frase che li ospita: lì il rimando è un'aggiunta, non il
 * contenuto.
 */
private fun String.withoutCrossReferences(): String =
    replace(Regex("""\s*\((?:vedi|consulta)\b[^)]*\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s{2,}"), " ")
        .replace(Regex("\\s+([.,;:])"), "$1")
        .trim()

/** Frase che rimanda altrove invece di dire cosa succede al tavolo. */
private fun String.pointsElsewhere(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("consulta ") ||
        normalized.startsWith("vedi ") ||
        normalized.startsWith("le informazioni sottostanti") ||
        "come indicato nella colonna" in normalized ||
        "come indicato nella tabella" in normalized
}
