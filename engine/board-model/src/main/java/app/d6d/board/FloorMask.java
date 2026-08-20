package app.d6d.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Caselle di pavimento disegnate sulla mappa; sono sempre attraversabili. */
public final class FloorMask {
    private final int columns;
    private final int rows;
    private final List<Long> words;

    public FloorMask(int columns, int rows, List<Long> words) {
        if (columns < 0 || rows < 0
                || columns > BoardLimits.MAX_FOG_DIMENSION || rows > BoardLimits.MAX_FOG_DIMENSION) {
            throw new IllegalArgumentException("Floor dimensions are outside the supported map limits");
        }
        int wordCount = wordCount(columns, rows);
        List<Long> copy = List.copyOf(Objects.requireNonNull(words, "words"));
        if (copy.size() != wordCount) {
            throw new IllegalArgumentException("Floor bitset has an invalid length");
        }
        if (wordCount > 0) {
            int used = Math.multiplyExact(columns, rows) & 63;
            if (used != 0) {
                long allowed = (1L << used) - 1L;
                if ((copy.get(wordCount - 1) & ~allowed) != 0L) {
                    throw new IllegalArgumentException("Floor bitset contains cells outside the map");
                }
            }
        }
        this.columns = columns;
        this.rows = rows;
        this.words = copy;
    }

    public static FloorMask empty(int columns, int rows) {
        return new FloorMask(columns, rows, java.util.Collections.nCopies(wordCount(columns, rows), 0L));
    }

    public static FloorMask filled(int columns, int rows) {
        int cells = Math.multiplyExact(columns, rows);
        List<Long> words = new ArrayList<>(java.util.Collections.nCopies(wordCount(columns, rows), -1L));
        if (!words.isEmpty() && (cells & 63) != 0) {
            words.set(words.size() - 1, (1L << (cells & 63)) - 1L);
        }
        return new FloorMask(columns, rows, words);
    }

    public int columns() { return columns; }
    public int rows() { return rows; }
    public List<Long> words() { return words; }

    public boolean painted(int column, int row) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) return false;
        int index = row * columns + column;
        return (words.get(index >>> 6) & (1L << (index & 63))) != 0L;
    }

    public FloorMask withCell(int column, int row, boolean painted) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) return this;
        int index = row * columns + column;
        int wordIndex = index >>> 6;
        long bit = 1L << (index & 63);
        long previous = words.get(wordIndex);
        long next = painted ? previous | bit : previous & ~bit;
        if (next == previous) return this;
        List<Long> changed = new ArrayList<>(words);
        changed.set(wordIndex, next);
        return new FloorMask(columns, rows, changed);
    }

    /** Ridimensiona conservando l'intersezione; le nuove caselle non sono disegnate. */
    public FloorMask resized(int nextColumns, int nextRows) {
        if (nextColumns == columns && nextRows == rows) return this;
        FloorMask result = empty(nextColumns, nextRows);
        List<Long> changed = new ArrayList<>(result.words);
        for (int row = 0; row < Math.min(rows, nextRows); row++) {
            for (int column = 0; column < Math.min(columns, nextColumns); column++) {
                if (!painted(column, row)) continue;
                int index = row * nextColumns + column;
                int word = index >>> 6;
                changed.set(word, changed.get(word) | (1L << (index & 63)));
            }
        }
        return new FloorMask(nextColumns, nextRows, changed);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FloorMask mask
                && columns == mask.columns && rows == mask.rows && words.equals(mask.words);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns, rows, words);
    }

    @Override
    public String toString() {
        return "FloorMask[columns=" + columns + ", rows=" + rows + ", words=" + words.size() + "]";
    }

    private static int wordCount(int columns, int rows) {
        int cells = Math.multiplyExact(columns, rows);
        return (cells + 63) >>> 6;
    }
}
