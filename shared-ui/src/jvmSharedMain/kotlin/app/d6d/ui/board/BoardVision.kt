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
    val presentation: VisionPresentation,
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
        if (bounds.right() <= 0.0 || bounds.bottom() <= 0.0 ||
            bounds.left() >= columns || bounds.top() >= rows
        ) return false
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

    /** In anteprima i contenuti informativi seguono la vista, non il solo velo traslucido. */
    internal fun showsToPlayers(bounds: BoardBounds): Boolean = !active || sees(bounds)

    companion object {
        /** Campo inerte: tutto visibile, per nebbia dipinta, strato spento o resa «Tutto». */
        fun inactive(): BoardVisionField =
            BoardVisionField(false, 0, 0, VisionPresentation.ALL, BooleanArray(0), ExploredMask.empty(0, 0))
    }
}

/**
 * Visita segmenti orizzontali omogenei, omettendo le celle già visibili. Il
 * renderer paga così un rettangolo per segmento, non uno per ogni casella.
 */
internal inline fun BoardVisionField.forEachFogRun(
    firstColumn: Int,
    lastColumnExclusive: Int,
    firstRow: Int,
    lastRowExclusive: Int,
    visit: (row: Int, firstColumn: Int, lastColumnExclusive: Int, tier: VisionTier) -> Unit,
) {
    val startColumn = firstColumn.coerceIn(0, columns)
    val endColumn = lastColumnExclusive.coerceIn(startColumn, columns)
    val startRow = firstRow.coerceIn(0, rows)
    val endRow = lastRowExclusive.coerceIn(startRow, rows)
    for (row in startRow until endRow) {
        var column = startColumn
        while (column < endColumn) {
            val runTier = tier(column, row)
            if (runTier == VisionTier.VISIBLE) {
                column++
                continue
            }
            val runStart = column
            do {
                column++
            } while (column < endColumn && tier(column, row) == runTier)
            visit(row, runStart, column, runTier)
        }
    }
}

/** Un occhio: da dove guarda e quanto lontano. */
data class VisionViewer(
    val combatantId: String,
    val placement: TokenPlacement,
    val radiusSquares: Int,
)

/**
 * Con quali occhi guarda il master. Non tocca mai l'uscita dei giocatori.
 *
 * Nessuna delle due ferma la memoria del gruppo: l'esplorato cresce lo stesso,
 * altrimenti il modo in cui il master guarda cambierebbe ciò che i giocatori
 * ricordano.
 */
enum class MasterLens {
    /** Gli occhi di chi ha il turno, mostri compresi: sapere cosa vede la creatura
     *  che si sta muovendo è metà del mestiere. */
    TURN,

    /** Gli occhi della squadra, gli stessi dei giocatori: risponde a «cosa vedono
     *  adesso i personaggi?» senza dover passare dall'anteprima. */
    PARTY,
}

/**
 * Quanto la nebbia nasconde la mappa. E' un asse distinto dagli occhi usati:
 * master e giocatori possono avere ciascuno una delle stesse tre rese.
 */
enum class VisionPresentation {
    /** Mappa intera, senza velo. */
    ALL,

    /** Vista e memoria; ciò che non è mai stato visto è nero pieno. */
    MEMORY_BLACK,

    /** Vista e memoria; ciò che non è mai stato visto resta leggibile, ma più scuro. */
    MEMORY_DIM,
}

/**
 * Gli occhi della mappa: chi guarda adesso, chi ricorda e come viene reso il velo.
 *
 * Sono elenchi diversi di proposito.
 *
 * [display] decide cosa si vede in questo istante e lo sceglie [MasterLens], ma
 * solo nella vista del master. Nell'anteprima giocatori guarda sempre la squadra,
 * tutta insieme — quello schermo è dei giocatori, e mostrarvi il campo visivo del
 * mostro di turno gli regalerebbe il corridoio in cui si trova e, peggio,
 * cancellerebbe dalla mappa i loro stessi personaggi ogni volta che il mostro non
 * li vede. [presentation] viene invece scelta separatamente per i due schermi.
 *
 * [memory] è chi scrive nell'esplorato, e sono sempre e solo i membri della
 * squadra. Aver visto una stanza non dipende da chi ha l'iniziativa in quel
 * momento, né da come il master ha scelto di guardare: se ne dipendesse, la stessa
 * partita ricorderebbe cose diverse a seconda di un suo interruttore.
 *
 * Con [VisionPresentation.ALL] niente viene nascosto, ma [memory] resta pieno e
 * il gruppo continua a ricordare.
 *
 * Chi è a terra non è un occhio, in nessuno dei due elenchi: un personaggio
 * privo di sensi ha ancora il suo turno — tira i suoi tiri salvezza contro morte —
 * ma non illumina più niente.
 */
