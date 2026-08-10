package app.d6d.ui.battle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FloatingPanelDragTest {

    @Test
    fun `i delta frazionari si accumulano senza rallentare il trascinamento`() {
        val bounds = IntSize(800, 600)
        var position = Offset(100f, 100f)

        repeat(8) {
            position = accumulatePanelDrag(position, Offset(0.25f, 0.375f), bounds)
        }

        assertEquals(102f, position.x)
        assertEquals(103f, position.y)
    }

    @Test
    fun `il trascinamento resta entro i bordi e produce frazioni valide`() {
        val bounds = IntSize(300, 200)

        val position = accumulatePanelDrag(Offset(295f, 4f), Offset(20f, -10f), bounds)
        val fraction = position.toPanelFraction(bounds)

        assertEquals(Offset(300f, 0f), position)
        assertEquals(Offset(1f, 0f), fraction)
    }
}
