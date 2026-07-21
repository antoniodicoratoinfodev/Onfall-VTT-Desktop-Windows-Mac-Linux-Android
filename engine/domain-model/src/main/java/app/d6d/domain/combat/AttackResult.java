package app.d6d.domain.combat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AttackResult(
        String attackerId,
        String targetId,
        String abilityId,
        D20RollResult attackRoll,
        AttackOutcome outcome,
        List<DamageComponent> rolledDamage,
        Optional<DamageResult> damageResult) {

    public AttackResult {
        Objects.requireNonNull(attackRoll, "attackRoll");
        Objects.requireNonNull(outcome, "outcome");
        rolledDamage = List.copyOf(Objects.requireNonNull(rolledDamage, "rolledDamage"));
        damageResult = Objects.requireNonNull(damageResult, "damageResult");
    }
}
