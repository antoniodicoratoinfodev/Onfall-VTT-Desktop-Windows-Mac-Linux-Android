package app.d6d.domain.combat;

import java.util.Objects;

/** remainingOccurrences is decremented at the selected boundary; zero means non-timed. */
public record ConditionDuration(ConditionExpiry expiry, int remainingOccurrences) {
    public ConditionDuration {
        Objects.requireNonNull(expiry, "expiry");
        if (remainingOccurrences < 0) {
            throw new IllegalArgumentException("remainingOccurrences cannot be negative");
        }
        if ((expiry == ConditionExpiry.MANUAL || expiry == ConditionExpiry.CONCENTRATION)
                && remainingOccurrences != 0) {
            throw new IllegalArgumentException("A non-timed duration must have zero occurrences");
        }
        if (expiry != ConditionExpiry.MANUAL && expiry != ConditionExpiry.CONCENTRATION
                && remainingOccurrences == 0) {
            throw new IllegalArgumentException("A timed duration needs at least one occurrence");
        }
    }

    public static ConditionDuration manual() {
        return new ConditionDuration(ConditionExpiry.MANUAL, 0);
    }

    public static ConditionDuration concentration() {
        return new ConditionDuration(ConditionExpiry.CONCENTRATION, 0);
    }

    public static ConditionDuration rounds(int rounds) {
        return new ConditionDuration(ConditionExpiry.END_OF_TARGET_TURN, rounds);
    }

    public static ConditionDuration until(ConditionExpiry expiry, int occurrences) {
        return new ConditionDuration(expiry, occurrences);
    }

    public boolean timed() {
        return expiry != ConditionExpiry.MANUAL && expiry != ConditionExpiry.CONCENTRATION;
    }

    public ConditionDuration decrement() {
        if (!timed()) {
            return this;
        }
        if (remainingOccurrences <= 1) {
            throw new IllegalStateException("The duration expires instead of decrementing past one");
        }
        return new ConditionDuration(expiry, remainingOccurrences - 1);
    }
}
