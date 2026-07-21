package app.d6d.domain.space;

/**
 * Dimensioni e scala della griglia.
 *
 * <p>Una casella vale normalmente cinque piedi, ma la scala e' configurabile: una
 * scaramuccia in una stanza e una battaglia campale non si rappresentano bene con
 * lo stesso passo. Cambiare {@code feetPerSquare} cambia solo la conversione fra
 * caselle e distanze, non le regole.</p>
 */
public record MapGrid(int columns, int rows, int feetPerSquare) {

    /** Griglia assente: l'incontro si gioca senza mappa, in modalita' astratta. */
    public static final MapGrid NONE = new MapGrid(0, 0, 5);

    /** Passo standard del regolamento: una casella, cinque piedi. */
    public static final int STANDARD_FEET_PER_SQUARE = 5;

    private static final int MAX_SIDE = 400;

    public MapGrid {
        if (columns < 0 || rows < 0) {
            throw new IllegalArgumentException("Grid size cannot be negative");
        }
        if (columns > MAX_SIDE || rows > MAX_SIDE) {
            throw new IllegalArgumentException("Grid side exceeds the supported limit of " + MAX_SIDE);
        }
        if (feetPerSquare <= 0) {
            throw new IllegalArgumentException("A square must cover a positive distance");
        }
        if ((columns == 0) != (rows == 0)) {
            throw new IllegalArgumentException("A grid needs both dimensions, or neither");
        }
    }

    public static MapGrid standard(int columns, int rows) {
        return new MapGrid(columns, rows, STANDARD_FEET_PER_SQUARE);
    }

    /** Falso quando non e' stata configurata alcuna mappa. */
    public boolean configured() {
        return columns > 0 && rows > 0;
    }

    public boolean contains(GridPosition position) {
        return position.column() < columns && position.row() < rows;
    }

    /** Piedi coperti da un numero di caselle. */
    public int feetFor(int squares) {
        return squares * feetPerSquare;
    }

    /** Caselle necessarie a coprire una distanza, arrotondate per eccesso. */
    public int squaresFor(int feet) {
        return (feet + feetPerSquare - 1) / feetPerSquare;
    }
}
