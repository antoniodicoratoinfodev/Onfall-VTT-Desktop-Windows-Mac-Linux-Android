package app.d6d.domain.space;

/**
 * Casella della griglia, indicizzata da zero.
 *
 * <p>La distanza usa la metrica di Chebyshev: sulla griglia una diagonale conta
 * come una casella, quindi il numero di caselle attraversate e' il maggiore fra
 * lo scostamento orizzontale e quello verticale.</p>
 */
public record GridPosition(int column, int row) {

    public GridPosition {
        if (column < 0 || row < 0) {
            throw new IllegalArgumentException("Grid coordinates cannot be negative");
        }
    }

    /** Caselle attraversate per raggiungere l'altra posizione. */
    public int squaresTo(GridPosition other) {
        return Math.max(Math.abs(column - other.column), Math.abs(row - other.row));
    }

    public GridPosition translated(int columnDelta, int rowDelta) {
        return new GridPosition(column + columnDelta, row + rowDelta);
    }

    @Override
    public String toString() {
        return column + "," + row;
    }

    /** Rilegge la forma prodotta da {@link #toString()}. */
    public static GridPosition parse(String value) {
        int comma = value.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Invalid grid position: " + value);
        }
        return new GridPosition(
                Integer.parseInt(value.substring(0, comma).trim()),
                Integer.parseInt(value.substring(comma + 1).trim()));
    }
}
