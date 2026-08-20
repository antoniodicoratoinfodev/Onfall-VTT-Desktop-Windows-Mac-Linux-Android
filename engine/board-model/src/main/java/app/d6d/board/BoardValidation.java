package app.d6d.board;

import java.util.Objects;

final class BoardValidation {
    private BoardValidation() {
    }

    static String id(String value) {
        String id = Objects.requireNonNull(value, "id").trim();
        if (id.isEmpty() || id.length() > BoardLimits.MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Board object id is empty or too long");
        }
        return id;
    }

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    static double coordinate(double value, String name) {
        finite(value, name);
        if (Math.abs(value) > BoardLimits.MAX_WORLD_COORDINATE) {
            throw new IllegalArgumentException(name + " is outside the supported board world");
        }
        return value;
    }

    static double positive(double value, double maximum, String name) {
        finite(value, name);
        if (value <= 0.0 || value > maximum) {
            throw new IllegalArgumentException(name + " is outside the supported range");
        }
        return value;
    }
}
