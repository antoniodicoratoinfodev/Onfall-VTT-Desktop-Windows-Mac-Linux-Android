package app.d6d.ui.maps

import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.ui.content.SampleEncounter
import app.d6d.ui.state.BattleViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import app.d6d.ui.i18n.ItalianStrings

class TokenArrangementTest {

    private val grid = MapGrid(20, 15, 5)

    private fun tokens(party: Int, enemies: Int, side: Int = 1) =
        List(party) { PendingToken("pg-$it", party = true, squaresPerSide = side) } +
            List(enemies) { PendingToken("nem-$it", party = false, squaresPerSide = side) }

    private fun squaresOf(origin: GridPosition, side: Int): List<Pair<Int, Int>> =
        (origin.column() until origin.column() + side).flatMap { column ->
            (origin.row() until origin.row() + side).map { row -> column to row }
        }

    /** `assertNotNull` di JUnit non restituisce il valore: qui serve anche quello. */
    private fun arrange(
        grid: MapGrid,
        tokens: List<PendingToken>,
        occupied: Set<GridPosition> = emptySet(),
    ): Map<String, GridPosition> = requireNotNull(arrangeTokens(grid, tokens, occupied)) {
        "Il piazzatore non ha trovato posto per tutti i segnaposti"
    }

    @Test
    fun `gli schieramenti finiscono su meta' opposte`() {
        val placements = arrange(grid, tokens(party = 4, enemies = 4))
        val middle = grid.columns() / 2

        placements.filterKeys { it.startsWith("pg-") }.values.forEach {
            assertTrue(it.column() < middle, "Un alleato è finito nella metà nemica: $it")
        }
        placements.filterKeys { it.startsWith("nem-") }.values.forEach {
            assertTrue(it.column() >= middle, "Un nemico è finito nella metà alleata: $it")
        }
    }

    @Test
    fun `nessun segnaposto si sovrappone a un altro`() {
        val placements = arrange(grid, tokens(party = 6, enemies = 6))
        val allSquares = placements.values.flatMap { squaresOf(it, 1) }

        assertEquals(allSquares.size, allSquares.toSet().size, "Due segnaposti condividono una casella")
    }

    @Test
    fun `le caselle gia' occupate vengono aggirate`() {
        // Una colonna intera nella meta' alleata e' gia' presa da chi e' sulla mappa.
        val occupied = (0 until grid.rows()).map { GridPosition(0, it) }.toSet()

        val placements = arrange(grid, tokens(party = 4, enemies = 0), occupied)

        placements.values.forEach {
            assertFalse(it in occupied, "Un segnaposto è finito su una casella occupata: $it")
        }
    }

    @Test
    fun `i segnaposti grandi restano dentro la griglia`() {
        val placements = arrange(grid, tokens(party = 2, enemies = 2, side = 4))

        placements.values.forEach { origin ->
            assertTrue(origin.column() + 4 <= grid.columns(), "Sfora a destra: $origin")
            assertTrue(origin.row() + 4 <= grid.rows(), "Sfora in basso: $origin")
        }
    }

    @Test
    fun `una griglia troppo piccola non produce alcuna posizione`() {
        assertNull(arrangeTokens(MapGrid(1, 1, 5), tokens(party = 2, enemies = 2)))
        assertNull(arrangeTokens(MapGrid(2, 2, 5), tokens(party = 1, enemies = 0, side = 4)))
    }

    @Test
    fun `una griglia assente non produce alcuna posizione`() {
        assertNull(arrangeTokens(MapGrid.NONE, tokens(party = 1, enemies = 1)))
    }

    // --- integrazione con il tavolo ----------------------------------------------------

    @Test
    fun `disponi tutti su una griglia minuscola spiega il motivo in italiano`() {
        val model = BattleViewModel(SampleEncounter.startedSession())
        model.configureMap(1, 1, 5)

        model.autoPlaceMissing { 1 }

        assertEquals(
            "La griglia 1×1 è troppo piccola per tutti i token selezionati.",
            model.message,
        )
    }

    @Test
    fun `disponi tutti non tocca chi e' gia' sulla mappa`() {
        val model = BattleViewModel(SampleEncounter.startedSession())
        val fermo = model.partyIds.first()
        model.place(fermo, 7, 7, 1)
        val prima = model.placementOf(fermo)?.origin()

        model.autoPlaceMissing { 1 }

        assertEquals(prima, model.placementOf(fermo)?.origin())
        model.state.combatants().keys.forEach {
            assertNotNull(model.placementOf(it), "«$it» è rimasto fuori dalla mappa")
        }
    }

    @Test
    fun `disponi tutti a mappa completa lo dice invece di ripetersi`() {
        val model = BattleViewModel(SampleEncounter.startedSession())
        model.autoPlaceMissing { 1 }

        model.autoPlaceMissing { 1 }

        assertEquals("Tutti i segnaposti sono già sulla mappa.", model.message)
    }
}
