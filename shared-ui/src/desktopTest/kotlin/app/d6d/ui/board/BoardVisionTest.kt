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
            lens = MasterLens.TURN,
            masterPresentation = VisionPresentation.MEMORY_DIM,
            playerPresentation = VisionPresentation.MEMORY_BLACK,
            activeIds = listOf("goblin"),
            partyIds = party,
            onTheirFeet = standing,
        )

        assertEquals(listOf("goblin"), eyes.display, "il master vede cosa vede il mostro che muove")
        assertEquals(party, eyes.memory, "ma il corridoio del goblin non entra nei ricordi del gruppo")
    }

    @Test
    fun `la lente Gruppo guarda con gli occhi dei personaggi anche nel turno del mostro`() {
        val eyes = visionEyes(
            playerPreview = false,
            lens = MasterLens.PARTY,
            masterPresentation = VisionPresentation.MEMORY_DIM,
            playerPresentation = VisionPresentation.MEMORY_BLACK,
            activeIds = listOf("goblin"),
            partyIds = party,
            onTheirFeet = standing,
        )

        assertEquals(party, eyes.display, "risponde a «cosa vedono adesso i personaggi?»")
        assertEquals(VisionPresentation.MEMORY_DIM, eyes.presentation)
    }

    @Test
    fun `la resa Tutto toglie il velo al master ma non la memoria al gruppo`() {
        val eyes = visionEyes(
            playerPreview = false,
            lens = MasterLens.TURN,
            masterPresentation = VisionPresentation.ALL,
            playerPresentation = VisionPresentation.MEMORY_BLACK,
            activeIds = listOf("hero"),
            partyIds = party,
            onTheirFeet = standing,
        )

        assertEquals(VisionPresentation.ALL, eyes.presentation)
        assertEquals(emptyList<String>(), eyes.display, "nessun campo da calcolare")
        assertEquals(party, eyes.memory, "ma il gruppo continua a ricordare dove e' passato")
    }

    @Test
    fun `in anteprima la resa e la lente del master non hanno voce`() {
        val eyes = visionEyes(
            playerPreview = true,
            lens = MasterLens.TURN,
            masterPresentation = VisionPresentation.ALL,
            playerPresentation = VisionPresentation.MEMORY_BLACK,
            activeIds = listOf("goblin"),
            partyIds = party,
            onTheirFeet = standing,
        )

        assertEquals(VisionPresentation.MEMORY_BLACK, eyes.presentation)
        assertEquals(party, eyes.display)
    }

    @Test
    fun `l anteprima giocatori guarda sempre la squadra, mai il mostro di turno`() {
        val eyes = visionEyes(
            playerPreview = true,
            lens = MasterLens.TURN,
            masterPresentation = VisionPresentation.MEMORY_DIM,
            playerPresentation = VisionPresentation.MEMORY_BLACK,
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
            lens = MasterLens.TURN,
            masterPresentation = VisionPresentation.MEMORY_DIM,
            playerPresentation = VisionPresentation.MEMORY_BLACK,
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
            lens = MasterLens.TURN,
            masterPresentation = VisionPresentation.MEMORY_DIM,
            playerPresentation = VisionPresentation.MEMORY_BLACK,
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
            lens = MasterLens.TURN,
            masterPresentation = VisionPresentation.MEMORY_DIM,
            playerPresentation = VisionPresentation.MEMORY_BLACK,
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
        return BoardVisionField(
            true, 4, 4, VisionPresentation.MEMORY_BLACK, visible, ExploredMask.empty(4, 4),
        )
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
    fun `un rettangolo oltre alto e sinistra non eredita la visibilita di zero zero`() {
        val field = onlyCellVisible(0, 0)

        assertFalse(field.sees(BoardBounds(-9.0, -9.0, -5.0, -5.0)))
        assertFalse(field.sees(BoardBounds(-4.0, 0.2, -1.0, 0.8)))
        assertFalse(field.sees(BoardBounds(0.2, -4.0, 0.8, -1.0)))
    }

    @Test
    fun `i contenuti informativi dei giocatori richiedono almeno una casella visibile`() {
        val field = onlyCellVisible(0, 0)

        assertTrue(field.showsToPlayers(BoardBounds(0.0, 0.0, 1.0, 1.0)))
        assertFalse(field.showsToPlayers(BoardBounds(2.0, 2.0, 3.0, 3.0)))
        assertTrue(BoardVisionField.inactive().showsToPlayers(BoardBounds(20.0, 20.0, 21.0, 21.0)))
    }

    @Test
    fun `la nebbia uniforme produce un segmento per riga non uno per casella`() {
        val field = BoardVisionField(
            true, 400, 400, VisionPresentation.MEMORY_BLACK,
            BooleanArray(400 * 400), ExploredMask.empty(400, 400),
        )
        var runs = 0
        var coveredCells = 0

        field.forEachFogRun(0, 400, 0, 400) { _, first, last, tier ->
            runs++
            coveredCells += last - first
            assertEquals(VisionTier.UNSEEN, tier)
        }

        assertEquals(400, runs)
        assertEquals(160_000, coveredCells)
    }

    @Test
    fun `con la nebbia dipinta il campo inerte non nasconde niente`() {
        assertTrue(BoardVisionField.inactive().sees(BoardBounds(40.0, 40.0, 44.0, 44.0)))
    }

    @Test
    fun `fuori dal combattimento nessuno ha il turno e guarda la squadra`() {
        val eyes = visionEyes(
            playerPreview = false,
            lens = MasterLens.TURN,
            masterPresentation = VisionPresentation.MEMORY_DIM,
            playerPresentation = VisionPresentation.MEMORY_BLACK,
            activeIds = emptyList(),
            partyIds = party,
            onTheirFeet = standing,
        )

        assertEquals(party, eyes.display, "una sessione appena aperta non deve nascere nera")
        assertEquals(party, eyes.memory)
    }

    @Test
    fun `la preparazione senza PG piazzati sospende il velo soltanto al master`() {
        assertTrue(shouldSuspendDynamicVisionForSetup(false, emptyList(), false))
        assertFalse(shouldSuspendDynamicVisionForSetup(true, emptyList(), false))
        assertFalse(shouldSuspendDynamicVisionForSetup(false, listOf("goblin"), false))
        assertFalse(shouldSuspendDynamicVisionForSetup(false, emptyList(), true))
    }

    @Test
    fun `anche i giocatori possono scegliere Tutto senza fermare la memoria`() {
        val eyes = visionEyes(
            playerPreview = true,
            lens = MasterLens.TURN,
            masterPresentation = VisionPresentation.MEMORY_DIM,
            playerPresentation = VisionPresentation.ALL,
            activeIds = listOf("goblin"),
            partyIds = party,
            onTheirFeet = standing,
        )

        assertEquals(VisionPresentation.ALL, eyes.presentation)
        assertEquals(emptyList<String>(), eyes.display)
        assertEquals(party, eyes.memory)
    }

    @Test
    fun `le tre rese di master e giocatori restano completamente indipendenti`() {
        VisionPresentation.entries.forEach { masterPresentation ->
            VisionPresentation.entries.forEach { playerPresentation ->
                val masterEyes = visionEyes(
                    playerPreview = false,
                    lens = MasterLens.PARTY,
                    masterPresentation = masterPresentation,
                    playerPresentation = playerPresentation,
                    activeIds = listOf("goblin"),
                    partyIds = party,
                    onTheirFeet = standing,
                )
                val playerEyes = visionEyes(
                    playerPreview = true,
                    lens = MasterLens.TURN,
                    masterPresentation = masterPresentation,
                    playerPresentation = playerPresentation,
                    activeIds = listOf("goblin"),
                    partyIds = party,
                    onTheirFeet = standing,
                )

                assertEquals(masterPresentation, masterEyes.presentation)
                assertEquals(playerPresentation, playerEyes.presentation)
                assertEquals(party, masterEyes.memory)
                assertEquals(party, playerEyes.memory)
            }
        }
    }
}
