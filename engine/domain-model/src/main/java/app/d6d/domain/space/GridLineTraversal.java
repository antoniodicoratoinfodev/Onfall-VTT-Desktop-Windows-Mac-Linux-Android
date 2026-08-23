package app.d6d.domain.space;

import java.util.Objects;

/** Tracciamento condiviso delle linee sulla griglia tattica. */
public final class GridLineTraversal {

    private GridLineTraversal() {
    }

    @FunctionalInterface
    public interface BlockedCell {
        boolean blocked(int column, int row);
    }

    /**
     * Bresenham conservativo: una diagonale attraversa un angolo soltanto quando
     * entrambe le caselle ortogonali sono libere. La destinazione resta visibile
     * o raggiungibile anche quando è bloccata; sono le caselle precedenti a
     * interrompere la linea.
     */
    public static boolean clear(
            int sourceColumn,
            int sourceRow,
            int targetColumn,
            int targetRow,
            BlockedCell blockedCell) {
        Objects.requireNonNull(blockedCell, "blockedCell");
        int x = sourceColumn;
        int y = sourceRow;
        int dx = Math.abs(targetColumn - x);
        int dy = Math.abs(targetRow - y);
        int sx = Integer.compare(targetColumn, x);
        int sy = Integer.compare(targetRow, y);
        int error = dx - dy;
        while (x != targetColumn || y != targetRow) {
            int twice = error * 2;
            boolean moveX = twice > -dy;
            boolean moveY = twice < dx;
            if (moveX && moveY) {
                if (blockedCell.blocked(x + sx, y) || blockedCell.blocked(x, y + sy)) return false;
            }
            if (moveX) { error -= dy; x += sx; }
            if (moveY) { error += dx; y += sy; }
            if (x == targetColumn && y == targetRow) return true;
            if (blockedCell.blocked(x, y)) return false;
        }
        return true;
    }
}
