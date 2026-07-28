package app.d6d.sheet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RulesTextTest {

    @Test
    fun `le righe spezzate a meta' frase tornano un paragrafo unico`() {
        val impaginato = """
            Il personaggio si è allenato per sferrare colpi particolarmente letali. Una volta per turno, quando
            colpisce un bersaglio con un'arma, puoi tirare due volte per i danni dell'arma e scegliere il risultato
            che preferisci.
        """.trimIndent()

        assertEquals(
            "Il personaggio si è allenato per sferrare colpi particolarmente letali. " +
                "Una volta per turno, quando colpisce un bersaglio con un'arma, puoi tirare " +
                "due volte per i danni dell'arma e scegliere il risultato che preferisci.",
            impaginato.reflowRulesText(),
        )
    }

    @Test
    fun `una riga che riparte in maiuscolo resta un capoverso`() {
        val testo = """
            Il barbaro è addestrato nell'uso delle armi, e per questo può avvalersi della proprietà
            di padronanza per due tipi di armi.
            Quando si raggiungono determinati livelli da barbaro, si ottiene la capacità di
            utilizzare le proprietà di padronanza di più tipi di armi.
        """.trimIndent()

        val flowed = testo.reflowRulesText()

        assertEquals(2, flowed.lines().size, flowed)
        assertTrue(flowed.lines()[0].endsWith("due tipi di armi."), flowed)
        assertTrue(flowed.lines()[1].startsWith("Quando si raggiungono"), flowed)
    }

    @Test
    fun `i capoversi separati da riga vuota restano distinti`() {
        val testo = """
            Il personaggio ottiene i seguenti benefici.

            Competenza in iniziativa. Quando tiri per l'iniziativa, puoi aggiungere il bonus
            di competenza al risultato del tiro.
        """.trimIndent()

        val flowed = testo.reflowRulesText()

        assertTrue(flowed.contains("\n\n"), flowed)
        assertEquals("Il personaggio ottiene i seguenti benefici.", flowed.substringBefore("\n\n"))
    }

    @Test
    fun `l'estratto tiene le prime frasi e lascia fuori i rimandi al manuale`() {
        val incantesimi = """
            Il paladino impara a lanciare incantesimi attraverso la preghiera e la meditazione.
            Consulta il capitolo "Incantesimi" per le regole relative al lancio degli incantesimi.
            Le informazioni sottostanti spiegano come applicare tali regole agli incantesimi da
            paladino, elencati nella relativa lista degli incantesimi più avanti nella descrizione
            della classe.
        """.trimIndent()

        val lead = incantesimi.rulesTextLead()

        assertEquals(
            "Il paladino impara a lanciare incantesimi attraverso la preghiera e la meditazione.",
            lead,
        )
    }

    @Test
    fun `l'estratto salta l'attacco che annuncia soltanto un elenco`() {
        val allerta = """
            Il personaggio ottiene i seguenti benefici.

            Competenza in iniziativa. Quando tiri per l'iniziativa, puoi aggiungere il bonus
            di competenza del personaggio al risultato del tiro.
        """.trimIndent()

        assertTrue(
            allerta.rulesTextLead().startsWith("Competenza in iniziativa."),
            allerta.rulesTextLead(),
        )
    }

    @Test
    fun `l'estratto tiene la frase che spiega la meccanica`() {
        val aggressore = """
            Il personaggio si è allenato per sferrare colpi particolarmente letali. Una volta per turno, quando
            colpisce un bersaglio con un'arma, puoi tirare due volte per i danni dell'arma e scegliere il risultato
            che preferisci.
        """.trimIndent()

        assertTrue(
            aggressore.rulesTextLead().contains("puoi tirare due volte per i danni"),
            aggressore.rulesTextLead(),
        )
    }

    @Test
    fun `un rimando fra parentesi sparisce ma la frase che lo ospita resta`() {
        val stile = "Il guerriero ha affinato le sue doti marziali e ottiene un talento " +
            "Stile di combattimento a scelta (vedi capitolo \"Talenti\"). È consigliata la Difesa."

        assertEquals(
            "Il guerriero ha affinato le sue doti marziali e ottiene un talento " +
                "Stile di combattimento a scelta. È consigliata la Difesa.",
            stile.rulesTextLead(),
        )
    }

    @Test
    fun `l'estratto si ferma alla prima frase che non entra`() {
        val testo = "Prima frase corta. " +
            "Seconda frase molto lunga che da sola supera abbondantemente il limite imposto " +
            "all'estratto e quindi non ci può stare. Terza corta."

        val lead = testo.rulesTextLead(maxCharacters = 60)

        assertEquals("Prima frase corta.", lead)
    }

    @Test
    fun `un estratto piu' corto del limite resta intatto`() {
        val breve = "Quando effettua l'azione di Attacco nel proprio turno, può attaccare due volte."

        assertEquals(breve, breve.rulesTextLead())
    }

    @Test
    fun `una prima frase piu' lunga del limite si taglia a parola intera`() {
        val lunga = "Parola ".repeat(60).trim() + "."

        val lead = lunga.rulesTextLead(maxCharacters = 50)

        assertTrue(lead.length <= 51, lead)
        assertTrue(lead.endsWith("…"), lead)
        assertTrue(lead.dropLast(1).endsWith("Parola"), lead)
    }
}
