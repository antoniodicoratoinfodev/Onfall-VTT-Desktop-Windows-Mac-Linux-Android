package app.d6d.domain.combat;

import java.util.List;
import java.util.Objects;

/** Immutable, versioned ability data interpreted by the engine. */
public record AbilityDefinition(
        String id,
        String version,
        String source,
        String rulesetVersion,
        String name,
        ActivationCost activationCost,
        ResolutionMethod resolutionMethod,
        int attackBonus,
        int rangeFeet,
        int maxTargets,
        List<DamageFormula> damage,
        AutomationStatus automationStatus,
        String rulesText) {

    public AbilityDefinition {
        id = requireText(id, "id");
        version = requireText(version, "version");
        source = requireText(source, "source");
        rulesetVersion = requireText(rulesetVersion, "rulesetVersion");
        name = requireText(name, "name");
        Objects.requireNonNull(activationCost, "activationCost");
        Objects.requireNonNull(resolutionMethod, "resolutionMethod");
        if (rangeFeet < 0) {
            throw new IllegalArgumentException("rangeFeet cannot be negative");
        }
        if (maxTargets <= 0) {
            throw new IllegalArgumentException("maxTargets must be positive");
        }
        damage = List.copyOf(Objects.requireNonNull(damage, "damage"));
        Objects.requireNonNull(automationStatus, "automationStatus");
        rulesText = rulesText == null ? "" : rulesText;
        if (resolutionMethod == ResolutionMethod.ATTACK_ROLL && damage.isEmpty()) {
            throw new IllegalArgumentException("An attack needs at least one damage component");
        }
    }

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public static AbilityDefinition attack(
            String id, String name, ActivationCost cost, int attackBonus, DamageFormula... damage) {
        return builder(id, name)
                .activationCost(cost)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackBonus(attackBonus)
                .damage(List.of(damage))
                .build();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private String version = "1";
        private String source = "user";
        private String rulesetVersion = "srd-5.2.1";
        private ActivationCost activationCost = ActivationCost.ACTION;
        private ResolutionMethod resolutionMethod = ResolutionMethod.AUTOMATIC;
        private int attackBonus;
        private int rangeFeet = 5;
        private int maxTargets = 1;
        private List<DamageFormula> damage = List.of();
        private AutomationStatus automationStatus = AutomationStatus.AUTOMATED;
        private String rulesText = "";

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder version(String value) { this.version = value; return this; }
        public Builder source(String value) { this.source = value; return this; }
        public Builder rulesetVersion(String value) { this.rulesetVersion = value; return this; }
        public Builder activationCost(ActivationCost value) { this.activationCost = value; return this; }
        public Builder resolutionMethod(ResolutionMethod value) { this.resolutionMethod = value; return this; }
        public Builder attackBonus(int value) { this.attackBonus = value; return this; }
        public Builder rangeFeet(int value) { this.rangeFeet = value; return this; }
        public Builder maxTargets(int value) { this.maxTargets = value; return this; }
        public Builder damage(List<DamageFormula> value) { this.damage = value; return this; }
        public Builder automationStatus(AutomationStatus value) { this.automationStatus = value; return this; }
        public Builder rulesText(String value) { this.rulesText = value; return this; }

        public AbilityDefinition build() {
            return new AbilityDefinition(id, version, source, rulesetVersion, name, activationCost,
                    resolutionMethod, attackBonus, rangeFeet, maxTargets, damage, automationStatus, rulesText);
        }
    }
}