data class VisionEyes(
    val display: List<String>,
    val memory: List<String>,
    val presentation: VisionPresentation,
)

fun visionEyes(
    playerPreview: Boolean,
    lens: MasterLens,
    masterPresentation: VisionPresentation,
    playerPresentation: VisionPresentation,
    activeIds: List<String>,
    partyIds: List<String>,
    onTheirFeet: (String) -> Boolean,
): VisionEyes {
    val party = partyIds.filter(onTheirFeet)
    val presentation = if (playerPreview) playerPresentation else masterPresentation
    if (presentation == VisionPresentation.ALL) {
        return VisionEyes(emptyList(), party, presentation)
    }
    if (playerPreview) return VisionEyes(party, party, presentation)
    val display = when (lens) {
        // Fuori dal combattimento nessuno ha il turno, e chi è a terra ce l'ha ma
        // non vede: in entrambi i casi guarda la squadra, altrimenti una sessione
        // appena aperta nascerebbe nera prima ancora del primo tiro.
        MasterLens.TURN -> activeIds.filter(onTheirFeet).ifEmpty { party }
        MasterLens.PARTY -> party
    }
    return VisionEyes(display, party, presentation)
}

/**
 * Durante la preparazione, prima di piazzare il primo PG e prima dell'inizio del
 * combattimento, il master deve poter amministrare la mappa. Non vale per
 * l'anteprima giocatori né per una squadra piazzata ma interamente a terra.
 */
internal fun shouldSuspendDynamicVisionForSetup(
    playerPreview: Boolean,
    activeIds: List<String>,
    hasPlacedPartyMember: Boolean,
): Boolean = !playerPreview && activeIds.isEmpty() && !hasPlacedPartyMember

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
 *
 * Con [VisionPresentation.ALL] non si disegna e non si nasconde niente, ma la
 * memoria del gruppo si calcola e si scrive lo stesso.
 */
@Composable
fun rememberBoardVision(
    dynamic: Boolean,
    presentation: VisionPresentation,
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
    val key = remember(dynamic, presentation, columns, rows, boardRevision, viewers, memoryViewers) {
        buildString {
            append(dynamic).append('/').append(presentation).append('/').append(columns).append('x').append(rows)
            append('/').append(boardRevision)
            appendViewers(viewers)
            append('#')
            appendViewers(memoryViewers)
        }
    }

    val computed = remember(key) {
        if (!dynamic || columns <= 0 || rows <= 0) {
            VisionFields(null, null)
        } else {
            val remembered = if (memoryViewers.isEmpty()) null else fieldOf(memoryViewers, walls, columns, rows)
            // Nessun occhio non è "nessun velo": una squadra tutta a terra non vede
            // niente, ed è un campo vuoto, non un campo assente.
            val visible = when {
                presentation == VisionPresentation.ALL -> null
                remembered != null && memoryViewers == viewers -> remembered
                else -> fieldOf(viewers, walls, columns, rows)
            }
            VisionFields(visible, remembered)
        }
    }

    LaunchedEffect(key) {
        val remembered = computed.remembered ?: return@LaunchedEffect
        onExplored(remembered, columns, rows)
    }

    val visible = computed.visible ?: return BoardVisionField.inactive()
    return remember(key, explored) {
        BoardVisionField(true, columns, rows, presentation, visible, explored.resized(columns, rows))
    }
}

/** Il campo da disegnare e quello da ricordare: il primo manca con la resa «Tutto». */
private class VisionFields(val visible: BooleanArray?, val remembered: BooleanArray?)

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
