package app.d6d.domain.combat;

import java.util.Objects;

public record ConcentrationCheckResult(int difficultyClass, D20RollResult roll, boolean maintained) {
    public ConcentrationCheckResult {
        if (difficultyClass < 1 || difficultyClass > 30) {
            throw new IllegalArgumentException("Concentration DC must be between 1 and 30");
        }
        Objects.requireNonNull(roll, "roll");
    }
}
