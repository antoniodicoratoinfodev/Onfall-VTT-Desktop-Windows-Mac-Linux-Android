package app.d6d.domain.combat;

import java.util.Objects;

/** Exactly one of dice or fixedAmount is populated. */
public record DamageFormula(DamageType type, DiceExpression dice, Integer fixedAmount) {
    public DamageFormula {
        Objects.requireNonNull(type, "type");
        if ((dice == null) == (fixedAmount == null)) {
            throw new IllegalArgumentException("Specify either dice or a fixed amount");
        }
        if (fixedAmount != null && fixedAmount < 0) {
            throw new IllegalArgumentException("Fixed damage cannot be negative");
        }
    }

    public static DamageFormula dice(DamageType type, int count, int sides, int modifier) {
        return new DamageFormula(type, new DiceExpression(count, sides, modifier), null);
    }

    public static DamageFormula fixed(DamageType type, int amount) {
        return new DamageFormula(type, null, amount);
    }

    public boolean usesDice() {
        return dice != null;
    }
}
