package app.d6d.ui.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.board.BoardDocument
import app.d6d.board.BoardLayers
import app.d6d.board.BoardLimits
import app.d6d.board.BoardObject
import app.d6d.board.FogMask
import app.d6d.board.FloorMask
import app.d6d.board.InkStroke
import app.d6d.board.Measurement
import app.d6d.board.WallMask

/**
 * Proprietario del Lucido di una singola sessione.
 *
 * Il draft dei gesti non entra qui: ogni chiamata a [commit] rappresenta una sola
 * operazione logica e produce un solo passo Undo e un solo incremento di revisione.
 */
class BoardController(
    initial: BoardDocument = BoardDocument.empty(),
    private val historyLimit: Int = 30,
    private val onDocumentChanged: (BoardDocument) -> Unit = {},
) {
    var document by mutableStateOf(initial)
        private set

    var revision by mutableLongStateOf(0L)
        private set

    private val undo = ArrayDeque<BoardDocument>()
    private val redo = ArrayDeque<BoardDocument>()

    init {
        onDocumentChanged(document)
    }

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun commit(next: BoardDocument): Boolean {
        if (next == document) return false
        undo.addLast(document)
        while (undo.size > historyLimit.coerceAtLeast(1)) undo.removeFirst()
        redo.clear()
        document = next
        revision++
        onDocumentChanged(document)
        return true
    }

    fun add(value: BoardObject): Boolean {
        if (document.objects().size >= BoardLimits.MAX_OBJECTS) return false
        val currentPoints = document.objects().sumOf { it.pathPointCount() }
        if (currentPoints + value.pathPointCount() > BoardLimits.MAX_TOTAL_POINTS) return false
        return commit(document.withObjects(document.objects() + value))
    }

    fun replace(value: BoardObject): Boolean {
        val index = document.objects().indexOfFirst { it.id() == value.id() }
        if (index < 0) return false
        val changed = document.objects().toMutableList().apply { this[index] = value }
        return commit(document.withObjects(changed))
    }

    fun remove(id: String): Boolean {
        val changed = document.objects().filterNot { it.id() == id }
        return if (changed.size == document.objects().size) false else commit(document.withObjects(changed))
    }

    /**
     * Consuma definitivamente un oggetto trasferito fuori dal Lucido.
     *
     * Il loot entra in una scheda posseduta da un altro archivio. Una normale
     * rimozione con Undo potrebbe quindi ricreare la pedina senza togliere
     * l'oggetto dall'inventario. Riscriviamo le fotografie precedenti senza
     * quell'ID e invalidiamo Redo: gli altri comandi restano annullabili, il loot
     * già raccolto no.
     */
    fun consume(id: String): Boolean {
        if (document.objects().none { it.id() == id }) return false
        val consumed = document.without(id)
        val safeUndo = undo
            .map { it.without(id) }
            .fold(mutableListOf<BoardDocument>()) { snapshots, candidate ->
                if (snapshots.lastOrNull() != candidate) snapshots += candidate
                snapshots
            }
            .apply {
                // Se la pedina era l'unica modifica, la fotografia precedente
                // coincide ora con lo stato corrente: non deve produrre un Undo
                // che dichiara successo senza cambiare nulla.
                while (lastOrNull() == consumed) removeLast()
            }
        document = consumed
        undo.clear()
        undo.addAll(safeUndo)
        redo.clear()
        revision++
        onDocumentChanged(document)
        return true
    }

    fun setLayers(value: BoardLayers): Boolean = commit(document.withLayers(value))

    /**
     * Accende il livello che lo strumento appena scelto disegna.
     *
     * Non passa da [commit] di proposito. Scegliere un pennello non e' un tratto:
     * non deve occupare un passo di Undo — «annulla» subito dopo deve annullare
     * l'ultimo disegno, non la scelta dello strumento — e non deve incrementare
     * [revision], che e' cio' da cui la sessione capisce di avere modifiche da
     * salvare. Il documento cambia comunque, quindi la mappa si ridisegna subito;
     * se poi arriva una modifica vera, il livello acceso viaggia con quella.
     */
    fun revealLayers(value: BoardLayers) {
        if (value == document.layers()) return
        document = document.withLayers(value)
        onDocumentChanged(document)
    }

    fun setFog(value: FogMask): Boolean = commit(document.withFog(value))

    fun setWalls(value: WallMask): Boolean = commit(document.withWalls(value))

    /** Un gesto Floor può anche riaprire le stesse caselle nei Walls, restando un singolo Undo. */
    fun setFloors(value: FloorMask, openedWalls: WallMask? = null): Boolean =
        commit(document.withFloors(value).let { next -> openedWalls?.let(next::withWalls) ?: next })

    fun undo(): Boolean {
        if (undo.isEmpty()) return false
        redo.addLast(document)
        document = undo.removeLast()
        revision++
        onDocumentChanged(document)
        return true
    }

    fun redo(): Boolean {
        if (redo.isEmpty()) return false
        undo.addLast(document)
        document = redo.removeLast()
        revision++
        onDocumentChanged(document)
        return true
    }

    /** Sostituzione da load/recovery: non è un comando modificabile dall'utente. */
    fun adopt(value: BoardDocument) {
        document = value
        undo.clear()
        redo.clear()
        revision++
        onDocumentChanged(document)
    }
}

private fun BoardDocument.without(id: String): BoardDocument =
    withObjects(objects().filterNot { it.id() == id })

private fun BoardObject.pathPointCount(): Int = when (this) {
    is InkStroke -> points().size
    is Measurement -> points().size
    else -> 0
}
