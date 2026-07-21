package app.d6d.domain.combat;

import java.util.List;
import java.util.Objects;

/** Manual damage values are raw per-component totals, before defenses. Empty means roll damage digitally. */
public record AttackRequest(
        String attackerId,
        String targetId,
        String abilityId,
        D20RollInput attackRoll,
        List<Integer> manualDamageValues) {

    public AttackRequest {
        attackerId = requireText(attackerId, "attackerId");
        targetId = requireText(targetId, "targetId");
        abilityId = requireText(abilityId, "abilityId");
        Objects.requireNonNull(attackRoll, "attackRoll");
        manualDamageValues = List.copyOf(Objects.requireNonNull(manualDamageValues, "manualDamageValues"));
        if (manualDamageValues.stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("Manual damage cannot contain null or negative values");
        }
    }

    public static AttackRequest digital(
            String attackerId, String targetId, String abilityId, D20Mode mode) {
        return new AttackRequest(attackerId, targetId, abilityId, D20RollInput.digital(mode), List.of());
    }

    public static AttackRequest manual(
            String attackerId, String targetId, String abilityId, int naturalD20, List<Integer> damageValues) {
        return new AttackRequest(attackerId, targetId, abilityId, D20RollInput.manual(naturalD20), damageValues);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value;
    }
}
