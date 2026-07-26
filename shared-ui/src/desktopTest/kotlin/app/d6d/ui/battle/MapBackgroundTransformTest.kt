package app.d6d.ui.battle

import app.d6d.domain.space.MapBackground
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MapBackgroundTransformTest {

    @Test
    fun `le quattro maniglie angolari mantengono le proporzioni e l'angolo opposto`() {
        val start = MapBackground(2.0, 3.0, 8.0, 4.0)
        val ratio = start.width() / start.height()
        val right = start.offsetX() + start.width()
        val bottom = start.offsetY() + start.height()

        val results = mapOf(
            BgHandle.TL to start.resizedBy(BgHandle.TL, -4.0, -0.2),
            BgHandle.TR to start.resizedBy(BgHandle.TR, 4.0, -0.2),
            BgHandle.BR to start.resizedBy(BgHandle.BR, 4.0, 0.2),
            BgHandle.BL to start.resizedBy(BgHandle.BL, -4.0, 0.2),
        )

        results.forEach { (handle, result) ->
            assertEquals(ratio, result.width() / result.height(), EPSILON, "$handle: proporzioni")
            assertEquals(12.0, result.width(), EPSILON, "$handle: larghezza")
            assertEquals(6.0, result.height(), EPSILON, "$handle: altezza")
            if (handle == BgHandle.TL || handle == BgHandle.BL) {
                assertEquals(right, result.offsetX() + result.width(), EPSILON, "$handle: ancora x")
            } else {
                assertEquals(start.offsetX(), result.offsetX(), EPSILON, "$handle: ancora x")
            }
            if (handle == BgHandle.TL || handle == BgHandle.TR) {
                assertEquals(bottom, result.offsetY() + result.height(), EPSILON, "$handle: ancora y")
            } else {
                assertEquals(start.offsetY(), result.offsetY(), EPSILON, "$handle: ancora y")
            }
        }
    }

    @Test
    fun `un movimento soprattutto verticale dell'angolo guida entrambi gli assi`() {
        val start = MapBackground(2.0, 3.0, 8.0, 4.0)

        val result = start.resizedBy(BgHandle.TR, 0.1, -2.0)

        assertEquals(12.0, result.width(), EPSILON)
        assertEquals(6.0, result.height(), EPSILON)
        assertEquals(2.0, result.offsetX(), EPSILON)
        assertEquals(1.0, result.offsetY(), EPSILON)
    }

    @Test
    fun `le maniglie laterali stirano soltanto l'asse afferrato`() {
        val start = MapBackground(2.0, 3.0, 8.0, 4.0)

        assertBackground(
            MapBackground(5.0, 3.0, 5.0, 4.0),
            start.resizedBy(BgHandle.L, 3.0, 99.0),
        )
        assertBackground(
            MapBackground(2.0, 3.0, 11.0, 4.0),
            start.resizedBy(BgHandle.R, 3.0, 99.0),
        )
        assertBackground(
            MapBackground(2.0, 5.0, 8.0, 2.0),
            start.resizedBy(BgHandle.T, 99.0, 2.0),
        )
        assertBackground(
            MapBackground(2.0, 3.0, 8.0, 7.0),
            start.resizedBy(BgHandle.B, 99.0, 3.0),
        )
    }

    @Test
    fun `il minimo non rompe le proporzioni e non oltrepassa l'ancora`() {
        val start = MapBackground(2.0, 3.0, 8.0, 4.0)

        val corner = start.resizedBy(BgHandle.BR, -100.0, -100.0)
        assertEquals(2.0, corner.width() / corner.height(), EPSILON)
        assertEquals(1.0, corner.width(), EPSILON)
        assertEquals(0.5, corner.height(), EPSILON)
        assertEquals(start.offsetX(), corner.offsetX(), EPSILON)
        assertEquals(start.offsetY(), corner.offsetY(), EPSILON)

        val left = start.resizedBy(BgHandle.L, 100.0, 0.0)
        assertEquals(0.5, left.width(), EPSILON)
        assertEquals(10.0, left.offsetX() + left.width(), EPSILON)

        val top = start.resizedBy(BgHandle.T, 0.0, 100.0)
        assertEquals(0.5, top.height(), EPSILON)
        assertEquals(7.0, top.offsetY() + top.height(), EPSILON)
    }

    private fun assertBackground(expected: MapBackground, actual: MapBackground) {
        assertEquals(expected.offsetX(), actual.offsetX(), EPSILON, "offset x")
        assertEquals(expected.offsetY(), actual.offsetY(), EPSILON, "offset y")
        assertEquals(expected.width(), actual.width(), EPSILON, "larghezza")
        assertEquals(expected.height(), actual.height(), EPSILON, "altezza")
    }

    private companion object {
        const val EPSILON = 0.000_001
    }
}
