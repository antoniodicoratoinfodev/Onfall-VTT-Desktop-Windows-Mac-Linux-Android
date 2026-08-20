package app.d6d.board;

/** Rettangolo mondo usato da culling e hit test. */
public record BoardBounds(double left, double top, double right, double bottom) {
    public BoardBounds {
        BoardValidation.finite(left, "left");
        BoardValidation.finite(top, "top");
        BoardValidation.finite(right, "right");
        BoardValidation.finite(bottom, "bottom");
        if (right < left || bottom < top) {
            throw new IllegalArgumentException("Invalid board bounds");
        }
    }

    public boolean intersects(BoardBounds other) {
        return other != null && right >= other.left && other.right >= left
                && bottom >= other.top && other.bottom >= top;
    }

    public boolean contains(GridPoint point, double tolerance) {
        return point.x() >= left - tolerance && point.x() <= right + tolerance
                && point.y() >= top - tolerance && point.y() <= bottom + tolerance;
    }

    public BoardBounds expanded(double amount) {
        double safe = Math.max(0.0, BoardValidation.finite(amount, "amount"));
        return new BoardBounds(left - safe, top - safe, right + safe, bottom + safe);
    }

    public static BoardBounds around(GridPoint point) {
        return new BoardBounds(point.x(), point.y(), point.x(), point.y());
    }
}
