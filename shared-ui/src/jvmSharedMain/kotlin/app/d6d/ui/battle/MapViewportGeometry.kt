package app.d6d.ui.battle

import app.d6d.board.BoardBounds
import app.d6d.board.GridPoint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Geometria della camera della mappa, espressa interamente in pixel.
 *
 * La mappa e' un mondo virtuale: non deve mai essere misurata come un enorme nodo
 * Compose, perche' i vincoli del viewport finirebbero per comprimerla. Questa
 * classe mantiene invece una sola trasformazione tra caselle e schermo:
 *
 *     schermo = offset + casella * cellPx
 *
 * La stessa trasformazione viene usata da disegno, zoom, pan e hit test.
 */
internal data class MapViewportGeometry(
    val viewportSize: IntSize,
    val columns: Int,
    val rows: Int,
    val cellPx: Float,
) {
    val contentSize: Size
        get() = Size(
            width = (cellPx * columns).coerceAtLeast(0f),
            height = (cellPx * rows).coerceAtLeast(0f),
        )

    /** Posizione iniziale: centro della mappa coincidente col centro del viewport. */
    fun centeredOffset(): Offset = Offset(
        x = (viewportSize.width - contentSize.width) / 2f,
        y = (viewportSize.height - contentSize.height) / 2f,
    )

    /**
     * Impedisce alla mappa di staccarsi dai bordi visibili.
     *
     * Se il mondo e' piu' grande del viewport, non lascia comparire spazio vuoto;
     * se e' piu' piccolo, lo mantiene interamente nel riquadro. Non ridimensiona mai
     * il mondo: corregge soltanto la posizione della camera.
     */
    fun constrain(offset: Offset): Offset = Offset(
        x = constrainAxis(offset.x, viewportSize.width.toFloat(), contentSize.width),
        y = constrainAxis(offset.y, viewportSize.height.toFloat(), contentSize.height),
    )

    /** Nuovo offset che lascia ferma la casella attualmente sotto [anchor]. */
    fun zoomedOffset(offset: Offset, nextCellPx: Float, anchor: Offset): Offset {
        if (cellPx <= 0f || nextCellPx <= 0f || nextCellPx == cellPx) return constrain(offset)
        val current = constrain(offset)
        val factor = nextCellPx / cellPx
        val raw = Offset(
            x = anchor.x + (current.x - anchor.x) * factor,
            y = anchor.y + (current.y - anchor.y) * factor,
        )
        return copy(cellPx = nextCellPx).constrain(raw)
    }

    /** Mantiene fermo il punto mondo al centro quando il viewport cambia misura. */
    fun resizedOffset(offset: Offset, nextViewportSize: IntSize): Offset {
        if (nextViewportSize == viewportSize) return constrain(offset)
        val current = constrain(offset)
        val centered = current + Offset(
            x = (nextViewportSize.width - viewportSize.width) / 2f,
            y = (nextViewportSize.height - viewportSize.height) / 2f,
        )
        return copy(viewportSize = nextViewportSize).constrain(centered)
    }

    /** Casella sotto un punto del viewport, oppure `null` fuori dalla mappa. */
    fun cellAt(point: Offset, mapOffset: Offset): IntOffset? =
        mapCellAt(point, mapOffset, cellPx, columns, rows)

    /** Punto continuo del mondo sotto il viewport, oppure null fuori griglia. */
    fun worldAt(point: Offset, mapOffset: Offset): GridPoint? {
        if (cellPx <= 0f || !cellPx.isFinite()) return null
        val x = (point.x - mapOffset.x) / cellPx
        val y = (point.y - mapOffset.y) / cellPx
        if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f || x >= columns || y >= rows) return null
        return GridPoint(x.toDouble(), y.toDouble())
    }

    /** Conversione inversa condivisa da tutti gli oggetti del Lucido. */
    fun screenAt(point: GridPoint, mapOffset: Offset): Offset = Offset(
        mapOffset.x + point.x().toFloat() * cellPx,
        mapOffset.y + point.y().toFloat() * cellPx,
    )

    /** Rettangolo mondo effettivamente visibile, limitato alla griglia corrente. */
    fun visibleWorld(mapOffset: Offset): BoardBounds {
        if (cellPx <= 0f || viewportSize.width <= 0 || viewportSize.height <= 0) {
            return BoardBounds(0.0, 0.0, 0.0, 0.0)
        }
        val left = (-mapOffset.x / cellPx).coerceIn(0f, columns.toFloat()).toDouble()
        val top = (-mapOffset.y / cellPx).coerceIn(0f, rows.toFloat()).toDouble()
        val right = ((viewportSize.width - mapOffset.x) / cellPx).coerceIn(0f, columns.toFloat()).toDouble()
        val bottom = ((viewportSize.height - mapOffset.y) / cellPx).coerceIn(0f, rows.toFloat()).toDouble()
        return BoardBounds(minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom))
    }

    fun visibleColumns(mapOffset: Offset): IntRange =
        visibleGridLines(mapOffset.x, cellPx, columns, viewportSize.width.toFloat())

    fun visibleRows(mapOffset: Offset): IntRange =
        visibleGridLines(mapOffset.y, cellPx, rows, viewportSize.height.toFloat())
}

private fun constrainAxis(raw: Float, viewport: Float, content: Float): Float {
    if (!raw.isFinite() || !viewport.isFinite() || !content.isFinite()) return 0f
    val slack = viewport - content
    return raw.coerceIn(minOf(0f, slack), maxOf(0f, slack))
}

/** Conversione condivisa da tap sulla mappa e trascinamento dalle barre laterali. */
internal fun mapCellAt(
    point: Offset,
    mapOffset: Offset,
    cellPx: Float,
    columns: Int,
    rows: Int,
): IntOffset? {
    if (cellPx <= 0f || !cellPx.isFinite()) return null
    val localX = point.x - mapOffset.x
    val localY = point.y - mapOffset.y
    val width = cellPx * columns
    val height = cellPx * rows
    if (localX < 0f || localY < 0f || localX >= width || localY >= height) return null
    return IntOffset(
        x = floor(localX / cellPx).toInt(),
        y = floor(localY / cellPx).toInt(),
    )
}

/** Solo le linee vicine al viewport vengono disegnate, anche su mappe 400 x 400. */
private fun visibleGridLines(origin: Float, cellPx: Float, count: Int, viewport: Float): IntRange {
    if (cellPx <= 0f || count < 0 || viewport <= 0f) return 1..0
    val first = floor(-origin / cellPx).toInt().coerceIn(0, count)
    val last = ceil((viewport - origin) / cellPx).toInt().coerceIn(0, count)
    return if (first <= last) first..last else 1..0
}
