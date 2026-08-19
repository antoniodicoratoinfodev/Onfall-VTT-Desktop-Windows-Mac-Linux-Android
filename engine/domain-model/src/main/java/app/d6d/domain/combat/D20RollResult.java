package app.d6d.domain.combat;

import java.util.List;
import java.util.Objects;

public record D20RollResult(
        RollSource source,
        D20Mode mode,
        List<Integer> dice,
        int naturalRoll,
        int modifier,
        int total) {

    public D20RollResult {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mode, "mode");
        dice = List.copyOf(Objects.requireNonNull(dice, "dice"));
        long expectedTotal = (long) naturalRoll + modifier;
        if (dice.isEmpty() || naturalRoll < 1 || naturalRoll > 20
                || expectedTotal < Integer.MIN_VALUE || expectedTotal > Integer.MAX_VALUE
                || total != expectedTotal) {
            throw new IllegalArgumentException("Invalid d20 result");
        }
    }
}
