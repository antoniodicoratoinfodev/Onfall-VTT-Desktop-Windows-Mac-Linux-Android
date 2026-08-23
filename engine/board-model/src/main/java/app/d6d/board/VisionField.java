package app.d6d.board;

import app.d6d.domain.space.GridLineTraversal;
import java.util.Objects;

/**
 * Quali caselle un occhio posato su una casella riesce a vedere.
 *
 * <p><b>La linea è la stessa del motore.</b> Vista e linea d'effetto delegano
 * entrambe a {@link GridLineTraversal}: non possono divergere nel caso peggiore
 * «lo vedo ma il motore mi dice che non posso colpirlo».</p>
 *
 * <p>Il raggio si misura in Chebyshev, come la gittata: sulla griglia la diagonale
 * vale una casella, quindi un raggio di dodici caselle è un quadrato di venticinque
 * per venticinque, non un cerchio.</p>
 *
 * <p>La casella occupata da un muro <em>è</em> visibile quando la linea la
 * raggiunge — si vede la parete — ma non si vede nulla oltre. È la stessa
 * asimmetria del motore, che non chiede se la casella di destinazione sia solida.</p>
 *
 * <p>Il campo visivo non si salva: è una funzione di muri, posizione e raggio, e
 * ricalcolarlo costa meno che tenerlo allineato. Quello che si salva è la memoria
 * di ciò che è stato visto, in {@link ExploredMask}.</p>
 */
public final class VisionField {

    private VisionField() {
    }

    /** Campo vuoto della misura della griglia: nessuna casella visibile. */
    public static boolean[] blank(int columns, int rows) {
        if (columns < 0 || rows < 0) {
            throw new IllegalArgumentException("Grid size cannot be negative");
        }
        return new boolean[Math.multiplyExact(columns, rows)];
    }

    /** Ciò che si vede da una sola casella. */
    public static boolean[] visibleFrom(
            WallMask walls, int columns, int rows, int originColumn, int originRow, int radiusSquares) {
        boolean[] field = blank(columns, rows);
        addVisibleFrom(field, walls, columns, rows, originColumn, originRow, radiusSquares);
        return field;
    }

    /**
     * Aggiunge al campo ciò che si vede da un'altra casella.
     *
     * <p>Serve a due cose che sono la stessa: una creatura Grande guarda da tutte le
     * caselle che occupa, e la vista del gruppo è l'unione di quelle dei suoi
     * membri. In entrambi i casi basta chiamarla una volta per origine.</p>
     */
    public static void addVisibleFrom(
            boolean[] field,
            WallMask walls,
            int columns,
            int rows,
            int originColumn,
            int originRow,
            int radiusSquares) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(walls, "walls");
        if (field.length != Math.multiplyExact(columns, rows)) {
            throw new IllegalArgumentException("Vision field does not match the grid size");
        }
        if (columns <= 0 || rows <= 0) return;
        if (originColumn < 0 || originRow < 0 || originColumn >= columns || originRow >= rows) return;
        // Zero è il valore esplicito di «cieco»: non vede neppure la casella che
        // occupa e, soprattutto, muovendosi non scrive una scia nella memoria.
        if (radiusSquares <= 0) return;

        // L'occhio vede sempre la propria casella, muro o no: è dove si trova.
        field[originRow * columns + originColumn] = true;

        int firstColumn = Math.max(0, originColumn - radiusSquares);
        int lastColumn = Math.min(columns - 1, originColumn + radiusSquares);
        int firstRow = Math.max(0, originRow - radiusSquares);
        int lastRow = Math.min(rows - 1, originRow + radiusSquares);
        for (int row = firstRow; row <= lastRow; row++) {
            for (int column = firstColumn; column <= lastColumn; column++) {
                int index = row * columns + column;
                // Già vista da un'altra origine: la linea non cambierebbe la risposta.
                if (field[index]) continue;
                if (clearLine(walls, originColumn, originRow, column, row)) field[index] = true;
            }
        }
    }

    /** Bresenham conservativo condiviso con la linea d'effetto del combattimento. */
    public static boolean clearLine(
            WallMask walls, int sourceColumn, int sourceRow, int targetColumn, int targetRow) {
        Objects.requireNonNull(walls, "walls");
        return GridLineTraversal.clear(
                sourceColumn, sourceRow, targetColumn, targetRow, walls::blocked);
    }

    /** Converte una distanza del regolamento nel raggio in caselle, arrotondando per difetto. */
    public static int radiusSquares(int radiusFeet, int feetPerSquare) {
        if (feetPerSquare <= 0 || radiusFeet <= 0) return 0;
        return radiusFeet / feetPerSquare;
    }
}
