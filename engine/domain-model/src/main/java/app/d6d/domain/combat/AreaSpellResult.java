package app.d6d.domain.combat;

import app.d6d.domain.space.GridPosition;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of an area spell: where it landed and what happened to each creature in it.
 *
 * <p>The rolled damage is the pre-defence total the spell produced once; each entry
 * in {@code targets} records that creature's save and the damage it actually took
 * (full, half, or none) after its own defences.</p>
 */
public record AreaSpellResult(
        String casterId,
        String abilityId,
        GridPosition center,
        int radiusFeet,
        int saveDc,
        List<DamageComponent> rolledDamage,
        List<AreaTargetResult> targets) {

    public AreaSpellResult {
        casterId = Objects.requireNonNull(casterId, "casterId");
        abilityId = Objects.requireNonNull(abilityId, "abilityId");
        center = Objects.requireNonNull(center, "center");
        rolledDamage = List.copyOf(Objects.requireNonNull(rolledDamage, "rolledDamage"));
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }
}
