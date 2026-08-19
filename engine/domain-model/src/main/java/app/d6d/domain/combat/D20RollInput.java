package app.d6d.domain.combat;

import java.util.Objects;

/** Chooses whether a d20 is generated or supplied by the table. */
public record D20RollInput(RollSource source, D20Mode mode, Integer manualNaturalRoll) {
    public D20RollInput {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mode, "mode");
        if (source == RollSource.MANUAL) {
            if (manualNaturalRoll == null || manualNaturalRoll < 1 || manualNaturalRoll > 20) {
                throw new IllegalArgumentException("A manual d20 must be between 1 and 20");
            }
        } else if (manualNaturalRoll != null) {
            throw new IllegalArgumentException("Digital rolls cannot carry a manual result");
        }
    }

    public static D20RollInput digital(D20Mode mode) {
        return new D20RollInput(RollSource.DIGITAL, mode, null);
    }

    public static D20RollInput digital() {
        return digital(D20Mode.NORMAL);
    }

    public static D20RollInput manual(int naturalRoll) {
        return new D20RollInput(RollSource.MANUAL, D20Mode.NORMAL, naturalRoll);
    }
}
