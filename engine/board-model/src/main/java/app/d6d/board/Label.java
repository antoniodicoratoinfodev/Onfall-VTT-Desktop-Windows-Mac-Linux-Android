package app.d6d.board;

import java.util.Objects;

/** Cartiglio scritto dall'utente; il testo non viene mai localizzato. */
public record Label(
        String id,
        GridPoint position,
        String text,
        int colorArgb,
        double textSizeSp,
        double rotationDegrees) implements BoardObject {
    public Label {
        id = BoardValidation.id(id);
        position = Objects.requireNonNull(position, "position");
        text = Objects.requireNonNull(text, "text");
        if (text.length() > BoardLimits.MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Board label is too long");
        }
        BoardValidation.positive(textSizeSp, 200.0, "textSizeSp");
        BoardValidation.finite(rotationDegrees, "rotationDegrees");
    }

    @Override
    public BoardBounds bounds(int feetPerSquare) {
        return BoardBounds.around(position);
    }

    @Override
    public Label translated(double dx, double dy) {
        return new Label(id, position.translated(dx, dy), text, colorArgb, textSizeSp, rotationDegrees);
    }
}
