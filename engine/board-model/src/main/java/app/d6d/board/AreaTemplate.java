package app.d6d.board;

import java.util.Objects;

/** Sagoma illustrativa; dimensioni in piedi come il motore. */
public record AreaTemplate(
        String id,
        TemplateShape shape,
        GridPoint anchor,
        GridPoint end,
        double sizeFeet,
        double widthFeet,
        double rotationDegrees,
        int colorArgb) implements BoardObject {
    public AreaTemplate {
        id = BoardValidation.id(id);
        shape = Objects.requireNonNull(shape, "shape");
        anchor = Objects.requireNonNull(anchor, "anchor");
        end = Objects.requireNonNull(end, "end");
        BoardValidation.positive(sizeFeet, 50_000.0, "sizeFeet");
        BoardValidation.finite(widthFeet, "widthFeet");
        if (widthFeet < 0.0 || widthFeet > 50_000.0) {
            throw new IllegalArgumentException("widthFeet is outside the supported range");
        }
        BoardValidation.finite(rotationDegrees, "rotationDegrees");
    }

    @Override
    public BoardBounds bounds(int feetPerSquare) {
        double step = Math.max(1, feetPerSquare);
        double extent = Math.max(sizeFeet, widthFeet) / step;
        double left = Math.min(anchor.x(), end.x()) - extent;
        double top = Math.min(anchor.y(), end.y()) - extent;
        double right = Math.max(anchor.x(), end.x()) + extent;
        double bottom = Math.max(anchor.y(), end.y()) + extent;
        return new BoardBounds(left, top, right, bottom);
    }

    @Override
    public AreaTemplate translated(double dx, double dy) {
        return new AreaTemplate(id, shape, anchor.translated(dx, dy), end.translated(dx, dy),
                sizeFeet, widthFeet, rotationDegrees, colorArgb);
    }
}
