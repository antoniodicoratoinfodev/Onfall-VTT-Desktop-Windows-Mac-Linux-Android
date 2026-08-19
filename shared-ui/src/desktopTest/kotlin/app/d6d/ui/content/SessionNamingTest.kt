package app.d6d.ui.content

import app.d6d.i18n.AppLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * I nomi dentro una partita gia' avviata devono seguire la lingua.
 *
 * Un combattente entra nella sessione come copia, col nome scritto una volta
 * sola: senza questa risoluzione si finiva per giocare in inglese contro un
 * «Cane di palude» che sferrava un'«Ascia bipenne».
 *
 * L'altra meta' della prova conta quanto la prima, ed e' la stessa che governa
 * schede e bestiario: cio' che il tavolo si e' scritto da se' non si tocca.
 */
class SessionNamingTest {

    private val marshCur = TemplateBestiary.of(AppLanguage.ITALIAN).all
        .first { it.name == "Cane di palude" }

    @Test
    fun `una creatura inclusa passa all'altra lingua`() {
        val english = SessionNaming.combatantName(
            definitionId = marshCur.id,
            storedName = "Cane di palude",
            language = AppLanguage.ENGLISH,
        )
        assertEquals("Marsh Cur", english)
    }

    @Test
    fun `e torna indietro`() {
        val italian = SessionNaming.combatantName(
            definitionId = marshCur.id,
            storedName = "Marsh Cur",
            language = AppLanguage.ITALIAN,
        )
        assertEquals("Cane di palude", italian)
    }

    /** Le quantita' del wizard numerano le copie: il numero non e' contenuto. */
    @Test
    fun `l'ordinale della copia sopravvive alla traduzione`() {
        val english = SessionNaming.combatantName(
            definitionId = marshCur.id,
            storedName = "Cane di palude 2",
            language = AppLanguage.ENGLISH,
        )
        assertEquals("Marsh Cur 2", english)
    }

    @Test
    fun `un nome scelto dal tavolo non viene toccato`() {
        val untouched = SessionNaming.combatantName(
            definitionId = marshCur.id,
            storedName = "Mordicchio",
            language = AppLanguage.ENGLISH,
        )
        assertEquals("Mordicchio", untouched)
    }

    /**
     * Un identificativo che non appartiene ad alcun contenuto incluso non ha una
     * forma canonica da confrontare: si lascia stare, non si indovina.
     */
    @Test
    fun `una creatura inventata resta com'e`() {
        val untouched = SessionNaming.combatantName(
            definitionId = "creatura-di-casa",
            storedName = "Il Vicino",
            language = AppLanguage.ENGLISH,
        )
        assertEquals("Il Vicino", untouched)
    }

    @Test
    fun `un personaggio delle partite incluse segue la lingua`() {
        val plans = SessionTemplates.of(AppLanguage.ITALIAN).all
            .flatMap { it.partyNames.entries }
        val (id, italianName) = plans.first()
        val englishName = SessionTemplates.of(AppLanguage.ENGLISH).all
            .flatMap { it.partyNames.entries }
            .first { it.key == id }
            .value

        val translated = SessionNaming.combatantName(
            definitionId = id,
            storedName = italianName,
            language = AppLanguage.ENGLISH,
        )
        assertEquals(englishName, translated)
    }

    @Test
    fun `una capacita' SRD segue la lingua`() {
        val italian = srdCatalogFor(AppLanguage.ITALIAN).first { it.name.isNotBlank() }
        val english = srdCatalogFor(AppLanguage.ENGLISH).first { it.id == italian.id }

        val translated = SessionNaming.abilityName(
            abilityId = italian.id,
            storedName = italian.name,
            language = AppLanguage.ENGLISH,
        )
        assertEquals(english.name, translated)
    }

    @Test
    fun `una capacita' rinominata dal tavolo non viene toccata`() {
        val italian = srdCatalogFor(AppLanguage.ITALIAN).first { it.name.isNotBlank() }

        val untouched = SessionNaming.abilityName(
            abilityId = italian.id,
            storedName = "Colpo del nonno",
            language = AppLanguage.ENGLISH,
        )
        assertEquals("Colpo del nonno", untouched)
    }

    /** Le armi entrano fra le capacita' ma vivono in un elenco tutto loro. */
    @Test
    fun `un'arma SRD segue la lingua`() {
        val translated = SessionNaming.abilityName(
            abilityId = "srd521-it:weapon:arco-lungo",
            storedName = "Arco lungo",
            language = AppLanguage.ENGLISH,
        )
        assertEquals("Longbow", translated)
    }

    /**
     * Le armi di una scheda diventano capacita' con un id locale
     * (`pg-aelis-arma-0`): non c'e' identificativo da cercare, resta il nome.
     */
    @Test
    fun `un'arma di scheda si riconosce dal nome`() {
        val translated = SessionNaming.abilityName(
            abilityId = "pg-aelis-arma-0",
            storedName = "Arco lungo",
            language = AppLanguage.ENGLISH,
        )
        assertEquals("Longbow", translated)
    }

    @Test
    fun `un'arma battezzata dal tavolo non viene toccata`() {
        val untouched = SessionNaming.abilityName(
            abilityId = "pg-aelis-arma-0",
            storedName = "Mordilupo",
            language = AppLanguage.ENGLISH,
        )
        assertEquals("Mordilupo", untouched)
    }

    @Test
    fun `il testo di regole segue la lingua`() {
        val italian = srdCatalogFor(AppLanguage.ITALIAN).first { it.rulesText.isNotBlank() }
        val english = srdCatalogFor(AppLanguage.ENGLISH).first { it.id == italian.id }

        val translated = SessionNaming.abilityRulesText(
            abilityId = italian.id,
            storedText = italian.rulesText,
            language = AppLanguage.ENGLISH,
        )
        assertEquals(english.rulesText, translated)
    }

    @Test
    fun `una regola riscritta dal tavolo non viene toccata`() {
        val italian = srdCatalogFor(AppLanguage.ITALIAN).first { it.rulesText.isNotBlank() }

        val untouched = SessionNaming.abilityRulesText(
            abilityId = italian.id,
            storedText = "Come dice il nonno.",
            language = AppLanguage.ENGLISH,
        )
        assertEquals("Come dice il nonno.", untouched)
    }
}
