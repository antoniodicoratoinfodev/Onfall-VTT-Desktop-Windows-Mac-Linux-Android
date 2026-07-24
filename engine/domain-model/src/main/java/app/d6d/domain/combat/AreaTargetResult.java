package app.d6d.domain.combat;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome for one creature caught in an area spell.
 *
 * <p>{@code saved} is true when the creature succeeded on its saving throw. The
 * roll is absent for abilities without a save (the whole area simply takes the
 * damage), and {@code damage} is absent when the creature took none — a full save
 * against a spell that does nothing on a success.</p>
 */
public record AreaTargetResult(
        String targetId,
        boolean saved,
        Optional<D20RollResult> saveRoll,
        Optional<DamageResult> damage) {

    public AreaTargetResult {
        targetId = Objects.requireNonNull(targetId, "targetId");
        saveRoll = Objects.requireNonNull(saveRoll, "saveRoll");
        damage = Objects.requireNonNull(damage, "damage");
    }
}
