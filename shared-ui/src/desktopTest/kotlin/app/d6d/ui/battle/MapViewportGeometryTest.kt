package app.d6d.ui.battle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.d6d.board.GridPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MapViewportGeometryTest {

    @Test
    fun `la dimensione del mondo cresce linearmente anche oltre il viewport`() {
        val geometry = MapViewportGeometry(
            viewportSize = IntSize(800, 600),
            columns = 400,
            rows = 300,
            cellPx = 140f,
        )

        assertEquals(56_000f, geometry.contentSize.width, EPSILON)
        assertEquals(42_000f, geometry.contentSize.height, EPSILON)
    }

    @Test
    fun `lo zoom centrato attraversa la soglia del viewport senza drift`() {
        val viewport = IntSize(800, 600)
        val anchor = Offset(viewport.width / 2f, viewport.height / 2f)
        var geometry = MapViewportGeometry(viewport, columns = 20, rows = 15, cellPx = 24f)
        var offset = geometry.centeredOffset()
        val expectedWorld = worldPoint(anchor, offset, geometry.cellPx)

        listOf(28f, 32f, 36f, 40f, 44f, 48f).forEach { nextCellPx ->
            offset = geometry.zoomedOffset(offset, nextCellPx, anchor)
            geometry = geometry.copy(cellPx = nextCellPx)

            assertOffsetEquals(expectedWorld, worldPoint(anchor, offset, geometry.cellPx))
        }
    }

    @Test
    fun `lo zoom conserva un'ancora decentrata quando il clamp non interviene`() {
        val geometry = MapViewportGeometry(
            viewportSize = IntSize(800, 600),
            columns = 20,
            rows = 15,
            cellPx = 50f,
        )
        val offset = Offset(-100f, -75f)
        val anchor = Offset(650f, 450f)
        val worldBefore = worldPoint(anchor, offset, geometry.cellPx)

        val zoomed = geometry.zoomedOffset(offset, nextCellPx = 60f, anchor = anchor)

        assertOffsetEquals(Offset(-250f, -180f), zoomed)
        assertOffsetEquals(worldBefore, worldPoint(anchor, zoomed, cellPx = 60f))
    }

    @Test
    fun `il clamp corregge soltanto quando si superano i bordi reali`() {
        val largerThanViewport = MapViewportGeometry(
            viewportSize = IntSize(800, 600),
            columns = 20,
            rows = 15,
            cellPx = 50f,
        )

        assertOffsetEquals(Offset(-125f, -75f), largerThanViewport.constrain(Offset(-125f, -75f)))
        assertOffsetEquals(Offset.Zero, largerThanViewport.constrain(Offset(25f, 30f)))
        assertOffsetEquals(Offset(-200f, -150f), largerThanViewport.constrain(Offset(-250f, -200f)))

        val smallerThanViewport = largerThanViewport.copy(cellPx = 20f)
        assertOffsetEquals(Offset(200f, 150f), smallerThanViewport.constrain(Offset(200f, 150f)))
        assertOffsetEquals(Offset.Zero, smallerThanViewport.constrain(Offset(-1f, -1f)))
        assertOffsetEquals(Offset(400f, 300f), smallerThanViewport.constrain(Offset(401f, 301f)))
    }

    @Test
    fun `uno zoom a scala invariata non sposta il pan`() {
        val geometry = MapViewportGeometry(
            viewportSize = IntSize(800, 600),
            columns = 20,
            rows = 15,
            cellPx = 50f,
        )
        val pan = Offset(-123.5f, -82.25f)

        val result = geometry.zoomedOffset(pan, nextCellPx = 50f, anchor = Offset(713f, 419f))

        assertOffsetEquals(pan, result)
    }

    @Test
    fun `il resize conserva il punto mondo al centro del viewport`() {
        val geometry = MapViewportGeometry(
            viewportSize = IntSize(800, 600),
            columns = 40,
            rows = 30,
            cellPx = 50f,
        )
        val offset = Offset(-550f, -400f)
        val oldCenter = Offset(400f, 300f)
        val expectedWorld = worldPoint(oldCenter, offset, geometry.cellPx)
        val nextViewport = IntSize(1_000, 720)

        val resized = geometry.resizedOffset(offset, nextViewport)

        assertOffsetEquals(Offset(-450f, -340f), resized)
        assertOffsetEquals(
            expectedWorld,
            worldPoint(Offset(500f, 360f), resized, geometry.cellPx),
        )
    }

    @Test
    fun `cellAt rifiuta coordinate appena negative rispetto alla mappa`() {
        val geometry = MapViewportGeometry(
            viewportSize = IntSize(800, 600),
            columns = 20,
            rows = 15,
            cellPx = 50f,
        )
        val mapOffset = Offset(100f, 80f)

        assertNull(geometry.cellAt(Offset(99.999f, 100f), mapOffset))
        assertNull(geometry.cellAt(Offset(120f, 79.999f), mapOffset))
        assertEquals(IntOffset.Zero, geometry.cellAt(mapOffset, mapOffset))
    }

    @Test
    fun `righe e colonne visibili limitano l'iterazione al viewport`() {
        val geometry = MapViewportGeometry(
            viewportSize = IntSize(800, 600),
            columns = 400,
            rows = 400,
            cellPx = 140f,
        )
        val mapOffset = Offset(-28_000f, -21_000f)

        val visibleColumns = geometry.visibleColumns(mapOffset)
        val visibleRows = geometry.visibleRows(mapOffset)

        assertEquals(200..206, visibleColumns)
        assertEquals(150..155, visibleRows)
        assertEquals(7, visibleColumns.count())
        assertEquals(6, visibleRows.count())
    }

    @Test
    fun `punti continui passano fra mondo e schermo senza geometrie duplicate`() {
        val geometry = MapViewportGeometry(IntSize(800, 600), 20, 15, 50f)
        val mapOffset = Offset(-100f, -50f)
        val world = GridPoint(7.25, 4.5)

        val screen = geometry.screenAt(world, mapOffset)

        assertEquals(world, geometry.worldAt(screen, mapOffset))
        assertNull(geometry.worldAt(Offset(mapOffset.x - 1f, mapOffset.y), mapOffset))
    }

    private fun worldPoint(screenPoint: Offset, mapOffset: Offset, cellPx: Float): Offset = Offset(
        x = (screenPoint.x - mapOffset.x) / cellPx,
        y = (screenPoint.y - mapOffset.y) / cellPx,
    )

    private fun assertOffsetEquals(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, EPSILON, "coordinata x")
        assertEquals(expected.y, actual.y, EPSILON, "coordinata y")
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
