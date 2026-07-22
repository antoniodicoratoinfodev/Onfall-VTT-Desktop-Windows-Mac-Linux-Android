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

    // Riferimenti alla griglia, pubblicati dalla mappa quando viene disposta.
    var gridCoordinates: LayoutCoordinates? = null
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
        val local = coords.windowToLocal(window)
        if (local.x < 0f || local.y < 0f) return null
        val column = (local.x / cellPx).toInt()
        val row = (local.y / cellPx).toInt()
        if (column !in 0 until columns || row !in 0 until rows) return null
        return IntOffset(column, row)
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
