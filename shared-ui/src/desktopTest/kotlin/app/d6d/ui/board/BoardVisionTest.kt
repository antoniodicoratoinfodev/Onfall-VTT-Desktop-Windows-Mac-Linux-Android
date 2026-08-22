package app.d6d.ui.board

import app.d6d.board.BoardBounds
import app.d6d.board.ExploredMask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardVisionTest {

    private val party = listOf("hero", "cleric")
    private val standing: (String) -> Boolean = { true }

    @Test
    fun `nella vista del master guarda chi ha il turno anche se e' un mostro`() {
        val eyes = visionEyes(
            playerPreview = false,
            activeIds = listOf("goblin"),
            partyIds = party,
            onTheirFeet = standing,
        )

        assertEquals(listOf("goblin"), eyes.display, "il master vede cosa vede il mostro che muove")
        assertEquals(party, eyes.memory, "ma il corridoio del goblin non entra nei ricordi del gruppo")
    }

    @Test
    fun `l anteprima giocatori guarda sempre la squadra, mai il mostro di turno`() {
        val eyes = visionEyes(
            playerPreview = true,
            activeIds = listOf("goblin"),
            partyIds = party,
            onTheirFeet = standing,
        )

        assertEquals(party, eyes.display, "altrimenti lo schermo dei giocatori nasconderebbe i loro personaggi")
        assertEquals(party, eyes.memory)
    }

    @Test
    fun `l anteprima mostra la squadra intera anche durante il turno di un solo eroe`() {
        val eyes = visionEyes(
            playerPreview = true,
            activeIds = listOf("hero"),
            partyIds = party,
            onTheirFeet = standing,
        )

        assertEquals(party, eyes.display, "il chierico non smette di vedere perche' non e' il suo turno")
    }

    @Test
    fun `chi e' a terra non e' un occhio, ne' per la vista ne' per la memoria`() {
        val eyes = visionEyes(
            playerPreview = false,
            activeIds = listOf("hero"),
            partyIds = party,
            onTheirFeet = { it != "hero" },
        )

        assertEquals(listOf("cleric"), eyes.display, "l eroe a terra ha ancora il turno ma non illumina")
        assertEquals(listOf("cleric"), eyes.memory)
    }

    @Test
    fun `senza nessuno in piedi non resta un occhio da nessuna parte`() {
        val eyes = visionEyes(
            playerPreview = false,
            activeIds = listOf("hero"),
            partyIds = party,
            onTheirFeet = { false },
        )

        assertEquals(emptyList<String>(), eyes.display)
        assertEquals(emptyList<String>(), eyes.memory)
    }

    /** Campo 4x4 con una sola casella accesa, quella in basso a destra dell'angolo. */
    private fun onlyCellVisible(column: Int, row: Int): BoardVisionField {
        val visible = BooleanArray(16)
        visible[row * 4 + column] = true
        return BoardVisionField(true, 4, 4, visible, ExploredMask.empty(4, 4))
    }

    @Test
    fun `una pedina si vede se una sua casella e' in vista, non solo il centro`() {
        val field = onlyCellVisible(2, 2)

        // Pedina 1x1 centrata in (1.5, 1.5): occupa solo la casella (1, 1).
        assertFalse(field.sees(BoardBounds(1.0, 1.0, 2.0, 2.0)), "nessuna delle sue caselle e' in vista")
        // Pedina 3x3 con lo stesso centro: il corpo arriva sulla casella accesa.
        assertTrue(field.sees(BoardBounds(0.0, 0.0, 3.0, 3.0)), "il centro e' al buio ma il corpo no")
    }

    @Test
    fun `un rettangolo fuori dalla griglia non si vede e non rompe nulla`() {
        val field = onlyCellVisible(2, 2)

        assertFalse(field.sees(BoardBounds(40.0, 40.0, 44.0, 44.0)))
        assertFalse(field.sees(BoardBounds(-9.0, -9.0, -5.0, -5.0)))
    }

    @Test
    fun `con la nebbia dipinta il campo inerte non nasconde niente`() {
        assertTrue(BoardVisionField.inactive().sees(BoardBounds(40.0, 40.0, 44.0, 44.0)))
    }

    @Test
    fun `fuori dal combattimento nessuno ha il turno e guarda la squadra`() {
        val eyes = visionEyes(
            playerPreview = false,
            activeIds = emptyList(),
            partyIds = party,
            onTheirFeet = standing,
        )

        assertEquals(party, eyes.display, "una sessione appena aperta non deve nascere nera")
        assertEquals(party, eyes.memory)
    }
}
