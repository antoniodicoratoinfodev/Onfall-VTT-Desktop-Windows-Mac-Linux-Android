package app.d6d.board;

import java.util.List;
import java.util.Objects;

/** Tratto a mano libera, semplificato prima del commit. */
public record InkStroke(String id, List<GridPoint> points, int colorArgb, double widthSquares)
        implements BoardObject {
    public InkStroke {
        id = BoardValidation.id(id);
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        if (points.size() < 2 || points.size() > BoardLimits.MAX_POINTS_PER_PATH) {
            throw new IllegalArgumentException("An ink stroke needs a bounded list of points");
        }
        BoardValidation.positive(widthSquares, 20.0, "widthSquares");
    }

    @Override
    public BoardBounds bounds(int feetPerSquare) {
        double left = points.stream().mapToDouble(GridPoint::x).min().orElseThrow();
        double top = points.stream().mapToDouble(GridPoint::y).min().orElseThrow();
        double right = points.stream().mapToDouble(GridPoint::x).max().orElseThrow();
        double bottom = points.stream().mapToDouble(GridPoint::y).max().orElseThrow();
        return new BoardBounds(left, top, right, bottom).expanded(widthSquares / 2.0);
    }

    @Override
    public InkStroke translated(double dx, double dy) {
        return new InkStroke(id, points.stream().map(point -> point.translated(dx, dy)).toList(), colorArgb, widthSquares);
    }
}
