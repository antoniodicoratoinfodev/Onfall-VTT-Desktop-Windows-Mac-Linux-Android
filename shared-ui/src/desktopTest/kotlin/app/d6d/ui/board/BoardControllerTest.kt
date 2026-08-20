package app.d6d.ui.board

import app.d6d.board.GridPoint
import app.d6d.board.Label
import app.d6d.board.SceneToken
import app.d6d.board.TokenCategory
import app.d6d.board.TokenLootCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardControllerTest {

    @Test
    fun `un gesto logico produce una revisione e un passo undo`() {
        val controller = BoardController()
        val label = Label("uno", GridPoint(2.5, 3.5), "Sala", 0, 14.0, 0.0)

        controller.add(label)

        assertEquals(1L, controller.revision)
        assertTrue(controller.canUndo)
        assertEquals(listOf(label), controller.document.objects())
        assertTrue(controller.undo())
        assertEquals(2L, controller.revision)
        assertTrue(controller.document.objects().isEmpty())
        assertTrue(controller.redo())
        assertEquals(listOf(label), controller.document.objects())
    }

    @Test
    fun `un commit identico non sporca revisione o cronologia`() {
        val controller = BoardController()

        assertFalse(controller.commit(controller.document))
        assertEquals(0L, controller.revision)
        assertFalse(controller.canUndo)
    }

    @Test
    fun `il router rispetta la precedenza delle regole e dello sfondo`() {
        assertEquals(
            MapInteraction.RuleTargeting,
            resolveMapInteraction(BoardTool.INK, ruleTargeting = true, backgroundEditing = true, cpuPlayback = false),
        )
        assertEquals(
            MapInteraction.BackgroundEditing,
            resolveMapInteraction(BoardTool.INK, ruleTargeting = false, backgroundEditing = true, cpuPlayback = false),
        )
        assertEquals(
            MapInteraction.CpuPlayback,
            resolveMapInteraction(BoardTool.INK, ruleTargeting = false, backgroundEditing = false, cpuPlayback = true),
        )
        assertEquals(
            MapInteraction.Board(BoardTool.INK),
            resolveMapInteraction(BoardTool.INK, ruleTargeting = false, backgroundEditing = false, cpuPlayback = false),
        )
        assertEquals(
            MapInteraction.TemporaryPan(BoardTool.INK),
            resolveMapInteraction(
                BoardTool.INK,
                ruleTargeting = false,
                backgroundEditing = false,
                cpuPlayback = false,
                temporaryPan = true,
            ),
        )
    }

    @Test
    fun `annullare una posa scarta l asset mentre consumarla lo conserva`() {
        val discarded = mutableListOf<String>()
        val state = BoardToolState(discarded::add)
        val draft = SceneTokenDraft(
            "Trappola", TokenCategory.TRAP, 1.0, 0xffcc8844.toInt(),
            "scene-image", showLabel = true, visibleToPlayers = false, notes = "Segreta",
        )

        state.prepareToken(draft)
        state.table()
        assertEquals(listOf("scene-image"), discarded)

        state.prepareToken(draft)
        assertEquals(draft, state.consumePendingToken())
        state.select(BoardTool.EDIT)
        assertEquals(listOf("scene-image"), discarded)
    }

    @Test
    fun `modificare i dettagli di una pedina resta un singolo passo annullabile`() {
        val controller = BoardController()
        val original = SceneToken(
            "mimic", "Cassa", TokenCategory.OBJECT, GridPoint(4.5, 5.5), 1.0, 0.0,
            0xffcc8844.toInt(), "", true, false, "Sospetta",
        )
        controller.add(original)
        val edited = SceneToken(
            original.id(), "Mimic", TokenCategory.MONSTER, original.position(), 2.0, 15.0,
            original.colorArgb(), "scene-image", true, true, "Rivelato",
        )

        assertTrue(controller.replace(edited))
        assertEquals(edited, controller.document.objects().single())
        assertTrue(controller.undo())
        assertEquals(original, controller.document.objects().single())
    }

    @Test
    fun `consumare loot lo elimina anche dalle fotografie undo senza perdere gli altri comandi`() {
        val controller = BoardController()
        val first = Label("prima", GridPoint(1.5, 1.5), "Prima", 0, 14.0, 0.0)
        val loot = SceneToken(
            "loot", "Pozione", TokenCategory.LOOT, GridPoint(2.5, 2.5), 1.0, 0.0,
            0xffcc8844.toInt(), "", true, true,
            true, TokenLootCategory.POTION, 1, "Cura", "Segreta",
        )
        val second = Label("dopo", GridPoint(3.5, 3.5), "Dopo", 0, 14.0, 0.0)
        controller.add(first)
        controller.add(loot)
        controller.add(second)

        assertTrue(controller.consume(loot.id()))
        assertEquals(listOf(first, second), controller.document.objects())

        assertTrue(controller.undo())
        assertEquals(listOf(first), controller.document.objects())
        assertTrue(controller.document.objects().none { it.id() == loot.id() })

        // La rimozione della pedina rendeva identiche due fotografie adiacenti:
        // il secondo Undo deve togliere davvero la prima etichetta, senza un
        // passaggio intermedio che non cambia nulla.
        assertTrue(controller.undo())
        assertTrue(controller.document.objects().isEmpty())
        assertFalse(controller.undo())

        assertTrue(controller.redo())
        assertEquals(listOf(first), controller.document.objects())
        assertTrue(controller.redo())
        assertEquals(listOf(first, second), controller.document.objects())
    }

    @Test
    fun `consumare l unico comando non lascia un undo senza effetto`() {
        val controller = BoardController()
        val loot = SceneToken(
            "loot", "Chiave", TokenCategory.LOOT, GridPoint(2.5, 2.5), 1.0, 0.0,
            0xffcc8844.toInt(), "", true, true,
            true, TokenLootCategory.MISC, 1, "Chiave della torre", "Segreta",
        )
        assertTrue(controller.add(loot))

        assertTrue(controller.consume(loot.id()))

        assertTrue(controller.document.objects().isEmpty())
        assertFalse(controller.canUndo)
        assertFalse(controller.undo())
    }
}
