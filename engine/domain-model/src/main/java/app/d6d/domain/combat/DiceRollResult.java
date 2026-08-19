package app.d6d.domain.combat;

import java.util.List;
import java.util.Objects;

public record DiceRollResult(List<Integer> dice, int modifier, int total) {
    public DiceRollResult {
        dice = List.copyOf(Objects.requireNonNull(dice, "dice"));
        long expected = modifier;
        for (Integer value : dice) {
            expected = Math.addExact(expected, Objects.requireNonNull(value, "dice contains null"));
        }
        if (dice.isEmpty() || expected != total) {
            throw new IllegalArgumentException("Invalid dice total");
        }
    }
}
