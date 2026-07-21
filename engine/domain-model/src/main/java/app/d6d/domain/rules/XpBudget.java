package app.d6d.domain.rules;

import java.util.Objects;

/** Low, Moderate and High XP caps, either per character or for a whole party. */
public record XpBudget(long low, long moderate, long high) {

    public XpBudget {
        if (low < 0 || moderate < low || high < moderate) {
            throw new IllegalArgumentException(
                    "XP budgets must be non-negative and ordered Low <= Moderate <= High");
        }
    }

    public long forDifficulty(EncounterDifficulty difficulty) {
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case LOW -> low;
            case MODERATE -> moderate;
            case HIGH -> high;
        };
    }

    public XpBudget plus(XpBudget other) {
        Objects.requireNonNull(other, "other");
        return new XpBudget(
                Math.addExact(low, other.low),
                Math.addExact(moderate, other.moderate),
                Math.addExact(high, other.high));
    }

    public static XpBudget zero() {
        return new XpBudget(0, 0, 0);
    }
}
