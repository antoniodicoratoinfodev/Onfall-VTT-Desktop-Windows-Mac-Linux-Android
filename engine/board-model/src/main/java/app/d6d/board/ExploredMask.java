package app.d6d.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Memoria di ciò che il party ha già visto almeno una volta.
 *
 * <p>Un bit acceso significa "esplorata", non "coperta": è l'opposto di
 * {@link FogMask}, e i due non vanno confusi. La nebbia dinamica ne ha bisogno
 * perché ciò che si è visto non torna nero quando si esce dalla stanza — resta in
 * penombra, e la penombra è una cosa che si ricorda, quindi si salva.</p>
 *
 * <p>Solo la vista dei membri del party ci scrive dentro. Se ci scrivessero anche
 * i mostri, un nemico che attraversa un corridoio mai visitato lo regalerebbe ai
 * giocatori senza che nessuno di loro ci sia mai entrato.</p>
 */
public final class ExploredMask {
    private final int columns;
    private final int rows;
    private final List<Long> words;

    public ExploredMask(int columns, int rows, List<Long> words) {
        if (columns < 0 || rows < 0
                || columns > BoardLimits.MAX_FOG_DIMENSION || rows > BoardLimits.MAX_FOG_DIMENSION) {
            throw new IllegalArgumentException("Explored dimensions are outside the supported map limits");
        }
        int wordCount = wordCount(columns, rows);
        List<Long> copy = List.copyOf(Objects.requireNonNull(words, "words"));
        if (copy.size() != wordCount) {
            throw new IllegalArgumentException("Explored bitset has an invalid length");
        }
        if (wordCount > 0) {
            int used = Math.multiplyExact(columns, rows) & 63;
            if (used != 0) {
                long allowed = (1L << used) - 1L;
                if ((copy.get(wordCount - 1) & ~allowed) != 0L) {
                    throw new IllegalArgumentException("Explored bitset contains cells outside the map");
                }
            }
        }
        this.columns = columns;
        this.rows = rows;
        this.words = copy;
    }

    public static ExploredMask empty(int columns, int rows) {
        return new ExploredMask(columns, rows, Collections.nCopies(wordCount(columns, rows), 0L));
    }

    public int columns() { return columns; }
    public int rows() { return rows; }
    public List<Long> words() { return words; }

    public boolean seen(int column, int row) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) return false;
        int index = row * columns + column;
        return (words.get(index >>> 6) & (1L << (index & 63))) != 0L;
    }

    public ExploredMask withCell(int column, int row, boolean seen) {
        if (column < 0 || row < 0 || column >= columns || row >= rows) return this;
        int index = row * columns + column;
        int wordIndex = index >>> 6;
        long bit = 1L << (index & 63);
        long previous = words.get(wordIndex);
        long next = seen ? previous | bit : previous & ~bit;
        if (next == previous) return this;
        List<Long> changed = new ArrayList<>(words);
        changed.set(wordIndex, next);
        return new ExploredMask(columns, rows, changed);
    }

    /**
     * Aggiunge in un colpo solo tutte le caselle di un campo visivo.
     *
     * <p>Casella per casella sarebbe una copia della lista di parole per ogni bit:
     * un turno che scopre trecento caselle pagherebbe trecento copie dell'intera
     * maschera. Qui la copia è una sola, e se non cambia nulla non se ne fa nessuna
     * — la memoria è quasi sempre già a posto, e restituire lo stesso oggetto
     * risparmia al Lucido una notifica di modifica inutile.</p>
     *
     * @param visible campo visivo lungo {@code columns * rows}, come lo produce
     *                {@link VisionField#visibleFrom}
     */
    public ExploredMask withVisible(boolean[] visible) {
        Objects.requireNonNull(visible, "visible");
        int cells = Math.multiplyExact(columns, rows);
        if (visible.length != cells) {
            throw new IllegalArgumentException("Vision field does not match the explored mask size");
        }
        long[] next = null;
        for (int index = 0; index < cells; index++) {
            if (!visible[index]) continue;
            int wordIndex = index >>> 6;
            long bit = 1L << (index & 63);
            long current = next != null ? next[wordIndex] : words.get(wordIndex);
            if ((current & bit) != 0L) continue;
            if (next == null) {
                next = new long[words.size()];
                for (int word = 0; word < next.length; word++) next[word] = words.get(word);
            }
            next[wordIndex] |= bit;
        }
        if (next == null) return this;
        List<Long> boxed = new ArrayList<>(next.length);
        for (long word : next) boxed.add(word);
        return new ExploredMask(columns, rows, boxed);
    }

    /** Ridimensiona conservando l'intersezione; le nuove caselle nascono inesplorate. */
    public ExploredMask resized(int nextColumns, int nextRows) {
        if (nextColumns == columns && nextRows == rows) return this;
        if (nextColumns < 0 || nextRows < 0
                || nextColumns > BoardLimits.MAX_FOG_DIMENSION || nextRows > BoardLimits.MAX_FOG_DIMENSION) {
            throw new IllegalArgumentException("Explored dimensions are outside the supported map limits");
        }
        List<Long> result = new ArrayList<>(Collections.nCopies(wordCount(nextColumns, nextRows), 0L));
        for (int row = 0; row < Math.min(rows, nextRows); row++) {
            for (int column = 0; column < Math.min(columns, nextColumns); column++) {
                if (!seen(column, row)) continue;
                int index = row * nextColumns + column;
                int word = index >>> 6;
                result.set(word, result.get(word) | (1L << (index & 63)));
            }
        }
        return new ExploredMask(nextColumns, nextRows, result);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExploredMask mask
                && columns == mask.columns && rows == mask.rows && words.equals(mask.words);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns, rows, words);
    }

    @Override
    public String toString() {
        return "ExploredMask[columns=" + columns + ", rows=" + rows + ", words=" + words.size() + "]";
    }

    private static int wordCount(int columns, int rows) {
        int cells = Math.multiplyExact(columns, rows);
        return (cells + 63) >>> 6;
    }
}
