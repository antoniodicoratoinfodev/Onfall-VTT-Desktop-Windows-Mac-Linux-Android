package app.d6d.ui.state

import app.d6d.ui.content.SampleEncounter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import app.d6d.i18n.AppLanguage
import app.d6d.ui.i18n.ItalianStrings

/**
 * I rifiuti del motore arrivano al tavolo in italiano.
 *
 * Le prove non passano stringhe inventate alla tabella: guidano il motore in
 * violazioni reali e leggono `message` come lo leggerebbe l'interfaccia. Una
 * riformulazione del testo inglese fa quindi fallire la prova invece di far
 * ricomparire l'inglese sullo schermo.
 */
class CombatMessagesTest {

    private fun model() = BattleViewModel(SampleEncounter.startedSession())

    @Test
    fun `una capacita' sconosciuta e' annunciata in italiano`() {
        val model = model()

        model.attack("capacita-inesistente")

        assertEquals("Capacità sconosciuta: capacita-inesistente", model.message)
    }

    @Test
    fun `un combattente sconosciuto conserva il proprio identificativo`() {
        val model = model()

        model.heal("nessuno", 5)

        assertEquals("Combattente sconosciuto: nessuno", model.message)
    }

    @Test
    fun `una cura non positiva e' rifiutata in italiano`() {
        val model = model()
        val target = model.partyIds.first()

        model.heal(target, 0)

        assertEquals("La cura deve essere di almeno 1 punto ferita.", model.message)
    }

    @Test
    fun `spostare chi non e' di turno nomina il combattente`() {
        val model = model()
        val other = model.state.combatants().keys.first { it != model.activeCombatantId }

        model.move(other, 2, 2)

        assertEquals("Non è il turno di $other.", model.message)
    }

    @Test
    fun `una casella negativa e' rifiutata in italiano`() {
        val model = model()

        model.place(model.partyIds.first(), -1, 0, 1)

        assertEquals("Le coordinate della griglia non possono essere negative.", model.message)
    }

    @Test
    fun `una destinazione fuori griglia e' rifiutata in italiano`() {
        val model = model()

        model.place(model.partyIds.first(), 999, 999, 1)

        assertEquals("Il segnaposto non entra nella griglia.", model.message)
    }

    @Test
    fun `il limite del lato della griglia e' annunciato in caselle`() {
        val model = model()

        model.configureMap(9_999, 10, 5)

        assertEquals("Il lato della griglia supera il limite di 400 caselle.", model.message)
    }

    @Test
    fun `nessun avviso resta in inglese dopo una violazione`() {
        val model = model()

        model.attack("capacita-inesistente")

        val avviso = model.message
        assertNotNull(avviso)
        assertFalse(
            avviso.orEmpty().contains("Unknown"),
            "L'avviso mostrato al tavolo non deve contenere il testo inglese del motore.",
        )
    }

    // --- tabella ----------------------------------------------------------------------

    @Test
    fun `le distanze fuori gittata sono annunciate in metri`() {
        assertEquals(
            "Il bersaglio è a 18 m: oltre la gittata di 1,5 m della capacità.",
            translateRuleMessage("Target is 60 feet away, beyond the ability range of 5 feet", AppLanguage.ITALIAN),
        )
        assertEquals(
            "Il centro dell'area è a 60 m: oltre la gittata di 45 m.",
            translateRuleMessage("The area centre is 200 feet away, beyond the range of 150 feet", AppLanguage.ITALIAN),
        )
    }

    @Test
    fun `lo stato richiesto dal comando e' tradotto in parole`() {
        assertEquals(
            "Il comando richiede uno scontro in corso, ma questo è in preparazione.",
            translateRuleMessage("Command requires ACTIVE but encounter is DRAFT", AppLanguage.ITALIAN),
        )
    }

    @Test
    fun `un messaggio sconosciuto passa immutato`() {
        val ignoto = "A brand new rule the engine learned yesterday"

        assertEquals(ignoto, translateRuleMessage(ignoto, AppLanguage.ITALIAN))
        assertNull(ruleMessage(null)?.resolve(ItalianStrings))
    }

    @Test
    fun `un messaggio gia' italiano non viene toccato`() {
        val italiano = "Le coordinate dello sfondo devono essere numeri finiti"

        assertEquals(italiano, translateRuleMessage(italiano, AppLanguage.ITALIAN))
    }

    @Test
    fun `nessuna traduzione e' vuota o uguale all originale`() {
        val campione = listOf(
            "Action already spent",
            "Bonus action already spent",
            "A dead combatant cannot be healed",
            "That space is already occupied",
            "Movement exceeds the remaining budget",
            "A spell slot was already spent this turn",
        )

        campione.forEach { inglese ->
            val tradotto = translateRuleMessage(inglese, AppLanguage.ITALIAN)
            assertNotNull(tradotto, "«$inglese» non è tradotto")
            assertTrue(tradotto.orEmpty().isNotBlank(), "«$inglese» ha una traduzione vuota")
            assertFalse(tradotto == inglese, "«$inglese» è rimasto in inglese")
        }
    }
}
