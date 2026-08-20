package app.d6d.ui.battle

import app.d6d.board.GridPoint
import app.d6d.board.BoardDocument
import app.d6d.board.BoardLayers
import app.d6d.board.SceneToken
import app.d6d.board.TokenCategory
import app.d6d.board.TokenLootCategory
import app.d6d.ui.board.BoardController
import app.d6d.ui.board.BoardTool
import app.d6d.ui.board.BoardToolState
import app.d6d.ui.board.SceneTokenDraft
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardGeometryTest {

    @Test
    fun `la misura usa la stessa diagonale Chebyshev del motore`() {
        val points = listOf(GridPoint(1.5, 1.5), GridPoint(6.5, 4.5))

        assertEquals(25, measurementFeet(points, feetPerSquare = 5))
    }

    @Test
    fun `il percorso somma i tratti senza confonderlo con l addebito diretto`() {
        val points = listOf(GridPoint(0.5, 0.5), GridPoint(5.5, 0.5), GridPoint(5.5, 5.5))

        assertEquals(50, measurementFeet(points, feetPerSquare = 5))
        assertEquals(25, measurementFeet(listOf(points.first(), points.last()), feetPerSquare = 5))
    }

    @Test
    fun `la semplificazione riduce i punti quasi allineati e conserva gli estremi`() {
        val source = (0..100).map { index ->
            GridPoint(index / 10.0, index / 1000.0)
        }

        val simplified = simplifyStroke(source, tolerance = 0.03)

        assertTrue(simplified.size < source.size / 4)
        assertEquals(source.first(), simplified.first())
        assertEquals(source.last(), simplified.last())
    }

    @Test
    fun `la semplificazione accetta il limite di punti senza gonfiare l output`() {
        val source = (0 until 10_000).map { index ->
            GridPoint(index / 3.0, index / 30_000.0)
        }

        val simplified = simplifyStroke(source, tolerance = 0.01)

        assertEquals(source.first(), simplified.first())
        assertEquals(source.last(), simplified.last())
        assertEquals(2, simplified.size)
    }

    @Test
    fun `la vista giocatori esclude le pedine riservate e rispetta lo strato`() {
        val public = token("public", visible = true)
        val secret = token("secret", visible = false)
        val document = BoardDocument.empty().withObjects(listOf(public, secret))

        assertEquals(listOf(public, secret), visibleSceneTokens(document, playerPreview = false))
        assertEquals(listOf(public), visibleSceneTokens(document, playerPreview = true))

        val hiddenLayer = document.withLayers(BoardLayers(true, true, false, true, false))
        assertTrue(visibleSceneTokens(hiddenLayer, playerPreview = false).isEmpty())
    }

    @Test
    fun `la posa della pedina produce un solo commit e passa a modifica`() {
        val controller = BoardController()
        val tools = BoardToolState()
        tools.prepareToken(
            SceneTokenDraft(
                "Mimic", TokenCategory.MONSTER, 2.0, 0xffcc8844.toInt(), "",
                showLabel = true,
                visibleToPlayers = false,
                lootable = true,
                lootCategory = TokenLootCategory.MISC,
                lootQuantity = 2,
                lootDescription = "Due denti",
                notes = "Finge di essere una cassa",
            ),
        )

        assertEquals("mimic", placePendingSceneToken(controller, tools, GridPoint(4.5, 5.5), "mimic"))

        assertEquals(1L, controller.revision)
        assertEquals(BoardTool.EDIT, tools.active)
        assertEquals("mimic", tools.selectedId)
        assertEquals(null, tools.pendingToken)
        val placed = controller.document.objects().single() as SceneToken
        assertTrue(placed.lootable())
        assertEquals(2, placed.lootQuantity())
        assertEquals("Due denti", placed.lootDescription())
        assertTrue(controller.canUndo)
        assertTrue(controller.undo())
        assertFalse(controller.document.objects().isNotEmpty())
    }

    private fun token(id: String, visible: Boolean) = SceneToken(
        id, id, TokenCategory.TRAP, GridPoint(1.5, 1.5), 1.0, 0.0,
        0xffcc8844.toInt(), "", true, visible, "",
    )
}
