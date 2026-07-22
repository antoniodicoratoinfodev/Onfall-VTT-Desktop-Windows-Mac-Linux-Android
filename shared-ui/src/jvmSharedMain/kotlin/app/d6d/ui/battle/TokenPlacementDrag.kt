package app.d6d.ui.battle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntOffset

/**
 * Coordina il trascinamento di un personaggio dalle barre laterali fino alla mappa.
 *
 * Le barre (squadra e nemici) avviano il trascinamento e ne aggiornano la
 * posizione; la mappa pubblica qui la propria griglia (coordinate, dimensione
 * della casella e limiti) cosi' che il punto di rilascio si possa tradurre in una
 * casella. Nessuna regola vive qui: la collocazione vera passa dal motore, che
 * rifiuta le caselle gia' occupate evitando i duplicati.
 */
class TokenPlacementDrag {
    /** Identificatore del combattente trascinato; `null` quando non si trascina. */
    var activeId by mutableStateOf<String?>(null)
        private set
    var isParty by mutableStateOf(false)
        private set

    /** Posizione corrente del puntatore, in coordinate della finestra. */
    var windowPosition by mutableStateOf(Offset.Zero)
        private set

    // Riferimenti al viewport e alla trasformazione della griglia, pubblicati dalla
    // mappa quando viene disposta. La griglia e' un mondo virtuale: non esiste piu'
    // un enorme nodo di layout da usare come sistema di coordinate.
    var gridCoordinates: LayoutCoordinates? = null
    var gridOriginPx: Offset = Offset.Zero
    var cellPx: Float = 0f
    var columns: Int = 0
    var rows: Int = 0

    /** Casella attualmente sotto il puntatore, se dentro la griglia. */
    val overCell: IntOffset?
        get() = cellAt(windowPosition)

    fun start(id: String, party: Boolean, window: Offset) {
        activeId = id
        isParty = party
        windowPosition = window
    }

    fun update(window: Offset) {
        windowPosition = window
    }

    private fun cellAt(window: Offset): IntOffset? {
        val coords = gridCoordinates ?: return null
        if (!coords.isAttached || cellPx <= 0f) return null
        val viewportPoint = coords.windowToLocal(window)
        if (
            viewportPoint.x < 0f || viewportPoint.y < 0f ||
            viewportPoint.x >= coords.size.width || viewportPoint.y >= coords.size.height
        ) return null
        return mapCellAt(viewportPoint, gridOriginPx, cellPx, columns, rows)
    }

    /** Chiude il trascinamento e restituisce la casella di rilascio, se valida. */
    fun drop(): IntOffset? {
        val cell = cellAt(windowPosition)
        activeId = null
        return cell
    }

    fun cancel() {
        activeId = null
    }
}
