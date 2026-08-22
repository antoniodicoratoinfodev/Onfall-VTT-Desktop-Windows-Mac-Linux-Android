package app.d6d.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.d6d.board.BoardBounds
import app.d6d.board.ExploredMask
import app.d6d.board.VisionField
import app.d6d.board.WallMask
import app.d6d.domain.space.MapGrid
import app.d6d.domain.space.TokenPlacement
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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

    /**
     * Vero se almeno una casella coperta dal rettangolo è in vista.
     *
     * È la stessa regola dei combattenti, applicata a ciò che occupa spazio senza
     * essere una creatura: un idolo Enorme che sporge dall'angolo si vede. Guardare
     * soltanto la casella del suo centro farebbe sparire per intero una pedina che
     * ha il centro dietro il muro e il corpo in piena luce.
     */
    fun sees(bounds: BoardBounds): Boolean {
        if (!active) return true
        val firstColumn = max(0, floor(bounds.left()).toInt())
        val firstRow = max(0, floor(bounds.top()).toInt())
        val lastColumn = min(columns - 1, max(firstColumn, ceil(bounds.right()).toInt() - 1))
        val lastRow = min(rows - 1, max(firstRow, ceil(bounds.bottom()).toInt() - 1))
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                if (sees(column, row)) return true
            }
        }
        return false
    }

    companion object {
        /** Campo inerte: tutto visibile, nessun calcolo. È la nebbia dipinta a mano. */
        fun inactive(): BoardVisionField =
            BoardVisionField(false, 0, 0, BooleanArray(0), ExploredMask.empty(0, 0))
    }
}

/** Un occhio: da dove guarda e quanto lontano. */
data class VisionViewer(
    val combatantId: String,
    val placement: TokenPlacement,
    val radiusSquares: Int,
)

/**
 * Gli occhi della mappa: chi guarda adesso, e chi ricorda.
 *
 * Sono due elenchi diversi di proposito.
 *
 * [display] decide cosa si vede in questo istante. Nella vista del master guarda
 * chi ha il turno, nemici compresi: sapere cosa vede il mostro che si sta muovendo
 * è metà del mestiere. Nell'anteprima giocatori guarda invece sempre la squadra,
 * tutta insieme — quello schermo è dei giocatori, e mostrarvi il campo visivo del
 * mostro di turno gli regalerebbe il corridoio in cui si trova e, peggio,
 * cancellerebbe dalla mappa i loro stessi personaggi ogni volta che il mostro non
 * li vede.
 *
 * [memory] è chi scrive nell'esplorato, e sono sempre e solo i membri della
 * squadra. Aver visto una stanza non dipende da chi ha l'iniziativa in quel
 * momento, né dal fatto che il master tenga aperta l'anteprima: se ne dipendesse,
 * la stessa partita ricorderebbe cose diverse a seconda di un suo interruttore.
 *
 * Chi è a terra non è un occhio, in nessuno dei due elenchi: un personaggio
 * privo di sensi ha ancora il suo turno — tira i suoi tiri salvezza contro morte —
 * ma non illumina più niente.
 */
data class VisionEyes(val display: List<String>, val memory: List<String>)

fun visionEyes(
    playerPreview: Boolean,
    activeIds: List<String>,
    partyIds: List<String>,
    onTheirFeet: (String) -> Boolean,
): VisionEyes {
    val party = partyIds.filter(onTheirFeet)
    if (playerPreview) return VisionEyes(party, party)
    // Fuori dal combattimento nessuno ha il turno, e chi è a terra ce l'ha ma non
    // vede: in entrambi i casi guarda la squadra, altrimenti una sessione appena
    // aperta nascerebbe nera prima ancora del primo tiro.
    val active = activeIds.filter(onTheirFeet)
    return VisionEyes(active.ifEmpty { party }, party)
}

/**
 * Calcola il campo visivo e ne affida la memoria al Lucido.
 *
 * Il calcolo non è per fotogramma: dipende solo da muri, griglia, posizioni e
 * raggi, quindi si rifà quando una di quelle cambia. `boardRevision` copre i muri
 * e le impostazioni di vista, perché entrambi passano dai comandi annullabili del
 * Lucido; segnare l'esplorato invece non la tocca di proposito, e non innesca un
 * ricalcolo a catena.
 *
 * [memoryViewers] sono gli occhi che ricordano — la squadra — e quasi sempre
 * coincidono con [viewers]: quando succede il campo si calcola una volta sola.
 */
@Composable
fun rememberBoardVision(
    dynamic: Boolean,
    grid: MapGrid,
    walls: WallMask,
    explored: ExploredMask,
    viewers: List<VisionViewer>,
    memoryViewers: List<VisionViewer>,
    boardRevision: Long,
    onExplored: (BooleanArray, Int, Int) -> Unit,
): BoardVisionField {
    val columns = grid.columns()
    val rows = grid.rows()
    val key = remember(dynamic, columns, rows, boardRevision, viewers, memoryViewers) {
        buildString {
            append(dynamic).append('/').append(columns).append('x').append(rows)
            append('/').append(boardRevision)
            appendViewers(viewers)
            append('#')
            appendViewers(memoryViewers)
        }
    }

    val computed = remember(key) {
        if (!dynamic || columns <= 0 || rows <= 0) {
            null
        } else {
            val visible = fieldOf(viewers, walls, columns, rows)
            val remembered = when {
                memoryViewers.isEmpty() -> null
                memoryViewers == viewers -> visible
                else -> fieldOf(memoryViewers, walls, columns, rows)
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

private fun fieldOf(
    viewers: List<VisionViewer>,
    walls: WallMask,
    columns: Int,
    rows: Int,
): BooleanArray {
    val field = VisionField.blank(columns, rows)
    viewers.forEach { viewer -> viewer.addTo(field, walls, columns, rows) }
    return field
}

private fun StringBuilder.appendViewers(viewers: List<VisionViewer>) {
    viewers.forEach {
        append('|').append(it.combatantId)
        append(':').append(it.placement.origin().column()).append(',').append(it.placement.origin().row())
        append(':').append(it.placement.squaresPerSide())
        append(':').append(it.radiusSquares)
    }
}

private fun VisionViewer.addTo(field: BooleanArray, walls: WallMask, columns: Int, rows: Int) {
    placement.occupiedSquares().forEach { square ->
        VisionField.addVisibleFrom(field, walls, columns, rows, square.column(), square.row(), radiusSquares)
    }
}
