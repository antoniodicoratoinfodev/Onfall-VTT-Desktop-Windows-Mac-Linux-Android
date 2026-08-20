package app.d6d.ui.battle

import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.TokenPlacement
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La maschera del raggio percorribile.
 *
 * Traduce le origini che il motore dichiara raggiungibili nelle caselle da tenere
 * illuminate. Sbagliarla significa promettere al tavolo una destinazione che il
 * comando poi rifiuta — o, al contrario, oscurare il segnaposto stesso.
 */
class MovementReachMaskTest {

    private fun mask(
        placement: TokenPlacement,
        reachable: Set<GridPosition>,
        startColumn: Int = 0,
        startRow: Int = 0,
        width: Int = 8,
        height: Int = 8,
    ) = reachMaskFor(placement, reachable, startColumn, startRow, width, height)

    private fun BooleanArray.at(column: Int, row: Int, width: Int = 8) = this[row * width + column]

    @Test
    fun `una creatura media illumina esattamente le origini raggiungibili`() {
        val placement = TokenPlacement("hero", GridPosition(2, 2), 1)

        val lit = mask(placement, setOf(GridPosition(3, 2), GridPosition(2, 3)))

        assertTrue(lit.at(3, 2))
        assertTrue(lit.at(2, 3))
        assertFalse(lit.at(4, 2), "una casella non dichiarata resta al buio")
    }

    @Test
    fun `restare fermi e' sempre lecito, quindi il segnaposto non si oscura mai`() {
        val placement = TokenPlacement("hero", GridPosition(2, 2), 2)

        val lit = mask(placement, emptySet())

        // Le quattro caselle dell'ingombro attuale, anche senza alcuna destinazione.
        assertTrue(lit.at(2, 2))
        assertTrue(lit.at(3, 2))
        assertTrue(lit.at(2, 3))
        assertTrue(lit.at(3, 3))
    }

    @Test
    fun `di una creatura grande si illumina l'ingombro, non il solo angolo`() {
        val placement = TokenPlacement("ogre", GridPosition(0, 0), 2)

        val lit = mask(placement, setOf(GridPosition(4, 4)))

        assertTrue(lit.at(4, 4))
        assertTrue(lit.at(5, 4))
        assertTrue(lit.at(4, 5))
        assertTrue(lit.at(5, 5), "arrivando in (4,4) l'ogre occupa fino a (5,5)")
        assertFalse(lit.at(6, 4), "ma non una casella oltre il proprio ingombro")
    }

    @Test
    fun `cio' che cade fuori dal riquadro viene ritagliato senza errori`() {
        val placement = TokenPlacement("ogre", GridPosition(5, 5), 2)

        // Riquadro 4x4 a partire da (4,4): l'ingombro d'arrivo sborda a destra.
        val lit = reachMaskFor(
            placement = placement,
            reachableOrigins = setOf(GridPosition(7, 7), GridPosition(0, 0)),
            startColumn = 4,
            startRow = 4,
            width = 4,
            height = 4,
        )

        assertTrue(lit.at(7 - 4, 7 - 4, width = 4), "la parte dentro il riquadro resta illuminata")
        assertTrue(lit.at(5 - 4, 5 - 4, width = 4), "il segnaposto e' dentro il riquadro")
        // Un'origine interamente fuori non deve accendere nulla né far uscire dagli indici.
        assertFalse(lit.at(0, 1, width = 4))
    }

    @Test
    fun `un riquadro degenere non fa esplodere il disegno`() {
        val placement = TokenPlacement("hero", GridPosition(1, 1), 1)

        val lit = reachMaskFor(placement, setOf(GridPosition(2, 2)), 0, 0, 0, 0)

        assertTrue(lit.isEmpty())
    }
}
