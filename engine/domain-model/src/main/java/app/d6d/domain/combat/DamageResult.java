package app.d6d.domain.combat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DamageResult(
        String sourceCombatantId,
        String targetCombatantId,
        List<DamageComponentResult> components,
        int totalRawDamage,
        int totalAdjustedDamage,
        int temporaryHitPointsAbsorbed,
        int hitPointsLost,
        int targetHitPointsAfter,
        boolean critical,
        Optional<ConcentrationCheckResult> concentrationCheck) {

    public DamageResult {
        sourceCombatantId = sourceCombatantId == null ? "" : sourceCombatantId;
        if (targetCombatantId == null || targetCombatantId.isBlank()) {
            throw new IllegalArgumentException("targetCombatantId cannot be blank");
        }
        components = List.copyOf(Objects.requireNonNull(components, "components"));
        if (totalRawDamage < 0 || totalAdjustedDamage < 0 || temporaryHitPointsAbsorbed < 0
                || hitPointsLost < 0 || targetHitPointsAfter < 0) {
            throw new IllegalArgumentException("Damage result values cannot be negative");
        }
        concentrationCheck = Objects.requireNonNull(concentrationCheck, "concentrationCheck");
    }
}
