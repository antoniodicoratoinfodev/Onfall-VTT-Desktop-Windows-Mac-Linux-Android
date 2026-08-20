package app.d6d.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Caselle solide della mappa: impediscono movimento e linea d'effetto. */
public final class WallMask {
    private final int columns;
    private final int rows;
    private final List<Long> words;

    public WallMask(int columns, int rows, List<Long> words) {
        if (columns < 0 || rows < 0
                || columns > BoardLimits.MAX_FOG_DIMENSION || rows > BoardLimits.MAX_FOG_DIMENSION) {
            throw new IllegalArgumentException("Wall dimensions are outside the supported map limits");
        }
        int wordCount = wordCount(columns, rows);
        List<Long> copy = List.copyOf(Objects.requireNonNull(words, "words"));
        if (copy.size() != wordCount) {
            throw new IllegalArgumentException("Wall bitset has an invalid length");
        }
        if (wordCount > 0) {
            int used = Math.multiplyExact(columns, rows) & 63;
            if (used != 0) {
                long allowed = (1L << used) - 1L;
                if ((copy.get(wordCount - 1) & ~allowed) != 0L) {
                    throw new IllegalArgumentException("Wall bitset contains cells outside the map");
                }
            }
        }
        this.columns = columns;
        this.rows = rows;
        this.words = copy;
    }

    public static WallMask empty(int columns, int rows) {
        return new WallMask(columns, rows, java.util.Collections.nCopies(wordCount(columns, rows), 0L));
    }

    public int columns() { return columns; }
    public int rows() { return rows; }
    public List<Long> words() { return words; }

    public boolean blocked(int column, int row) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) return false;
        int index = row * columns + column;
        return (words.get(index >>> 6) & (1L << (index & 63))) != 0L;
    }

    public WallMask withCell(int column, int row, boolean blocked) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) return this;
        int index = row * columns + column;
        int wordIndex = index >>> 6;
        long bit = 1L << (index & 63);
        long previous = words.get(wordIndex);
        long next = blocked ? previous | bit : previous & ~bit;
        if (next == previous) return this;
        List<Long> changed = new ArrayList<>(words);
        changed.set(wordIndex, next);
        return new WallMask(columns, rows, changed);
    }

    /** Ridimensiona conservando l'intersezione; le nuove caselle restano libere. */
    public WallMask resized(int nextColumns, int nextRows) {
        if (nextColumns == columns && nextRows == rows) return this;
        if (nextColumns < 0 || nextRows < 0
                || nextColumns > BoardLimits.MAX_FOG_DIMENSION || nextRows > BoardLimits.MAX_FOG_DIMENSION) {
            throw new IllegalArgumentException("Wall dimensions are outside the supported map limits");
        }
        List<Long> result = new ArrayList<>(java.util.Collections.nCopies(wordCount(nextColumns, nextRows), 0L));
        for (int row = 0; row < Math.min(rows, nextRows); row++) {
            for (int column = 0; column < Math.min(columns, nextColumns); column++) {
                if (!blocked(column, row)) continue;
                int index = row * nextColumns + column;
                int word = index >>> 6;
                result.set(word, result.get(word) | (1L << (index & 63)));
            }
        }
        return new WallMask(nextColumns, nextRows, result);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WallMask mask && columns == mask.columns && rows == mask.rows && words.equals(mask.words);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns, rows, words);
    }

    @Override
    public String toString() {
        return "WallMask[columns=" + columns + ", rows=" + rows + ", words=" + words.size() + "]";
    }

    private static int wordCount(int columns, int rows) {
        int cells = Math.multiplyExact(columns, rows);
        return (cells + 63) >>> 6;
    }
}
