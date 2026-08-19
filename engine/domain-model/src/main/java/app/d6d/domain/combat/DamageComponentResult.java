package app.d6d.domain.combat;

import java.util.Objects;

public record DamageComponentResult(
        DamageType type,
        int rawAmount,
        int adjustedAmount,
        boolean immune,
        boolean resistant,
        boolean vulnerable) {

    public DamageComponentResult {
        Objects.requireNonNull(type, "type");
        if (rawAmount < 0 || adjustedAmount < 0) {
            throw new IllegalArgumentException("Damage values cannot be negative");
        }
    }
}
