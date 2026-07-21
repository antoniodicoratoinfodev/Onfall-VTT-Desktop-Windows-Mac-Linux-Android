package app.d6d.domain.space;

import java.util.ArrayList;
import java.util.List;

/**
 * Posizione di un combattente sulla griglia.
 *
 * <p>L'ingombro e' espresso in caselle per lato, non come taglia di creatura: la
 * geometria appartiene al motore, il vocabolario di gioco — Media, Grande,
 * Enorme — appartiene alla scheda. Una creatura Grande occupa due caselle per
 * lato, una Enorme tre, una Mastodontica quattro.</p>
 *
 * <p>{@code origin} e' l'angolo in alto a sinistra dello spazio occupato.</p>
 */
public record TokenPlacement(String combatantId, GridPosition origin, int squaresPerSide) {

    private static final int MAX_SIDE = 8;

    public TokenPlacement {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId cannot be blank");
        }
        if (origin == null) {
            throw new IllegalArgumentException("origin is required");
        }
        if (squaresPerSide < 1 || squaresPerSide > MAX_SIDE) {
            throw new IllegalArgumentException("A token covers between 1 and " + MAX_SIDE + " squares per side");
        }
    }

    public static TokenPlacement single(String combatantId, GridPosition origin) {
        return new TokenPlacement(combatantId, origin, 1);
    }

    /** Tutte le caselle coperte dal segnaposto. */
    public List<GridPosition> occupiedSquares() {
        List<GridPosition> squares = new ArrayList<>(squaresPerSide * squaresPerSide);
        for (int columnOffset = 0; columnOffset < squaresPerSide; columnOffset++) {
            for (int rowOffset = 0; rowOffset < squaresPerSide; rowOffset++) {
                squares.add(origin.translated(columnOffset, rowOffset));
            }
        }
        return List.copyOf(squares);
    }

    public boolean occupies(GridPosition position) {
        int columnDelta = position.column() - origin.column();
        int rowDelta = position.row() - origin.row();
        return columnDelta >= 0 && columnDelta < squaresPerSide
                && rowDelta >= 0 && rowDelta < squaresPerSide;
    }

    /**
     * Caselle che separano due segnaposti, misurate fra i bordi.
     *
     * <p>Si prende la coppia di caselle occupate piu' vicina, cosi' una creatura
     * Grande minaccia con la propria portata da tutto il suo spazio e non dal solo
     * angolo di origine.</p>
     */
    public int squaresTo(TokenPlacement other) {
        int best = Integer.MAX_VALUE;
        for (GridPosition mine : occupiedSquares()) {
            for (GridPosition theirs : other.occupiedSquares()) {
                best = Math.min(best, mine.squaresTo(theirs));
                if (best == 0) return 0;
            }
        }
        return best;
    }

    /** Sposta il segnaposto conservando l'ingombro. */
    public TokenPlacement movedTo(GridPosition destination) {
        return new TokenPlacement(combatantId, destination, squaresPerSide);
    }

    /** Vero se i due segnaposti si sovrappongono. */
    public boolean overlaps(TokenPlacement other) {
        return occupiedSquares().stream().anyMatch(other::occupies);
    }
}
