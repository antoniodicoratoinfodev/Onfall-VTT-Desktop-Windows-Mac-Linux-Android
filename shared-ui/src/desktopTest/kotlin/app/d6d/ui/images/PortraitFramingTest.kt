package app.d6d.ui.images

import app.d6d.sheet.PortraitFraming
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PortraitFramingTest {

    @Test
    fun `un'immagine orizzontale centrata copre il token senza deformarsi`() {
        val placement = framedImagePlacement(400, 200, 100f, 100f, PortraitFraming.DEFAULT)

        assertEquals(-50f, placement.left, 0.001f)
        assertEquals(0f, placement.top, 0.001f)
        assertEquals(200f, placement.width, 0.001f)
        assertEquals(100f, placement.height, 0.001f)
    }

    @Test
    fun `il fuoco sposta l'inquadratura fra i due bordi`() {
        val left = framedImagePlacement(400, 200, 100f, 100f, PortraitFraming(focusX = 0f))
        val right = framedImagePlacement(400, 200, 100f, 100f, PortraitFraming(focusX = 1f))

        assertEquals(0f, left.left, 0.001f)
        assertEquals(-100f, right.left, 0.001f)
    }

    @Test
    fun `lo zoom conserva sempre la copertura completa del riquadro`() {
        val placement = framedImagePlacement(
            240,
            360,
            120f,
            120f,
            PortraitFraming(focusX = 1f, focusY = 1f, zoom = 4f),
        )

        assertTrue(placement.left <= 0f)
        assertTrue(placement.top <= 0f)
        assertTrue(placement.left + placement.width >= 120f)
        assertTrue(placement.top + placement.height >= 120f)
    }
}
