package app.d6d.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DrawnCursorBoundsTest {

    @Test
    fun `il punto attivo cade esattamente sotto il puntatore`() {
        val bounds = bounds(pointer = Offset(100f, 80f), hotspot = Offset(8f, 6f))

        assertEquals(Rect(92f, 74f, 156f, 138f), bounds)
        assertHotspotAtPointer(bounds, Offset(100f, 80f), Offset(8f, 6f), 1f, 1f)
    }

    @Test
    fun `il punto attivo non scivola alla misura piccola`() {
        val pointer = Offset(141.5f, 93.25f)
        val hotspot = Offset(23f, 18f)
        val bounds = bounds(pointer, hotspot, scale = 0.65f)

        assertEquals(41.6f, bounds.width, 0.0001f)
        assertHotspotAtPointer(bounds, pointer, hotspot, 0.65f, 1f)
    }

    @Test
    fun `il punto attivo non scivola alla misura media`() {
        val pointer = Offset(320f, 240f)
        val hotspot = Offset(20f, 17f)
        val bounds = bounds(pointer, hotspot, scale = 0.82f)

        assertEquals(52.48f, bounds.height, 0.0001f)
        assertHotspotAtPointer(bounds, pointer, hotspot, 0.82f, 1f)
    }

    @Test
    fun `densita alta scala insieme disegno e punto attivo`() {
        val pointer = Offset(400f, 300f)
        val hotspot = Offset(8f, 6f)
        val bounds = bounds(pointer, hotspot, density = 2f)

        assertEquals(128f, bounds.width, 0.0001f)
        assertEquals(128f, bounds.height, 0.0001f)
        assertHotspotAtPointer(bounds, pointer, hotspot, 1f, 2f)
    }

    @Test
    fun `la geometria non presume immagini quadrate`() {
        val pointer = Offset(50f, 70f)
        val hotspot = Offset(3f, 11f)
        val bounds = drawnCursorBounds(
            pointerPosition = pointer,
            imageSize = Size(48f, 32f),
            hotspot = hotspot,
            scale = 0.65f,
            density = 1.5f,
        )

        assertEquals(46.8f, bounds.width, 0.0001f)
        assertEquals(31.2f, bounds.height, 0.0001f)
        assertHotspotAtPointer(bounds, pointer, hotspot, 0.65f, 1.5f)
    }

    private fun bounds(
        pointer: Offset,
        hotspot: Offset,
        scale: Float = 1f,
        density: Float = 1f,
    ): Rect = drawnCursorBounds(
        pointerPosition = pointer,
        imageSize = Size(64f, 64f),
        hotspot = hotspot,
        scale = scale,
        density = density,
    )

    private fun assertHotspotAtPointer(
        bounds: Rect,
        pointer: Offset,
        hotspot: Offset,
        scale: Float,
        density: Float,
    ) {
        val renderedHotspot = hotspot * (scale * density)
        assertEquals(pointer.x, bounds.left + renderedHotspot.x, 0.0001f)
        assertEquals(pointer.y, bounds.top + renderedHotspot.y, 0.0001f)
    }
}
