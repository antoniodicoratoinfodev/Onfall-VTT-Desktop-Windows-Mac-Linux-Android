package app.d6d.board;

import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.TokenPlacement;

import java.util.Objects;

/**
 * Chi si accorge di chi.
 *
 * <p>È la stessa vista di {@link VisionField}, chiesta però su una sola coppia:
 * invece di illuminare tutta la griglia per poi guardare una casella, misura la
 * distanza e traccia la linea. Serve a decidere se una creatura è ancora
 * inattiva, un conto che si rifà a ogni passo e per ogni nemico — calcolare un
 * campo intero a testa costerebbe la mappa moltiplicata per il bestiario.</p>
 *
 * <p>Le regole sono quelle del campo visivo, non altre: raggio in Chebyshev
 * (la diagonale vale una casella), linea di {@link VisionField#clearLine}, e una
 * creatura Grande guarda da tutte le caselle che occupa ed è vista se una sola
 * delle sue è raggiunta. Raggio zero è cieco: non vede nulla, nemmeno chi gli sta
 * addosso.</p>
 */
public final class Perception {

    private Perception() {
    }

    /**
     * Vero se chi guarda vede il bersaglio.
     *
     * <p>Non è simmetrica: due creature con raggi diversi — la scurovisione di
     * un nano, un cane da guardia in un corridoio buio — si accorgono l'una
     * dell'altra in momenti diversi, ed è esattamente la differenza che
     * l'attivazione deve rispettare.</p>
     */
    public static boolean sees(
            WallMask walls, TokenPlacement viewer, int radiusSquares, TokenPlacement target) {
        Objects.requireNonNull(walls, "walls");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (radiusSquares <= 0) return false;
        for (GridPosition eye : viewer.occupiedSquares()) {
            for (GridPosition seen : target.occupiedSquares()) {
                if (chebyshev(eye, seen) > radiusSquares) continue;
                if (VisionField.clearLine(walls, eye.column(), eye.row(), seen.column(), seen.row())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Distanza sulla griglia: la diagonale vale una casella, come la gittata. */
    private static int chebyshev(GridPosition first, GridPosition second) {
        return Math.max(
                Math.abs(first.column() - second.column()),
                Math.abs(first.row() - second.row()));
    }
}
