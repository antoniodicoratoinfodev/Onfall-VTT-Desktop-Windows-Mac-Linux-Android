package app.d6d.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.d6d.board.ExploredMask
import app.d6d.board.VisionField
import app.d6d.board.WallMask
import app.d6d.domain.space.MapGrid
import app.d6d.domain.space.TokenPlacement

/** Quanto una casella è nota a chi sta guardando la mappa adesso. */
enum class VisionTier {
    /** La si vede in questo istante. */
    VISIBLE,

    /** La si è vista prima e la si ricorda: resta in penombra. */
    EXPLORED,

    /** Mai vista da nessuno: nera. */
    UNSEEN,
}

/**
 * Il campo visivo già risolto, pronto da disegnare.
 *
 * [active] è falso quando la nebbia è quella dipinta a mano, e allora nessuno deve
 * chiedergli nulla: la mappa si disegna come si è sempre disegnata.
 */
@Immutable
class BoardVisionField(
    val active: Boolean,
    val columns: Int,
    val rows: Int,
    private val visible: BooleanArray,
    private val explored: ExploredMask,
) {
    /** Fuori dalla griglia non c'e' mappa da nascondere: la nebbia non deve uscirne. */
    fun insideGrid(column: Int, row: Int): Boolean =
        column >= 0 && row >= 0 && column < columns && row < rows

    fun tier(column: Int, row: Int): VisionTier = when {
        !active -> VisionTier.VISIBLE
        sees(column, row) -> VisionTier.VISIBLE
        explored.seen(column, row) -> VisionTier.EXPLORED
        else -> VisionTier.UNSEEN
    }

    fun sees(column: Int, row: Int): Boolean {
        if (!active) return true
        if (column < 0 || row < 0 || column >= columns || row >= rows) return false
        return visible[row * columns + column]
    }

    /**
     * Vero se almeno una casella del segnaposto è in vista.
     *
     * Una creatura Grande che sporge dall'angolo si vede: nasconderla finché non è
     * interamente allo scoperto significherebbe farla comparire dal nulla adosso al
     * gruppo.
     */
    fun sees(placement: TokenPlacement): Boolean {
        if (!active) return true
        return placement.occupiedSquares().any { sees(it.column(), it.row()) }
    }

    companion object {
        /** Campo inerte: tutto visibile, nessun calcolo. È la nebbia dipinta a mano. */
        fun inactive(): BoardVisionField =
            BoardVisionField(false, 0, 0, BooleanArray(0), ExploredMask.empty(0, 0))
    }
}

/** Un occhio: da dove guarda, quanto lontano, e se il suo sguardo si ricorda. */
data class VisionViewer(
    val combatantId: String,
    val placement: TokenPlacement,
    val radiusSquares: Int,
    val party: Boolean,
)

/**
 * Calcola il campo visivo e ne affida la memoria al Lucido.
 *
 * Il calcolo non è per fotogramma: dipende solo da muri, griglia, posizioni e
 * raggi, quindi si rifà quando una di quelle cambia. `boardRevision` copre i muri
 * e le impostazioni di vista, perché entrambi passano dai comandi annullabili del
 * Lucido; segnare l'esplorato invece non la tocca di proposito, e non innesca un
 * ricalcolo a catena.
 */
@Composable
fun rememberBoardVision(
    dynamic: Boolean,
    grid: MapGrid,
    walls: WallMask,
    explored: ExploredMask,
    viewers: List<VisionViewer>,
    boardRevision: Long,
    onExplored: (BooleanArray, Int, Int) -> Unit,
): BoardVisionField {
    val columns = grid.columns()
    val rows = grid.rows()
    val key = remember(dynamic, columns, rows, boardRevision, viewers) {
        buildString {
            append(dynamic).append('/').append(columns).append('x').append(rows)
            append('/').append(boardRevision)
            viewers.forEach {
                append('|').append(it.combatantId)
                append(':').append(it.placement.origin().column()).append(',').append(it.placement.origin().row())
                append(':').append(it.placement.squaresPerSide())
                append(':').append(it.radiusSquares)
                append(':').append(it.party)
            }
        }
    }

    val computed = remember(key) {
        if (!dynamic || columns <= 0 || rows <= 0) {
            null
        } else {
            val visible = VisionField.blank(columns, rows)
            viewers.forEach { viewer -> viewer.addTo(visible, walls, columns, rows) }
            // La memoria è solo del gruppo. Se ci scrivessero anche i mostri, il
            // turno di un nemico regalerebbe ai giocatori i corridoi che loro non
            // hanno mai percorso.
            val partyViewers = viewers.filter { it.party }
            val remembered = when {
                partyViewers.isEmpty() -> null
                partyViewers.size == viewers.size -> visible
                else -> VisionField.blank(columns, rows).also { field ->
                    partyViewers.forEach { viewer -> viewer.addTo(field, walls, columns, rows) }
                }
            }
            visible to remembered
        }
    }

    LaunchedEffect(key) {
        val remembered = computed?.second ?: return@LaunchedEffect
        onExplored(remembered, columns, rows)
    }

    val visible = computed?.first ?: return BoardVisionField.inactive()
    return remember(key, explored) {
        BoardVisionField(true, columns, rows, visible, explored.resized(columns, rows))
    }
}

private fun VisionViewer.addTo(field: BooleanArray, walls: WallMask, columns: Int, rows: Int) {
    placement.occupiedSquares().forEach { square ->
        VisionField.addVisibleFrom(field, walls, columns, rows, square.column(), square.row(), radiusSquares)
    }
}
