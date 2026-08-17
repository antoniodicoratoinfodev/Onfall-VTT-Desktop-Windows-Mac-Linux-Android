package app.d6d.ui.maps

import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.ui.i18n.LocalizedText
import kotlin.math.abs

/** Segnaposto ancora da collocare: identita', schieramento e ingombro in caselle. */
data class PendingToken(
    val combatantId: String,
    val party: Boolean,
    val squaresPerSide: Int,
)

/**
 * Dispone due schieramenti contrapposti attorno al centro della griglia.
 *
 * E' la stessa disposizione che la procedura Nuova partita applica in modalita'
 * Combattimento, e per questo vive qui invece che dentro uno dei due schermi:
 * anche «Disponi tutti», al tavolo, deve produrre un fronte riconoscibile e non
 * due file appoggiate ai bordi.
 *
 * Non tocca la sessione e non decide nulla di regolamentare: e' pura geometria,
 * quindi si prova senza motore. Ogni segnaposto viene collocato tenendo conto di
 * quelli gia' sistemati, [occupied] compreso, cosi' il tavolo puo' completare una
 * mappa a meta' senza sovrapposizioni. Restituisce null quando anche un solo
 * segnaposto non trova posto: e' il caso che [gridTooSmallMessage] racconta.
 */
fun arrangeTokens(
    grid: MapGrid,
    tokens: List<PendingToken>,
    occupied: Set<GridPosition> = emptySet(),
): Map<String, GridPosition>? {
    if (!grid.configured()) return null
    val taken = occupied.mapTo(mutableSetOf()) { it.column() to it.row() }
    val middle = grid.columns() / 2
    val centerRow = grid.rows() / 2
    val placements = LinkedHashMap<String, GridPosition>()

    // Gli alleati per primi: occupano la meta' che il tavolo si aspetta di avere
    // davanti, e i nemici si dispongono su quel che resta.
    tokens.sortedBy { !it.party }.forEach { token ->
        val side = token.squaresPerSide
        val anchorColumn = if (token.party) {
            (middle - side - 1).coerceAtLeast(0)
        } else {
            (middle + 1).coerceAtMost(grid.columns() - side)
        }
        val origin = buildList {
            for (row in 0..grid.rows() - side) {
                for (column in 0..grid.columns() - side) {
                    add(GridPosition(column, row))
                }
            }
        }.sortedBy { position ->
            // La meta' sbagliata costa cosi' tanto da diventare l'ultima risorsa;
            // dentro quella giusta contano la colonna di riferimento e poi la
            // vicinanza alla riga centrale.
            val wrongHalf = if (token.party) {
                if (position.column() + side <= middle) 0 else 1_000
            } else {
                if (position.column() >= middle) 0 else 1_000
            }
            wrongHalf + abs(position.column() - anchorColumn) * 4 + abs(position.row() - centerRow)
        }.firstOrNull { position ->
            (position.column() until position.column() + side).all { column ->
                (position.row() until position.row() + side).all { row -> column to row !in taken }
            }
        } ?: return null

        (origin.column() until origin.column() + side).forEach { column ->
            (origin.row() until origin.row() + side).forEach { row -> taken += column to row }
        }
        placements[token.combatantId] = origin
    }
    return placements
}

/** Motivo del rifiuto, con le misure che il tavolo legge sullo schermo. */
fun gridTooSmallMessage(grid: MapGrid): LocalizedText =
    LocalizedText { it.maps.gridTooSmall(grid.columns(), grid.rows()) }
