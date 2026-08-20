package app.d6d.board;

import java.util.List;
import java.util.Objects;

/** Misura appuntata: conserva le caselle risolte, non gli ID dei token. */
public record Measurement(String id, List<GridPoint> points, int colorArgb) implements BoardObject {
    public Measurement {
        id = BoardValidation.id(id);
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        if (points.size() < 2 || points.size() > BoardLimits.MAX_POINTS_PER_PATH) {
            throw new IllegalArgumentException("A measurement needs a bounded list of points");
        }
    }

    @Override
    public BoardBounds bounds(int feetPerSquare) {
        double left = points.stream().mapToDouble(GridPoint::x).min().orElseThrow();
        double top = points.stream().mapToDouble(GridPoint::y).min().orElseThrow();
        double right = points.stream().mapToDouble(GridPoint::x).max().orElseThrow();
        double bottom = points.stream().mapToDouble(GridPoint::y).max().orElseThrow();
        return new BoardBounds(left, top, right, bottom).expanded(0.15);
    }

    @Override
    public Measurement translated(double dx, double dy) {
        return new Measurement(id, points.stream().map(point -> point.translated(dx, dy)).toList(), colorArgb);
    }
}
