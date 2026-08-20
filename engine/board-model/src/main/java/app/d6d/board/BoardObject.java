package app.d6d.board;

/** Oggetto immutabile del Lucido. */
public sealed interface BoardObject permits InkStroke, Measurement, AreaTemplate, Label, StaticStamp, SceneToken {
    String id();

    BoardBounds bounds(int feetPerSquare);

    BoardObject translated(double dx, double dy);
}
