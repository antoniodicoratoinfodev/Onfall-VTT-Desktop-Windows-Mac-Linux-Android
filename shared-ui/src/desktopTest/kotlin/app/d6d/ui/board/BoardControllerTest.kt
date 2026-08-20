package app.d6d.ui.board

import app.d6d.board.GridPoint
import app.d6d.board.Label
import app.d6d.board.SceneToken
import app.d6d.board.TokenCategory
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
}
