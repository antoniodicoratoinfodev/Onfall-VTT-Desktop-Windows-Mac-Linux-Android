package app.d6d.board;

import java.util.Objects;

public record StaticStamp(
        String id,
        GridPoint position,
        StampKind kind,
        double sizeSquares,
        double rotationDegrees,
        int colorArgb) implements BoardObject {
    public StaticStamp {
        id = BoardValidation.id(id);
        position = Objects.requireNonNull(position, "position");
        kind = Objects.requireNonNull(kind, "kind");
        BoardValidation.positive(sizeSquares, 100.0, "sizeSquares");
        BoardValidation.finite(rotationDegrees, "rotationDegrees");
    }

    @Override
    public BoardBounds bounds(int feetPerSquare) {
        return BoardBounds.around(position).expanded(sizeSquares / 2.0);
    }

    @Override
    public StaticStamp translated(double dx, double dy) {
        return new StaticStamp(id, position.translated(dx, dy), kind, sizeSquares, rotationDegrees, colorArgb);
    }
}
