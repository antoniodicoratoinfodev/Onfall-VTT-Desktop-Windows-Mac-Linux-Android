package app.d6d.domain.combat;

import java.util.Objects;

/** A resolved raw damage component, before the target's defenses. */
public record DamageComponent(DamageType type, int amount) {
    public DamageComponent {
        Objects.requireNonNull(type, "type");
        if (amount < 0) throw new IllegalArgumentException("Damage cannot be negative");
    }
}
