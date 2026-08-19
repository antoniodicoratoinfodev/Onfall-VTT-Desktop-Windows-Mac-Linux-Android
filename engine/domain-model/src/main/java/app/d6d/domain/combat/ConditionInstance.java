package app.d6d.domain.combat;

import java.util.Objects;

/** Separate instances preserve source and expiry even for duplicate condition types. */
public record ConditionInstance(
        String id,
        ConditionType type,
        String sourceCombatantId,
        String sourceAbilityId,
        int appliedRound,
        ConditionDuration duration,
        String concentrationOwnerId,
        String note) {

    public ConditionInstance {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        Objects.requireNonNull(type, "type");
        if (sourceCombatantId == null || sourceCombatantId.isBlank()) {
            throw new IllegalArgumentException("sourceCombatantId cannot be blank");
        }
        sourceAbilityId = sourceAbilityId == null ? "" : sourceAbilityId;
        if (appliedRound < 0) {
            throw new IllegalArgumentException("appliedRound cannot be negative");
        }
        Objects.requireNonNull(duration, "duration");
        concentrationOwnerId = concentrationOwnerId == null ? "" : concentrationOwnerId;
        note = note == null ? "" : note;
        if (duration.expiry() == ConditionExpiry.CONCENTRATION && concentrationOwnerId.isBlank()) {
            throw new IllegalArgumentException("A concentration duration needs an owner");
        }
    }

    public static ConditionInstance manual(
            String id, ConditionType type, String sourceCombatantId, String sourceAbilityId, int appliedRound) {
        return new ConditionInstance(id, type, sourceCombatantId, sourceAbilityId, appliedRound,
                ConditionDuration.manual(), "", "");
    }

    public ConditionInstance withDuration(ConditionDuration newDuration) {
        return new ConditionInstance(id, type, sourceCombatantId, sourceAbilityId, appliedRound,
                newDuration, concentrationOwnerId, note);
    }
}
