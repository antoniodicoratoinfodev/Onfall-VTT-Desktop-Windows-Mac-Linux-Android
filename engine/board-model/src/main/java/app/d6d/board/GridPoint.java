package app.d6d.board;

/** Punto continuo in coordinate di casella, mai in pixel. */
public record GridPoint(double x, double y) {
    public GridPoint {
        BoardValidation.coordinate(x, "x");
        BoardValidation.coordinate(y, "y");
    }

    public GridPoint translated(double dx, double dy) {
        return new GridPoint(x + dx, y + dy);
    }
}
